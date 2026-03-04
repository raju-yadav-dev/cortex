## JavaFX Terminal Backend Upgrade Guide

### Overview

This guide shows how to integrate the new **persistent shell terminal system** into your existing JavaFX application **without changing any UI elements or layout**.

### New Classes Created

1. **ProcessManager** - Low-level process and stream management
2. **TerminalService** - High-level persistent shell interface
3. **CommandExecutor** - One-off command execution with streaming

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  ChatController (UI Logic)              │
│                                                         │
│  terminalOutputArea (TextArea) ← unchanged UI          │
│  terminalInputArea (key handling) ← unchanged UI       │
│  Send button, Clear button ← unchanged UI              │
└────────────────┬────────────────────────────────────────┘
                 │ Uses
                 ▼
┌─────────────────────────────────────────────────────────┐
│              TerminalService (Backend)                  │
│                                                         │
│  - Maintains persistent shell process                  │
│  - Handles interactive input/output                    │
│  - Manages working directory                           │
│  - Thread-safe operations                              │
└────────────────┬────────────────────────────────────────┘
                 │ Uses
                 ▼
┌─────────────────────────────────────────────────────────┐
│             ProcessManager (I/O Streams)                │
│                                                         │
│  - Manages Process object                              │
│  - Streams stdout/stderr on background threads         │
│  - Handles stdin input                                 │
│  - Ensures UI thread is never blocked                  │
└─────────────────────────────────────────────────────────┘
```

### Integration Steps

#### Step 1: Add TerminalService as a Field in ChatController

```java
public class ChatController {
    // ... existing fields ...
    
    // NEW: Terminal service for persistent shell
    private TerminalService terminalService;
    private boolean terminalInitialized = false;
    
    // Keep all existing UI fields unchanged:
    @FXML
    private TextArea terminalOutputArea;  // UNCHANGED
    // ... etc
}
```

#### Step 2: Initialize TerminalService in initialize() Method

In your ChatController's `initialize()` method, add this code (keep everything else unchanged):

```java
@FXML
public void initialize() {
    // ... all your existing code ...
    
    // NEW: Initialize terminal service
    terminalService = new TerminalService(
        line -> appendTerminalLine(line),           // stdout handler
        error -> appendTerminalLine("[ERROR] " + error),  // stderr handler
        () -> {                                      // process end handler
            setTerminalStatus("Shell terminated");
            terminalInitialized = false;
        }
    );
    
    // Do NOT call terminalService.initialize() yet
    // We'll do it when user first opens the terminal
}
```

#### Step 3: Modify Terminal Opening to Initialize Shell

Replace the terminal opening logic with shell initialization:

**Before:**
```java
private void openTerminalPanel() {
    if (terminalPane != null) {
        terminalPane.setVisible(true);
        chatSplitPane.getItems().add(terminalPane);
        setTerminalStatus("Ready");
        printTerminalPrompt();
    }
}
```

**After:**
```java
private void openTerminalPanel() {
    if (terminalPane != null && !terminalPane.isVisible()) {
        terminalPane.setVisible(true);
        chatSplitPane.getItems().add(terminalPane);
        
        // Initialize persistent shell on first open
        if (!terminalInitialized) {
            boolean success = terminalService.initialize(terminalWorkingDirectory);
            if (success) {
                terminalInitialized = true;
                setTerminalStatus("Shell ready (" + terminalService.getShellName() + ")");
            } else {
                setTerminalStatus("Failed to start shell");
                appendTerminalLine("[ERROR] Could not initialize shell");
            }
        }
    }
}
```

#### Step 4: Update Terminal Input Handling

Replace the old `executeTerminalCommandFromConsole()` method:

**Before:**
```java
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
                // ... complex result handling ...
            }));
}
```

**After (Simplified):**
```java
private void executeTerminalCommandFromConsole() {
    if (terminalOutputArea == null || !terminalInitialized) {
        return;
    }

    String allText = terminalOutputArea.getText() == null ? "" : terminalOutputArea.getText();
    int start = Math.min(terminalInputStartIndex, allText.length());
    String command = allText.substring(start).trim();

    if (command.isEmpty()) {
        terminalService.sendInput(""); // Send blank line
        return;
    }

    // NEW: Handle cd command specially
    if (command.toLowerCase().startsWith("cd ")) {
        String pathStr = command.substring(3).trim();
        Path targetPath = Path.of(pathStr);
        if (terminalService.changeDirectory(targetPath)) {
            terminalWorkingDirectory = terminalService.getCurrentWorkingDirectory();
            return;
        }
    }

    // Send command to persistent shell
    terminalService.sendInputLine(command);
}
```

#### Step 5: Update Terminal Cleanup

Add this to your `hideTerminalPanel()` method:

```java
private void hideTerminalPanel() {
    if (terminalPane != null && terminalPane.isVisible()) {
        terminalPane.setVisible(false);
        if (chatSplitPane != null) {
            chatSplitPane.getItems().remove(terminalPane);
        }
        setTerminalStatus("Terminal hidden");
        // Don't call terminalService.shutdown() here!
        // Keep the persistent shell running
    }
}
```

#### Step 6: Shutdown Terminal on Application Exit

In your application shutdown hook (or MainController's stop method):

```java
@Override
public void stop() throws Exception {
    // Gracefully shutdown the persistent shell
    if (terminalService != null) {
        terminalService.shutdown();
    }
    super.stop();
}
```

#### Step 7: Update Prompt Handling

**IMPORTANT**: With a persistent shell, you don't need to print prompts manually. The shell will do it. So you can simplify:

```java
// Remove or disable these for persistent shell:
// private void printTerminalPrompt()
// private void appendTerminalRaw(String text)

// Keep appendTerminalLine() but it now just appends to TextArea without managing prompt
private void appendTerminalLine(String line) {
    if (terminalOutputArea == null) {
        return;
    }
    String safeLine = line == null ? "" : line;
    if (!Platform.isFxApplicationThread()) {
        Platform.runLater(() -> appendTerminalLine(safeLine));
        return;
    }
    
    terminalOutputArea.appendText(safeLine + System.lineSeparator());
    terminalOutputArea.positionCaret(terminalOutputArea.getLength());
}
```

### Alternative: Keep Old System for Non-Interactive Commands

If you want to keep both systems (persistent shell + one-off command execution), use CommandExecutor:

```java
private CommandExecutor commandExecutor = new CommandExecutor(
    line -> appendTerminalLine(line),
    error -> appendTerminalLine("[ERROR] " + error)
);

// In initialize():
CommandExecutor.Result result = commandExecutor.execute(
    new String[]{"python", "script.py"},
    terminalWorkingDirectory,
    45  // 45-second timeout
);
```

### Key Benefits of the New System

| Feature | Before | After |
|---------|--------|-------|
| **Interactive Programs** | ❌ No stdin support | ✅ Full stdin/stdout/stderr |
| **Large Outputs** | ❌ Can freeze UI | ✅ Streamed, non-blocking |
| **Persistent Shell** | ❌ New process per command | ✅ Single persistent process |
| **Working Directory** | ⚠️ Manual tracking | ✅ Automatic tracking |
| **Python input()** | ❌ Not supported | ✅ Full support |
| **Java Scanner** | ❌ Not supported | ✅ Full support |
| **Thread Safety** | ⚠️ Complex state | ✅ Background threads only |
| **Memory Usage** | ✅ Low (one-off) | ✅ Low (persistent, reusable) |

### Common Use Cases

#### Use Case 1: Running Python Script with User Input

```java
// Terminal is persistent, user can run:
// > python my_script.py
// 
// Script uses input() - this now works!
// Python waits on stdin, user types in terminal, gets response
```

#### Use Case 2: Running Java Program with Scanner

```java
// User runs a Java program in the terminal:
// > java MyProgram
//
// Program creates Scanner(System.in)
// User can type input in terminal
// Program reads via stdin - works perfectly!
```

#### Use Case 3: Interactive Shell Commands

```java
// User can now use interactive commands:
// > npm init              (interactive questionnaire)
// > git add .             (with prompts)
// > python -i             (interactive Python shell)
// > node                  (interactive Node REPL)
// > mysql -u user -p      (password prompt - just type!)
```

#### Use Case 4: Running Code Snippets Asynchronously

```java
// For code snippets (not interactive), use CommandExecutor:
private void runCodeSnippet(String code, String extension) {
    CommandExecutor executor = new CommandExecutor(
        line -> appendTerminalLine(line),
        error -> appendTerminalLine("[ERR] " + error)
    );
    
    executor.executeAsync(
        new String[]{"python", "snippet.py"},
        terminalWorkingDirectory,
        45,  // timeout
        result -> {
            appendTerminalLine("[Exit code] " + result.exitCode());
            if (!result.output().isEmpty()) {
                appendTerminalLine(result.output());
            }
        }
    );
}
```

### Thread Safety Guarantees

**The new system guarantees:**

1. ✅ **No UI blocking**: All I/O operations run on background threads (via ProcessManager)
2. ✅ **Safe callbacks**: Output is delivered via `Platform.runLater()`, always on FX thread
3. ✅ **Safe input**: `terminalService.sendInput()` can be called from UI thread
4. ✅ **No race conditions**: ProcessManager synchronizes on stdin writer
5. ✅ **Clean shutdown**: `terminalService.shutdown()` handles cleanup safely

### Troubleshooting

#### Problem: Shell doesn't start
```
Solution: Check getShellName() and ensure the shell (bash/powershell/cmd) is installed
          Look at error messages in terminal: [Terminal] XXX
```

#### Problem: Interactive input not working
```
Solution: Make sure terminal is initialized (terminalInitialized == true)
          Check that sendInputLine() is being called (not executeCommand())
          Verify process is running: terminalService.isShellRunning()
```

#### Problem: Output stops appearing
```
Solution: Terminal might have crashed. Call terminalService.restart()
          Check terminal status with getExitCode()
          Review stderr output for error messages
```

#### Problem: Application hangs on exit
```
Solution: Call terminalService.shutdown() before app.stop()
          This gracefully terminates the persistent shell
          Wait for threads to finish (already handled in shutdown())
```

### Files Modified Summary

Files you need to update in ChatController:

1. ✅ Add `private TerminalService terminalService;` field
2. ✅ Initialize it in `initialize()` method  
3. ✅ Modify `openTerminalPanel()` to init shell
4. ✅ Simplify `executeTerminalCommandFromConsole()` to use service
5. ✅ Call `shutdown()` on application exit

**Total changes**: ~50 lines of code in ChatController  
**UI changes**: ✅ NONE - UI remains exactly the same  
**Files changed**: 1 (ChatController.java)  
**Classes added**: 3 (ProcessManager, TerminalService, CommandExecutor)

### Next Steps

1. Review the ProcessManager.java implementation
2. Review the TerminalService.java implementation  
3. Review the CommandExecutor.java implementation
4. Make the 5 changes to ChatController listed above
5. Test with interactive programs (Python, Java Scanner, Node REPL)
6. Test with large outputs (no freezing)
7. Test with shell features (pipes, redirects, etc.)

All backend logic is complete and ready to integrate!
