## Terminal Backend System: Visual Overview

### Before vs After Comparison

#### BEFORE: Spawn-Per-Command (Old System)

```
User: python script.py ↵

ChatController
  └─> runShellCommand(command)
      └─> ProcessBuilder.start()
          └─ Process P1 starts
          └─ Executes: python script.py
          └─ Reads entire output
          └─ Process exits
          └─ Appends to TextArea
          
Result:
  ✓ Works for simple commands
  ✗ No stdin support (interactive programs hang)
  ✗ Buffers entire output (large outputs freeze UI)
  ✗ Process overhead (spawn/wait per command)
  ✗ No shell features (pipes, variables, etc.)

Timeline:
  User types ──> Submit ──> Process start ──> Read output ──> Display
  (user waits)    (instant)  (100-200ms)      (may freeze)   (once done)
```

#### AFTER: Persistent Shell (New System)

```
Terminal Open

ChatController
  └─> terminalService.initialize()
      └─ ProcessManager.startProcess(shell)
         └─ Process Shell starts (persistent!)
         └─ Waits for input
         └─ Three background threads spawned:
            • Stdout reader (streaming output)
            • Stderr reader (streaming errors)
            • Waiter (process lifetime monitor)

User: python script.py ↵

ChatController
  └─> terminalService.sendInputLine(command)
      └─ ProcessManager.sendInput(command)
         └─ Sends to Process stdin (shell process)
         └─ ProcessManager returns immediately
         
Shell Process
  └─ Reads command from stdin
  └─ Executes: python script.py
  └─ Script runs, produces output
  └─ Writes to stdout
  └─ (Script can read from stdin if needed)

Stdout Reader Thread (background)
  └─ Continuously reading from shell's stdout
  └─ For each line:
     └─ Platform.runLater( () -> appendTerminalLine(line) )
     └─ Returns immediately (non-blocking)
  └─ Result: Output appears progressively in TextArea

Result:
  ✓ Stdin works (interactive programs receive input!)
  ✓ No freezing (output streamed in real-time)
  ✓ Low overhead (single persistent shell)
  ✓ Shell features work (pipes, variables, etc.)

Timeline:
  User types ──> Submit ──> Send to stdin ──> Shell receives
  (instant)     (instant)   (instant)        (starts executing)
                                    ↓
                            Output starts streaming
                                    ↓
                            Stdout thread reads
                                    ↓
                            appendTerminalLine()
                                    ↓
                            TextArea updates progressively
                                    ↓
                            User sees output instantly
```

---

### Component Interaction Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                       User (JavaFX UI)                           │
│                                                                  │
│  TextArea: terminalOutputArea                                    │
│  Button: sendButton, clearButton, closeButton                   │
│  KeyEvent handlers: handleTerminalKeyPressed                     │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    Data: command text
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                ChatController (UI Logic)                         │
│                                                                  │
│  • Handle user input from TextArea                              │
│  • Extract command from typed text                              │
│  • Call terminalService.sendInputLine(cmd)                      │
│  • Append output via appendTerminalLine(line)                   │
│  • Unchanged: UI layout, styling, component names               │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    Method: sendInputLine()
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│           TerminalService (Shell Management)                     │
│                                                                  │
│ Method          Purpose                                          │
│ ─────────────────────────────────────────────────────────────── │
│ initialize()        Start persistent shell process               │
│ sendInputLine()     Send command with newline                    │
│ sendInput()         Send raw input (no newline)                  │
│ changeDirectory()   Change working directory                     │
│ isShellRunning()    Check if shell alive                         │
│ shutdown()          Stop shell gracefully                        │
│ restart()           Recover from crash                           │
│                                                                  │
│ Features:                                                        │
│ • Detects shell (bash/pwsh/cmd)                                │
│ • Manages working directory                                     │
│ • Thread-safe API (can call from FX thread)                     │
│ • Handles callbacks from background threads                      │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    Method: sendInput()
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│         ProcessManager (I/O & Stream Handling)                   │
│                                                                  │
│ startProcess()                                                   │
│   ├─ ProcessBuilder.start()                                     │
│   ├─ Get stdin/stdout/stderr streams                            │
│   └─ Spawn 3 background threads                                 │
│                                                                  │
│ sendInput()                                                      │
│   └─ Write to stdin (synchronized, thread-safe)                 │
│                                                                  │
│ Background Threads:                                              │
│   ├─ Stdout Reader                                              │
│   │  └─ Reads shell's stdout line-by-line                      │
│   │     └─ Platform.runLater( () -> onOutput.accept(line) )    │
│   │                                                             │
│   ├─ Stderr Reader                                              │
│   │  └─ Reads shell's stderr line-by-line                      │
│   │     └─ Platform.runLater( () -> onError.accept(line) )     │
│   │                                                             │
│   └─ Waiter Thread                                              │
│      └─ process.waitFor() (blocked until process ends)         │
│         └─ Platform.runLater( () -> onProcessEnd.run() )       │
│                                                                  │
│ Thread Safety:                                                   │
│ • volatile flags prevent race conditions                         │
│ • synchronized(stdin) prevents garbled input                    │
│ • Platform.runLater() ensures UI thread safety                  │
│ • Daemon threads auto-cleanup on app exit                       │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    Pipes: stdin/stdout/stderr
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                  Java ProcessBuilder                             │
│                                                                  │
│  Process: /bin/bash (Linux) or cmd.exe (Windows)               │
│                                                                  │
│  stdin  ◄──────── Commands from user (shell reads)             │
│  stdout ────────► Output from commands (we read)               │
│  stderr ────────► Errors from commands (we read)               │
│                                                                  │
│  Status: Running persistently until shutdown()                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### Data Flow: User Inputs Command

```
1. TYPING
   ┌─────────────────────────┐
   │   TextArea receives      │
   │   key events            │
   │   "python my.py"        │
   └──────────┬──────────────┘
              │
2. KEY EVENT
   ┌─────────────────────────┐
   │ handleTerminalKeyPressed│
   │ (Enter key detected)    │
   └──────────┬──────────────┘
              │
3. EXTRACT
   ┌─────────────────────────┐
   │ executeTerminalCommand  │
   │ extract("python my.py") │
   │ from TextArea text      │
   └──────────┬──────────────┘
              │
4. SEND
   ┌─────────────────────────┐
   │ terminalService.        │
   │   sendInputLine(cmd)    │
   │ (queue for sending)     │
   └──────────┬──────────────┘
              │
5. WRITE TO PIPE
   ┌─────────────────────────┐
   │ ProcessManager.         │
   │   sendInput(cmd)        │
   │ (write to stdin)        │
   │ synchronized - no data  │
   │ corruption              │
   └──────────┬──────────────┘
              │
6. SHELL RECEIVES
   ┌─────────────────────────┐
   │ Shell process reads     │
   │ from stdin              │
   │ "python my.py"          │
   └──────────┬──────────────┘
              │
7. EXECUTE
   ┌─────────────────────────┐
   │ Shell executes:         │
   │ python my.py            │
   │ (spawns subprocess)     │
   └──────────┬──────────────┘
              │
8. OUTPUT
   ┌─────────────────────────┐
   │ Program produces output │
   │ writes to stdout        │
   └──────────┬──────────────┘
              │
              ▼
       (continues on next ->)
```

### Data Flow: Shell Produces Output

```
(continued from above)

1. STDOUT PRODUCED
   ┌──────────────────────────────┐
   │ Python script writes:         │
   │ "Hello, World!"              │
   │ (to process stdout)           │
   └──────────┬─────────────────────┘
              │
2. STDOUT READER WAKES UP
   ┌──────────────────────────────┐
   │ Reader thread (daemon):       │
   │ readline() returns            │
   │ "Hello, World!"              │
   │ (from stdout stream)          │
   └──────────┬─────────────────────┘
              │
3. CALLBACK INVOKED (off FX thread)
   ┌──────────────────────────────┐
   │ outputConsumer.accept(line)   │
   │ (this is ProcessManager's    │
   │  callback)                    │
   └──────────┬─────────────────────┘
              │
4. FX THREAD DISPATCH
   ┌──────────────────────────────┐
   │ Platform.runLater(            │
   │   () -> callback()            │
   │ )                             │
   │ (queue work for FX thread)    │
   └──────────┬─────────────────────┘
              │
5. FX THREAD PROCESSES
   ┌──────────────────────────────┐
   │ FX thread (on next pulse):    │
   │ executes queued work          │
   │ onOutput.accept(line)         │
   │ (TerminalService's callback)  │
   └──────────┬─────────────────────┘
              │
6. APPEND TO UI
   ┌──────────────────────────────┐
   │ appendTerminalLine(line)      │
   │ terminalOutputArea.append(    │
   │   "Hello, World!"             │
   │ )                             │
   └──────────┬─────────────────────┘
              │
7. RENDER
   ┌──────────────────────────────┐
   │ TextArea renders:             │
   │ "Hello, World!"              │
   │ on screen                     │
   └──────────────────────────────┘
              │
              ▼
      USER SEES OUTPUT INSTANTLY!
```

---

### Interactive Program Flow

```
Python Script:
  name = input("What's your name? ")
  print(f"Hello, {name}!")

Timeline:
─────────────────────────────────────────────────────────────────

00ms: User types: python script.py ↵
      └─ Shell executes: python script.py

50ms: Script asks: "What's your name? "
      └─ Script calls: input()
      └─ Script waits on stdin...
      └─ Stdout reader sees question
         └─ appendTerminalLine("What's your name? ")
         └─ User sees question in TextArea

100ms: User types: Alice ↵
       └─ handleTerminalKeyPressed(Enter)
       └─ executeTerminalCommandFromConsole()
       └─ terminalService.sendInputLine("Alice")
       └─ ProcessManager.sendInput("Alice")
       └─ Writes to process stdin

110ms: Shell is connected to stdin
       └─ input() receives "Alice\n"
       └─ Script executes: print(f"Hello, Alice!")

120ms: Script produces output: "Hello, Alice!"
       └─ Stdout reader sees it
       └─ appendTerminalLine("Hello, Alice!")
       └─ User sees response in TextArea

130ms: Script exits
       └─ Waiter thread detects process end
       └─ Calls onProcessEnd()
       └─ Status updates: "Ready"

Result: Perfect interactive experience!
```

**Key Point:** Because we maintain a persistent shell process with proper stdin/stdout/stderr connections, interactive programs work perfectly. The script can:
- Wait on stdin (input() or Scanner())
- User types response in terminal
- Shell passes input to waiting process
- Process produces more output
- All visible in real-time!

---

### Class Responsibilities

```
ProcessManager (I/O Layer)
├─ Responsibilities:
│  ├─ Create Process object
│  ├─ Manage stdin/stdout/stderr streams
│  ├─ Spawn 3 reader/waiter threads
│  ├─ Write to stdin (synchronized)
│  └─ Call callbacks on main/background threads
│
├─ Thread Model:
│  ├─ Main thread: constructor, public methods
│  ├─ Stdout thread: reads Process.getInputStream()
│  ├─ Stderr thread: reads Process.getErrorStream()
│  └─ Waiter thread: waits process.waitFor()
│
└─ Example:
   ProcessManager pm = new ProcessManager(
       line -> System.out.println(line),      // stdout
       error -> System.err.println(error),    // stderr
       () -> System.out.println("Done")       // process end
   );
   pm.startProcess(["bash"], "/tmp");
   pm.sendInput("echo hello");
   pm.terminate();

TerminalService (Shell Layer)
├─ Responsibilities:
│  ├─ Maintain persistent shell process
│  ├─ Detect shell (bash/pwsh/cmd)
│  ├─ Manage working directory
│  ├─ High-level API for terminal operations
│  └─ Handle initialization/shutdown
│
├─ Uses:
│  └─ ProcessManager (internal)
│
└─ Example:
   TerminalService terminal = new TerminalService(
       line -> appendTerminalLine(line),
       error -> appendTerminalLine("[ERR] " + error),
       () -> setStatus("Done")
   );
   terminal.initialize(Path.of("/tmp"));
   terminal.sendInputLine("ls");
   terminal.changeDirectory(Path.of("/"));
   terminal.shutdown();

CommandExecutor (One-Off Layer)
├─ Responsibilities:
│  ├─ Execute single commands without state
│  ├─ Support sync and async modes
│  ├─ Enforce timeouts
│  ├─ Capture full output
│  └─ Return Result record
│
├─ Uses:
│  └─ ProcessManager (for each execution)
│
└─ Example:
   CommandExecutor executor = new CommandExecutor(
       line -> appendTerminalLine(line),
       error -> appendTerminalLine("[ERR] " + error)
   );
   executor.executeAsync(
       new String[]{"python", "script.py"},
       Path.of("."),
       45,  // timeout
       result -> handleResult(result)
   );
```

---

### Memory Layout

```
Stack (FX Thread):
  ├─ ChatController
  ├─ terminalService (reference)
  └─ (small local variables)

Heap:
  ├─ TerminalService object
  │  ├─ ProcessManager reference
  │  ├─ Path currentWorkingDirectory
  │  ├─ Consumer callbacks (3x)
  │  └─ volatile flags
  │
  ├─ ProcessManager object
  │  ├─ Process reference
  │  ├─ PrintWriter stdin
  │  ├─ Consumer callbacks (3x)
  │  └─ volatile flags
  │
  ├─ Process object (JVM internal)
  │  ├─ Pipe: stdin
  │  ├─ Pipe: stdout
  │  └─ Pipe: stderr
  │
  ├─ Three reader/waiter threads
  │  ├─ Thread 1: Stdout reader (small stack)
  │  ├─ Thread 2: Stderr reader (small stack)
  │  └─ Thread 3: Waiter thread (small stack)
  │
  └─ TextArea (UI node)
       └─ All appended text stored here

Total Memory per Session:
  ~10-20 KB base (objects, threads, buffers)
  +text size (grows with terminal output)
  
Compared to OLD system spawning 100 processes:
  OLD: 100 × 10 KB = 1 MB
  NEW: 1 × 10 KB = 10 KB
  Savings: 99%
```

---

### State Transitions

```
TerminalService States:

1. UNINITIALIZED
   ├─ terminalInitialized = false
   ├─ processManager = null
   ├─ Can call: initialize()
   └─ Cannot call: sendInputLine() - error

2. INITIALIZING
   ├─ ProcessBuilder.start() running
   ├─ Reader threads starting
   ├─ Cannot call: any methods (not thread safe yet)
   └─ Once complete → RUNNING

3. RUNNING
   ├─ Shell process alive
   ├─ Can call: sendInputLine(), changeDirectory(), etc.
   ├─ Output is streaming
   └─ If process dies → TERMINATED

4. TERMINATING
   ├─ shutdown() called
   ├─ process.destroy() in progress
   ├─ Waiting for graceful shutdown
   ├─ Cannot call: sendInputLine() - error
   └─ Once complete → UNINITIALIZED

5. FORCE KILLING
   ├─ Graceful shutdown timed out
   ├─ process.destroyForcibly() called
   ├─ Threads being cleaned up
   └─ Once complete → UNINITIALIZED

6. CRASHED (TERMINATED unexpectedly)
   ├─ Process died without explicit shutdown()
   ├─ Waiter thread detected exit
   ├─ Called onProcessEnd() callback
   ├─ terminalInitialized set to false
   ├─ Can call: restart()
   └─ restart() → UNINITIALIZED → initialize() → RUNNING
```

---

### Error Handling Flow

```
User runs Python with invalid syntax:
  python -c "print(x y)"

Timeline:
  1. User types command
  2. sendInputLine("python -c \"print(x y)\"")
  3. Shell executes, Python starts
  4. Python says: "SyntaxError..."
  5. This goes to stderr
  
Stderr Reader Thread:
  └─ Reads: "SyntaxError: ..."
  └─ Platform.runLater( () -> onError.accept(error) )
  
TerminalService's error handler:
  └─ appendTerminalLine("[ERROR] SyntaxError: ...")
  
User sees: Error message in terminal
Status: Updated to "Command completed with errors"

Process alive check:
  └─ Still alive (waiting for next command)
  └─ Exit code: still running

User can type next command immediately!

---

Process crashes (e.g., segmentation fault in C program):
  
Shell detects process crash
Waiter thread: process.waitFor() returns (-1)
Calls: Platform.runLater( () -> onProcessEnd.run() )

TerminalService's end handler:
  └─ setTerminalStatus("Shell terminated")
  └─ terminalInitialized = false

Next command attempt sees:
  if (!terminalInitialized):
      appendTerminalLine("[ERROR] Shell not initialized")
      return

User can:
  Option 1: Click to close/reopen terminal
            └─ Calls openTerminalPanel()
            └─ Detects !terminalInitialized
            └─ Calls initialize()
            └─ Fresh shell starts
            
  Option 2: Call restart() programmatically
            └─ shutdown() then initialize()
            └─ Seamless recovery
```

---

### Threading Diagram

```
Time ─────────────────────────────────────────────────────────────►

FX Thread
│
├─ 00ms: User interaction
├─ 01ms: executeTerminalCommandFromConsole()
├─ 02ms: sendInputLine() [returns immediately]
├─ 03ms: ... handling other UI events ...
├─ 50ms: Platform.runLater() queue processed
├─ 51ms: appendTerminalLine() [updates TextArea]
├─ 52ms: TextArea re-renders
├─ ... continues ...
│
├─ 999ms: cleanup at shutdown
├─ 1000ms: shutdown() [waits for grace period]
├─ 1500ms: threads ended, process cleaned up
└─ Application exits


Stdout Reader Thread (Daemon)
│
├─ 05ms: Thread started in startProcess()
├─ 10ms: Waiting on readline() [blocked]
├─ 50ms: readline() returns a line (shell produced output)
├─ 51ms: Platform.runLater( () -> onOutput.accept(line) )
├─ 52ms: Returns to readline() [blocked again]
├─ 100ms: More output
├─ 101ms: Platform.runLater( () -> onOutput.accept(line) )
├─ 102ms: Returns to readline()
└─ [continues until process.getInputStream() EOF]


Stderr Reader Thread (Daemon)
│
├─ 05ms: Thread started in startProcess()
├─ ... same pattern as stdout ...
└─ [continues independently]


Waiter Thread (Daemon)
│
├─ 05ms: Thread started in startProcess()
├─ 06ms: Calls process.waitFor() [blocked]
├─ ...
├─ 5000ms: Process terminates
├─ 5001ms: waitFor() returns
├─ 5002ms: Platform.runLater( () -> onProcessEnd.run() )
└─ Thread exits


Shutdown Sequence:
  1000ms: Main calls shutdown()
  1001ms: shutdown() calls terminate()
  1002ms: terminate() closes stdin
  1003ms: terminate() calls process.destroy()
  1004ms: Shell detects stdin closed, gracefully exits
  1005ms: Waiter thread: waitFor() returns
  1006ms: Stdout/Stderr readers: readline() returns EOF
  1007ms: All reader threads exit
  1008ms: shutdown() calls process.waitFor(2 seconds)
  1510ms: shutdown() complete
  1511ms: Main continues
  [JVM continues, daemon threads auto-cleaned]

Key: No waiting/blocking on FX thread!
     All activity happens off-thread!
```

---

### Quick Comparison: All Three Approaches

```
APPROACH 1: Old System (Spawn-Per-Command)
─────────────────────────────────────────
Process lifecycle:
  spawn → execute → read → cleanup → repeat

Use case: Simple commands
Stdout handling: Buffered (read all at once)
Interactive: ✗ (no stdin)
Performance: Slow (spawn overhead)
Large outputs: ✗ (freezes)
Shell features: ✗ (no shell session)
Concurrency: Tricky (multiple processes)


APPROACH 2: New System (Persistent Shell)
──────────────────────────────────────────
Process lifecycle:
  spawn → run continuously

Use case: Interactive terminal
Stdout handling: Streamed (read line-by-line)
Interactive: ✓ (stdin supported)
Performance: Fast (no spawn overhead)
Large outputs: ✓ (progressive streaming)
Shell features: ✓ (real shell session)
Concurrency: Safe (single persistent process)

Status: ✅ IMPLEMENTED


APPROACH 3: CommandExecutor (Hybrid)
─────────────────────────────────────
Process lifecycle:
  spawn → execute with timeout → cleanup

Use case: One-off commands, code snippets
Stdout handling: Streamed or buffered
Interactive: ✗ (no stdin)
Timeout: ✓ (enforced)
Return value: ✓ (exit code, output)
Parallel: ✓ (can run multiple)

Status: ✅ IMPLEMENTED (bonus)
```

---

This visual overview should help you understand how all the pieces fit together!
