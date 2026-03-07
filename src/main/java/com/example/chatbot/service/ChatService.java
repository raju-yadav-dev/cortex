package com.example.chatbot.service;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-memory chat service with OpenAI API integration.
 */
public class ChatService {
    // ================= IN-MEMORY STORE =================
    private final List<Conversation> conversations = new ArrayList<>();
    private static final int TITLE_MAX_LENGTH = 28;
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final String APP_PROPERTIES_FILE = "app.properties";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "but", "by",
            "for", "from", "how", "i", "in", "is", "it", "me", "my",
            "of", "on", "or", "please", "so", "that", "the", "this",
            "to", "we", "what", "when", "where", "which", "who", "why",
            "with", "you", "your", "about", "can", "could", "would", "should"
    ));
    private static final String SYSTEM_PROMPT = """
            You are a structured educational assistant.

            Respond ONLY in clean, valid Markdown optimized for HTML rendering inside a JavaFX WebView.

            Formatting rules:
            - Use ## for the main title.
            - Use ### for section headings.
            - Do NOT use horizontal rules like ---.
            - Do NOT manually insert separators.
            - Use numbered lists for methods or types.
            - Use bullet points for explanations.
            - Keep paragraphs short (2-3 lines max).
            - Do NOT generate code unless the user explicitly asks for code, implementation, snippet, or example.
            - When code is explicitly requested, use proper fenced code blocks with language tags.
            - Do not use emojis.
            - Do not add conversational text.
            - Do not repeat the question.
            - Maintain professional tone.
            - Add spacing between sections naturally through Markdown headings.

            The output must render cleanly when converted to HTML and styled with CSS.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("openai-api-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final String apiBaseUrl;
    private final String model;
    private final String apiKey;
    private final SettingsManager settingsManager = SettingsManager.getInstance();

    public ChatService() {
        Properties props = loadAppProperties();
        this.apiBaseUrl = firstNonBlank(
                System.getenv("OPENAI_BASE_URL"),
                props.getProperty("openai_base_url"),
                "https://api.openai.com"
        );
        this.model = firstNonBlank(
                System.getenv("OPENAI_MODEL"),
                props.getProperty("openai_model"),
                DEFAULT_MODEL
        );
        this.apiKey = firstNonBlank(
                System.getenv("OPENAI_API_KEY"),
                System.getenv("PAST_API"),
                props.getProperty("past_api")
        );
    }

    // ================= CONVERSATION API =================
    public Conversation createConversation() {
        Conversation conv = new Conversation("New Chat");
        conversations.add(conv);
        return conv;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    // ================= MESSAGE API =================
    public CompletableFuture<Message> sendMessageAsync(Conversation conv, String text) {
        if (shouldAutoRenameConversation(conv)) {
            conv.setTitle(buildTitleFromUserText(text));
            conv.setTitleFinalized(true);
        }

        Message userMsg = new Message(Message.Sender.USER, text);
        conv.addMessage(userMsg);
        List<Message> historySnapshot = new ArrayList<>(conv.getMessages());

        return CompletableFuture.supplyAsync(() -> requestAssistantReply(historySnapshot), apiExecutor);
    }

    public void appendAssistantMessage(Conversation conv, Message botMessage) {
        if (conv != null && botMessage != null) {
            conv.addMessage(botMessage);
        }
    }

    /**
     * Ask a contextual question about selected text. Returns a future with the AI response.
     */
    public CompletableFuture<String> askAboutSelection(String selectedText, String question) {
        return CompletableFuture.supplyAsync(() -> {
            String contextPrompt = "The user selected the following text:\n\n"
                    + selectedText + "\n\nUser question: " + question;
            List<Message> context = List.of(new Message(Message.Sender.USER, contextPrompt));
            Message reply = requestAssistantReply(context);
            return reply.getContent();
        }, apiExecutor);
    }

    private Message requestAssistantReply(List<Message> historySnapshot) {
        if (apiKey == null || apiKey.isBlank() || "PASTE_YOUR_API_KEY_HERE".equals(apiKey)) {
            return new Message(Message.Sender.BOT, """
                    API key is missing.

                    Configure it in external `../app.properties`:

                    ```properties
                    past_api=PASTE_YOUR_API_KEY_HERE
                    ```

                    You can also set environment variable `OPENAI_API_KEY`.
                    """);
        }

        try {
            String body = buildChatRequestJson(historySnapshot);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(apiBaseUrl) + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = extractErrorMessage(response.body());
                return new Message(Message.Sender.BOT,
                        "I could not call the AI API (HTTP " + response.statusCode() + ").\n\n- " + error);
            }

            String content = extractAssistantContent(response.body());
            return new Message(Message.Sender.BOT, content);
        } catch (Throwable ex) {
            return new Message(Message.Sender.BOT,
                    "I could not reach the AI API.\n\n- " + ex.getMessage());
        }
    }

    private String buildChatRequestJson(List<Message> historySnapshot) {
        List<Message> sorted = historySnapshot.stream()
                .sorted(Comparator.comparing(Message::getTimestamp))
                .toList();

        double temperature = settingsManager.getDouble("ai.temperature", 0.4);
        int maxTokens = settingsManager.getInt("ai.maxTokens", 4096);
        String customPrompt = settingsManager.getString("ai.systemPrompt", "");
        String effectivePrompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : SYSTEM_PROMPT;

        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"model\":\"").append(jsonEscape(model)).append("\",");
        builder.append("\"temperature\":").append(temperature).append(",");
        builder.append("\"max_tokens\":").append(maxTokens).append(",");
        builder.append("\"messages\":[");
        builder.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(effectivePrompt)).append("\"}");

        for (Message msg : sorted) {
            String role = msg.getSender() == Message.Sender.USER ? "user" : "assistant";
            builder.append(",{\"role\":\"")
                    .append(role)
                    .append("\",\"content\":\"")
                    .append(jsonEscape(msg.getContent()))
                    .append("\"}");
        }

        builder.append("]}");
        return builder.toString();
    }

    private boolean shouldAutoRenameConversation(Conversation conv) {
        if (conv.isTitleFinalized()) {
            return false;
        }
        String currentTitle = conv.getTitle();
        return currentTitle == null || currentTitle.isBlank() || "New Chat".equalsIgnoreCase(currentTitle.trim());
    }

    private String buildTitleFromUserText(String text) {
        if (text == null) {
            return "New Chat";
        }
        String normalized = text.replaceAll("https?://\\S+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "New Chat";
        }

        String inferred = inferTopicTitle(normalized);
        if (inferred.length() <= TITLE_MAX_LENGTH) {
            return inferred;
        }
        return inferred.substring(0, TITLE_MAX_LENGTH - 3).trim() + "...";
    }

    private String inferTopicTitle(String normalized) {
        String[] words = normalized.replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");

        List<String> keywords = new ArrayList<>();
        for (String rawWord : words) {
            if (rawWord.isBlank()) {
                continue;
            }
            String lower = rawWord.toLowerCase(Locale.ROOT);
            if (lower.length() < 3 || STOP_WORDS.contains(lower)) {
                continue;
            }
            keywords.add(toTitleCase(lower));
            if (keywords.size() == 4) {
                break;
            }
        }

        if (!keywords.isEmpty()) {
            return String.join(" ", keywords);
        }

        String fallback = normalized;
        int questionMark = fallback.indexOf('?');
        if (questionMark > 0) {
            fallback = fallback.substring(0, questionMark);
        }
        fallback = fallback.trim();
        if (fallback.isEmpty()) {
            return "New Chat";
        }
        String[] fallbackWords = fallback.split(" ");
        int take = Math.min(4, fallbackWords.length);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(toTitleCase(fallbackWords[i].toLowerCase(Locale.ROOT)));
        }
        return builder.toString();
    }

    private String toTitleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Properties loadAppProperties() {
        Properties properties = new Properties();
        // Preferred order:
        // 1) explicit path (app.config.path / APP_CONFIG_PATH),
        // 2) parent folder (../app.properties),
        // 3) working folder (./app.properties),
        // 4) classpath fallback for older setups.
        if (loadPropertiesFromFile(properties, resolveConfiguredPropertiesPath())) {
            return properties;
        }
        if (loadPropertiesFromFile(properties, Path.of("..", APP_PROPERTIES_FILE).toAbsolutePath().normalize())) {
            return properties;
        }
        if (loadPropertiesFromFile(properties, Path.of(APP_PROPERTIES_FILE).toAbsolutePath().normalize())) {
            return properties;
        }
        try (InputStream input = getClass().getResourceAsStream("/" + APP_PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Keep defaults if config file cannot be read.
        }
        return properties;
    }

    private Path resolveConfiguredPropertiesPath() {
        String configuredPath = firstNonBlank(
                System.getProperty("app.config.path"),
                System.getenv("APP_CONFIG_PATH")
        );
        if (configuredPath == null) {
            return null;
        }
        try {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean loadPropertiesFromFile(Properties properties, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String extractAssistantContent(String json) {
        int messageIndex = json.indexOf("\"message\"");
        if (messageIndex < 0) {
            return "I received a response, but could not parse assistant content.";
        }
        int contentKeyIndex = json.indexOf("\"content\"", messageIndex);
        String content = extractJsonStringValueAtKey(json, contentKeyIndex);
        return content == null ? "I received a response, but could not parse assistant content." : content;
    }

    private String extractErrorMessage(String json) {
        int errorIndex = json.indexOf("\"error\"");
        int messageKeyIndex = errorIndex >= 0
                ? json.indexOf("\"message\"", errorIndex)
                : json.indexOf("\"message\"");
        String message = extractJsonStringValueAtKey(json, messageKeyIndex);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Unknown API error.";
    }

    private String extractJsonStringValueAtKey(String json, int keyIndex) {
        if (json == null || keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return null;
        }

        int startQuote = -1;
        for (int i = colonIndex + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '"') {
                startQuote = i;
            }
            break;
        }
        if (startQuote < 0) {
            return null;
        }

        StringBuilder raw = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                raw.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                raw.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return jsonUnescape(raw.toString());
            }
            raw.append(ch);
        }
        return null;
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private String jsonUnescape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
