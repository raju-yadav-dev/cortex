package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.Message;
import com.example.chatbot.service.ChatService;
import com.example.chatbot.service.ExportService;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.control.Tooltip;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ChatController {
    // ================= CHAT VIEW NODES =================
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox messageBox;
    @FXML
    private TextArea inputArea;
    @FXML
    private HBox exportToastBanner;
    @FXML
    private Label exportToastLabel;
    @FXML
    private Button exportToastCloseButton;
    @FXML
    private Button sendButton;
    @FXML
    private HBox inputShell;
    @FXML
    private MenuButton exportButton;
    @FXML
    private SplitPane chatSplitPane;
    @FXML
    private Button terminalToggleButton;
    @FXML
    private VBox terminalPane;
    @FXML
    private Label terminalStatusLabel;
    @FXML
    private TextArea terminalOutputArea;
    @FXML
    private Button terminalClearButton;
    @FXML
    private Button terminalCloseButton;
    @FXML
    private Button terminalDockLeftButton;
    @FXML
    private Button terminalDockRightButton;
    @FXML
    private Button terminalDockBottomButton;
    @FXML
    private Button terminalDockTopButton;

    // ================= STATE =================
    private Conversation conversation;
    private ChatService chatService = new ChatService();
    private Runnable onConversationUpdated;
    private CompletableFuture<Message> inFlightRequest;
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\R([\\s\\S]*?)```");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.*)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^[-*]\\s+(.*)$");
    private static final Pattern INLINE_MARKDOWN_PATTERN = Pattern.compile("(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\*([^*]+)\\*)");
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final double TERMINAL_MIN_WIDTH = 280;
    private static final double TERMINAL_DEFAULT_WIDTH = 360;
    private static final double TERMINAL_MAX_WIDTH = 520;
    private static final double TERMINAL_WINDOWED_WIDTH_BOOST = 10;
    private static final double TERMINAL_MIN_HEIGHT = 180;
    private static final double TERMINAL_DEFAULT_HEIGHT = 240;
    private static final double TERMINAL_MAX_HEIGHT = 420;
    private static final String TERMINAL_PROMPT_SUFFIX = "> ";
    private final BooleanProperty waitingForResponse = new SimpleBooleanProperty(false);
    private final Map<String, Boolean> runtimeAvailabilityCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> commandAvailabilityCache = new ConcurrentHashMap<>();
    private PauseTransition exportToastTimer;
    private volatile RunningExecution activeExecution;
    private Path terminalWorkingDirectory = Path.of(System.getProperty("user.home"));
    private int terminalInputStartIndex;
    private TerminalDockPosition terminalDockPosition = TerminalDockPosition.RIGHT;
    private Timeline generatingIndicatorTimeline;
    private Timeline responseTypingTimeline;
    private HBox pendingBotBubbleRow;
    private Label pendingBotBubbleLabel;

    private enum TerminalDockPosition {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP
    }

    // ================= INITIALIZATION =================
    @FXML
    public void initialize() {
        // ---- Disable Send For Empty Input ----
        sendButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> waitingForResponse.get() || inputArea.getText() == null || inputArea.getText().trim().isEmpty(),
                        inputArea.textProperty(),
                        waitingForResponse
                )
        );

        // ---- Send Actions ----
        sendButton.setOnAction(e -> sendMessage());

        // ---- Enter To Send, Shift+Enter For Newline ----
        inputArea.setWrapText(true);
        Tooltip inputTooltip = new Tooltip("Enter to send\nShift+Enter for new line\nCtrl+C to copy selected text");
        inputTooltip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(inputArea, inputTooltip);
        
        inputArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    int pos = inputArea.getCaretPosition();
                    inputArea.insertText(pos, "\n");
                } else {
                    sendMessage();
                }
                event.consume();
            }
        });

        inputArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                if (!inputShell.getStyleClass().contains("input-focused")) {
                    inputShell.getStyleClass().add("input-focused");
                }
            } else {
                inputShell.getStyleClass().remove("input-focused");
            }
        });

        if (exportToastCloseButton != null) {
            exportToastCloseButton.setOnAction(event -> hideNotificationBanner(true));
        }

        if (terminalClearButton != null) {
            terminalClearButton.setOnAction(event -> clearTerminal());
        }
        if (terminalCloseButton != null) {
            terminalCloseButton.setOnAction(event -> hideTerminalPanel());
        }
        if (terminalToggleButton != null) {
            terminalToggleButton.setOnAction(event -> toggleTerminalPanel());
        }
        if (terminalDockLeftButton != null) {
            terminalDockLeftButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.LEFT));
        }
        if (terminalDockRightButton != null) {
            terminalDockRightButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.RIGHT));
        }
        if (terminalDockBottomButton != null) {
            terminalDockBottomButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.BOTTOM));
        }
        if (terminalDockTopButton != null) {
            terminalDockTopButton.setOnAction(event -> setTerminalDockPosition(TerminalDockPosition.TOP));
        }
        initializeTerminalDockIcons();
        updateTerminalDockButtons();
        initializeTerminalConsole();
        hideTerminalPanel();
        setTerminalStatus("Terminal hidden");
    }

    // ================= CONVERSATION BINDING =================
    public void setConversation(Conversation conversation) {
        stopResponseAnimations();
        this.conversation = conversation;
        refreshMessages();
    }

    public void setOnConversationUpdated(Runnable onConversationUpdated) {
        this.onConversationUpdated = onConversationUpdated;
    }

    public void setChatService(ChatService chatService) {
        if (chatService != null) {
            this.chatService = chatService;
        }
    }

    // ================= MESSAGE RENDER =================
    private void refreshMessages() {
        stopResponseAnimations();
        messageBox.getChildren().clear();
        if (conversation == null) {
            return;
        }
        for (Message msg : conversation.getMessages()) {
            messageBox.getChildren().add(createBubble(msg));
        }
        scrollToBottom();
    }

    // ================= SEND FLOW =================
    private void sendMessage() {
        if (conversation == null) {
            return;
        }

        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (inFlightRequest != null && !inFlightRequest.isDone()) {
            return;
        }

        int previousSize = conversation.getMessages().size();
        String previousTitle = conversation.getTitle();
        inputArea.clear();
        setComposerBusy(true);
        stopResponseAnimations();

        inFlightRequest = chatService.sendMessageAsync(conversation, text);

        for (int i = previousSize; i < conversation.getMessages().size(); i++) {
            HBox bubble = createBubble(conversation.getMessages().get(i));
            messageBox.getChildren().add(bubble);
            playFadeIn(bubble);
        }

        if (onConversationUpdated != null && !Objects.equals(previousTitle, conversation.getTitle())) {
            onConversationUpdated.run();
        }

        HBox pendingBubble = createGeneratingBubble();
        messageBox.getChildren().add(pendingBubble);
        playFadeIn(pendingBubble);
        startGeneratingIndicator();
        scrollToBottom();

        inFlightRequest.whenComplete((botMessage, error) -> Platform.runLater(() -> {
            if (conversation == null) {
                stopResponseAnimations();
                setComposerBusy(false);
                return;
            }

            Message responseMessage = botMessage;
            if (error != null) {
                String errorMessage = error.getMessage() == null ? error.toString() : error.getMessage();
                responseMessage = new Message(
                        Message.Sender.BOT,
                        "I could not generate a reply.\n\n- " + errorMessage
                );
            }
            if (responseMessage == null) {
                responseMessage = new Message(
                        Message.Sender.BOT,
                        "I could not generate a reply.\n\n- Empty response from assistant."
                );
            }

            chatService.appendAssistantMessage(conversation, responseMessage);
            animateAssistantResponse(responseMessage, () -> {
                inFlightRequest = null;
                setComposerBusy(false);
            });
        }));
    }

    private HBox createGeneratingBubble() {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");
        row.setAlignment(Pos.TOP_LEFT);

        Label generatingLabel = new Label("Cortex is generating");
        generatingLabel.setWrapText(true);
        generatingLabel.setMaxWidth(520);
        generatingLabel.getStyleClass().addAll("message-text", "message-generating-text");

        VBox bubble = new VBox(generatingLabel);
        bubble.getStyleClass().addAll("message-bubble", "bot-bubble", "generating-bubble");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(bubble, spacer);

        pendingBotBubbleRow = row;
        pendingBotBubbleLabel = generatingLabel;
        return row;
    }

    private void startGeneratingIndicator() {
        stopGeneratingIndicator();
        if (pendingBotBubbleLabel == null) {
            return;
        }

        final String base = "Cortex is generating";
        final int[] dots = {0};
        pendingBotBubbleLabel.setText(base);

        generatingIndicatorTimeline = new Timeline(new KeyFrame(Duration.millis(260), event -> {
            if (pendingBotBubbleLabel == null) {
                stopGeneratingIndicator();
                return;
            }
            dots[0] = (dots[0] + 1) % 4;
            pendingBotBubbleLabel.setText(base + ".".repeat(dots[0]));
            scrollToBottomNow();
        }));
        generatingIndicatorTimeline.setCycleCount(Timeline.INDEFINITE);
        generatingIndicatorTimeline.play();
    }

    private void stopGeneratingIndicator() {
        if (generatingIndicatorTimeline != null) {
            generatingIndicatorTimeline.stop();
            generatingIndicatorTimeline = null;
        }
    }

    private void stopResponseTyping() {
        if (responseTypingTimeline != null) {
            responseTypingTimeline.stop();
            responseTypingTimeline = null;
        }
    }

    private void clearPendingBotBubble() {
        pendingBotBubbleRow = null;
        pendingBotBubbleLabel = null;
    }

    private void stopResponseAnimations() {
        stopGeneratingIndicator();
        stopResponseTyping();
        clearPendingBotBubble();
    }

    private void animateAssistantResponse(Message responseMessage, Runnable onComplete) {
        stopGeneratingIndicator();

        if (responseMessage == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        if (pendingBotBubbleRow == null
                || pendingBotBubbleLabel == null
                || !messageBox.getChildren().contains(pendingBotBubbleRow)) {
            HBox bubble = createBubbleSafely(responseMessage);
            messageBox.getChildren().add(bubble);
            playFadeIn(bubble);
            scrollToBottomNow();
            clearPendingBotBubble();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        stopResponseTyping();

        String fullText = responseMessage.getContent() == null ? "" : responseMessage.getContent();
        if (fullText.isEmpty()) {
            replacePendingBubbleWithFinal(responseMessage);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        pendingBotBubbleLabel.setText("");
        final int totalLength = fullText.length();
        final int[] cursor = {0};

        responseTypingTimeline = new Timeline(new KeyFrame(Duration.millis(14), event -> {
            int remaining = totalLength - cursor[0];
            int step = remaining > 420 ? 8
                    : remaining > 220 ? 6
                    : remaining > 120 ? 4
                    : remaining > 60 ? 3
                    : 1;

            cursor[0] = Math.min(totalLength, cursor[0] + step);
            if (pendingBotBubbleLabel != null) {
                pendingBotBubbleLabel.setText(fullText.substring(0, cursor[0]));
            }
            scrollToBottomNow();

            if (cursor[0] >= totalLength) {
                stopResponseTyping();
                replacePendingBubbleWithFinal(responseMessage);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }));
        responseTypingTimeline.setCycleCount(Timeline.INDEFINITE);
        responseTypingTimeline.play();
    }

    private void replacePendingBubbleWithFinal(Message responseMessage) {
        int index = pendingBotBubbleRow == null ? -1 : messageBox.getChildren().indexOf(pendingBotBubbleRow);
        HBox finalBubble = createBubbleSafely(responseMessage);

        if (index >= 0) {
            messageBox.getChildren().set(index, finalBubble);
        } else {
            messageBox.getChildren().add(finalBubble);
            playFadeIn(finalBubble);
        }

        clearPendingBotBubble();
        scrollToBottomNow();
    }

    // ================= BUBBLE FACTORY =================
    private HBox createBubble(Message msg) {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");

        Node content = buildMessageContent(msg.getContent());
        VBox bubble = new VBox(content);
        bubble.getStyleClass().add("message-bubble");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (msg.getSender() == Message.Sender.USER) {
            row.setAlignment(Pos.TOP_RIGHT);
            bubble.getStyleClass().add("user-bubble");
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            bubble.getStyleClass().add("bot-bubble");
            row.getChildren().addAll(bubble, spacer);
        }

        return row;
    }

    private HBox createBubbleSafely(Message msg) {
        try {
            return createBubble(msg);
        } catch (Exception ex) {
            Message fallback = new Message(
                    msg.getSender(),
                    msg.getContent() + "\n\n(Render warning: " + ex.getMessage() + ")"
            );
            return createPlainTextBubble(fallback);
        }
    }

    private HBox createPlainTextBubble(Message msg) {
        HBox row = new HBox();
        row.getStyleClass().add("message-row");

        Label content = new Label(msg.getContent() == null ? "" : msg.getContent());
        content.setWrapText(true);
        content.setMaxWidth(520);
        content.getStyleClass().add("message-text");

        // Add copy button for non-empty messages
        Button copyButton = null;
        if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
            copyButton = new Button("Copy");
            copyButton.getStyleClass().add("message-copy-button");
            copyButton.setFocusTraversable(false);
            copyButton.setOnAction(event -> copyCodeToClipboard(msg.getContent()));
        }

        VBox bubble;
        if (copyButton != null) {
            VBox contentContainer = new VBox(4, content);
            contentContainer.getStyleClass().add("message-content-container");
            HBox buttonBar = new HBox(copyButton);
            buttonBar.getStyleClass().add("message-button-bar");
            bubble = new VBox(6, contentContainer, buttonBar);
        } else {
            bubble = new VBox(content);
        }
        bubble.getStyleClass().add("message-bubble");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (msg.getSender() == Message.Sender.USER) {
            row.setAlignment(Pos.TOP_RIGHT);
            bubble.getStyleClass().add("user-bubble");
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            bubble.getStyleClass().add("bot-bubble");
            row.getChildren().addAll(bubble, spacer);
        }

        return row;
    }

    // ================= MESSAGE ANIMATION =================
    private void playFadeIn(HBox bubbleRow) {
        bubbleRow.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(220), bubbleRow);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // ================= SCROLL HELPERS =================
    private void scrollToBottom() {
        Platform.runLater(this::scrollToBottomNow);
    }

    private void scrollToBottomNow() {
        if (scrollPane == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::scrollToBottomNow);
            return;
        }
        scrollPane.setVvalue(1.0);
    }

    private Node buildMessageContent(String content) {
        VBox container = new VBox(8);
        String safeContent = content == null ? "" : content;
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(safeContent);
        int current = 0;

        while (matcher.find()) {
            if (matcher.start() > current) {
                String textPart = safeContent.substring(current, matcher.start()).trim();
                if (!textPart.isEmpty()) {
                    container.getChildren().add(createMarkdownBlock(textPart));
                }
            }

            String language = matcher.group(1) == null ? "text" : matcher.group(1).trim();
            String code = matcher.group(2) == null ? "" : matcher.group(2).stripTrailing();
            container.getChildren().add(createCodeBlock(language, code));
            current = matcher.end();
        }

        if (current < safeContent.length()) {
            String tail = safeContent.substring(current).trim();
            if (!tail.isEmpty()) {
                container.getChildren().add(createMarkdownBlock(tail));
            }
        }

        if (container.getChildren().isEmpty()) {
            container.getChildren().add(createMarkdownBlock(content == null ? "" : content));
        }
        return container;
    }

    private VBox createMarkdownBlock(String textValue) {
        VBox block = new VBox(6);
        block.getStyleClass().add("message-markdown");
        String[] lines = textValue.split("\\R", -1);

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                Region spacer = new Region();
                spacer.setMinHeight(6);
                block.getChildren().add(spacer);
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                TextFlow headingFlow = createInlineTextFlow(headingMatcher.group(2));
                headingFlow.getStyleClass().add("message-heading");
                headingFlow.getStyleClass().add("message-heading-" + level);
                block.getChildren().add(headingFlow);
                continue;
            }

            Matcher orderedMatcher = ORDERED_LIST_PATTERN.matcher(line);
            if (orderedMatcher.matches()) {
                TextFlow orderedFlow = createInlineTextFlow(orderedMatcher.group(2));
                orderedFlow.getStyleClass().add("message-list-item");
                orderedFlow.getChildren().add(0, createStyledText(orderedMatcher.group(1) + ". ", "message-list-marker"));
                block.getChildren().add(orderedFlow);
                continue;
            }

            Matcher unorderedMatcher = UNORDERED_LIST_PATTERN.matcher(line);
            if (unorderedMatcher.matches()) {
                TextFlow unorderedFlow = createInlineTextFlow(unorderedMatcher.group(1));
                unorderedFlow.getStyleClass().add("message-list-item");
                unorderedFlow.getChildren().add(0, createStyledText("- ", "message-list-marker"));
                block.getChildren().add(unorderedFlow);
                continue;
            }

            block.getChildren().add(createInlineTextFlow(line));
        }

        return block;
    }

    private TextFlow createInlineTextFlow(String textValue) {
        String safeText = textValue == null ? "" : textValue;
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(520);
        flow.getStyleClass().add("message-flow");

        Matcher matcher = INLINE_MARKDOWN_PATTERN.matcher(safeText);
        int current = 0;
        while (matcher.find()) {
            if (matcher.start() > current) {
                flow.getChildren().add(createStyledText(safeText.substring(current, matcher.start()), "message-inline-plain"));
            }

            if (matcher.group(2) != null) {
                flow.getChildren().add(createStyledText(matcher.group(2), "message-inline-bold"));
            } else if (matcher.group(4) != null) {
                flow.getChildren().add(createStyledText(matcher.group(4), "message-inline-code"));
            } else if (matcher.group(6) != null) {
                flow.getChildren().add(createStyledText(matcher.group(6), "message-inline-italic"));
            }
            current = matcher.end();
        }

        if (current < safeText.length()) {
            flow.getChildren().add(createStyledText(safeText.substring(current), "message-inline-plain"));
        }
        if (flow.getChildren().isEmpty()) {
            flow.getChildren().add(createStyledText("", "message-inline-plain"));
        }
        return flow;
    }

    private Text createStyledText(String textValue, String styleClass) {
        Text text = new Text(textValue == null ? "" : textValue);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private VBox createCodeBlock(String language, String code) {
        Label languageLabel = new Label(language);
        languageLabel.getStyleClass().add("code-language");

        Button copyButton = new Button("Copy");
        copyButton.getStyleClass().add("message-code-copy");
        copyButton.setFocusTraversable(false);
        copyButton.setOnAction(event -> copyCodeToClipboard(code));

        String normalizedLanguage = normalizeLanguage(language);
        Button runButton = new Button("Run");
        runButton.getStyleClass().addAll("message-code-copy", "message-code-run");
        runButton.setFocusTraversable(false);
        runButton.setDisable(!isRuntimeAvailableForLanguage(normalizedLanguage));

        Button stopButton = new Button("x");
        stopButton.getStyleClass().addAll("message-code-copy", "message-code-stop");
        stopButton.setFocusTraversable(false);
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        stopButton.setOnAction(event -> stopActiveExecution());

        runButton.setOnAction(event -> runCodeSnippet(normalizedLanguage, code, runButton, stopButton));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, languageLabel, headerSpacer, copyButton, runButton, stopButton);
        header.getStyleClass().add("message-code-header");
        header.setAlignment(Pos.CENTER_LEFT);

        TextArea codeArea = new TextArea(code);
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.setFocusTraversable(false);
        codeArea.setPrefRowCount(Math.max(3, code.lines().toList().size()));
        codeArea.getStyleClass().add("message-code");

        VBox codeBox = new VBox(6, header, codeArea);
        codeBox.getStyleClass().add("message-code-block");
        return codeBox;
    }

    private void copyCodeToClipboard(String code) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(code == null ? "" : code);
        clipboard.setContent(content);
    }

    private void runCodeSnippet(String language, String code, Button runButton, Button stopButton) {
        if (activeExecution != null) {
            setTerminalStatus("Run in progress");
            appendTerminalLine("[Run] Another command is already running. Stop it first.");
            return;
        }

        String normalizedLanguage = normalizeLanguage(language);
        if (!isRunnableLanguage(normalizedLanguage)) {
            setTerminalStatus("Unsupported language");
            appendTerminalLine("[Run] Language '" + normalizedLanguage + "' is not supported.");
            return;
        }
        if (!isRuntimeAvailableForLanguage(normalizedLanguage)) {
            setTerminalStatus("Runtime missing");
            appendTerminalLine("[Run] Runtime for '" + normalizedLanguage + "' is not installed.");
            runButton.setDisable(true);
            return;
        }
        if (code == null || code.isBlank()) {
            setTerminalStatus("Nothing to run");
            appendTerminalLine("[Run] Code block is empty.");
            return;
        }

        RunningExecution execution = new RunningExecution(normalizedLanguage, runButton, stopButton);
        activeExecution = execution;
        updateExecutionControls(execution, true);
        setTerminalStatus("Running " + normalizedLanguage + "...");
        appendTerminalLine("[Run] Executing " + normalizedLanguage + " snippet...");

        CompletableFuture
                .supplyAsync(() -> executeSnippet(normalizedLanguage, code, execution))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            setTerminalStatus("Run failed");
                            appendTerminalLine("[Error] " + error.getMessage());
                            return;
                        }

                        if (result != null && !result.streamed() && result.output() != null && !result.output().isBlank()) {
                            appendTerminalLine(result.output().stripTrailing());
                        }

                        int exitCode = result == null ? -1 : result.exitCode();
                        appendTerminalLine("[Exit code] " + exitCode);
                        if (execution.stopRequestedByUser) {
                            appendTerminalLine("[Run] Execution stopped by user.");
                            setTerminalStatus("Run stopped");
                        } else {
                            setTerminalStatus(exitCode == 0 ? "Run completed" : "Run failed");
                        }
                    } finally {
                        if (activeExecution == execution) {
                            activeExecution = null;
                        }
                        updateExecutionControls(execution, false);
                        printTerminalPrompt();
                    }
                }));
    }

    private RunResult executeSnippet(String language, String code, RunningExecution execution) {
        try {
            return switch (language) {
                case "python" -> runWithInterpreter(code, ".py", resolvePythonCommand(), "Python", execution);
                case "javascript" -> runWithInterpreter(code, ".js", resolveNodeCommand(), "Node.js", execution);
                case "powershell" -> runWithInterpreter(code, ".ps1", resolvePowerShellCommand(), "PowerShell", execution);
                case "bash" -> runWithInterpreter(code, ".sh", resolveBashCommand(), "Bash", execution);
                case "c" -> runCSnippet(code, false, execution);
                case "cpp" -> runCSnippet(code, true, execution);
                case "java" -> runJavaSnippet(code, execution);
                default -> new RunResult(-1, "[Run] Unsupported language: " + language, false);
            };
        } catch (Exception ex) {
            return new RunResult(-1, "[Run] Failed: " + ex.getMessage(), false);
        }
    }

    private RunResult runWithInterpreter(String code, String extension, List<String> commandPrefix, String runtimeName, RunningExecution execution)
            throws IOException, InterruptedException {
        if (commandPrefix == null || commandPrefix.isEmpty()) {
            return new RunResult(-1, "[Run] " + runtimeName + " is not installed on this system.", false);
        }

        Path tempDir = Files.createTempDirectory("chat-run-");
        try {
            Path scriptFile = tempDir.resolve("snippet" + extension);
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>(commandPrefix);
            command.add(scriptFile.toAbsolutePath().toString());
            return runProcess(command, tempDir, execution);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private RunResult runJavaSnippet(String code, RunningExecution execution) throws IOException, InterruptedException {
        if (!isCommandAvailable("javac") || !isCommandAvailable("java")) {
            return new RunResult(-1, "[Run] Java runtime/compiler (java/javac) is not installed on this system.", false);
        }

        Path tempDir = Files.createTempDirectory("chat-java-run-");
        try {
            String source = code;
            String className = extractJavaClassName(code);

            if (className == null) {
                className = "SnippetMain";
                source = "public class " + className + " {\n"
                        + "    public static void main(String[] args) {\n"
                        + code.indent(8)
                        + "    }\n"
                        + "}\n";
            }

            Path sourceFile = tempDir.resolve(className + ".java");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            RunResult compileResult = runProcess(List.of("javac", sourceFile.toAbsolutePath().toString()), tempDir, execution);
            if (compileResult.exitCode() != 0) {
                appendTerminalLine("[Compile] Java compilation failed.");
                return new RunResult(compileResult.exitCode(), "", true);
            }

            return runProcess(List.of("java", "-cp", tempDir.toAbsolutePath().toString(), className), tempDir, execution);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private RunResult runCSnippet(String code, boolean isCpp, RunningExecution execution) throws IOException, InterruptedException {
        List<String> compilerCommand = isCpp ? resolveCppCompiler() : resolveCCompiler();
        String langLabel = isCpp ? "C++" : "C";
        String compilerList = isCpp ? "g++/clang++/cl" : "gcc/clang/cc/tcc";
        
        if (compilerCommand == null || compilerCommand.isEmpty()) {
            return new RunResult(-1, "[Run] " + langLabel + " compiler is not installed on this system (" + compilerList + ").", false);
        }

        Path tempDir = Files.createTempDirectory("chat-" + (isCpp ? "cpp" : "c") + "-run-");
        try {
            String sourceExt = isCpp ? ".cpp" : ".c";
            Path sourceFile = tempDir.resolve("snippet" + sourceExt);
            Path binaryFile = tempDir.resolve(isWindows() ? "snippet.exe" : "snippet.out");
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8);

            List<String> compileCommand = new ArrayList<>(compilerCommand);
            
            // Add compiler-specific output flags
            if (!compileCommand.isEmpty()) {
                String compiler = compileCommand.get(0).toLowerCase();
                if (compiler.equals("cl")) {
                    // MSVC: cl source.c /Fe:output.exe
                    compileCommand.add("/Fe:" + binaryFile.toAbsolutePath());
                    compileCommand.add(sourceFile.toAbsolutePath().toString());
                } else {
                    // GCC/Clang: g++ source.cpp -o output
                    compileCommand.add(sourceFile.toAbsolutePath().toString());
                    compileCommand.add("-o");
                    compileCommand.add(binaryFile.toAbsolutePath().toString());
                }
            }

            RunResult compileResult = runProcess(compileCommand, tempDir, execution);
            if (compileResult.exitCode() != 0) {
                appendTerminalLine("[Compile] " + langLabel + " compilation failed.");
                return new RunResult(compileResult.exitCode(), "", true);
            }

            return runProcess(List.of(binaryFile.toAbsolutePath().toString()), tempDir, execution);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private RunResult runProcess(List<String> command, Path workingDirectory, RunningExecution execution)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        execution.process = process;
        execution.processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        CompletableFuture<Void> outputPump = CompletableFuture.runAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    appendTerminalRaw(new String(buffer, 0, read));
                }
            } catch (IOException ex) {
                if (!execution.cancelRequested && ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    appendTerminalLine("[Run] Output stream closed: " + ex.getMessage());
                }
            }
        });

        while (true) {
            if (execution.cancelRequested) {
                process.destroyForcibly();
                break;
            }
            if (process.waitFor(120, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        process.waitFor(2, TimeUnit.SECONDS);
        outputPump.join();
        BufferedWriter processInput = execution.processInput;
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException ignored) {
                // Best-effort close for process stdin.
            }
        }
        execution.processInput = null;
        execution.process = null;

        if (execution.cancelRequested) {
            return new RunResult(-1, "", true);
        }
        return new RunResult(process.isAlive() ? -1 : process.exitValue(), "", true);
    }

    private String extractJavaClassName(String code) {
        Matcher publicMatcher = PUBLIC_CLASS_PATTERN.matcher(code);
        if (publicMatcher.find()) {
            return publicMatcher.group(1);
        }
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        return null;
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null ? "text" : language.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "txt", "text", "plaintext" -> "text";
            case "py" -> "python";
            case "js", "node", "nodejs" -> "javascript";
            case "ps", "ps1", "pwsh" -> "powershell";
            case "shell", "sh", "zsh" -> "bash";
            case "c89", "c90", "c99", "c11", "c17", "c18" -> "c";
            case "cpp", "c++", "cc", "cxx", "c++11", "c++14", "c++17", "c++20", "c++23" -> "cpp";
            default -> normalized;
        };
    }

    private boolean isRunnableLanguage(String language) {
        return switch (language) {
            case "python", "javascript", "java", "powershell", "bash", "c", "cpp" -> true;
            default -> false;
        };
    }

    private boolean isRuntimeAvailableForLanguage(String language) {
        if (!isRunnableLanguage(language)) {
            return false;
        }
        return runtimeAvailabilityCache.computeIfAbsent(language, this::detectRuntimeForLanguage);
    }

    private boolean detectRuntimeForLanguage(String language) {
        return switch (language) {
            case "python" -> resolvePythonCommand() != null;
            case "javascript" -> resolveNodeCommand() != null;
            case "powershell" -> resolvePowerShellCommand() != null;
            case "bash" -> resolveBashCommand() != null;
            case "c" -> resolveCCompiler() != null;
            case "cpp" -> resolveCppCompiler() != null;
            case "java" -> isCommandAvailable("javac") && isCommandAvailable("java");
            default -> false;
        };
    }

    private List<String> resolvePythonCommand() {
        if (isCommandAvailable("python")) {
            return List.of("python");
        }
        if (isCommandAvailable("py")) {
            return List.of("py", "-3");
        }
        if (isCommandAvailable("python3")) {
            return List.of("python3");
        }
        return null;
    }

    private List<String> resolveNodeCommand() {
        return isCommandAvailable("node") ? List.of("node") : null;
    }

    private List<String> resolvePowerShellCommand() {
        if (isCommandAvailable("pwsh")) {
            return List.of("pwsh", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File");
        }
        if (isCommandAvailable("powershell")) {
            return List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File");
        }
        return null;
    }

    private List<String> resolveBashCommand() {
        return isCommandAvailable("bash") ? List.of("bash") : null;
    }

    private List<String> resolveCCompiler() {
        // Try most common compilers first (in order of preference)
        String[] compilers = isWindows() 
            ? new String[]{"gcc", "clang", "cl", "tcc", "cc"}
            : new String[]{"gcc", "clang", "cc", "tcc"};
        
        for (String compiler : compilers) {
            if (isCommandAvailable(compiler)) {
                return compiler.equals("cl") ? List.of("cl", "/TC") : List.of(compiler);
            }
        }
        return null;
    }

    private List<String> resolveCppCompiler() {
        // Try most common C++ compilers first
        String[] compilers = isWindows()
            ? new String[]{"g++", "clang++", "cl", "c++", "tcc"}
            : new String[]{"g++", "clang++", "c++", "tcc"};
        
        for (String compiler : compilers) {
            if (isCommandAvailable(compiler)) {
                return compiler.equals("cl") ? List.of("cl", "/TP") : List.of(compiler);
            }
        }
        
        // Fallback to C compiler for C++ if g++/clang++ not available
        List<String> cCompiler = resolveCCompiler();
        return cCompiler != null && (cCompiler.contains("gcc") || cCompiler.contains("clang") || cCompiler.contains("cc")) 
            ? cCompiler 
            : null;
    }

    private boolean isCommandAvailable(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        // Use cache to avoid repeated slow probes
        return commandAvailabilityCache.computeIfAbsent(commandName, cmd -> {
            List<String> probe = isWindows()
                    ? List.of("cmd", "/c", "where", cmd)
                    : List.of("sh", "-lc", "command -v " + cmd);
            try {
                Process process = new ProcessBuilder(probe).redirectErrorStream(true).start();
                // Use shorter timeout (2 seconds instead of 5) for faster detection
                boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
                return process.exitValue() == 0;
            } catch (Exception ex) {
                return false;
            }
        });
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void initializeTerminalConsole() {
        if (terminalOutputArea == null) {
            return;
        }
        terminalOutputArea.setWrapText(true);
        
        // Add tooltip for copy functionality
        Tooltip terminalTooltip = new Tooltip("Click to enter input\nSelect text and Ctrl+C to copy\nClear button resets output");
        terminalTooltip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(terminalOutputArea, terminalTooltip);
        
        terminalOutputArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTerminalKeyPressed);
        terminalOutputArea.addEventFilter(KeyEvent.KEY_TYPED, this::handleTerminalKeyTyped);
        terminalOutputArea.setOnMouseClicked(event -> {
            if (terminalOutputArea.getCaretPosition() < terminalInputStartIndex) {
                Platform.runLater(() -> terminalOutputArea.positionCaret(terminalOutputArea.getLength()));
            }
        });
        clearTerminal();
    }

    private void handleTerminalKeyPressed(KeyEvent event) {
        if (terminalOutputArea == null) {
            return;
        }

        int caret = terminalOutputArea.getCaretPosition();
        KeyCode code = event.getCode();

        if (code == KeyCode.ENTER) {
            event.consume();
            executeTerminalCommandFromConsole();
            return;
        }
        if (code == KeyCode.HOME) {
            event.consume();
            terminalOutputArea.positionCaret(terminalInputStartIndex);
            return;
        }
        if (code == KeyCode.BACK_SPACE && caret <= terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (code == KeyCode.DELETE && caret < terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (code == KeyCode.LEFT && caret <= terminalInputStartIndex) {
            event.consume();
            return;
        }
        if (caret < terminalInputStartIndex && !event.isControlDown() && !event.isMetaDown()) {
            event.consume();
            terminalOutputArea.positionCaret(terminalOutputArea.getLength());
        }
    }

    private void handleTerminalKeyTyped(KeyEvent event) {
        if (terminalOutputArea == null) {
            return;
        }
        if (terminalOutputArea.getCaretPosition() < terminalInputStartIndex) {
            event.consume();
            terminalOutputArea.positionCaret(terminalOutputArea.getLength());
        }
    }

    private void executeTerminalCommandFromConsole() {
        if (terminalOutputArea == null) {
            return;
        }

        String allText = terminalOutputArea.getText() == null ? "" : terminalOutputArea.getText();
        int start = Math.min(terminalInputStartIndex, allText.length());
        String command = allText.substring(start).trim();

        appendTerminalRaw(System.lineSeparator());

        if (command.isEmpty()) {
            printTerminalPrompt();
            return;
        }

        if (forwardInputToActiveExecution(command)) {
            return;
        }

        if (handleTerminalBuiltins(command)) {
            printTerminalPrompt();
            return;
        }

        if (activeExecution != null) {
            setTerminalStatus("Run in progress");
            appendTerminalLine("[Run] Another command is already running. Stop it first.");
            printTerminalPrompt();
            return;
        }

        RunningExecution execution = new RunningExecution("terminal", null, null);
        activeExecution = execution;
        setTerminalStatus("Running command...");

        CompletableFuture
                .supplyAsync(() -> runShellCommand(command, execution))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            setTerminalStatus("Command failed");
                            appendTerminalLine("[Error] " + error.getMessage());
                            return;
                        }

                        if (result != null && !result.streamed() && result.output() != null && !result.output().isBlank()) {
                            appendTerminalLine(result.output().stripTrailing());
                        }

                        int exitCode = result == null ? -1 : result.exitCode();
                        appendTerminalLine("[Exit code] " + exitCode);
                        if (execution.stopRequestedByUser) {
                            appendTerminalLine("[Run] Execution stopped by user.");
                            setTerminalStatus("Run stopped");
                        } else {
                            setTerminalStatus(exitCode == 0 ? "Ready" : "Command failed");
                        }
                    } finally {
                        if (activeExecution == execution) {
                            activeExecution = null;
                        }
                        printTerminalPrompt();
                    }
                }));
    }

    private RunResult runShellCommand(String command, RunningExecution execution) {
        try {
            List<String> shellCommand = buildDefaultShellCommand(command);
            if (shellCommand == null || shellCommand.isEmpty()) {
                return new RunResult(-1, "[Run] No default shell is available on this system.", false);
            }
            return runProcess(shellCommand, terminalWorkingDirectory, execution);
        } catch (Exception ex) {
            return new RunResult(-1, "[Run] Failed to execute command: " + ex.getMessage(), false);
        }
    }

    private List<String> buildDefaultShellCommand(String command) {
        if (isWindows()) {
            if (isCommandAvailable("pwsh")) {
                return List.of("pwsh", "-NoProfile", "-Command", command);
            }
            if (isCommandAvailable("powershell")) {
                return List.of("powershell", "-NoProfile", "-Command", command);
            }
            return List.of("cmd", "/c", command);
        }
        if (isCommandAvailable("bash")) {
            return List.of("bash", "-lc", command);
        }
        return List.of("sh", "-lc", command);
    }

    private boolean handleTerminalBuiltins(String command) {
        if (!isCdCommand(command)) {
            return false;
        }

        String target = command.length() <= 2 ? "~" : command.substring(2).trim();
        if (target.isEmpty()) {
            target = "~";
        }
        target = unquote(target);

        Path nextDirectory;
        if ("~".equals(target)) {
            nextDirectory = Path.of(System.getProperty("user.home"));
        } else {
            Path rawPath = Path.of(target);
            nextDirectory = rawPath.isAbsolute() ? rawPath : terminalWorkingDirectory.resolve(rawPath);
        }
        nextDirectory = nextDirectory.normalize();

        if (!Files.exists(nextDirectory) || !Files.isDirectory(nextDirectory)) {
            setTerminalStatus("Invalid directory");
            appendTerminalLine("[Shell] Directory not found: " + nextDirectory);
            return true;
        }

        terminalWorkingDirectory = nextDirectory;
        setTerminalStatus("Directory: " + terminalWorkingDirectory);
        return true;
    }

    private boolean isCdCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.equals("cd") || normalized.startsWith("cd ");
    }

    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        boolean quotedWithDouble = value.startsWith("\"") && value.endsWith("\"");
        boolean quotedWithSingle = value.startsWith("'") && value.endsWith("'");
        if (quotedWithDouble || quotedWithSingle) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String terminalPrompt() {
        return ">>" + TERMINAL_PROMPT_SUFFIX;
    }

    private void toggleTerminalPanel() {
        if (isTerminalOpen()) {
            hideTerminalPanel();
        } else {
            openTerminalPanel();
            if (terminalOutputArea != null) {
                Platform.runLater(() -> terminalOutputArea.requestFocus());
            }
        }
    }

    private boolean isTerminalOpen() {
        return chatSplitPane != null
                && terminalPane != null
                && chatSplitPane.getItems().contains(terminalPane)
                && terminalPane.isVisible();
    }

    private void stopActiveExecution() {
        RunningExecution execution = activeExecution;
        if (execution == null || execution.cancelRequested) {
            return;
        }

        execution.cancelRequested = true;
        execution.stopRequestedByUser = true;
        if (execution.stopButton != null) {
            execution.stopButton.setDisable(true);
        }
        setTerminalStatus("Stopping...");
        appendTerminalLine("[Run] Stop requested...");

        Process process = execution.process;
        BufferedWriter processInput = execution.processInput;
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException ignored) {
                // Best-effort close while stopping active process.
            }
            execution.processInput = null;
        }
        if (process != null) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean forwardInputToActiveExecution(String input) {
        RunningExecution execution = activeExecution;
        if (execution == null || execution.cancelRequested) {
            return false;
        }

        BufferedWriter processInput = execution.processInput;
        if (processInput == null) {
            return false;
        }

        try {
            processInput.write(input);
            processInput.newLine();
            processInput.flush();
            setTerminalStatus("Input sent");
            return true;
        } catch (IOException ex) {
            appendTerminalLine("[Run] Failed to send input: " + ex.getMessage());
            return false;
        }
    }

    private void updateExecutionControls(RunningExecution execution, boolean running) {
        if (execution == null) {
            return;
        }

        Button runButton = execution.runButton;
        Button stopButton = execution.stopButton;
        if (runButton != null) {
            runButton.setText(running ? "Running..." : "Run");
            runButton.setDisable(running || !isRuntimeAvailableForLanguage(execution.language));
        }
        if (stopButton != null) {
            stopButton.setManaged(running);
            stopButton.setVisible(running);
            stopButton.setDisable(false);
        }
    }

    private void setTerminalStatus(String status) {
        if (terminalStatusLabel == null) {
            return;
        }
        terminalStatusLabel.setText(status == null || status.isBlank() ? "Ready" : status);
    }

    private void hideTerminalPanel() {
        if (terminalPane != null) {
            terminalPane.setVisible(false);
            terminalPane.setManaged(false);
        }
        if (chatSplitPane != null && terminalPane != null) {
            chatSplitPane.getItems().remove(terminalPane);
        }
        if (terminalToggleButton != null) {
            terminalToggleButton.setText(">_");
        }
        setTerminalStatus("Terminal hidden");
    }

    private void openTerminalPanel() {
        if (chatSplitPane == null || terminalPane == null) {
            return;
        }

        boolean wasOpen = isTerminalOpen();

        ensureTerminalInSplitPane();
        applyTerminalDockLayout();
        terminalPane.setManaged(true);
        terminalPane.setVisible(true);
        if (terminalToggleButton != null) {
            terminalToggleButton.setText("<");
        }

        if (!wasOpen) {
            if (terminalOutputArea != null && terminalOutputArea.getText().isEmpty()) {
                printTerminalPrompt();
            }
            Platform.runLater(this::positionTerminalDivider);
        }
    }

    private void positionTerminalDivider() {
        if (chatSplitPane == null
                || terminalPane == null
                || chatSplitPane.getItems().size() < 2
                || !chatSplitPane.getItems().contains(terminalPane)) {
            return;
        }

        boolean verticalDock = isVerticalDockPosition(terminalDockPosition);
        double availableSize = verticalDock ? chatSplitPane.getHeight() : chatSplitPane.getWidth();
        if (availableSize <= 0 && chatSplitPane.getScene() != null) {
            availableSize = verticalDock ? chatSplitPane.getScene().getHeight() : chatSplitPane.getScene().getWidth();
        }
        if (availableSize <= 0) {
            chatSplitPane.setDividerPositions(defaultDividerPositionForDock(terminalDockPosition));
            return;
        }

        double desiredSize = verticalDock
                ? clamp(TERMINAL_DEFAULT_HEIGHT, TERMINAL_MIN_HEIGHT, TERMINAL_MAX_HEIGHT)
            : clamp(effectiveTerminalDefaultWidth(), TERMINAL_MIN_WIDTH, TERMINAL_MAX_WIDTH);
        desiredSize = Math.min(desiredSize, availableSize * 0.45);

        double terminalShare = clamp(desiredSize / availableSize, 0.16, 0.55);
        double divider = isTerminalLeading(terminalDockPosition)
                ? terminalShare
                : 1.0 - terminalShare;
        divider = clamp(divider, 0.12, 0.88);
        chatSplitPane.setDividerPositions(divider);
    }

    private void setTerminalDockPosition(TerminalDockPosition position) {
        if (position == null) {
            return;
        }
        terminalDockPosition = position;
        updateTerminalDockButtons();
        applyTerminalDockLayout();
        if (isTerminalOpen()) {
            Platform.runLater(this::positionTerminalDivider);
        }
    }

    private void initializeTerminalDockIcons() {
        configureDockButtonIcon(terminalDockLeftButton, TerminalDockPosition.LEFT);
        configureDockButtonIcon(terminalDockRightButton, TerminalDockPosition.RIGHT);
        configureDockButtonIcon(terminalDockBottomButton, TerminalDockPosition.BOTTOM);
        configureDockButtonIcon(terminalDockTopButton, TerminalDockPosition.TOP);
    }

    private void configureDockButtonIcon(Button button, TerminalDockPosition position) {
        if (button == null || position == null) {
            return;
        }
        button.setGraphic(null);
        button.setText(switch (position) {
            case LEFT -> "←";
            case RIGHT -> "→";
            case TOP -> "↑";
            case BOTTOM -> "↓";
        });
    }

    private void updateTerminalDockButtons() {
        updateDockButtonState(terminalDockLeftButton, terminalDockPosition == TerminalDockPosition.LEFT);
        updateDockButtonState(terminalDockRightButton, terminalDockPosition == TerminalDockPosition.RIGHT);
        updateDockButtonState(terminalDockBottomButton, terminalDockPosition == TerminalDockPosition.BOTTOM);
        updateDockButtonState(terminalDockTopButton, terminalDockPosition == TerminalDockPosition.TOP);
    }

    private void updateDockButtonState(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        if (selected) {
            if (!button.getStyleClass().contains("terminal-dock-button-selected")) {
                button.getStyleClass().add("terminal-dock-button-selected");
            }
        } else {
            button.getStyleClass().remove("terminal-dock-button-selected");
        }
    }

    private void ensureTerminalInSplitPane() {
        if (chatSplitPane == null || terminalPane == null) {
            return;
        }
        if (!chatSplitPane.getItems().contains(terminalPane)) {
            int insertIndex = isTerminalLeading(terminalDockPosition) ? 0 : chatSplitPane.getItems().size();
            chatSplitPane.getItems().add(insertIndex, terminalPane);
        }
    }

    private void applyTerminalDockLayout() {
        if (chatSplitPane == null || terminalPane == null || !chatSplitPane.getItems().contains(terminalPane)) {
            return;
        }

        boolean verticalDock = isVerticalDockPosition(terminalDockPosition);
        chatSplitPane.setOrientation(verticalDock ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        applyTerminalPaneSizeConstraints(verticalDock);

        int desiredIndex = isTerminalLeading(terminalDockPosition) ? 0 : 1;
        int currentIndex = chatSplitPane.getItems().indexOf(terminalPane);
        if (currentIndex != desiredIndex && chatSplitPane.getItems().size() >= 2) {
            chatSplitPane.getItems().remove(terminalPane);
            chatSplitPane.getItems().add(Math.min(desiredIndex, chatSplitPane.getItems().size()), terminalPane);
        }
    }

    private void applyTerminalPaneSizeConstraints(boolean verticalDock) {
        if (terminalPane == null) {
            return;
        }
        if (verticalDock) {
            terminalPane.setMinWidth(0);
            terminalPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
            terminalPane.setMaxWidth(Double.MAX_VALUE);
            terminalPane.setMinHeight(TERMINAL_MIN_HEIGHT);
            terminalPane.setPrefHeight(TERMINAL_DEFAULT_HEIGHT);
            terminalPane.setMaxHeight(TERMINAL_MAX_HEIGHT);
        } else {
            terminalPane.setMinWidth(TERMINAL_MIN_WIDTH);
            terminalPane.setPrefWidth(effectiveTerminalDefaultWidth());
            terminalPane.setMaxWidth(TERMINAL_MAX_WIDTH);
            terminalPane.setMinHeight(0);
            terminalPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            terminalPane.setMaxHeight(Double.MAX_VALUE);
        }
    }

    private double effectiveTerminalDefaultWidth() {
        return isStageFullScreenLike()
                ? TERMINAL_DEFAULT_WIDTH
                : TERMINAL_DEFAULT_WIDTH + TERMINAL_WINDOWED_WIDTH_BOOST;
    }

    private boolean isStageFullScreenLike() {
        if (chatSplitPane == null || chatSplitPane.getScene() == null) {
            return false;
        }
        Window window = chatSplitPane.getScene().getWindow();
        if (!(window instanceof Stage stage)) {
            return false;
        }
        return stage.isFullScreen() || stage.isMaximized();
    }

    private boolean isTerminalLeading(TerminalDockPosition position) {
        return position == TerminalDockPosition.LEFT || position == TerminalDockPosition.TOP;
    }

    private boolean isVerticalDockPosition(TerminalDockPosition position) {
        return position == TerminalDockPosition.TOP || position == TerminalDockPosition.BOTTOM;
    }

    private double defaultDividerPositionForDock(TerminalDockPosition position) {
        return isTerminalLeading(position) ? 0.28 : 0.72;
    }

    private void clearTerminal() {
        if (terminalOutputArea != null) {
            terminalOutputArea.clear();
        }
        terminalInputStartIndex = 0;
        setTerminalStatus("Ready");
        printTerminalPrompt();
    }

    private void printTerminalPrompt() {
        if (terminalOutputArea == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::printTerminalPrompt);
            return;
        }

        if (!terminalOutputArea.getText().isEmpty() && !terminalOutputArea.getText().endsWith(System.lineSeparator())) {
            terminalOutputArea.appendText(System.lineSeparator());
        }
        terminalOutputArea.appendText(terminalPrompt());
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private void appendTerminalRaw(String text) {
        if (terminalOutputArea == null) {
            return;
        }
        String safeText = text == null ? "" : text;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendTerminalRaw(safeText));
            return;
        }
        openTerminalPanel();
        terminalOutputArea.appendText(safeText);
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private void appendTerminalLine(String line) {
        if (terminalOutputArea == null) {
            return;
        }
        String safeLine = line == null ? "" : line;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendTerminalLine(safeLine));
            return;
        }

        openTerminalPanel();

        if (!terminalOutputArea.getText().isEmpty() && !terminalOutputArea.getText().endsWith(System.lineSeparator())) {
            terminalOutputArea.appendText(System.lineSeparator());
        }
        terminalOutputArea.appendText(safeLine + System.lineSeparator());
        terminalInputStartIndex = terminalOutputArea.getLength();
        terminalOutputArea.positionCaret(terminalOutputArea.getLength());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup for temporary run artifacts.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup for temporary run artifacts.
        }
    }

    private record RunResult(int exitCode, String output, boolean streamed) {
    }

    private static final class RunningExecution {
        private final String language;
        private final Button runButton;
        private final Button stopButton;
        private volatile Process process;
        private volatile BufferedWriter processInput;
        private volatile boolean cancelRequested;
        private volatile boolean stopRequestedByUser;

        private RunningExecution(String language, Button runButton, Button stopButton) {
            this.language = language;
            this.runButton = runButton;
            this.stopButton = stopButton;
        }
    }

    private void setComposerBusy(boolean busy) {
        waitingForResponse.set(busy);
        inputArea.setDisable(busy);
        if (!busy) {
            inputArea.requestFocus();
        }
    }

    // ================= EXPORT METHODS =================
    @FXML
    private void exportAsText() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Text File", "*.txt", file -> 
            ExportService.exportToText(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsMarkdown() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Markdown File", "*.md", file -> 
            ExportService.exportToMarkdown(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsPDF() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("PDF File", "*.pdf", file -> 
            ExportService.exportToPDF(conversation, file.toPath())
        );
    }

    @FXML
    private void exportAsWord() {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            showNotification("Nothing to export");
            return;
        }
        
        handleExport("Word Document", "*.docx", file -> 
            ExportService.exportToWord(conversation, file.toPath())
        );
    }

    private void handleExport(String format, String fileExtension, ExportHandler handler) {
        try {
            Stage stage = (Stage) messageBox.getScene().getWindow();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Chat as " + format);
            fileChooser.setInitialFileName(sanitizeFileName(conversation.getTitle()) + fileExtension.substring(1));
            
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(format, fileExtension);
            fileChooser.getExtensionFilters().add(filter);
            
            java.io.File selectedFile = fileChooser.showSaveDialog(stage);
            if (selectedFile != null) {
                boolean success = handler.export(selectedFile);
                if (success) {
                    showNotification("✓ Chat exported successfully to " + selectedFile.getName());
                } else {
                    showNotification("✗ Failed to export chat");
                }
            }
        } catch (Exception ex) {
            showNotification("✗ Export error: " + ex.getMessage());
        }
    }

    private String sanitizeFileName(String filename) {
        return filename.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_");
    }

    private void showNotification(String message) {
        Platform.runLater(() -> {
            if (exportToastBanner == null || exportToastLabel == null) {
                return;
            }

            if (exportToastTimer != null) {
                exportToastTimer.stop();
            }

            exportToastLabel.setText(message == null ? "" : message);
            exportToastBanner.getStyleClass().removeAll("export-toast-success", "export-toast-error", "export-toast-info");
            if (message != null && message.startsWith("✓")) {
                exportToastBanner.getStyleClass().add("export-toast-success");
            } else if (message != null && message.startsWith("✗")) {
                exportToastBanner.getStyleClass().add("export-toast-error");
            } else {
                exportToastBanner.getStyleClass().add("export-toast-info");
            }
            exportToastBanner.setOpacity(1);
            exportToastBanner.setManaged(true);
            exportToastBanner.setVisible(true);

            exportToastTimer = new PauseTransition(Duration.seconds(2));
            exportToastTimer.setOnFinished(event -> hideNotificationBanner(false));
            exportToastTimer.playFromStart();
        });
    }

    private void hideNotificationBanner(boolean instant) {
        if (exportToastBanner == null) {
            return;
        }
        if (exportToastTimer != null) {
            exportToastTimer.stop();
            exportToastTimer = null;
        }

        if (instant) {
            exportToastBanner.setVisible(false);
            exportToastBanner.setManaged(false);
            exportToastBanner.setOpacity(1);
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(260), exportToastBanner);
        fade.setFromValue(exportToastBanner.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            exportToastBanner.setVisible(false);
            exportToastBanner.setManaged(false);
            exportToastBanner.setOpacity(1);
        });
        fade.play();
    }

    @FunctionalInterface
    private interface ExportHandler {
        boolean export(java.io.File file) throws IOException;
    }

}

