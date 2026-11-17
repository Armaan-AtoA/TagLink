package com.example.nfc

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DynamicReceiveWindow(
  val receiveUntilMs: Long = 0L,
  val generation: Long = 0L,
) {
  val isActive: Boolean get() = receiveUntilMs > 0L
}

object DynamicSwitchController {

  const val RECEIVE_WINDOW_MS = 3_000L
  const val APDU_HANDOFF_DELAY_MS = 100L

  private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val stateLock = Any()

  private val _windowState = MutableStateFlow(DynamicReceiveWindow())
  val windowState: StateFlow<DynamicReceiveWindow> = _windowState.asStateFlow()

  @Volatile private var pendingArmJob: Job? = null
  @Volatile private var expiryJob: Job? = null
  @Volatile internal var testClockMs: (() -> Long)? = null

  fun nowMs(): Long = testClockMs?.invoke() ?: SystemClock.elapsedRealtime()

  fun isSuppressingHce(now: Long = nowMs()): Boolean = _windowState.value.receiveUntilMs > now

  fun remainingWindowMs(now: Long = nowMs()): Long =
    (_windowState.value.receiveUntilMs - now).coerceAtLeast(0L)

  fun onNdefTapCompleted(handoffDelayMs: Long = APDU_HANDOFF_DELAY_MS) {
    val currentNow = nowMs()
    synchronized(stateLock) {
      if (_windowState.value.receiveUntilMs > currentNow || pendingArmJob?.isActive == true) {
        return
      }
      if (handoffDelayMs <= 0L) {
        activateReceiveWindowLocked(durationMs = RECEIVE_WINDOW_MS, now = currentNow, scheduleAutoClear = true)
        return
      }
      pendingArmJob =
        controllerScope.launch {
          delay(handoffDelayMs)
          activateReceiveWindow()
        }
    }
  }

  fun onLinkDeactivatedAfterRead() {
    synchronized(stateLock) {
      val currentNow = nowMs()
      val hadPending = pendingArmJob?.isActive == true
      if (!hadPending && _windowState.value.receiveUntilMs > currentNow) {
        return
      }
      if (hadPending) {
        activateReceiveWindowLocked(durationMs = RECEIVE_WINDOW_MS, now = currentNow, scheduleAutoClear = true)
      }
    }
  }

  fun activateReceiveWindow(
    durationMs: Long = RECEIVE_WINDOW_MS,
    now: Long = nowMs(),
    scheduleAutoClear: Boolean = testClockMs == null,
  ) {
    synchronized(stateLock) {
      activateReceiveWindowLocked(
        durationMs = durationMs,
        now = now,
        scheduleAutoClear = scheduleAutoClear,
      )
    }
  }

  private fun activateReceiveWindowLocked(
    durationMs: Long,
    now: Long,
    scheduleAutoClear: Boolean,
  ) {
    pendingArmJob?.cancel()
    pendingArmJob = null
    expiryJob?.cancel()
    expiryJob = null

    val clampedDuration = durationMs.coerceAtLeast(0L)
    val until = now + clampedDuration
    var createdGeneration = 0L
    _windowState.update { current ->
      createdGeneration = current.generation + 1L
      DynamicReceiveWindow(
        receiveUntilMs = until,
        generation = createdGeneration,
      )
    }

    if (scheduleAutoClear && clampedDuration > 0L) {
      expiryJob =
        controllerScope.launch {
          delay(clampedDuration)
          clearIfWindowMatches(createdGeneration)
        }
    }
  }

  fun clearIfWindowMatches(generation: Long) {
    synchronized(stateLock) {
      val current = _windowState.value
      if (current.generation == generation && current.receiveUntilMs > 0L) {
        expiryJob?.cancel()
        expiryJob = null
        _windowState.value =
          DynamicReceiveWindow(
            receiveUntilMs = 0L,
            generation = current.generation + 1L,
          )
      }
    }
  }

  fun clear() {
    synchronized(stateLock) {
      pendingArmJob?.cancel()
      pendingArmJob = null
      expiryJob?.cancel()
      expiryJob = null
      val current = _windowState.value
      if (current.receiveUntilMs != 0L) {
        _windowState.value =
          DynamicReceiveWindow(
            receiveUntilMs = 0L,
            generation = current.generation + 1L,
          )
      }
    }
  }

  internal fun resetForTest() {
    synchronized(stateLock) {
      pendingArmJob?.cancel()
      pendingArmJob = null
      expiryJob?.cancel()
      expiryJob = null
      testClockMs = null
      _windowState.value = DynamicReceiveWindow(receiveUntilMs = 0L, generation = 0L)
    }
  }
}
