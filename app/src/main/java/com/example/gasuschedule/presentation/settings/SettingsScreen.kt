package com.example.gasuschedule.presentation.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.BuildConfig
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.theme.MonoStyles
import com.example.gasuschedule.work.ReminderNotifications
import com.example.gasuschedule.domain.model.ScheduleTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsRoute(onChangeGroup: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    StatusBarIcons(onBrickHeader = false)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val context = LocalContext.current
    var permissions by remember { mutableStateOf(ReminderPermissions.read(context)) }
    // Разрешения меняются в системных настройках — перечитываем при каждом возвращении на экран.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val fresh = ReminderPermissions.read(context)
        if (fresh != permissions) {
            permissions = fresh
            viewModel.onPermissionsChanged()
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = ReminderPermissions.read(context)
        viewModel.onPermissionsChanged()
    }

    SettingsScreen(
        state = state,
        permissions = permissions,
        snackbar = snackbar,
        onChangeGroup = onChangeGroup,
        onRemindersEnabled = { viewModel.setRemindersEnabled(it) },
        onChangeNotifications = { viewModel.setChangeNotificationsEnabled(it) },
        onReminderMinutes = { viewModel.setReminderMinutes(it) },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notificationsPermissionAsked) {
                ReminderPermissions.markNotificationsAsked(context)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Уже отказывали — системный диалог больше не покажется, ведём в настройки приложения.
                context.startActivity(ReminderPermissions.notificationSettingsIntent(context))
            }
        },
        onRequestExactAlarms = { context.startActivity(ReminderPermissions.exactAlarmSettingsIntent(context)) },
        onTestNotification = {
            ReminderNotifications.show(
                context,
                key = "test",
                title = "Через ${state.reminderMinutes} мин · Пример пары (л.)",
                text = "09:00–10:30 · 712/С · Так будет выглядеть напоминание",
            )
        },
        onRefresh = viewModel::refresh,
        onDebugBackgroundSync = if (BuildConfig.DEBUG) viewModel::runBackgroundSyncNow else null,
    )
}

/** Состояние системных разрешений, от которых зависят напоминания. */
data class ReminderPermissions(
    val notificationsAllowed: Boolean,
    val notificationsPermissionAsked: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    companion object {
        private const val PREFS = "permissions"
        private const val KEY_ASKED = "notifications_asked"

        fun read(context: Context) = ReminderPermissions(
            notificationsAllowed = ReminderNotifications.canNotify(context),
            notificationsPermissionAsked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ASKED, false),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService<AlarmManager>()!!.canScheduleExactAlarms(),
        )

        fun markNotificationsAsked(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
        }

        fun notificationSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        fun exactAlarmSettingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    permissions: ReminderPermissions,
    snackbar: SnackbarHostState,
    onChangeGroup: () -> Unit,
    onRemindersEnabled: (Boolean) -> Unit,
    onChangeNotifications: (Boolean) -> Unit,
    onReminderMinutes: (Int) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onTestNotification: () -> Unit,
    onRefresh: () -> Unit,
    onDebugBackgroundSync: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)

            SectionTitle("ГРУППА")
            SettingsCard {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.group.orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.04.em),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChangeGroup) { Text("Изменить", fontWeight = FontWeight.SemiBold) }
                }
            }

            SectionTitle("УВЕДОМЛЕНИЯ")
            SettingsCard {
                SwitchRow("Напоминать о парах", state.remindersEnabled, onRemindersEnabled)
                RowDivider()
                MinutesRow(state.reminderMinutes, enabled = state.remindersEnabled, onSelect = onReminderMinutes)
                RowDivider()
                SwitchRow("Изменения в расписании", state.changeNotificationsEnabled, onChangeNotifications)
                if ((state.remindersEnabled || state.changeNotificationsEnabled) && !permissions.notificationsAllowed) {
                    RowDivider()
                    WarningRow(
                        text = "Уведомления запрещены — напоминания и сообщения о заменах не придут.",
                        action = "Разрешить",
                        onClick = onRequestNotifications,
                    )
                }
                if (state.remindersEnabled && !permissions.exactAlarmsAllowed) {
                    RowDivider()
                    WarningRow(
                        text = "Точные будильники запрещены — напоминание может прийти на 5 минут раньше или позже.",
                        action = "Разрешить",
                        onClick = onRequestExactAlarms,
                    )
                }
                if (state.remindersEnabled && permissions.notificationsAllowed) {
                    RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTestNotification)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text("Показать пример напоминания", color = scheme.primary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.refreshing, onClick = onRefresh)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(8.dp))
                Text("Обновить расписание вручную", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            state.lastSyncAt?.let {
                Text(
                    "Последнее обновление: ${LAST_SYNC.format(it.atZone(ScheduleTime.ZONE))}",
                    style = MonoStyles.label,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            if (onDebugBackgroundSync != null) {
                TextButton(onClick = onDebugBackgroundSync, modifier = Modifier.fillMaxWidth()) {
                    Text("Отладка: фоновая сверка сейчас", style = MonoStyles.label)
                }
            }
        }
    }
}

private val LAST_SYNC = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MonoStyles.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun MinutesRow(minutes: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Напоминать о паре за",
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$minutes мин",
                style = MonoStyles.time,
                color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        // Якорь меню — правый край строки, под значением "15 мин".
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                UserPreferencesRepository.REMINDER_MINUTES_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("$option мин", fontWeight = if (option == minutes) FontWeight.SemiBold else null) },
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningRow(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}
