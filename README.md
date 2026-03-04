# AI Chatbot Desktop Application (JavaFX)

A desktop chatbot application built with Java 17+, JavaFX, and Gradle using an MVC-style architecture. This chatbot integrates with OpenAI's API (or compatible services) to provide intelligent conversational responses.

## How to Use This Chatbot

### Getting Started
1. **Create Conversations**: Click the "New Chat" button in the sidebar to start a new conversation.
2. **Send Messages**: Type your message in the input area at the bottom and press **Enter** to send. Use **Shift+Enter** to create a new line without sending.
3. **View Chat History**: Your previous conversations appear in the left sidebar. Click on any conversation to view and continue that chat.
4. **Multiple Chats**: Keep multiple conversations open simultaneously and switch between them using the sidebar.

### Features
- **Chat Bubbles**: Your messages appear on the right, AI responses appear on the left
- **Timestamps**: Each message includes a timestamp for reference
- **Dark Theme**: Modern dark interface for comfortable reading and typing
- **Responsive UI**: Real-time message updates and smooth scrolling
- **Conversation Management**: Create, view, and manage multiple conversations

### Code Execution
Cortex supports executing code snippets directly in the terminal. You can run code in multiple programming languages with built-in compilation and execution.

#### Supported Languages

| Language | Compilers/Runtimes | Status |
|----------|-------------------|--------|
| **C** | gcc, clang, cl (MSVC), tcc, cc | ✅ Full Support |
| **C++** | g++, clang++, cl (MSVC), c++, tcc | ✅ Full Support |
| **Python** | python, py, python3 | ✅ Full Support |
| **JavaScript** | Node.js | ✅ Full Support |
| **Java** | javac, java | ✅ Full Support |
| **Bash** | bash | ✅ Full Support |
| **PowerShell** | pwsh, powershell | ✅ Full Support |

**How to Use Code Execution:**
1. Paste code in a markdown code block with language tag (e.g., ` ```c ` or ` ```python `)
2. Click the **Run** button that appears on the code block
3. Output displays in the terminal panel on the right
4. Use **Shift+Enter** in the terminal for multi-line input

#### Compiler Detection
- Compilers are auto-detected on startup (cached for performance)
- Windows: Supports MSVC (`cl`), GCC, Clang, and TCC
- Linux/Mac: Supports GCC, Clang, and system C compiler
- If multiple compilers available, uses order: gcc → clang → MSVC → custom


Before running the application, ensure you have:
- **Java 17 or higher** installed on your system
- **Gradle** (included as a wrapper, so you can use `./gradlew` on Windows)
- **API Credentials** for OpenAI or a compatible service

## Configuration Setup (IMPORTANT)

The application requires an `app.properties` file to function. **This file must be created outside the `ai-project` folder**, at the parent level.

### Step 1: Create the Configuration File

1. Navigate to `d:\GitHub\Cortex\` (the parent directory of `ai-project`)
2. Create a new file named `app.properties`
3. Add the following required configuration:

```properties
past_api=YOUR_API_KEY_HERE
openai_model=YOUR_MODEL_NAME_HERE
openai_base_url=YOUR_BASE_URL_HERE
```

### Step 2: Fill in Your Details

Replace the placeholders with your actual values:

- **`past_api`**: Your OpenAI API key (e.g., `sk-xxxxxxxxxxxxxxxx`)
- **`openai_model`**: The model to use (e.g., `gpt-4`, `gpt-4-turbo`, `gpt-3.5-turbo`)
- **`openai_base_url`**: The API endpoint URL (e.g., `https://api.openai.com/v1`)

Example configuration:
```properties
past_api=sk-proj-1234567890abcdefghijklmnopqrstuv
openai_model=gpt-4-turbo
openai_base_url=https://api.openai.com/v1
```

### File Location
```
d:\GitHub\Cortex\
├── app.properties          ← Create this file here
└── ai-project/
    ├── build.gradle
    ├── README.md
    └── src/
```

**Note**: The application will look for `app.properties` in the parent directory. Without this file and proper configuration, the chatbot will not be able to communicate with the AI service.

## Building and Running

### Run the Application
Open a terminal in the `ai-project` directory and run:

```powershell
./gradlew run
```

### Build a JAR Executable
To create a compiled JAR file:

```powershell
./gradlew clean jar
```

The JAR will be located in `build/libs/ChatbotApp.jar` and can be run with:

```powershell
java -jar build/libs/ChatbotApp.jar
```

## Project Architecture

The application follows an MVC (Model-View-Controller) pattern:

- **Controllers**: Handle UI logic and user interactions
- **Models**: Represent data structures (Messages, Conversations)
- **Services**: Manage business logic and API communication
- **Resources**: FXML layouts, CSS styling, and assets
- **UI Components**: JavaFX controls for chat bubbles and message display

## Environment Variables (Optional)

You can also use environment variables to configure the application:

- `OPENAI_API_KEY`: Set this to override the `past_api` property
- `APP_CONFIG_PATH`: Specify a custom path for the `app.properties` file

## Troubleshooting

- **"app.properties not found"**: Ensure the file is created in the correct location (parent directory of `ai-project`)
- **API errors**: Verify your API key is valid, the model name is correct, and the base URL is accessible
- **Java version errors**: Ensure you have Java 17 or higher installed

## Future Enhancements

- Persistent chat history storage to disk
- Markdown message rendering
- Message search and filtering
- Custom theme switching
- Export conversations as PDF or text
