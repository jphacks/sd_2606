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
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 通知と履歴。上に、必要な手続きのこれから1年以内の期限（毎月・毎年の納付や申告。それぞれ次の1回）を日付の近い順に、
 * 下に、完了した手続き（完了した日の新しい順）を並べる。完了したものは「未完了に戻す」で戻せる。
 * スイッチで、関係がありそうな手続きの期限も出せる（薄い文字と「関係があるかも」の但し書きで、必要なものと区別する）。
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
            var showLikely by rememberSaveable { mutableStateOf(false) }
            val evaluation = state.evaluation ?: return@ResultContent
            val today = remember { LocalDate.now() }
            val done = evaluation.items
                .mapNotNull { item -> state.completed[item.procedure.id]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { item to it } }
                .sortedByDescending { it.second }
            val upcoming = RuleEngine.upcomingReminders(evaluation, state.completed.keys, today, includeLikely = showLikely)
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                item(key = "note") {
                    Text(
                        "必要な手続きの、これから1年以内の期限を日付の近い順に並べています。完了した手続きは下にまとめています。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "likely-switch") { LikelySwitch(showLikely) { showLikely = it } }

                item(key = "h-upcoming") { SectionTitle("これからの期限（今日 ${today.monthValue}月${today.dayOfMonth}日から1年）") }
                if (upcoming.isEmpty()) {
                    item(key = "no-upcoming") {
                        Text("これからの期限はありません。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
                items(upcoming, key = { "due:" + it.id }) { r ->
                    val days = ChronoUnit.DAYS.between(today, r.due)
                    val soon = r.soon && !r.likely
                    TimelineRow(
                        date = r.due,
                        today = today,
                        icon = Icons.Default.Schedule,
                        iconColor = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        title = r.reminder.label,
                        note = if (r.likely) "関係があるかも・${r.procedure.title}" else r.procedure.title,
                        faint = r.likely,
                        onClick = { onOpen(r.procedure.id) },
                    ) {
                        Text(
                            if (days == 0L) "今日" else "あと${days}日",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (soon) FontWeight.Bold else null,
                            color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (upcoming.any { it.likely }) {
                    item(key = "likely-note") {
                        Text(
                            "薄く表示しているものは、関係がありそうな手続きの期限です。当てはまるかどうかは、押して手続きの画面で確かめてください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (done.isNotEmpty()) {
                    item(key = "h-done") { SectionTitle("完了した手続き（${done.size}件）") }
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
                }
            }
        }
    }
}

/** 関係がありそうな手続きの期限も出すかどうかのスイッチ。 */
@Composable
private fun LikelySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("関係がありそうな手続きも表示", style = MaterialTheme.typography.bodyLarge)
                Text("必要かどうかまだ分からない手続きの期限も、薄く並べます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

/** 時系列の1行: 日付、アイコン、タイトルと補足、右端（戻すボタンや「あと〇日」）。押すと手続きの画面を開く。faint なら薄く出す。 */
@Composable
private fun TimelineRow(
    date: LocalDate,
    today: LocalDate,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    note: String,
    onClick: () -> Unit,
    faint: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).alpha(if (faint) 0.5f else 1f).padding(vertical = 8.dp),
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
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (faint) null else FontWeight.Bold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            trailing()
        }
        HorizontalDivider()
    }
}
