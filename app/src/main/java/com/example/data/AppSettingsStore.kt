package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

enum class SavedTagViewMode(val label: String) {
  SIMPLIFIED("Simplified"),
  WALLET("Wallet"),
}

enum class PeerExchangeMode(val label: String) {
  OFF("Off"),
  PULSE("Pulse"),
  TRUE_P2P("True P2P"),
}

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val savedTagViewMode: SavedTagViewMode = SavedTagViewMode.SIMPLIFIED,
  val defaultReadOnly: Boolean = true,
  val autoActivateOnWrite: Boolean = false,
  val autoActivateOnSelect: Boolean = false,
  val preferForegroundHce: Boolean = true,
  val stopOnLeaveApp: Boolean = false,
  val peerExchangeMode: PeerExchangeMode = PeerExchangeMode.PULSE,
  val bridgeDeviceId: String = "",
  val hasCompletedTutorial: Boolean = false,
)

class AppSettingsStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun readSettings(): AppSettings {
    val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
    val themeMode =
      ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
    val viewModeName =
      prefs.getString(KEY_SAVED_VIEW_MODE, SavedTagViewMode.SIMPLIFIED.name)
        ?: SavedTagViewMode.SIMPLIFIED.name
    val savedTagViewMode =
      SavedTagViewMode.entries.firstOrNull { it.name == viewModeName }
        ?: SavedTagViewMode.SIMPLIFIED
    val peerModeName =
      prefs.getString(KEY_PEER_EXCHANGE_MODE, PeerExchangeMode.PULSE.name)
        ?: PeerExchangeMode.PULSE.name
    val peerExchangeMode =
      PeerExchangeMode.entries.firstOrNull { it.name == peerModeName }
        ?: PeerExchangeMode.PULSE
    val deviceId = ensureBridgeDeviceId()
    return AppSettings(
      themeMode = themeMode,
      savedTagViewMode = savedTagViewMode,
      defaultReadOnly = prefs.getBoolean(KEY_DEFAULT_READ_ONLY, true),
      autoActivateOnWrite = prefs.getBoolean(KEY_AUTO_ACTIVATE_WRITE, false),
      autoActivateOnSelect = prefs.getBoolean(KEY_AUTO_ACTIVATE_SELECT, false),
      preferForegroundHce = prefs.getBoolean(KEY_PREFER_FOREGROUND, true),
      stopOnLeaveApp = prefs.getBoolean(KEY_STOP_ON_LEAVE, false),
      peerExchangeMode = peerExchangeMode,
      bridgeDeviceId = deviceId,
      hasCompletedTutorial = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false),
    )
  }

  fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
    val updated = transform(readSettings())
    val resolvedDeviceId = updated.bridgeDeviceId.ifBlank { ensureBridgeDeviceId() }
    prefs
      .edit()
      .putString(KEY_THEME_MODE, updated.themeMode.name)
      .putString(KEY_SAVED_VIEW_MODE, updated.savedTagViewMode.name)
      .putBoolean(KEY_DEFAULT_READ_ONLY, updated.defaultReadOnly)
      .putBoolean(KEY_AUTO_ACTIVATE_WRITE, updated.autoActivateOnWrite)
      .putBoolean(KEY_AUTO_ACTIVATE_SELECT, updated.autoActivateOnSelect)
      .putBoolean(KEY_PREFER_FOREGROUND, updated.preferForegroundHce)
      .putBoolean(KEY_STOP_ON_LEAVE, updated.stopOnLeaveApp)
      .putString(KEY_PEER_EXCHANGE_MODE, updated.peerExchangeMode.name)
      .putString(KEY_BRIDGE_DEVICE_ID, resolvedDeviceId)
      .putBoolean(KEY_TUTORIAL_COMPLETED, updated.hasCompletedTutorial)
      .apply()
    return updated.copy(bridgeDeviceId = resolvedDeviceId)
  }

  private fun ensureBridgeDeviceId(): String {
    val existing = prefs.getString(KEY_BRIDGE_DEVICE_ID, null)
    if (!existing.isNullOrBlank()) return existing
    synchronized(AppSettingsStore::class.java) {
      val recheck = prefs.getString(KEY_BRIDGE_DEVICE_ID, null)
      if (!recheck.isNullOrBlank()) return recheck
      val generated = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
      prefs.edit().putString(KEY_BRIDGE_DEVICE_ID, generated).apply()
      return generated
    }
  }

  companion object {
    private const val PREFS_NAME = "taglink_app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SAVED_VIEW_MODE = "saved_tag_view_mode"
    private const val KEY_DEFAULT_READ_ONLY = "default_read_only"
    private const val KEY_AUTO_ACTIVATE_WRITE = "auto_activate_write"
    private const val KEY_AUTO_ACTIVATE_SELECT = "auto_activate_select"
    private const val KEY_PREFER_FOREGROUND = "prefer_foreground"
    private const val KEY_STOP_ON_LEAVE = "stop_on_leave"
    private const val KEY_PEER_EXCHANGE_MODE = "peer_exchange_mode"
    private const val KEY_BRIDGE_DEVICE_ID = "bridge_device_id"
    private const val KEY_TUTORIAL_COMPLETED = "has_completed_tutorial"
  }
}
