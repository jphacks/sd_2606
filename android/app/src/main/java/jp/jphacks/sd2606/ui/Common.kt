package jp.jphacks.sd2606.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
        actions = actions,
    )
}

/** UiResult の Loading / Error を共通の見た目で出し、Success のときだけ content を描く。 */
@Composable
fun <T> ResultContent(result: UiResult<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (result) {
        is UiResult.Success -> content(result.data)
        is UiResult.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(result.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                Button(onClick = onRetry) { Text("再試行") }
            }
        }
        UiResult.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/** 「10月17日に完了」（今年でなければ年も付ける）。 */
fun completedLabel(isoDate: String, today: LocalDate = LocalDate.now()): String {
    val d = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return "完了"
    val year = if (d.year == today.year) "" else "${d.year}年"
    return "$year${d.monthValue}月${d.dayOfMonth}日に完了"
}
