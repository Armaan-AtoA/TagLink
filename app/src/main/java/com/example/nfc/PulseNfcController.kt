package com.example.nfc

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

object PulseNfcController {

  const val MIN_INTERVAL_MS = 500L
  const val MAX_INTERVAL_MS = 1_000L

  fun nextIntervalMs(random: Random = Random.Default): Long =
    random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1L)

  suspend fun runPulseLoop(
    startSending: Boolean = true,
    random: Random = Random.Default,
    onPhaseChanged: (isSending: Boolean) -> Unit,
    delayBlock: suspend (Long) -> Unit = { durationMs -> delay(durationMs) },
  ) {
    var sending = startSending
    while (currentCoroutineContext().isActive) {
      onPhaseChanged(sending)
      delayBlock(nextIntervalMs(random))
      sending = !sending
    }
  }
}
