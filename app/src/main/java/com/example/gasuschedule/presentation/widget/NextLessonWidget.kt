package com.example.gasuschedule.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.gasuschedule.R
import com.example.gasuschedule.domain.usecase.GetNextLessonUseCase
import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.presentation.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDateTime

/** Glance-виджеты не поддерживают внедрение через конструктор — берём зависимости из графа Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getNextLesson(): GetNextLessonUseCase
    fun clock(): Clock
    fun refresher(): WidgetRefresher
}

/** Виджет "Ближайшая пара" по макету. Данные — только из локальной базы, без сети. */
class NextLessonWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val next = deps.getNextLesson()()
        val now = LocalDateTime.now(deps.clock())
        // Пара закончилась или началась — показанное устарело, перерисуем ровно в этот момент.
        (next as? NextLesson.Found)?.let {
            deps.refresher().scheduleRefreshAt(it.validUntil, now)
        }
        val model = widgetModel(next, now)
        provideContent { WidgetBody(model) }
    }

    /** Превью в списке виджетов лаунчера (Android 15+) — пример пары из макета. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            WidgetBody(
                WidgetModel(
                    label = "БЛИЖАЙШАЯ ПАРА",
                    corner = "10:45",
                    title = "История России",
                    subtitle = "Актовый зал/Г · Гурьев Е.П.",
                    chip = "2 пара · 10:45–12:15",
                ),
            )
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}

private val OnBrick = ColorProvider(day = Color.White, night = Color(0xFFF4ECE9))
private val OnBrickMuted = ColorProvider(day = Color.White.copy(alpha = 0.72f), night = Color(0xFFF4ECE9).copy(alpha = 0.66f))

@Composable
private fun WidgetBody(model: WidgetModel) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        // Высота виджета задаётся сеткой лаунчера — центрируем, чтобы не было пустой полосы снизу.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_notification),
                contentDescription = null,
                colorFilter = ColorFilter.tint(OnBrick),
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                model.label,
                style = TextStyle(color = OnBrickMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            model.corner?.let {
                Text(it, style = TextStyle(color = OnBrickMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(
            model.title,
            style = TextStyle(color = OnBrick, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        model.subtitle?.let {
            Spacer(GlanceModifier.height(2.dp))
            Text(it, style = TextStyle(color = OnBrickMuted, fontSize = 13.sp), maxLines = 1)
        }
        model.chip?.let {
            Spacer(GlanceModifier.height(10.dp))
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_chip))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    it,
                    style = TextStyle(color = OnBrick, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
        }
    }
}
