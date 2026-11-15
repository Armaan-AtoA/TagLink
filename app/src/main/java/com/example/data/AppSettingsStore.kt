package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val defaultReadOnly: Boolean = true,
  val autoActivateOnWrite: Boolean = false,
  val autoActivateOnSelect: Boolean = false,
  val preferForegroundHce: Boolean = true,
  val stopOnLeaveApp: Boolean = false,
)

class AppSettingsStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun readSettings(): AppSettings {
    val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
    val themeMode =
      ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
    return AppSettings(
      themeMode = themeMode,
      defaultReadOnly = prefs.getBoolean(KEY_DEFAULT_READ_ONLY, true),
      autoActivateOnWrite = prefs.getBoolean(KEY_AUTO_ACTIVATE_WRITE, false),
      autoActivateOnSelect = prefs.getBoolean(KEY_AUTO_ACTIVATE_SELECT, false),
      preferForegroundHce = prefs.getBoolean(KEY_PREFER_FOREGROUND, true),
      stopOnLeaveApp = prefs.getBoolean(KEY_STOP_ON_LEAVE, false),
    )
  }

  fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
    val updated = transform(readSettings())
    prefs
      .edit()
      .putString(KEY_THEME_MODE, updated.themeMode.name)
      .putBoolean(KEY_DEFAULT_READ_ONLY, updated.defaultReadOnly)
      .putBoolean(KEY_AUTO_ACTIVATE_WRITE, updated.autoActivateOnWrite)
      .putBoolean(KEY_AUTO_ACTIVATE_SELECT, updated.autoActivateOnSelect)
      .putBoolean(KEY_PREFER_FOREGROUND, updated.preferForegroundHce)
      .putBoolean(KEY_STOP_ON_LEAVE, updated.stopOnLeaveApp)
      .apply()
    return updated
  }

  companion object {
    private const val PREFS_NAME = "taglink_app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_READ_ONLY = "default_read_only"
    private const val KEY_AUTO_ACTIVATE_WRITE = "auto_activate_write"
    private const val KEY_AUTO_ACTIVATE_SELECT = "auto_activate_select"
    private const val KEY_PREFER_FOREGROUND = "prefer_foreground"
    private const val KEY_STOP_ON_LEAVE = "stop_on_leave"
  }
}
