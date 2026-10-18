package jp.jphacks.sd2606.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jp.jphacks.sd2606.data.Labels
import androidx.compose.material.icons.outlined.Payments
import jp.jphacks.sd2606.data.AmountView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material3.OutlinedButton
import jp.jphacks.sd2606.data.Scored
import jp.jphacks.sd2606.data.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, bold: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = if (bold) FontWeight.Bold else null) },
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

/** 分類（税金・保険・年金・給付・届出）ごとの色のタグ。 */
@Composable
fun CategoryChip(category: String, onClick: () -> Unit) {
    val (container, label) = categoryColors(category)
    AssistChip(
        onClick = onClick,
        label = { Text(Labels.category[category] ?: category) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = label),
        border = null,
    )
}

/** 分類の色（背景と文字）。暗いテーマでは背景を暗く、文字を明るくする。 */
@Composable
private fun categoryColors(category: String): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    val (light, night) = when (category) {
        "tax" -> (Color(0xFFDCE7FF) to Color(0xFF1B3A7A)) to (Color(0xFF2A3D66) to Color(0xFFD6E2FF))
        "insurance" -> (Color(0xFFD8F3E3) to Color(0xFF1E5B3A)) to (Color(0xFF24493A) to Color(0xFFCDEFD9))
        "pension" -> (Color(0xFFFFE7CC) to Color(0xFF7A4A12)) to (Color(0xFF5A3E1E) to Color(0xFFFFDDB8))
        "benefit" -> (Color(0xFFFBD9E8) to Color(0xFF7A1F4A)) to (Color(0xFF5A2A40) to Color(0xFFFFD6E7))
        else -> (Color(0xFFE6E0F5) to Color(0xFF3F2F6B)) to (Color(0xFF3A3352) to Color(0xFFE4DDF9))
    }
    return if (dark) night else light
}

/** 下部のナビゲーションバーで切り替える画面。 */
enum class Tab { PROCEDURES, METER, HISTORY }

/** 「手続き」「バイト代」「通知と履歴」を切り替える下部のナビゲーションバー。 */
@Composable
fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == Tab.PROCEDURES,
            onClick = { onSelect(Tab.PROCEDURES) },
            icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
            label = { Text("手続き") },
        )
        NavigationBarItem(
            selected = selected == Tab.METER,
            onClick = { onSelect(Tab.METER) },
            icon = { Icon(Icons.Default.Savings, contentDescription = null) },
            label = { Text("バイト代") },
        )
        NavigationBarItem(
            selected = selected == Tab.HISTORY,
            onClick = { onSelect(Tab.HISTORY) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            label = { Text("通知と履歴") },
        )
    }
}

/**
 * 一覧のカードと手続きの画面の下部に並べるボタン（どちらも同じ並び）。
 * - 必要な手続き: 「完了にする」だけ
 * - 関係がありそうな手続き: 左「関係ない」（対象外へ移す）、右「当てはまる」（必要な手続きへ移す）
 * - 「関係ない」にした手続き: 左「関係ありに戻す」、右「完了にする」
 * 完了したものは、右が「未完了に戻す」になる。
 */
@Composable
fun ProcedureActions(
    item: Scored,
    completed: Boolean,
    onComplete: (Boolean) -> Unit,
    onDismiss: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item.status != Status.REQUIRED) {
            OutlinedButton(onClick = { onDismiss(!item.dismissed) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (item.dismissed) "関係ありに戻す" else "関係ない")
            }
        }
        when {
            completed -> OutlinedButton(onClick = { onComplete(false) }, modifier = Modifier.weight(1f)) { Text("未完了に戻す") }
            item.status == Status.LIKELY -> Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("当てはまる")
            }
            else -> Button(onClick = { onComplete(true) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("完了にする")
            }
        }
    }
}

/** お金の目安の1行（一覧のカード用）: 「あなたの住民税（2027年6月から納める）　年 約5,000円」。 */
@Composable
fun AmountLine(amount: AmountView, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Payments, contentDescription = "金額の目安", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(amount.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(amount.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
