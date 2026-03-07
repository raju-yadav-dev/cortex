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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainController {
    private static final String DEFAULT_THEME = "theme-dark-purple";

    private static final Map<String, String> THEME_STYLESHEETS = Map.ofEntries(
        Map.entry("theme-dark-purple", "/styles/themes/dark/dark-purple.css"),
        Map.entry("theme-dark-green", "/styles/themes/dark/dark-green.css"),
        Map.entry("theme-dark-ocean", "/styles/themes/dark/dark-ocean.css"),
        Map.entry("theme-dark-ember", "/styles/themes/dark/dark-ember.css"),
        Map.entry("theme-dark-slate", "/styles/themes/dark/dark-slate.css"),
        Map.entry("theme-light", "/styles/themes/light/light.css"),
        Map.entry("theme-light-sky", "/styles/themes/light/light-sky.css"),
        Map.entry("theme-light-mint", "/styles/themes/light/light-mint.css"),
        Map.entry("theme-light-rose", "/styles/themes/light/light-rose.css"),
        Map.entry("theme-light-sand", "/styles/themes/light/light-sand.css")
    );

    private static final Map<String, String> THEME_MODE = Map.ofEntries(
        Map.entry("theme-dark-purple", "theme-dark"),
        Map.entry("theme-dark-green", "theme-dark"),
        Map.entry("theme-dark-ocean", "theme-dark"),
        Map.entry("theme-dark-ember", "theme-dark"),
        Map.entry("theme-dark-slate", "theme-dark"),
        Map.entry("theme-light", "theme-light"),
        Map.entry("theme-light-sky", "theme-light"),
        Map.entry("theme-light-mint", "theme-light"),
        Map.entry("theme-light-rose", "theme-light"),
        Map.entry("theme-light-sand", "theme-light")
    );

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
    private ImageView appIconView;
    @FXML
    private BorderPane appShell;
    @FXML
    private StackPane windowRoot;

    // ================= TITLE BAR ACTION NODES =================
    @FXML
    private MenuButton settingsButton;
    @FXML
    private RadioMenuItem themeDarkPurpleItem;
    @FXML
    private RadioMenuItem themeDarkGreenItem;
    @FXML
    private RadioMenuItem themeDarkOceanItem;
    @FXML
    private RadioMenuItem themeDarkEmberItem;
    @FXML
    private RadioMenuItem themeDarkSlateItem;
    @FXML
    private RadioMenuItem themeLightItem;
    @FXML
    private RadioMenuItem themeLightSkyItem;
    @FXML
    private RadioMenuItem themeLightMintItem;
    @FXML
    private RadioMenuItem themeLightRoseItem;
    @FXML
    private RadioMenuItem themeLightSandItem;
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
    private Rectangle windowRootClip;
    private Rectangle shellClip;
    private String activeThemeStylesheet;
    private final Map<RadioMenuItem, String> themeMenuBindings = new LinkedHashMap<>();

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
        loadTitleBarIcon();
        fixPopupTransparency();

        // ---- Default Theme + First Conversation ----
        applyTheme(DEFAULT_THEME);

        Conversation first = chatService.createConversation();
        chatList.getItems().add(first);
        chatList.getSelectionModel().select(first);
    }

    private void loadTitleBarIcon() {
        if (appIconView == null) {
            return;
        }
        try {
            var resource = getClass().getResource("/icon/Cortex.png");
            if (resource != null) {
                appIconView.setImage(new Image(resource.toExternalForm()));
                return;
            }
            var icoResource = getClass().getResource("/icon/Cortex.ico");
            if (icoResource != null) {
                appIconView.setImage(new Image(icoResource.toExternalForm()));
            }
        } catch (Exception ignored) {
            // Keep UI stable even if icon resource is unavailable.
        }
    }

    // ================= STAGE BINDING =================
    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            stage.maximizedProperty().addListener((obs, oldValue, maximized) -> updateWindowChromeStateDeferred());
            stage.fullScreenProperty().addListener((obs, oldValue, fullScreen) -> updateWindowChromeStateDeferred());
            // Listen for window bounds changes to detect pseudo-maximized state
            stage.widthProperty().addListener((obs, oldValue, newValue) -> updateWindowChromeStateDeferred());
            stage.heightProperty().addListener((obs, oldValue, newValue) -> updateWindowChromeStateDeferred());
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
            boolean isMaximized = stage.isMaximized() || stage.isFullScreen() || isWindowFillingScreen();
            updateMaximizedClass(isMaximized);
        });
    }
    
    private boolean isWindowFillingScreen() {
        if (stage == null) {
            return false;
        }
        try {
            Rectangle2D visualBounds = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()
            ).get(0).getVisualBounds();
            
            // Check if window is filling the screen (within 10px tolerance)
            return Math.abs(stage.getX() - visualBounds.getMinX()) < 10 &&
                   Math.abs(stage.getY() - visualBounds.getMinY()) < 10 &&
                   Math.abs(stage.getWidth() - visualBounds.getWidth()) < 10 &&
                   Math.abs(stage.getHeight() - visualBounds.getHeight()) < 10;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateMaximizedClass(boolean maximized) {
        if (appShell == null || windowRoot == null) {
            return;
        }
        // Update maximize button icon: □ (maximize) ↔ ❐ (restore)
        if (maximizeButton != null) {
            maximizeButton.setText(maximized ? "\u2750" : "\u25A1");
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
                if (windowRootClip != null) {
                    windowRootClip.setArcWidth(maximized ? 0 : 32);
                    windowRootClip.setArcHeight(maximized ? 0 : 32);
                }
        }
        if (shellClip != null) {
            shellClip.setArcWidth(maximized ? 0 : 32);
            shellClip.setArcHeight(maximized ? 0 : 32);
        }
    }

    // ================= ROUNDED SHELL CLIP =================
    private void applyRoundedClip() {
            // Clip windowRoot to prevent black corners in dark mode
            windowRootClip = new Rectangle();
            windowRootClip.setArcWidth(32);
            windowRootClip.setArcHeight(32);
            windowRootClip.widthProperty().bind(windowRoot.widthProperty());
            windowRootClip.heightProperty().bind(windowRoot.heightProperty());
            windowRoot.setClip(windowRootClip);
        
            // Clip appShell for consistent rounded corners
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

    // ================= POPUP ROUNDED CORNERS =================
    private void fixPopupTransparency() {
        // On Windows, popup windows (context menus) have a native opaque rectangular
        // background. Setting the scene fill to transparent removes that background
        // so CSS border-radius actually produces visible rounded corners.
        Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window w : change.getAddedSubList()) {
                        if (w instanceof javafx.stage.PopupWindow) {
                            if (w.getScene() != null) {
                                w.getScene().setFill(Color.TRANSPARENT);
                            }
                            w.sceneProperty().addListener((obs, oldScene, newScene) -> {
                                if (newScene != null) {
                                    newScene.setFill(Color.TRANSPARENT);
                                }
                            });
                        }
                    }
                }
            }
        });
        // Also handle any popups already open
        for (Window w : Window.getWindows()) {
            if (w instanceof javafx.stage.PopupWindow && w.getScene() != null) {
                w.getScene().setFill(Color.TRANSPARENT);
            }
        }
    }

    // ================= THEME MENU =================
    private void wireThemeMenu() {
        // ---- Mutually Exclusive Theme Items ----
        ToggleGroup group = new ToggleGroup();

        themeMenuBindings.clear();
        themeMenuBindings.put(themeDarkPurpleItem, "theme-dark-purple");
        themeMenuBindings.put(themeDarkGreenItem, "theme-dark-green");
        themeMenuBindings.put(themeDarkOceanItem, "theme-dark-ocean");
        themeMenuBindings.put(themeDarkEmberItem, "theme-dark-ember");
        themeMenuBindings.put(themeDarkSlateItem, "theme-dark-slate");
        themeMenuBindings.put(themeLightItem, "theme-light");
        themeMenuBindings.put(themeLightSkyItem, "theme-light-sky");
        themeMenuBindings.put(themeLightMintItem, "theme-light-mint");
        themeMenuBindings.put(themeLightRoseItem, "theme-light-rose");
        themeMenuBindings.put(themeLightSandItem, "theme-light-sand");

        for (RadioMenuItem item : themeMenuBindings.keySet()) {
            item.setToggleGroup(group);
        }

        // ---- Theme Switch Actions ----
        themeMenuBindings.forEach((item, themeKey) -> item.setOnAction(e -> applyTheme(themeKey)));
    }

    // ================= THEME APPLY =================
    private void applyTheme(String themeKey) {
        String resolvedTheme = THEME_STYLESHEETS.containsKey(themeKey) ? themeKey : DEFAULT_THEME;
        String modeClass = THEME_MODE.getOrDefault(resolvedTheme, "theme-dark");
        windowRoot.getStyleClass().removeAll("theme-dark", "theme-light");
        windowRoot.getStyleClass().add(modeClass);

        themeMenuBindings.forEach((item, key) -> item.setSelected(key.equals(resolvedTheme)));
        applyThemeStylesheet(resolvedTheme);
    }

    private void applyThemeStylesheet(String themeClass) {
        String cssResourcePath = THEME_STYLESHEETS.get(themeClass);
        if (cssResourcePath == null) {
            return;
        }
        if (windowRoot.getScene() == null) {
            Platform.runLater(() -> applyThemeStylesheet(themeClass));
            return;
        }

        String stylesheet = getClass().getResource(cssResourcePath) != null
                ? getClass().getResource(cssResourcePath).toExternalForm()
                : null;
        if (stylesheet == null) {
            return;
        }

        if (activeThemeStylesheet != null) {
            windowRoot.getScene().getStylesheets().remove(activeThemeStylesheet);
        }
        windowRoot.getScene().getStylesheets().add(stylesheet);
        activeThemeStylesheet = stylesheet;
    }
}
