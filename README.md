# Juniper

Juniper is a desktop app that uses a **local LLM** to perform actions on, or answer questions about, a connected Android device.

- **Local only:** all model inference runs on this machine through [Ollama](https://ollama.com). No cloud model is used.
- **Suggested model:** `qwen2.5:7b`. The app shows install steps for it when it is missing.
- **Device control:** commands run through the Android Debug Bridge (`adb`) on the device you select in the app.

This is a Kotlin Multiplatform project targeting Desktop (JVM), built with the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

- [/desktopApp](./desktopApp) contains the desktop (JVM) application entry point.
- [/shared](./shared) holds the shared Compose UI and logic. [src](./shared/src) is for common code; [test](./shared/test) holds the tests.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required. Build the whole project with `./kotlin build`.

## Requirements

- JDK is provided by the toolchain. No separate install is needed.
- [Ollama](https://ollama.com) running locally, with `ollama pull qwen2.5:7b` done once.
- Android platform-tools (`adb`) for device features. Juniper looks in `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `~/Library/Android/sdk` and `~/Android/Sdk`, then falls back to `adb` on the PATH.

## Running

Use the run configurations provided by the run widget in your IDE's toolbar, or from the command line:

```bash
./kotlin run -m desktopApp --no-compose-hot-reload
```

The window opens maximized. To open it at a fixed size instead, pass pixel sizes after `--`:

```bash
./kotlin run -m desktopApp --no-compose-hot-reload -- --width=1200 --height=850
```

Either flag can be given on its own; the other dimension then uses the screen's usable area.

## Testing

```bash
./kotlin test            # all modules
./kotlin test -m shared  # shared module only
```

Tests use fake command runners and a mocked Ollama HTTP server, so they do not need a device or a running Ollama.

## What is implemented

### Chat
- Chat window with a message list and input box. **Enter sends**, **Shift+Enter** adds a new line.
- Replies stream token by token from the local model. **Stop** keeps the partial reply.
- Conversation history is sent with each request. A failed request shows as an error bubble and is not sent back to the model.
- Replies are rendered as Markdown. Message text can be selected and copied.
- Chat sidebar: create, open, rename and delete chats. Chats and messages are stored in a local SQLite database, so they survive restarts.
- Light and dark theme, following the OS setting until toggled.

### Model selection
- **Model chip** under the input box shows the model used by the open chat.
- Clicking it lists the models Ollama has installed. Pick one to switch the open chat.
- If no usable model is selected (Ollama not running, or the model is missing), clicking the chip opens a **setup guide**: install and start Ollama, a link to ollama.com, and the `ollama pull` command with a Copy button.
- The list refreshes every 5 seconds, so a model installed while the app is open appears without a restart.

### Device selection
- **Device chip** under the input box shows the device commands go to.
- The first ready device is selected automatically. Choose another from the menu.
- Devices that are unauthorized or offline are shown with the reason, and cannot be selected.
- When no device is connected, the chip says "No device" and the menu explains how to connect one.
- If `adb` is missing, the chip says so and the menu explains how to install it.
- If the selected device is unplugged, the next ready device is selected. If `adb` fails temporarily, the selection comes back when the device reappears.
- The list refreshes every 3 seconds.

### Device tools for the model
- `AdbChatTools` exposes adb commands to the chat model (tap, swipe, text input, key events, starting an activity, reading the UI hierarchy).
- Every command goes to the device selected in the app. If no device is selected, the tool fails rather than using adb's default device.
- Tools that change the device wait for the user to allow them before they run.
- Each chat writes a debug trajectory (`trajectory.jsonl` per chat): the messages sent, what came back, and every tool call and approval, with timestamps.

### Desktop window
- Opens maximized by default.
- `--width=` and `--height=` flags set a fixed window size (see [Running](#running)).

## Project layout

| Path | Contents |
|------|----------|
| `desktopApp/src/main.kt` | Window setup and command-line flags |
| `shared/src/App.kt` | Root layout: wires the sidebar, chat, model and device managers |
| `shared/src/ollama/` | Ollama HTTP client (chat, streaming, model list) and request/response types |
| `shared/src/adb/` | `adb` command wrapper, command runner, UI hierarchy parser, chat tools |
| `shared/src/device/` | Device manager: polling, selection rules, adb error handling |
| `shared/src/model/` | Model manager: installed models and Ollama reachability |
| `shared/src/chat/` | Chat session, workspace, repository, sidebar, chat screen, selector chips, Markdown, tools, trajectory log |
| `shared/src/db/` | SQLite database setup |
| `shared/src/ui/` | Theme and spacing tokens |

## Changelog

Newest first. Commit hashes are on the `feature/ollama-adb-chat` branch unless noted.

- **2026-11-14: Device and model selectors** (`cbfd1fc`)
  - Device chip with auto-selection, edge-case handling and an adb-missing state.
  - Model chip with installed-model list and a setup guide.
  - Chat sidebar, SQLite-backed storage, Markdown rendering for replies.
  - Tests for device rules, model checks, repository, workspace and Markdown.
- **2026-11-14: Ollama client, adb wrapper and chat window** (`a7cc1d7`)
  - Ollama client for `/api/chat` (chat, streaming, model list).
  - `adb` wrapper for devices, input, `am start` and `uiautomator dump`, with a UI hierarchy parser.
  - Chat window with streaming replies, Stop, Clear and Enter-to-send.
  - Desktop window opens maximized; `--width` / `--height` flags added.
- **2026-11-14: Project notes** (`89dd1ae`): project intro in the README.
- **Project setup** (`bde9001`, `09f0a5a`): IDE configuration, confidentiality rule, project `CLAUDE.md` and `run.sh`.
- **Initial commit** (`3959efb`): Kotlin Multiplatform desktop template.

### Working tree (not yet committed)

- Tool calling for the chat model: `ChatTools`, `AdbChatTools`, and approval for device-changing tools.
- Trajectory logging per chat (`TrajectoryRecorder`).
- Tests for these: `AdbChatToolsTest`, `ChatToolSessionTest`, `TrajectoryTest`.

## Known gaps

- The model chosen in a chat is not saved. Reopening the app uses the model stored when the chat was created.
- Device-changing tools are not yet exercised against a live device in the automated tests.
- Only the Desktop (JVM) target is built.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/).
