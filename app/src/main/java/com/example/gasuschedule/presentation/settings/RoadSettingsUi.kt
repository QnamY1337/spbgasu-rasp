package com.example.gasuschedule.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.presentation.theme.MonoStyles
import kotlinx.coroutines.launch

/** Раздел "Дорога до вуза" по макету: адрес дома, способ передвижения, запас, "Пора выходить". */
@Composable
internal fun RoadCard(
    road: RoadSettings,
    onEditHome: () -> Unit,
    onTravelMode: (TravelMode) -> Unit,
    onLeaveBuffer: (Int) -> Unit,
    onLeaveReminders: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditHome)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Адрес дома", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(16.dp))
            Text(
                road.home?.label ?: "не указан",
                style = MaterialTheme.typography.bodyMedium,
                color = if (road.home == null) scheme.onSurfaceVariant else scheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        RowDivider()
        Column(Modifier.padding(16.dp)) {
            Text("Способ передвижения", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            ModeToggle(road.mode, onTravelMode)
        }
        RowDivider()
        OptionRow(
            title = "Запас времени",
            value = road.bufferMinutes,
            options = UserPreferencesRepository.LEAVE_BUFFER_OPTIONS,
            label = { "$it мин" },
            onSelect = onLeaveBuffer,
        )
        RowDivider()
        SwitchRow("Напоминать, когда пора выходить", road.leaveReminders, onLeaveReminders)
    }
}

/** Сегментный переключатель "Пешком | Транспорт" как в макете. */
@Composable
private fun ModeToggle(mode: TravelMode, onChange: (TravelMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.background, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        listOf(TravelMode.WALKING to "Пешком", TravelMode.TRANSIT to "Транспорт").forEach { (m, title) ->
            val selected = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selected) scheme.surface else scheme.background, RoundedCornerShape(10.dp))
                    .clickable { onChange(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Выбор дома: поиск по адресу (OpenStreetMap) или текущая геолокация. */
@Composable
internal fun HomeAddressDialog(
    current: HomeLocation?,
    search: AddressSearchState,
    onSearch: (String) -> Unit,
    onChoose: (HomeLocation) -> Unit,
    onClear: () -> Unit,
    onLocation: (GeoPoint) -> Unit,
    onLocationFailed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        scope.launch {
            val point = CurrentLocation.get(context)
            locating = false
            if (point != null) onLocation(point)
            else onLocationFailed("Не удалось определить местоположение. Включите геолокацию или найдите адрес.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else onLocationFailed("Нет доступа к геолокации")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Адрес дома") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Улица, дом") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSearch(query) }, enabled = query.isNotBlank()) { Text("Найти") }
                    TextButton(
                        onClick = {
                            val fine = Manifest.permission.ACCESS_FINE_LOCATION
                            val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
                            val has = listOf(fine, coarse).any {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (has) locate() else permission.launch(arrayOf(fine, coarse))
                        },
                        enabled = !locating,
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Я дома")
                    }
                    if (search.searching || locating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                search.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(search.results) { place ->
                        Text(
                            place.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(place) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                current?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Сейчас: ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = if (current != null) {
            { TextButton(onClick = onClear) { Text("Удалить адрес") } }
        } else null,
    )
}

/** Строка "Название ... значение" с выпадающим списком вариантов. */
@Composable
internal fun <T> OptionRow(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(value), style = MonoStyles.time, color = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option), fontWeight = if (option == value) FontWeight.SemiBold else null) },
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}
