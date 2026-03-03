# Chatbot Desktop (JavaFX)

A minimal ChatGPT-like desktop chatbot built with Java 17+, JavaFX, Gradle, using an MVC-style architecture. The current implementation uses a mock service for AI responses. The project is structured for easy extension and production readiness.

## Project Structure
```
ChatbotApp/
├── build.gradle
├── settings.gradle
├── README.md
└── src/main/
    ├── java/com/example/chatbot/
    │   ├── MainApp.java             # Application entry point
    │   ├── controller/
    │   │   ├── MainController.java  # Handles overall layout and chat switching
    │   │   ├── ChatController.java  # Manages a single conversation view
    │   │   └── ConversationCell.java# Custom ListCell for sidebar
    │   ├── model/
    │   │   ├── Conversation.java    # Holds messages in a chat
    │   │   └── Message.java         # Represents a message with timestamp
    │   └── service/
    │       └── ChatService.java     # In-memory conversation manager with mock AI
    └── resources/
        ├── css/styles.css          # Dark mode styles and animations
        └── fxml/
            ├── main.fxml           # Sidebar + container layout
            └── chat.fxml           # Chat area (messages + input)
```

## Key Features
- **Sidebar**: New chat button; list of previous conversations.
- **Chat area**: Message bubbles (user right, bot left), timestamps, scrolling.
- **Input**: Multiline `TextArea` with Enter to send, Shift+Enter for newline.
- **State**: Conversations held in memory; multiple chats supported.
- **Styling**: Dark theme, rounded corners, simple animations via CSS.
- **Architecture**: MVC; controllers separate UI logic, services manage data.

## How Each Module Works
1. **MainApp**: Launches JavaFX, loads `main.fxml`, applies CSS, sets window.
2. **MainController**: Initializes sidebar, keeps a single `ChatService` instance for conversation list. Handles creation and selection of chats. When a conversation is chosen, loads `chat.fxml` and injects its controller.
3. **ChatController**: Receives a `Conversation` object and displays messages. Sends input to `ChatService` which appends user and bot messages. UI updates and scrolls automatically.
4. **ChatService**: Simple in-memory list; `createConversation()` returns a new titled conversation. `sendMessage()` adds the user message and a mocked bot response.
5. **Models**: `Message` holds sender, content, timestamp; `Conversation` wraps a list of messages with a title.
6. **FXML Layouts**: `main.fxml` defines a `BorderPane` with a `VBox` sidebar and container. `chat.fxml` defines a message scroll area and input bar.
7. **CSS**: Styles define dark backgrounds, bubble colors, fonts, and basic control styling.

## Running the Project
1. **Prerequisites**: Java 17+ installed. Gradle (or use the included wrapper by running `gradlew` on Windows).
2. Open a terminal in project root (`d:\GitHub\Projects\AI\another`).
3. To run:
   ```powershell
   ./gradlew run
   ```
4. To build a JAR:
   ```powershell
   ./gradlew clean jar
   ```
   The resulting JAR is in `build/libs/` and can be executed with:
   ```powershell
   java -jar build/libs/ChatbotApp.jar
   ```

## Extending for Real AI API
- The app now uses OpenAI Chat Completions from `ChatService` with asynchronous calls.
- Set API key in `../app.properties` (outside `ai-project`):
  ```properties
  past_api=PASTE_YOUR_API_KEY_HERE
  ```
- You can also use environment variable `OPENAI_API_KEY` (takes priority over `past_api`).
- Optional override path: set `APP_CONFIG_PATH` or JVM arg `-Dapp.config.path=...`.
- Optional:
  - `openai_model=gpt-4.1-mini`
  - `openai_base_url=https://api.openai.com`

## Future Improvements
- **Markdown Rendering**: integrate a library like `flexmark-java` and render in a `WebView` or custom control.
- **Typing Indicator**: show an animation while waiting for AI response.
- **Persistent Storage**: serialize conversations to disk (JSON or database) to resume later.
- **Dark/Light Theme Switcher**: toggle CSS at runtime.
- **Animations**: use `TranslateTransition` or `FadeTransition` for smooth message entry.
- **Unit Tests**: add tests for models and service layer.

Feel free to customize UI further; the current code is commented and designed for clarity while remaining production-minded.
