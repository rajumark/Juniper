package raju.shingadiya.juniper.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Runs an external command and returns its output. Abstracted so tests can replace the real process. */
fun interface CommandRunner {
    suspend fun run(command: List<String>, timeout: Duration): CommandResult
}

/** Runs commands as real OS processes. Arguments are passed directly, not through a shell. */
class ProcessCommandRunner : CommandRunner {
    override suspend fun run(command: List<String>, timeout: Duration): CommandResult =
        withContext(Dispatchers.IO) {
            val process = try {
                ProcessBuilder(command).start()
            } catch (e: IOException) {
                throw AdbException("Could not start '${command.first()}': ${e.message}", e)
            }
            try {
                coroutineScope {
                    // Read both streams concurrently so a full pipe cannot block the process.
                    val stdout = async { process.inputStream.bufferedReader().readText() }
                    val stderr = async { process.errorStream.bufferedReader().readText() }
                    val finished = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        throw AdbException("Timed out after $timeout: ${command.joinToString(" ")}")
                    }
                    CommandResult(process.exitValue(), stdout.await(), stderr.await())
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
}

/**
 * Finds the adb executable. Checks ANDROID_HOME, ANDROID_SDK_ROOT and the default SDK
 * install locations on macOS, Linux and Windows. Falls back to plain "adb" on the PATH.
 */
fun locateAdb(
    env: Map<String, String> = System.getenv(),
    home: String = System.getProperty("user.home"),
): String {
    val sdkRoots = listOfNotNull(
        env["ANDROID_HOME"],
        env["ANDROID_SDK_ROOT"],
        "$home/Library/Android/sdk",
        "$home/Android/Sdk",
        env["LOCALAPPDATA"]?.let { "$it/Android/Sdk" },
    )
    val names = listOf("adb", "adb.exe")
    for (root in sdkRoots) {
        for (name in names) {
            val candidate = File(root, "platform-tools/$name")
            if (candidate.isFile && candidate.canExecute()) return candidate.path
        }
    }
    return "adb"
}
