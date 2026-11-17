package com.example.nfc

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DynamicSwitchWindow(
  val trueP2pUntilMs: Long = 0L,
  val generation: Long = 0L,
)

object DynamicSwitchController {

  const val TRUE_P2P_WINDOW_MS = 5_000L
  private const val MAX_SANE_WINDOW_MS = 6_000L
  private const val PREFS_NAME = "taglink_dynamic_switch"
  private const val KEY_TRUE_P2P_UNTIL_MS = "true_p2p_until_ms"

  private val lock = Any()
  private val _windowState = MutableStateFlow(DynamicSwitchWindow())
  val windowState: StateFlow<DynamicSwitchWindow> = _windowState.asStateFlow()

  @Volatile private var cachedUntilMs: Long = Long.MIN_VALUE
  @Volatile internal var testWallClockMs: (() -> Long)? = null

  fun nowMs(): Long = testWallClockMs?.invoke() ?: System.currentTimeMillis()

  fun isTrueP2pWindowActive(context: Context, now: Long = nowMs()): Boolean {
    val until = readUntilMs(context)
    if (until <= 0L) return false
    val remaining = until - now
    if (remaining <= 0L || remaining > MAX_SANE_WINDOW_MS) {
      clear(context)
      return false
    }
    return true
  }

  fun remainingWindowMs(context: Context, now: Long = nowMs()): Long {
    val until = readUntilMs(context)
    if (until <= 0L) return 0L
    val remaining = until - now
    if (remaining <= 0L || remaining > MAX_SANE_WINDOW_MS) {
      return 0L
    }
    return remaining
  }

  fun activateTrueP2pWindow(
    context: Context,
    durationMs: Long = TRUE_P2P_WINDOW_MS,
    now: Long = nowMs(),
  ) {
    val appContext = context.applicationContext
    val until = now + durationMs.coerceIn(0L, MAX_SANE_WINDOW_MS)
    synchronized(lock) {
      cachedUntilMs = until
      appContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_TRUE_P2P_UNTIL_MS, until)
        .commit()
      val nextGen = _windowState.value.generation + 1L
      _windowState.value = DynamicSwitchWindow(trueP2pUntilMs = until, generation = nextGen)
    }
  }

  fun clear(context: Context) {
    val appContext = context.applicationContext
    synchronized(lock) {
      cachedUntilMs = 0L
      appContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_TRUE_P2P_UNTIL_MS)
        .commit()
      val current = _windowState.value
      if (current.trueP2pUntilMs != 0L) {
        _windowState.value =
          DynamicSwitchWindow(trueP2pUntilMs = 0L, generation = current.generation + 1L)
      }
    }
  }

  private fun readUntilMs(context: Context): Long {
    val cached = cachedUntilMs
    if (cached != Long.MIN_VALUE) {
      return cached
    }
    val appContext = context.applicationContext
    synchronized(lock) {
      val recheck = cachedUntilMs
      if (recheck != Long.MIN_VALUE) {
        return recheck
      }
      val stored =
        appContext
          .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
          .getLong(KEY_TRUE_P2P_UNTIL_MS, 0L)
      cachedUntilMs = stored
      return stored
    }
  }

  internal fun resetMemoryCacheForTest() {
    synchronized(lock) {
      cachedUntilMs = Long.MIN_VALUE
    }
  }

  internal fun resetForTest(context: Context? = null) {
    synchronized(lock) {
      testWallClockMs = null
      cachedUntilMs = 0L
      context
        ?.applicationContext
        ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?.edit()
        ?.clear()
        ?.commit()
      _windowState.value = DynamicSwitchWindow(trueP2pUntilMs = 0L, generation = 0L)
    }
  }
}
