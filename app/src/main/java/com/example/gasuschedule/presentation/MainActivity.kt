package com.example.gasuschedule.presentation

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
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { startViewModel.hasGroup.value == null }
        // Шапка всегда тёмно-кирпичная, поэтому иконки статус-бара светлые в обеих темах.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        setContent {
            GasuTheme {
                val hasGroup by startViewModel.hasGroup.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // Стартовый экран выбираем один раз; дальше переходами управляет навигация.
                    hasGroup?.let { AppNavHost(hasGroup = it) }
                }
            }
        }
    }
}
