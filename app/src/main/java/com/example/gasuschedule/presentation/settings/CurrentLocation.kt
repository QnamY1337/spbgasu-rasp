package com.example.gasuschedule.presentation.settings

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.gasuschedule.domain.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Одна точка геолокации через системный LocationManager — без Google Play Services
 * (на части телефонов, например китайских vivo/Xiaomi, их нет).
 */
object CurrentLocation {
    @SuppressLint("MissingPermission") // разрешение проверяет вызывающий экран
    suspend fun get(context: Context): GeoPoint? {
        val manager = context.getSystemService<LocationManager>() ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            val point = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { location -> cont.resume(location?.let { GeoPoint(it.latitude, it.longitude) }) }
                }
            }
            if (point != null) return point
        }
        // Последняя известная точка — лучше, чем ничего (например, в помещении без GPS).
        return providers.firstNotNullOfOrNull { p ->
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        }
    }
}
