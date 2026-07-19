package com.railfancopilot.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.railfancopilot.app.MainActivity

// ── Preference keys for widget state ─────────────────────────────────────────

private val KEY_SYMBOL   = stringPreferencesKey("widget_symbol")
private val KEY_RAILROAD = stringPreferencesKey("widget_railroad")
private val KEY_SPEED    = stringPreferencesKey("widget_speed")
private val KEY_ETA      = stringPreferencesKey("widget_eta")
private val KEY_STATUS   = stringPreferencesKey("widget_status")

// ── Widget content ────────────────────────────────────────────────────────────

class RailFanWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val symbol   = prefs[KEY_SYMBOL]   ?: "No trains nearby"
    val railroad = prefs[KEY_RAILROAD] ?: ""
    val speed    = prefs[KEY_SPEED]    ?: ""
    val eta      = prefs[KEY_ETA]      ?: ""

    val bgColor      = ColorProvider(Color(0xFF141B27))
    val primaryColor = ColorProvider(Color(0xFFE2E8F0))
    val mutedColor   = ColorProvider(Color(0xFF6B7280))
    val blueColor    = ColorProvider(Color(0xFF3B82F6))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🚂",
                    style = TextStyle(fontSize = 14.sp)
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = symbol,
                    style = TextStyle(
                        color = primaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            if (railroad.isNotBlank() || speed.isNotBlank() || eta.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = buildString {
                        if (railroad.isNotBlank()) append(railroad)
                        if (speed.isNotBlank()) append(" · $speed")
                        if (eta.isNotBlank()) append(" · ETA $eta min")
                    },
                    style = TextStyle(color = mutedColor, fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }

        // "LIVE" badge top-right
        if (railroad.isNotBlank()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = "LIVE",
                    style = TextStyle(
                        color = blueColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ── Receiver ──────────────────────────────────────────────────────────────────

class RailFanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RailFanWidget()
}

// ── Widget updater helper — call from ViewModel after each train refresh ──────

object WidgetUpdater {

    /**
     * Push the nearest train data into the widget's Glance state.
     * Safe to call from any coroutine; silently swallows errors so a
     * widget update failure never surfaces to the user.
     */
    suspend fun update(
        context:  Context,
        symbol:   String,
        railroad: String,
        speedMph: Int,
        etaMin:   Int?
    ) {
        runCatching {
            val glanceIds = androidx.glance.appwidget.GlanceAppWidgetManager(context)
                .getGlanceIds(RailFanWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_SYMBOL]   = symbol
                        this[KEY_RAILROAD] = railroad
                        this[KEY_SPEED]    = if (speedMph > 0) "$speedMph mph" else ""
                        this[KEY_ETA]      = etaMin?.toString() ?: ""
                    }
                }
                RailFanWidget().update(context, glanceId)
            }
        }
    }
}
