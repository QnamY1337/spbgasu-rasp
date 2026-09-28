package com.example.gasuschedule.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.TravelMode

/**
 * Маршрут в Яндекс.Картах: точное время в пути с учётом транспорта — бесплатно, в самих Картах.
 * Приложение Карт, если установлено, иначе — сайт в браузере.
 */
object YandexMaps {
    fun routeQuery(from: GeoPoint?, to: GeoPoint, mode: TravelMode): String {
        // Без точки отправления Карты строят маршрут от текущего местоположения.
        val start = from?.let { "${it.latitude},${it.longitude}" }.orEmpty()
        val rtt = if (mode == TravelMode.WALKING) "pd" else "mt"
        return "rtext=$start~${to.latitude},${to.longitude}&rtt=$rtt"
    }

    fun openRoute(context: Context, from: GeoPoint?, to: GeoPoint, mode: TravelMode) {
        val query = routeQuery(from, to, mode)
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?$query"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(app)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?$query"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
