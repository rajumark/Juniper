package raju.shingadiya.juniper.settings

import java.sql.Connection

/** Which light/dark scheme to show. [Device] follows the operating system. */
enum class ThemeMode { Light, Dark, Device }

/**
 * The user's appearance choices. [seedArgb] is the colour every palette is generated from; it is
 * either one of [SeedPalette.presets] or the colour picked with the custom picker. [customArgb] is
 * the last custom colour, shown on the custom tile even when another colour is selected.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Device,
    val seedArgb: Int = SeedPalette.DEFAULT_SEED_ARGB,
    val customArgb: Int = SeedPalette.DEFAULT_SEED_ARGB,
)

/** Reads and writes [AppSettings] in the `app_settings` table, so choices survive a restart. */
class SettingsStore(private val database: Connection) {

    fun load(): AppSettings {
        val values = mutableMapOf<String, String>()
        database.prepareStatement("SELECT key, value FROM app_settings").use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) values[rs.getString(1)] = rs.getString(2)
            }
        }
        val defaults = AppSettings()
        return AppSettings(
            themeMode = values[KEY_THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
                ?: defaults.themeMode,
            seedArgb = values[KEY_SEED]?.toIntOrNull() ?: defaults.seedArgb,
            customArgb = values[KEY_CUSTOM]?.toIntOrNull() ?: defaults.customArgb,
        )
    }

    fun save(settings: AppSettings) {
        put(KEY_THEME_MODE, settings.themeMode.name)
        put(KEY_SEED, settings.seedArgb.toString())
        put(KEY_CUSTOM, settings.customArgb.toString())
    }

    private fun put(key: String, value: String) {
        database.prepareStatement(
            "INSERT INTO app_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEED = "seed_argb"
        const val KEY_CUSTOM = "custom_argb"
    }
}

/** The fifteen preset accent colours offered in Settings. */
object SeedPalette {
    const val DEFAULT_SEED_ARGB: Int = 0xFF2F6B57.toInt()

    val presets: List<Int> = listOf(
        0xFF1E40AF, 0xFF4B5563, 0xFF2B4C7E, 0xFF3D4B5C,
        0xFF3A4F55, 0xFF1F7A70, 0xFF2F6B57, 0xFF4E6B3A,
        0xFF5C6B4D, 0xFF8A7A1F, 0xFFB5651D, 0xFF7B5E4E,
        0xFFA04A6A, 0xFF6A3E5E, 0xFF7B4FA8,
    ).map { it.toInt() }
}
