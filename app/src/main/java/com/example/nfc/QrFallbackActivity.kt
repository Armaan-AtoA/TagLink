package com.example.nfc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettings
import com.example.data.AppSettingsStore
import com.example.data.ThemeMode
import com.example.ui.FallbackQrCard
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.TagLinkTheme

class QrFallbackActivity : ComponentActivity() {

  private var cardSnapshot by mutableStateOf<ActiveCardSnapshot?>(null)
  private var appSettings by mutableStateOf(AppSettings())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    reloadState()

    setContent {
      val snapshot = cardSnapshot
      val settings = appSettings
      val darkTheme =
        when (settings.themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }

      TagLinkTheme(darkTheme = darkTheme) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = 0.72f))
              .pointerInput(Unit) { detectTapGestures { finish() } }
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .padding(horizontal = 16.dp, vertical = 20.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier.pointerInput(Unit) { detectTapGestures {} },
          ) {
            val record = snapshot?.records?.firstOrNull()
            if (record != null) {
              FallbackQrCard(
                record = record,
                label = snapshot.label,
                foregroundArgb = settings.qrForegroundArgb,
                backgroundArgb = settings.qrBackgroundArgb,
                moduleShape = settings.qrModuleShape,
                allowCustomize = false,
                onSelectColors = { _, _ -> },
                onSelectShape = {},
                onDismiss = { finish() },
                extraFooterAction = {
                  OutlinedButton(
                    onClick = { openMainApp() },
                    shape = MaterialTheme.shapes.small,
                    modifier =
                      Modifier.fillMaxWidth()
                        .height(48.dp)
                        .testTag("qr_overlay_open_app_button"),
                  ) {
                    Text(text = stringResource(R.string.qr_open_app))
                  }
                },
              )
            } else {
              EmptyQrOverlayCard(
                onOpenApp = { openMainApp() },
                onClose = { finish() },
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    reloadState()
  }

  override fun onResume() {
    super.onResume()
    reloadState()
  }

  internal fun reloadState() {
    cardSnapshot = ActiveCardStore(this).readSnapshot()
    appSettings = AppSettingsStore(this).readSettings()
  }

  private fun openMainApp() {
    startActivity(
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    )
    finish()
  }
}

@Composable
private fun EmptyQrOverlayCard(
  onOpenApp: () -> Unit,
  onClose: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .widthIn(max = 420.dp)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, extendedColors.border)
        .padding(16.dp)
        .testTag("qr_overlay_empty_card"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.QrCode2,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = stringResource(R.string.qr_no_tag_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Text(
      text = stringResource(R.string.qr_no_tag_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onClose,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Text(text = stringResource(R.string.action_close))
      }
      Button(
        onClick = onOpenApp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Text(text = stringResource(R.string.qr_open_app))
      }
    }
  }
}
