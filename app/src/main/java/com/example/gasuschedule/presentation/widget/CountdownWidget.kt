package com.example.gasuschedule.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.gasuschedule.R
import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.presentation.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime

/**
 * Виджет "Отсчёт до пары" (2×2): "через 25 мин" до начала, во время пары — прогресс и сколько осталось.
 * Пока до пары меньше 6 часов или она идёт, перерисовывается раз в минуту.
 */
class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val next = deps.getNextLesson()()
        val now = LocalDateTime.now(deps.clock())
        (next as? NextLesson.Found)?.let { deps.refresher().scheduleCountdownAt(countdownRefreshAt(it, now), now) }
        val model = countdownModel(next, now)
        provideContent { CountdownBody(model) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { CountdownBody(CountdownModel("ЧЕРЕЗ", "25", "мин", "Физика", "316/Г · 09:00")) }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

private val ProgressTrack = ColorProvider(day = Color(0x2E96331A), night = Color(0x33F6C3B3))
private val ProgressFill = ColorProvider(day = Color(0xFFC14524), night = Color(0xFFE8795A))

@Composable
private fun CountdownBody(model: CountdownModel) {
    // Во время пары — светлый тон бренда, до пары — кирпичный, как в макете.
    val progress = model.progress
    val strong = if (progress != null) OnTint else OnBrick
    val muted = if (progress != null) OnTintMuted else OnBrickMuted
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(if (progress != null) R.drawable.widget_tint_background else R.drawable.widget_background))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            model.caption,
            style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (progress != null) {
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ProgressFill,
                backgroundColor = ProgressTrack,
            )
        } else if (model.value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    model.value,
                    style = TextStyle(color = strong, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
                model.unit?.let {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(it, style = TextStyle(color = strong, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            model.title,
            style = TextStyle(color = strong, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        model.subtitle?.let {
            Text(it, style = TextStyle(color = muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
        }
    }
}
