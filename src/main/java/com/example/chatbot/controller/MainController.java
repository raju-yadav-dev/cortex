package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.service.ChatService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainController {
    // ================= SIDEBAR + CONTENT NODES =================
    @FXML
    private VBox sidebar;
    @FXML
    private Button newChatButton;
    @FXML
    private ListView<Conversation> chatList;
    @FXML
    private VBox chatContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private BorderPane appShell;
    @FXML
    private StackPane windowRoot;

    // ================= TITLE BAR ACTION NODES =================
    @FXML
    private MenuButton settingsButton;
    @FXML
    private RadioMenuItem themeDarkItem;
    @FXML
    private RadioMenuItem themeLightItem;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private Button closeButton;

    // ================= STATE =================
    private final ChatService chatService = new ChatService();
    private Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private Rectangle shellClip;

    // ================= INITIALIZATION =================
    @FXML
    public void initialize() {
        // ---- Sidebar Actions + Selection ----
        newChatButton.setOnAction(e -> createNewConversation());
        chatList.setEditable(true);
        chatList.setCellFactory(list -> new ConversationCell(this::deleteConversation, this::togglePinConversation));
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadConversation(newVal);
            }
        });

        // ---- Window Chrome ----
        setupTitleBarDrag();
        applyRoundedClip();
        wireWindowButtons();
        wireThemeMenu();

        // ---- Default Theme + First Conversation ----
        applyTheme("theme-dark");

        Conversation first = chatService.createConversation();
        chatList.getItems().add(first);
        chatList.getSelectionModel().select(first);
    }

    // ================= STAGE BINDING =================
    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            stage.maximizedProperty().addListener((obs, oldValue, maximized) -> updateWindowChromeStateDeferred());
            stage.fullScreenProperty().addListener((obs, oldValue, fullScreen) -> updateWindowChromeStateDeferred());
            updateWindowChromeStateDeferred();
        }
    }

    // ================= TITLE BAR DRAG =================
    private void setupTitleBarDrag() {
        titleBar.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || isWindowControl(event.getTarget()) || isResizeCursorActive()) {
                return;
            }
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            if (stage == null || event.getButton() != MouseButton.PRIMARY || isWindowControl(event.getTarget()) || isResizeCursorActive()) {
                return;
            }

            if (stage.isMaximized()) {
                double dragRatio = event.getSceneX() / Math.max(1.0, titleBar.getWidth());
                stage.setMaximized(false);
                Platform.runLater(() -> {
                    stage.setX(event.getScreenX() - stage.getWidth() * dragRatio);
                    stage.setY(event.getScreenY() - dragOffsetY);
                });
                return;
            }

            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        titleBar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isWindowControl(event.getTarget())) {
                toggleMaximize();
            }
        });
    }

    private boolean isResizeCursorActive() {
        if (titleBar == null || titleBar.getScene() == null) {
            return false;
        }
        Cursor cursor = titleBar.getScene().getCursor();
        return cursor == Cursor.N_RESIZE
                || cursor == Cursor.NE_RESIZE
                || cursor == Cursor.E_RESIZE
                || cursor == Cursor.SE_RESIZE
                || cursor == Cursor.S_RESIZE
                || cursor == Cursor.SW_RESIZE
                || cursor == Cursor.W_RESIZE
                || cursor == Cursor.NW_RESIZE;
    }

    // ================= TITLE BAR HIT TEST =================
    private boolean isWindowControl(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (current instanceof ButtonBase) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // ================= WINDOW BUTTON ACTIONS =================
    private void wireWindowButtons() {
        bindWindowAction(closeButton, () -> stage.close());
        bindWindowAction(minimizeButton, () -> stage.setIconified(true));
        bindWindowAction(maximizeButton, this::toggleMaximize);
    }

    // ================= BUTTON HELPER =================
    private void bindWindowAction(Button button, Runnable action) {
        button.setOnAction(e -> {
            if (stage != null) {
                action.run();
            }
        });
    }

    // ================= MAXIMIZE TOGGLE =================
    private void toggleMaximize() {
        if (stage == null) {
            return;
        }
        stage.setMaximized(!stage.isMaximized());
        updateWindowChromeStateDeferred();
    }

    // ================= MAXIMIZED CSS STATE =================
    private void updateWindowChromeStateDeferred() {
        Platform.runLater(() -> {
            if (stage == null) {
                return;
            }
            updateMaximizedClass(stage.isMaximized() || stage.isFullScreen());
        });
    }

    private void updateMaximizedClass(boolean maximized) {
        if (appShell == null || windowRoot == null) {
            return;
        }
        if (maximized) {
            if (!appShell.getStyleClass().contains("maximized")) {
                appShell.getStyleClass().add("maximized");
            }
            if (!windowRoot.getStyleClass().contains("maximized")) {
                windowRoot.getStyleClass().add("maximized");
            }
        } else {
            appShell.getStyleClass().remove("maximized");
            windowRoot.getStyleClass().remove("maximized");
        }
        if (shellClip != null) {
            shellClip.setArcWidth(maximized ? 0 : 32);
            shellClip.setArcHeight(maximized ? 0 : 32);
        }
    }

    // ================= ROUNDED SHELL CLIP =================
    private void applyRoundedClip() {
        shellClip = new Rectangle();
        shellClip.setArcWidth(32);
        shellClip.setArcHeight(32);
        shellClip.widthProperty().bind(appShell.widthProperty());
        shellClip.heightProperty().bind(appShell.heightProperty());
        appShell.setClip(shellClip);
    }

    // ================= CONVERSATION ACTIONS =================
    private void createNewConversation() {
        Conversation conv = chatService.createConversation();
        int insertIndex = getPinnedSectionSize();
        chatList.getItems().add(insertIndex, conv);
        chatList.getSelectionModel().select(conv);
    }

    private void deleteConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        List<Conversation> items = chatList.getItems();
        int index = items.indexOf(conversation);
        if (index < 0) {
            return;
        }

        boolean wasSelected = chatList.getSelectionModel().getSelectedItem() == conversation;
        items.remove(index);

        if (items.isEmpty()) {
            createNewConversation();
            return;
        }

        if (wasSelected) {
            int nextIndex = Math.min(index, items.size() - 1);
            chatList.getSelectionModel().select(nextIndex);
        }
        chatList.refresh();
    }

    private void togglePinConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }
        conversation.setPinned(!conversation.isPinned());
        reorderConversations();
        chatList.getSelectionModel().select(conversation);
        chatList.refresh();
    }

    private void reorderConversations() {
        List<Conversation> current = new ArrayList<>(chatList.getItems());
        List<Conversation> pinned = new ArrayList<>();
        List<Conversation> unpinned = new ArrayList<>();

        for (Conversation conv : current) {
            if (conv.isPinned()) {
                pinned.add(conv);
            } else {
                unpinned.add(conv);
            }
        }

        chatList.getItems().setAll(pinned);
        chatList.getItems().addAll(unpinned);
    }

    private int getPinnedSectionSize() {
        int pinnedCount = 0;
        for (Conversation conv : chatList.getItems()) {
            if (conv.isPinned()) {
                pinnedCount++;
            }
        }
        return pinnedCount;
    }

    // ================= CHAT VIEW LOADING =================
    private void loadConversation(Conversation conversation) {
        try {
            // ---- Load Chat FXML + Inject Conversation ----
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Node chatPane = loader.load();
            ChatController controller = loader.getController();
            controller.setChatService(chatService);
            controller.setConversation(conversation);
            controller.setOnConversationUpdated(chatList::refresh);

            // ---- Replace Center Content + Stretch to Fill ----
            chatContainer.getChildren().setAll(chatPane);
            VBox.setVgrow(chatPane, Priority.ALWAYS);
            if (chatPane instanceof Region region) {
                region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ================= THEME MENU =================
    private void wireThemeMenu() {
        // ---- Mutually Exclusive Theme Items ----
        ToggleGroup group = new ToggleGroup();
        themeDarkItem.setToggleGroup(group);
        themeLightItem.setToggleGroup(group);

        // ---- Theme Switch Actions ----
        themeDarkItem.setOnAction(e -> applyTheme("theme-dark"));
        themeLightItem.setOnAction(e -> applyTheme("theme-light"));
    }

    // ================= THEME APPLY =================
    private void applyTheme(String themeClass) {
        windowRoot.getStyleClass().removeAll("theme-dark", "theme-light");
        windowRoot.getStyleClass().add(themeClass);
        themeDarkItem.setSelected("theme-dark".equals(themeClass));
        themeLightItem.setSelected("theme-light".equals(themeClass));
    }
}
