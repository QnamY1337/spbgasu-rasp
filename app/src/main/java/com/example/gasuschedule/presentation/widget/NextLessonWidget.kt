package com.example.gasuschedule.presentation.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.gasuschedule.R
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.usecase.EstimateLeaveTimeUseCase
import com.example.gasuschedule.domain.usecase.GetNextLessonUseCase
import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.presentation.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDateTime

/** Glance-виджеты не поддерживают внедрение через конструктор — берём зависимости из графа Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getNextLesson(): GetNextLessonUseCase
    fun estimateLeaveTime(): EstimateLeaveTimeUseCase
    fun preferences(): UserPreferencesRepository
    fun clock(): Clock
    fun refresher(): WidgetRefresher
}

/**
 * Виджет "Дорога" (4×1): во сколько выходить к первой паре дня и кнопка маршрута в Яндекс.Картах,
 * в остальное время — ближайшая пара. Данные — только из локальной базы, без сети.
 */
class NextLessonWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val next = deps.getNextLesson()()
        val now = LocalDateTime.now(deps.clock())
        // Пара закончилась или началась — показанное устарело, перерисуем ровно в этот момент.
        (next as? NextLesson.Found)?.let {
            deps.refresher().scheduleRefreshAt(it.validUntil, now)
        }
        val leave = (next as? NextLesson.Found)
            ?.takeIf { it.firstOfDay && !it.ongoing }
            ?.let { deps.estimateLeaveTime()(it.lesson) }
        val home = deps.preferences().home.first()?.point
        val model = widgetModel(next, now, leave, home)
        provideContent { RoadWidgetBody(model) }
    }

    /** Превью в списке виджетов лаунчера (Android 15+). */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            RoadWidgetBody(
                WidgetModel(
                    label = "К 09:00 · ФИЗИКА · 316/Г",
                    title = "Выйти в 08:10",
                    subtitle = "≈35 мин на транспорте · через 40 мин",
                    routeQuery = "",
                ),
            )
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}

internal val OnTint = ColorProvider(day = Color(0xFF5E2012), night = Color(0xFFF6C3B3))
internal val OnTintMuted = ColorProvider(day = Color(0xFF5E2012).copy(alpha = 0.8f), night = Color(0xFFF6C3B3).copy(alpha = 0.78f))
internal val OnBrick = ColorProvider(day = Color.White, night = Color(0xFFF4ECE9))
internal val OnBrickMuted = ColorProvider(day = Color.White.copy(alpha = 0.75f), night = Color(0xFFF4ECE9).copy(alpha = 0.7f))

@Composable
private fun RoadWidgetBody(model: WidgetModel) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_tint_background))
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                model.label,
                style = TextStyle(color = OnTint, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
            Text(
                model.title,
                style = TextStyle(color = OnTint, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            model.subtitle?.let {
                Text(it, style = TextStyle(color = OnTintMuted, fontSize = 12.sp), maxLines = 1)
            }
        }
        model.routeQuery?.let { query ->
            Spacer(GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .size(56.dp)
                    .background(ImageProvider(R.drawable.widget_route_button))
                    .clickable(
                        actionStartActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?$query"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_route),
                    contentDescription = "Маршрут в Яндекс.Картах",
                    colorFilter = ColorFilter.tint(OnBrick),
                    modifier = GlanceModifier.size(26.dp),
                )
            }
        }
    }
}
