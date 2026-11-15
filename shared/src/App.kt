package raju.shingadiya.juniper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import raju.shingadiya.juniper.chat.ChatScreen
import raju.shingadiya.juniper.chat.ChatSidebar
import raju.shingadiya.juniper.chat.ChatWorkspace
import raju.shingadiya.juniper.chat.EmptyState
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.chat.SelectorBar
import raju.shingadiya.juniper.chat.SqliteChatRepository
import raju.shingadiya.juniper.chat.TrajectoryRecorder
import raju.shingadiya.juniper.device.DeviceManager
import raju.shingadiya.juniper.model.ModelManager
import raju.shingadiya.juniper.db.SqliteDatabase
import ai.koog.prompt.executor.ollama.client.OllamaClient
import raju.shingadiya.juniper.agent.AgentHistoryStore
import raju.shingadiya.juniper.agent.KoogChatAgentRunner
import raju.shingadiya.juniper.model.KoogModelCatalog
import raju.shingadiya.juniper.ui.JuniperTheme

private const val DEFAULT_MODEL = ModelManager.SUGGESTED_MODEL
private const val SYSTEM_PROMPT = "You are Juniper, a helpful assistant for Android developers. " +
    "You can read facts about the selected Android device with tools, such as its Android version, " +
    "battery, screen and storage. Answer from the tool results and say when a tool could not give an answer."

/** Root layout: chat list on the left, the open chat on the right. */
@Composable
@DevelopmentEntryPoint
fun App() {
    // null = follow the OS setting until the user flips the toggle.
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkOverride ?: isSystemInDarkTheme()

    JuniperTheme(darkTheme = darkTheme) {
        val scope = rememberCoroutineScope()
        val client = remember { OllamaClient() }
        DisposableEffect(client) {
            onDispose { client.close() }
        }
        val modelManager = remember(client) { ModelManager(KoogModelCatalog(client)) }
        val adb = remember { AdbClient() }
        val deviceManager = remember(adb) { DeviceManager(adb) }
        LaunchedEffect(modelManager) { modelManager.start(this) }
        LaunchedEffect(deviceManager) { deviceManager.start(this) }
        val modelState by modelManager.state.collectAsState()
        val deviceState by deviceManager.state.collectAsState()
        val database = remember { SqliteDatabase.open(SqliteDatabase.defaultUrl()) }
        DisposableEffect(database) {
            onDispose { database.close() }
        }
        // Debug trajectories: one trajectory.jsonl per chat under ~/.juniper/chats.
        val trajectory = remember { TrajectoryRecorder(TrajectoryRecorder.defaultDir()) }
        DisposableEffect(trajectory) {
            onDispose { trajectory.close() }
        }
        val workspace = remember(database) {
            ChatWorkspace(
                // The agent reads the device selected in the device menu when each tool runs.
                runner = KoogChatAgentRunner(
                    client = client,
                    history = AgentHistoryStore(database),
                    adb = adb,
                    selectedSerial = { deviceManager.state.value.selectedSerial },
                ),
                repository = SqliteChatRepository(database),
                scope = scope,
                defaultModel = DEFAULT_MODEL,
                systemPrompt = SYSTEM_PROMPT,
                trajectory = trajectory,
            )
        }
        LaunchedEffect(workspace) { workspace.start() }

        val chats by workspace.chats.collectAsState()
        val session by workspace.activeSession.collectAsState()

        Row(modifier = Modifier.fillMaxSize()) {
            ChatSidebar(
                chats = chats,
                activeChatId = session?.chatId,
                darkTheme = darkTheme,
                onNewChat = { scope.launch { workspace.newChat() } },
                onSelect = { id -> scope.launch { workspace.select(id) } },
                onRename = { id, title -> scope.launch { workspace.rename(id, title) } },
                onDelete = { id -> scope.launch { workspace.delete(id) } },
                onDarkThemeChange = { darkOverride = it },
            )
            VerticalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                val active = session
                if (active == null) {
                    EmptyState(text = "Create a chat to get started.")
                } else {
                    // key() gives each chat its own scroll position and draft.
                    key(active.chatId) {
                        val selectedModel by active.currentModel.collectAsState()
                        ChatScreen(
                            session = active,
                            title = chats.find { it.id == active.chatId }?.title.orEmpty(),
                            chipBar = {
                                SelectorBar(
                                    selectedModel = selectedModel,
                                    models = modelState,
                                    onSelectModel = { active.selectModel(it) },
                                    onRefreshModels = { scope.launch { modelManager.refresh() } },
                                    devices = deviceState,
                                    onSelectDevice = { deviceManager.select(it) },
                                    onRefreshDevices = { scope.launch { deviceManager.refresh() } },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
