package com.example

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.TagDatabase
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.PulseNfcController
import com.example.nfc.SilentPeerBridgeCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PulseNfcControllerTest {

  @Test
  fun nextIntervalMs_alwaysWithin500To1000MsAndRandomized() {
    val rng = Random(42)
    val intervals = (1..200).map { PulseNfcController.nextIntervalMs(rng) }
    assertTrue(intervals.all { it in 500L..1000L })
    assertTrue(intervals.distinct().size > 20)
  }

  @Test
  fun runPulseLoop_alternatesSendAndReceivePhasesAtRandomIntervals() = runBlocking {
    val observedPhases = mutableListOf<Boolean>()
    val observedDelays = mutableListOf<Long>()
    val rng = Random(1234)

    try {
      PulseNfcController.runPulseLoop(
        startSending = true,
        random = rng,
        onPhaseChanged = { sending -> observedPhases += sending },
        delayBlock = { durationMs ->
          observedDelays += durationMs
          if (observedDelays.size >= 6) {
            throw CancellationException("stop")
          }
        },
      )
    } catch (_: CancellationException) {
      // Expected stop after 6 alternations
    }

    assertEquals(listOf(true, false, true, false, true, false), observedPhases)
    assertTrue(observedDelays.all { it in 500L..1000L })
  }

  @Test
  fun pulseScannedIncomingTag_executesActionWithoutSavingNewCard() = runBlocking {
    SilentPeerBridgeCoordinator.resetDedupeForTest()
    val app = ApplicationProvider.getApplicationContext<Application>()
    val dao = TagDatabase.getInstance(app).tagDao()
    val beforeCount = dao.observeSavedLinks().first().size

    val incomingBytes =
      NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://github.com/peer_pulse")))
    val launched = SilentPeerBridgeCoordinator.executeIncomingPeerCard(app, incomingBytes, "")
    assertTrue(launched)

    val started = shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals(Intent.ACTION_VIEW, started!!.action)
    assertEquals("https://github.com/peer_pulse", started.dataString)
    assertEquals(beforeCount, dao.observeSavedLinks().first().size)
  }
}
