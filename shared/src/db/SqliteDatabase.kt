package raju.shingadiya.juniper.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

/**
 * Opens the local SQLite database and brings its schema up to date.
 *
 * The schema version is stored in SQLite's `user_version`. Each migration runs once, in order,
 * when the version on disk is lower than [SCHEMA_VERSION].
 */
object SqliteDatabase {
    const val SCHEMA_VERSION = 4

    /** JDBC URL for the app's database file: `~/.juniper/juniper.db`. */
    fun defaultUrl(): String {
        val dir = File(System.getProperty("user.home"), ".juniper").apply { mkdirs() }
        return "jdbc:sqlite:${File(dir, "juniper.db").absolutePath}"
    }

    /** Opens [jdbcUrl] (a file or `jdbc:sqlite::memory:`), enables foreign keys and migrates. */
    fun open(jdbcUrl: String): Connection {
        val connection = DriverManager.getConnection(jdbcUrl)
        try {
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            migrate(connection)
        } catch (e: Exception) {
            connection.close()
            throw e
        }
        return connection
    }

    private fun migrate(connection: Connection) {
        connection.createStatement().use { statement ->
            val current = statement.executeQuery("PRAGMA user_version").use { rs ->
                rs.next()
                rs.getInt(1)
            }
            if (current < 1) {
                createVersion1(statement)
            }
            if (current < 2) {
                createVersion2(statement)
            }
            if (current < 3) {
                createVersion3(statement)
            }
            if (current < 4) {
                createVersion4(statement)
            }
            statement.execute("PRAGMA user_version = $SCHEMA_VERSION")
        }
    }

    /** Version 4: app settings as key/value pairs (theme mode, accent colour). */
    private fun createVersion4(statement: Statement) {
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    /** Version 2: tool name and input for tool-call messages. */
    private fun createVersion2(statement: Statement) {
        statement.execute("ALTER TABLE messages ADD COLUMN tool_name TEXT")
        statement.execute("ALTER TABLE messages ADD COLUMN tool_input TEXT")
    }

    /** Version 1: chats and their messages. */
    private fun createVersion1(statement: Statement) {
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS chats (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                model TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS messages (
                chat_id TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                is_error INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (chat_id, position)
            )
            """.trimIndent(),
        )
        statement.execute("CREATE INDEX IF NOT EXISTS idx_chats_updated ON chats(updated_at DESC)")
    }

    /** Version 3: the agent's own conversation history (Koog messages, one JSON document per row). */
    private fun createVersion3(statement: Statement) {
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS agent_messages (
                chat_id TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                message TEXT NOT NULL,
                PRIMARY KEY (chat_id, position)
            )
            """.trimIndent(),
        )
    }
}
