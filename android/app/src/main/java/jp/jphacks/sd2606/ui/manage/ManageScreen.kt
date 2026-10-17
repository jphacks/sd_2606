package jp.jphacks.sd2606.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.ui.AppBottomBar
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.Tab

/**
 * 通知と履歴。完了した手続き（完了した日）と、これからの期限（毎月・毎年の納付や申告）を、
 * 日付の古い順に上から1本の時系列に並べる。完了したものは「未完了に戻す」で戻せる。
 * 通知そのものは送らず、期限の before_days 日前から「もうすぐ」と目立たせる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    state: AppUiState,
    onComplete: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onTab: (Tab) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("通知と履歴", fontWeight = FontWeight.Bold) }) },
        bottomBar = { AppBottomBar(Tab.HISTORY, onTab) },
    ) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) {
            val evaluation = state.evaluation ?: return@ResultContent
            val today = remember { LocalDate.now() }
            val done = evaluation.items
                .mapNotNull { item -> state.completed[item.procedure.id]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { item to it } }
                .sortedBy { it.second }
            val upcoming = RuleEngine.upcomingReminders(evaluation, state.completed.keys, today)
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                item {
                    Text(
                        "完了した手続きと、これからの期限を日付の順に並べています。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(done, key = { "done:" + it.first.procedure.id }) { (item, date) ->
                    TimelineRow(
                        date = date,
                        today = today,
                        icon = Icons.Default.CheckCircle,
                        iconColor = MaterialTheme.colorScheme.primary,
                        title = item.procedure.title,
                        note = "完了",
                        onClick = { onOpen(item.procedure.id) },
                    ) {
                        TextButton(onClick = { onComplete(item.procedure.id, false) }) { Text("未完了に戻す") }
                    }
                }
                item(key = "today") { TodayDivider(today) }
                if (upcoming.isEmpty()) {
                    item { Text("これからの期限はありません。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
                }
                items(upcoming, key = { "due:" + it.id }) { r ->
                    val days = ChronoUnit.DAYS.between(today, r.due)
                    TimelineRow(
                        date = r.due,
                        today = today,
                        icon = Icons.Default.Schedule,
                        iconColor = if (r.soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        title = r.reminder.label,
                        note = r.procedure.title,
                        onClick = { onOpen(r.procedure.id) },
                    ) {
                        Text(
                            if (days == 0L) "今日" else "あと${days}日",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (r.soon) FontWeight.Bold else null,
                            color = if (r.soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 「今日」の区切り。上が過去（完了）、下がこれから（期限）。 */
@Composable
private fun TodayDivider(today: LocalDate) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            "今日 ${today.monthValue}月${today.dayOfMonth}日",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

/** 時系列の1行: 日付、アイコン、タイトルと補足、右端（戻すボタンや「あと〇日」）。押すと手続きの画面を開く。 */
@Composable
private fun TimelineRow(
    date: LocalDate,
    today: LocalDate,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    note: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (date.year != today.year) {
                    Text("${date.year}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Text("${date.monthValue}月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("${date.dayOfMonth}日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            trailing()
        }
        HorizontalDivider()
    }
}
