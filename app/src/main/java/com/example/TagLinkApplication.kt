package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger

object AppForegroundTracker {

  private val startedCount = AtomicInteger(0)

  @VisibleForTesting internal var testForegroundOverride: Boolean? = null

  fun isAppVisible(): Boolean = testForegroundOverride ?: (startedCount.get() > 0)

  internal fun onActivityStarted() {
    startedCount.incrementAndGet()
  }

  internal fun onActivityStopped() {
    startedCount.updateAndGet { current -> if (current > 0) current - 1 else 0 }
  }
}

class TagLinkApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    registerActivityLifecycleCallbacks(
      object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) {
          AppForegroundTracker.onActivityStarted()
        }

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
          AppForegroundTracker.onActivityStopped()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      }
    )

    com.example.nfc.NfcHardwareHelper.ensureDynamicNdefAidRegistered(this)
  }
}
