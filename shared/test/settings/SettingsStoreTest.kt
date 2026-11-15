package raju.shingadiya.juniper.settings

import raju.shingadiya.juniper.db.SqliteDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {

    @Test
    fun `defaults are used when nothing has been saved`() {
        val store = SettingsStore(SqliteDatabase.open("jdbc:sqlite::memory:"))

        assertEquals(AppSettings(), store.load())
    }

    @Test
    fun `saved settings are read back`() {
        val store = SettingsStore(SqliteDatabase.open("jdbc:sqlite::memory:"))
        val chosen = AppSettings(themeMode = ThemeMode.Dark, seedArgb = 0xFF7B4FA8.toInt(), customArgb = 0xFF123456.toInt())

        store.save(chosen)

        assertEquals(chosen, store.load())
    }

    @Test
    fun `settings survive closing and reopening the database`() {
        val file = File.createTempFile("juniper-settings", ".db").apply { deleteOnExit() }
        val chosen = AppSettings(themeMode = ThemeMode.Light, seedArgb = 0xFF1E40AF.toInt(), customArgb = 0xFFB5651D.toInt())

        SqliteDatabase.open("jdbc:sqlite:${file.absolutePath}").use { SettingsStore(it).save(chosen) }
        val reopened = SqliteDatabase.open("jdbc:sqlite:${file.absolutePath}")
        try {
            assertEquals(chosen, SettingsStore(reopened).load())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `saving again replaces the previous value`() {
        val store = SettingsStore(SqliteDatabase.open("jdbc:sqlite::memory:"))

        store.save(AppSettings(themeMode = ThemeMode.Dark))
        store.save(AppSettings(themeMode = ThemeMode.Device))

        assertEquals(ThemeMode.Device, store.load().themeMode)
    }
}
