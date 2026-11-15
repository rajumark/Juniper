# Juniper

Juniper is a desktop app that uses a **local LLM** to answer questions about a connected Android device and to read facts from it.

- **Local only:** all model inference runs on this machine through [Ollama](https://ollama.com). No cloud model is used.
- **Suggested model:** `qwen2.5:7b`. The app shows install steps for it when it is missing.
- **Device access:** read-only `adb` commands run on the device you select in the app.

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

The default tests run offline. They use fake command runners and a fake agent, so they do not need a device or a running Ollama.

A live test runs the real agent against your local Ollama server. It is skipped unless you opt in:

```bash
JUNIPER_LIVE=1 ./kotlin test -m shared
```

## What we have right now

### Desktop app
- Compose Multiplatform desktop window, opens maximized by default.
- Left sidebar for chats, with the open chat on the right.
- Light and dark theme that follows the OS, with a toggle in Settings.
- Settings dialog opened from the gear icon at the bottom-left of the sidebar.
- `--width` and `--height` flags for a fixed window size.

### Material design
- Built only from Material 3 components.
- One shared theme (`shared/src/ui/`) holds colors, type, shapes and spacing.
- Chat bubbles for user, assistant, tool and error messages.
- Assistant replies render Markdown: headings, bold, italic, lists and code blocks.
- Hover copy button on each message, with the Downloads icon converted to a vector drawable.

### Chat
- Chat list: new, open, rename and delete. Right-click a chat for Rename or Delete. Delete asks first.
- A chat is titled from its first message, unless you rename it.
- Each chat keeps its own draft and can be cleared.
- Enter sends, Shift+Enter adds a new line, Stop ends a reply.
- Messages and tool results are saved in SQLite, so chats survive restarts.

### Device manager
- Polls `adb` every 3 seconds and lists connected devices with their status: ready, unauthorized or offline.
- The first ready device is selected automatically. Pick another from the device chip.
- If `adb` is missing, the chip says so and explains how to install it.
- If the selected device is unplugged, the next ready device is selected.

### LLM model manager
- Lists the models installed on the local Ollama server, using Koog's Ollama client.
- Refreshes every 5 seconds, so a newly installed model appears without a restart.
- The model chip shows the model for the open chat. Pick another to switch.
- If Ollama is not running or the model is missing, the chip opens a setup guide with install steps and a copy button for `ollama pull`.

### Agent (Koog)
- Each message runs through a [Koog](https://docs.koog.ai) agent (`koog-agents` 1.3.0).
- Koog's `singleRunStrategy` sends the message to the model, runs the tools the model asks for, and repeats until there is an answer.
- The agent stops after 8 model calls.
- Conversation memory is kept in SQLite through Koog's chat memory, so the model remembers earlier messages in the same chat.
- Only the local Ollama server is used. The default address is loopback, and a test checks it.

### Tools for the model
- Registered, read-only:
  - `get_android_version`: release and API level
  - `get_device_info`: manufacturer, brand, model and device
  - `get_battery_info`: level, temperature and charging state
  - `get_screen_info`: resolution and density
  - `get_storage_info`: data partition usage
  - `dump_screen`: visible text and clickable elements with coordinates
- Written but not registered, so the model cannot use them yet: `tap`, `swipe`, `input_text`, `press_key`, `start_activity`. Registering one turns it on.
- Every tool runs against the device selected in the app. If none is selected, the tool says so instead of using adb's default device.

### Storage and logs
- SQLite database at `~/.juniper/juniper.db` with three schema versions: chats and messages, tool columns, and the agent's history.
- Per-chat trajectory log at `~/.juniper/chats/<chat-id>/trajectory.jsonl`. It records your messages, tool calls and results, and how each reply ended.

### Tests
- 77 offline tests cover the agent wrapper, device tools, history store, chat session, workspace, repository, trajectory log, model manager, device manager, Markdown and the ADB client.
- The live agent test is opt-in (see [Testing](#testing)).

## Project layout

| Path | Contents |
|------|----------|
| `desktopApp/src/main.kt` | Window setup and command-line flags |
| `shared/src/App.kt` | Root layout: wires the sidebar, chat, model and device managers, and the agent |
| `shared/src/agent/` | Koog agent runner, agent history store, device tools |
| `shared/src/adb/` | `adb` client, command runner, UI hierarchy parser |
| `shared/src/device/` | Device manager: polling, selection rules, adb error handling |
| `shared/src/model/` | Model manager and Koog model catalog |
| `shared/src/chat/` | Chat session, workspace, repository, sidebar, chat screen, selector chips, Markdown, trajectory log |
| `shared/src/db/` | SQLite database setup and schema versions |
| `shared/src/ui/` | Theme, typography, shapes and spacing tokens |

## Changelog

Newest first. Commit hashes are on the `feature/ollama-adb-chat` branch unless noted.

- **2026-11-15: Koog agent and chat history** (`68f692e`)
  - Chats run through a Koog agent with `singleRunStrategy`, local Ollama only.
  - Agent conversation memory stored in SQLite (schema v3).
  - Read-only device tools registered; device-changing tools ported but not registered.
  - Hand-written Ollama client and tool loop removed; model list comes from Koog.
  - Opt-in live test against the local Ollama server.
- **2026-11-14: Device and model selectors** (`cbfd1fc`)
  - Device chip with auto-selection, edge-case handling and an adb-missing state.
  - Model chip with installed-model list and a setup guide.
  - Chat sidebar, SQLite-backed storage, Markdown rendering for replies.
- **2026-11-14: Ollama client, adb wrapper and chat window** (`a7cc1d7`)
  - Ollama client, `adb` wrapper with a UI hierarchy parser, and the chat window.
  - Desktop window opens maximized; `--width` and `--height` flags added.
- **2026-11-14: Project notes** (`89dd1ae`): project intro in the README.
- **Project setup** (`bde9001`, `09f0a5a`): IDE configuration, confidentiality rule, project `CLAUDE.md` and `run.sh`.
- **Initial commit** (`3959efb`): Kotlin Multiplatform desktop template.

## Known gaps

- Replies appear all at once. The Koog agent loop does not stream tokens yet.
- Device-changing tools have no approval step yet. They are not registered, so the model cannot call them.
- The trajectory log records tool activity but not the full prompt sent to the model.
- Koog does not separate "Ollama is down" from other errors, so both show as an Ollama problem.
- The model chosen in a chat is not saved. Reopening the app uses the model stored when the chat was created.
- There are no UI tests yet.
- Only the Desktop (JVM) target is built.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/).
