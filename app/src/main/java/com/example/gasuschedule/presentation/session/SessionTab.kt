package com.example.gasuschedule.presentation.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.presentation.theme.GasuTheme

/**
 * Вкладка "Сессия" в расписании. Пока расписание сессии не опубликовано на сайте, здесь
 * только пояснение — экзамены появятся после публикации (запрос getRasp с ONLY_SESSIA=Y,
 * см. README, раздел "Сессия").
 */
@Composable
fun SessionTab() {
    Column(
        Modifier
            .fillMaxSize()
            // Прокрутка нужна, чтобы pull-to-refresh работал и на этой вкладке.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Расписание сессии ещё не опубликовано",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Когда оно появится на сайте СПбГАСУ, здесь будут экзамены и зачёты с обратным отсчётом до ближайшего.",
            style = MaterialTheme.typography.bodyMedium,
            color = GasuTheme.colors.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
