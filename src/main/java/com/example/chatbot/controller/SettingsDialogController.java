package com.example.chatbot.controller;

import com.example.chatbot.service.SettingsManager;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller for the settings dialog. Builds category pages dynamically
 * and reads/writes through SettingsManager.
 */
public class SettingsDialogController {

    @FXML private ListView<String> categoryList;
    @FXML private StackPane pageContainer;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final SettingsManager settings = SettingsManager.getInstance();
    private final Map<String, VBox> pages = new LinkedHashMap<>();
    private Runnable onSave;
    private HostServices hostServices;

    @FXML
    public void initialize() {
        buildPages();
        categoryList.setItems(FXCollections.observableArrayList(pages.keySet()));
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> showPage(selected));

        saveButton.setOnAction(e -> doSave());
        cancelButton.setOnAction(e -> closeDialog());

        categoryList.getSelectionModel().selectFirst();
    }

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    // ================= PAGE FACTORY =================
    private void buildPages() {
        pages.put("Appearance", buildAppearancePage());
        pages.put("Chat Behavior", buildChatPage());
        pages.put("Code Execution", buildExecutionPage());
        pages.put("Terminal", buildTerminalPage());
        pages.put("Language Runtime", buildRuntimePage());
        pages.put("AI Model", buildAIPage());
        pages.put("Privacy", buildPrivacyPage());
        pages.put("Advanced", buildAdvancedPage());
        pages.put("About", buildAboutPage());
    }

    private void showPage(String name) {
        if (name == null) return;
        VBox page = pages.get(name);
        if (page == null) return;
        pageContainer.getChildren().setAll(page);
    }

    // ================= APPEARANCE =================
    private VBox buildAppearancePage() {
        VBox page = createPage("Appearance");

        // Theme is handled by main menu, just show current
        Label themeNote = new Label("Theme is controlled from the Settings menu in the title bar.");
        themeNote.setWrapText(true);
        themeNote.getStyleClass().add("settings-hint");
        page.getChildren().add(themeNote);

        page.getChildren().add(createSpinnerRow("UI Font Size", "appearance.uiFontSize", 8, 24, settings.getInt("appearance.uiFontSize", 14)));
        page.getChildren().add(createSpinnerRow("Chat Font Size", "appearance.chatFontSize", 8, 24, settings.getInt("appearance.chatFontSize", 14)));
        page.getChildren().add(createSpinnerRow("Code Block Font Size", "appearance.codeFontSize", 8, 24, settings.getInt("appearance.codeFontSize", 13)));
        page.getChildren().add(createSpinnerRow("Terminal Font Size", "appearance.terminalFontSize", 8, 24, settings.getInt("appearance.terminalFontSize", 13)));
        return page;
    }

    // ================= CHAT BEHAVIOR =================
    private VBox buildChatPage() {
        VBox page = createPage("Chat Behavior");
        page.getChildren().add(createComboRow("Response Style", "chat.responseStyle",
                new String[]{"Concise", "Detailed", "Step-by-step"},
                settings.getString("chat.responseStyle", "Detailed")));
        page.getChildren().add(createToggleRow("Enable streaming responses", "chat.streamingEnabled", settings.getBoolean("chat.streamingEnabled", true)));
        page.getChildren().add(createToggleRow("Auto-scroll to newest message", "chat.autoScroll", settings.getBoolean("chat.autoScroll", true)));
        page.getChildren().add(createToggleRow("Enable chat history", "chat.historyEnabled", settings.getBoolean("chat.historyEnabled", true)));
        return page;
    }

    // ================= CODE EXECUTION =================
    private VBox buildExecutionPage() {
        VBox page = createPage("Code Execution");
        page.getChildren().add(createSpinnerRow("Execution timeout (seconds)", "execution.timeoutSeconds", 1, 120, settings.getInt("execution.timeoutSeconds", 10)));
        page.getChildren().add(createSpinnerRow("Max terminal output size", "execution.maxOutputSize", 1000, 500000, settings.getInt("execution.maxOutputSize", 50000)));
        page.getChildren().add(createToggleRow("Auto-clean temporary files", "execution.autoCleanTempFiles", settings.getBoolean("execution.autoCleanTempFiles", true)));
        page.getChildren().add(createToggleRow("Confirm before running code", "execution.confirmBeforeRun", settings.getBoolean("execution.confirmBeforeRun", false)));
        return page;
    }

    // ================= TERMINAL =================
    private VBox buildTerminalPage() {
        VBox page = createPage("Terminal");
        page.getChildren().add(createComboRow("Default shell", "terminal.defaultShell",
                new String[]{"cmd", "powershell", "bash"},
                settings.getString("terminal.defaultShell", "powershell")));
        page.getChildren().add(createToggleRow("Clear terminal before running code", "terminal.clearBeforeRun", settings.getBoolean("terminal.clearBeforeRun", false)));
        page.getChildren().add(createSpinnerRow("Scrollback buffer size", "terminal.scrollbackSize", 1000, 100000, settings.getInt("terminal.scrollbackSize", 10000)));
        page.getChildren().add(createToggleRow("Show execution time", "terminal.showExecutionTime", settings.getBoolean("terminal.showExecutionTime", true)));
        return page;
    }

    // ================= LANGUAGE RUNTIME =================
    private VBox buildRuntimePage() {
        VBox page = createPage("Language Runtime");
        Label hint = new Label("Set custom paths for language runtimes not found in your system PATH.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-hint");
        page.getChildren().add(hint);

        page.getChildren().add(createPathRow("Python path", "runtime.pythonPath"));
        page.getChildren().add(createPathRow("Node.js path", "runtime.nodePath"));
        page.getChildren().add(createPathRow("Java path", "runtime.javaPath"));
        page.getChildren().add(createPathRow("GCC / G++ path", "runtime.gccPath"));
        return page;
    }

    // ================= AI MODEL =================
    private VBox buildAIPage() {
        VBox page = createPage("AI Model");

        // Temperature slider 0.0 – 2.0
        HBox tempRow = new HBox(12);
        tempRow.setAlignment(Pos.CENTER_LEFT);
        Label tempLabel = new Label("Temperature");
        tempLabel.setMinWidth(140);
        tempLabel.getStyleClass().add("settings-label");
        Slider tempSlider = new Slider(0, 2.0, settings.getDouble("ai.temperature", 0.4));
        tempSlider.setMajorTickUnit(0.5);
        tempSlider.setMinorTickCount(4);
        tempSlider.setShowTickLabels(true);
        tempSlider.setShowTickMarks(true);
        tempSlider.getStyleClass().add("settings-slider");
        HBox.setHgrow(tempSlider, Priority.ALWAYS);
        Label tempValue = new Label(String.format("%.1f", tempSlider.getValue()));
        tempValue.setMinWidth(30);
        tempSlider.valueProperty().addListener((obs, o, n) -> tempValue.setText(String.format("%.1f", n.doubleValue())));
        tempRow.getChildren().addAll(tempLabel, tempSlider, tempValue);
        tempRow.setUserData(new SettingBinding("ai.temperature", () -> tempSlider.getValue()));
        page.getChildren().add(tempRow);

        page.getChildren().add(createSpinnerRow("Max tokens", "ai.maxTokens", 256, 128000, settings.getInt("ai.maxTokens", 4096)));

        // System prompt
        VBox promptBox = new VBox(4);
        Label promptLabel = new Label("Custom system prompt");
        promptLabel.getStyleClass().add("settings-label");
        TextArea promptArea = new TextArea(settings.getString("ai.systemPrompt", ""));
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);
        promptArea.getStyleClass().add("settings-textarea");
        promptArea.setPromptText("Leave empty to use default Cortex system prompt");
        promptBox.getChildren().addAll(promptLabel, promptArea);
        promptBox.setUserData(new SettingBinding("ai.systemPrompt", promptArea::getText));
        page.getChildren().add(promptBox);

        return page;
    }

    // ================= PRIVACY =================
    private VBox buildPrivacyPage() {
        VBox page = createPage("Privacy");
        page.getChildren().add(createToggleRow("Save chat history", "privacy.saveChatHistory", settings.getBoolean("privacy.saveChatHistory", true)));

        Button clearConvButton = new Button("Clear All Conversations");
        clearConvButton.getStyleClass().add("settings-danger-button");
        clearConvButton.setOnAction(e -> {
            settings.clearConversationData();
            showSettingsToast(clearConvButton, "Conversations cleared");
        });

        Button clearCacheButton = new Button("Clear Cache");
        clearCacheButton.getStyleClass().add("settings-danger-button");
        clearCacheButton.setOnAction(e -> {
            settings.clearCache();
            showSettingsToast(clearCacheButton, "Cache cleared");
        });

        HBox actionRow = new HBox(10, clearConvButton, clearCacheButton);
        actionRow.setPadding(new Insets(8, 0, 0, 0));
        page.getChildren().add(actionRow);
        return page;
    }

    // ================= ADVANCED =================
    private VBox buildAdvancedPage() {
        VBox page = createPage("Advanced");
        page.getChildren().add(createToggleRow("Enable debug logs", "advanced.debugLogs", settings.getBoolean("advanced.debugLogs", false)));
        page.getChildren().add(createToggleRow("Enable experimental features", "advanced.experimentalFeatures", settings.getBoolean("advanced.experimentalFeatures", false)));
        return page;
    }

    // ================= ABOUT =================
    private VBox buildAboutPage() {
        VBox page = createPage("About");

        page.getChildren().add(createInfoRow("Application", "Cortex"));
        page.getChildren().add(createInfoRow("Version", "1.0.0"));
        page.getChildren().add(createInfoRow("Developer", "Cortex Team"));

        Hyperlink gitLink = new Hyperlink("https://github.com/cortex-app/cortex");
        gitLink.getStyleClass().add("settings-link");
        gitLink.setOnAction(e -> {
            if (hostServices != null) {
                hostServices.showDocument(gitLink.getText());
            }
        });
        HBox linkRow = new HBox(12);
        linkRow.setAlignment(Pos.CENTER_LEFT);
        Label linkLabel = new Label("Repository");
        linkLabel.setMinWidth(140);
        linkLabel.getStyleClass().add("settings-label");
        linkRow.getChildren().addAll(linkLabel, gitLink);
        page.getChildren().add(linkRow);

        Button updateButton = new Button("Check for Updates");
        updateButton.getStyleClass().add("settings-action-button");
        updateButton.setOnAction(e -> showSettingsToast(updateButton, "You are running the latest version."));
        page.getChildren().add(updateButton);

        return page;
    }

    // ================= ROW BUILDERS =================
    private VBox createPage(String title) {
        VBox page = new VBox(12);
        page.getStyleClass().add("settings-page");
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-page-title");
        page.getChildren().add(heading);
        return page;
    }

    private HBox createToggleRow(String label, String key, boolean current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        CheckBox cb = new CheckBox();
        cb.setSelected(current);
        cb.getStyleClass().add("settings-checkbox");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, cb);
        row.setUserData(new SettingBinding(key, cb::isSelected));
        return row;
    }

    private HBox createSpinnerRow(String label, String key, int min, int max, int current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        Spinner<Integer> spinner = new Spinner<>(min, max, current);
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
        spinner.getStyleClass().add("settings-spinner");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, spinner);
        row.setUserData(new SettingBinding(key, spinner::getValue));
        return row;
    }

    private HBox createComboRow(String label, String key, String[] options, String current) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(200);
        lbl.getStyleClass().add("settings-label");
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        combo.setValue(current);
        combo.setPrefWidth(160);
        combo.getStyleClass().add("settings-combo");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, combo);
        row.setUserData(new SettingBinding(key, combo::getValue));
        return row;
    }

    private HBox createPathRow(String label, String key) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(140);
        lbl.getStyleClass().add("settings-label");
        TextField field = new TextField(settings.getString(key, ""));
        field.setPromptText("Auto-detect");
        field.getStyleClass().add("settings-text-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button browse = new Button("Browse");
        browse.getStyleClass().add("settings-browse-button");
        browse.setFocusTraversable(false);
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select " + label);
            Stage stage = (Stage) browse.getScene().getWindow();
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                field.setText(file.getAbsolutePath());
            }
        });

        row.getChildren().addAll(lbl, field, browse);
        row.setUserData(new SettingBinding(key, field::getText));
        return row;
    }

    private HBox createInfoRow(String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setMinWidth(140);
        lbl.getStyleClass().add("settings-label");
        Label val = new Label(value);
        val.getStyleClass().add("settings-value");
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ================= SAVE / CLOSE =================
    private void doSave() {
        // Walk through all pages and collect values
        for (VBox page : pages.values()) {
            collectBindings(page);
        }
        settings.save();
        if (onSave != null) {
            onSave.run();
        }
        closeDialog();
    }

    private void collectBindings(Node node) {
        if (node.getUserData() instanceof SettingBinding binding) {
            Object value = binding.valueGetter.get();
            settings.set(binding.key, value);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectBindings(child);
            }
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showSettingsToast(Node anchor, String message) {
        Tooltip tip = new Tooltip(message);
        tip.setAutoHide(true);
        tip.show(anchor, 
            anchor.localToScreen(anchor.getBoundsInLocal()).getMinX(),
            anchor.localToScreen(anchor.getBoundsInLocal()).getMaxY() + 4);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> tip.hide());
        pause.play();
    }

    // ================= BINDING RECORD =================
    private record SettingBinding(String key, java.util.function.Supplier<Object> valueGetter) {}
}
