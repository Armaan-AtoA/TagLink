package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppSettingsStore
import com.example.data.SavedLinkEntity
import com.example.data.TagDatabase
import com.example.data.ThemeMode
import com.example.nfc.ActiveCardStore
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle
import com.example.ui.theme.TagLinkTheme

class WidgetConfigActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setResult(RESULT_CANCELED)

    val appWidgetId =
      intent?.extras?.getInt(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
      ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    val dao = TagDatabase.getInstance(applicationContext).tagDao()
    val currentSnapshot = ActiveCardStore(applicationContext).readSnapshot()
    val themeMode = AppSettingsStore(applicationContext).readSettings().themeMode

    setContent {
      val savedCards by dao.observeSavedLinks().collectAsStateWithLifecycle(initialValue = emptyList())
      val darkTheme =
        when (themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }
      val extendedColors = LocalTagExtendedColors.current

      TagLinkTheme(darkTheme = darkTheme) {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item {
              Text(
                text = stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }

            if (currentSnapshot.hasPayload) {
              item {
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .background(MaterialTheme.colorScheme.surface)
                      .border(1.dp, MaterialTheme.colorScheme.primary)
                      .clickable {
                        WidgetPrefs.savePinnedSnapshot(this@WidgetConfigActivity, appWidgetId, currentSnapshot)
                        finishWithResult(appWidgetId)
                      }
                      .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = currentSnapshot.label.ifBlank { stringResource(R.string.widget_current_card) },
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = currentSnapshot.summary,
                    style = MonoBodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }

            if (savedCards.isEmpty() && !currentSnapshot.hasPayload) {
              item {
                Text(
                  text = stringResource(R.string.empty_saved_cards),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              items(savedCards, key = { it.id }) { card: SavedLinkEntity ->
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .background(MaterialTheme.colorScheme.surface)
                      .border(1.dp, extendedColors.border)
                      .clickable {
                        WidgetPrefs.savePinnedCard(this@WidgetConfigActivity, appWidgetId, card)
                        finishWithResult(appWidgetId)
                      }
                      .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = card.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = card.summary,
                    style = MonoBodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  private fun finishWithResult(appWidgetId: Int) {
    val manager = AppWidgetManager.getInstance(this)
    TagPresetWidgetProvider.updateWidget(this, manager, appWidgetId)
    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    setResult(RESULT_OK, result)
    finish()
  }
}
