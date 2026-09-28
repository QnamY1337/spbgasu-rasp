package com.example.gasuschedule.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.StudyGroup
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles

@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    StatusBarIcons(onBrickHeader = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    OnboardingScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSelect = viewModel::select,
        onSubmit = viewModel::submit,
        onRetryGroups = viewModel::loadGroups,
        onBack = onBack,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (StudyGroup) -> Unit,
    onSubmit: () -> Unit,
    onRetryGroups: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val colors = GasuTheme.colors
    val focus = LocalFocusManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.header),
    ) {
        // Шапка
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = if (onBack != null) 8.dp else 48.dp, bottom = 36.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = colors.onHeader)
                }
                Spacer(Modifier.height(8.dp))
            }
            Text("ШАГ 1 ИЗ 1", style = MonoStyles.label, color = colors.onHeaderMuted)
            Spacer(Modifier.height(10.dp))
            Text(
                "Найди свою группу",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onHeader,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Расписание подтянется автоматически с сайта СПбГАСУ",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onHeaderMuted,
            )
        }

        // Лист с поиском
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp),
                    placeholder = { Text("Например, 3-ТТП-26") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.04.em),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Box(Modifier.weight(1f)) {
                    GroupsList(state, onSelect, onRetryGroups)
                }

                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                Button(
                    onClick = { focus.clearFocus(); onSubmit() },
                    enabled = state.candidate != null && !state.submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Загружаем расписание…")
                    } else {
                        Text(state.candidate?.let { "Продолжить с $it" } ?: "Выбери группу")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsList(state: OnboardingUiState, onSelect: (StudyGroup) -> Unit, onRetry: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val groups = state.groups) {
        GroupsState.Loading -> Row(
            Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text("Загружаем список групп…", color = muted)
        }

        is GroupsState.Error -> Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(groups.message, color = muted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { Text("Повторить") }
        }

        is GroupsState.Loaded -> when {
            state.query.isBlank() -> Text(
                "Начни вводить номер группы — например, «ТТП» или «3-ТТП-26»",
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )

            state.matches.isEmpty() -> Text(
                "Ничего не найдено по «${state.query.trim()}»",
                color = muted,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "СОВПАДЕНИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
                items(state.matches, key = { it.name }) { group ->
                    GroupItem(group, selected = group.name == state.selected, onClick = { onSelect(group) })
                }
            }
        }
    }
}

@Composable
private fun GroupItem(group: StudyGroup, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.04.em),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                group.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = "Выбрана", tint = scheme.primary)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    val groups = listOf(
        StudyGroup("3-ТТП-26", "Автомобильно-дорожный факультет", "Бакалавриат", "1 курс"),
        StudyGroup("3-ТТП-25", "Автомобильно-дорожный факультет", "Бакалавриат", "2 курс"),
    )
    GasuTheme {
        OnboardingScreen(
            state = OnboardingUiState(
                query = "3-ТТП-26",
                groups = GroupsState.Loaded(groups),
                matches = groups,
                selected = "3-ТТП-26",
            ),
            onQueryChange = {}, onSelect = {}, onSubmit = {}, onRetryGroups = {}, onBack = null,
        )
    }
}
