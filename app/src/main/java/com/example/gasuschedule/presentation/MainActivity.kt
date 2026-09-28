package com.example.gasuschedule.presentation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.presentation.navigation.AppNavHost
import com.example.gasuschedule.presentation.theme.GasuTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(preferences: UserPreferencesRepository) : ViewModel() {
    /**
     * null — ещё читаем DataStore; true/false — была ли выбрана группа на момент запуска.
     * Читаем один раз: иначе после онбординга NavHost пересоздался бы с другим стартовым экраном.
     */
    val hasGroup: StateFlow<Boolean?> = flow { emit(preferences.groupName.first() != null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Счётчик запросов "открыть замены" из уведомлений; во ViewModel — чтобы пережить поворот. */
    val openChangesRequest = MutableStateFlow(0)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { startViewModel.hasGroup.value == null }
        // Иконки статус-бара по умолчанию светлые (под кирпичной шапкой); экраны без шапки
        // переключают их сами — см. StatusBarIcons.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        // При пересоздании Activity (поворот) intent тот же — повторно не обрабатываем.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            GasuTheme {
                val hasGroup by startViewModel.hasGroup.collectAsStateWithLifecycle()
                val openChanges by startViewModel.openChangesRequest.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // Стартовый экран выбираем один раз; дальше переходами управляет навигация.
                    hasGroup?.let { AppNavHost(hasGroup = it, openChangesRequest = openChanges) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_CHANGES) startViewModel.openChangesRequest.value++
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_CHANGES = "changes"
    }
}
