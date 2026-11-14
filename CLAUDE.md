# Juniper – project notes for Claude

Kotlin Multiplatform project targeting Desktop (JVM) with Compose Multiplatform.

- `desktopApp/` – desktop entry point (`desktopApp/src/main.kt`).
- `shared/` – shared Compose UI and logic (`shared/src/App.kt`).

## Run / test / build

- Run desktop app: `./kotlin run -m desktopApp --no-compose-hot-reload`
- Build: `./kotlin build`
- Test: `./kotlin test` (or `./kotlin test -m <module>`)

## UI rules

- **Use Compose layouts and components only.** Build screens from Compose Multiplatform layouts (`Column`, `Row`, `Box`, `LazyColumn`, `LazyRow`, `Spacer`, `FlowRow`) and Material 3 components (`Scaffold`, `TopAppBar`, `NavigationRail`, `Card`, `Button`, `IconButton`, `TextField`, `OutlinedTextField`, `Surface`, `AlertDialog`, `ModalBottomSheet`, `Snackbar`, `CircularProgressIndicator`, etc.).
- **Do not create custom UI widgets or draw custom components** (raw `Canvas`, hand-rolled buttons, custom text fields, bespoke layout math) unless the user explicitly approves it for that specific case.
- **Keep the UI consistent across the whole app.** Put colors, typography and shapes in one shared `MaterialTheme` (in `shared/`) and reuse it everywhere. Do not hard-code colors, font sizes or paddings in individual screens; use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`, and shared spacing constants.
- Reusable screen pieces go in `shared/src/` as small composables that wrap Material 3 components, so every screen uses the same look.
- Before adding any new UI, check whether an existing Compose or Material 3 component already does the job.

### Where these fit an AI agent UI

- **Chat / conversation:** `Scaffold` + `LazyColumn` for the message list (auto-scroll to newest), `OutlinedTextField` with an `IconButton` send button pinned at the bottom.
- **Messages:** `Card` or `Surface` per message, differing by role (user vs. agent) through `colorScheme` tokens, not custom shapes.
- **Streaming / thinking state:** `LinearProgressIndicator` or `CircularProgressIndicator`, and `AnimatedVisibility` for showing and hiding status text.
- **Tool calls and results:** collapsible `Card` with an expand `IconButton`, shown inside the message list.
- **Approvals (e.g. a tool wants permission):** `AlertDialog` or `ModalBottomSheet` with clear confirm and deny buttons.
- **Errors and notices:** `Snackbar` via `SnackbarHostState`.
- **Navigation (sessions, settings):** `NavigationRail` on desktop, `TopAppBar` for titles and actions.
- **Empty and loading states:** `Text` plus an icon in a centered `Column`, using theme typography.

## Working agreements

- Do not commit or push without asking the user first.
- Before every commit, check the files being committed for confidential details: passwords, API keys, tokens, private keys, `.env` values, personal file paths, or machine-specific data. If anything is found, stop, remove it or ask the user, and only then commit and push.
- Do not commit local caches or IDE state that is specific to one machine (for example `.idea/caches/`, `.idea/workspace.xml`).
