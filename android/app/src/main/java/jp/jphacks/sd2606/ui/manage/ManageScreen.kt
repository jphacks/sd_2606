package jp.jphacks.sd2606.ui.manage

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jp.jphacks.sd2606.data.ReminderView
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.notify.Reminders
import jp.jphacks.sd2606.ui.AppUiState
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.completedLabel

/**
 * 通知と履歴。期限の前の通知をオン・オフし、完了した手続きと「関係ない」にした手続きを見返して元に戻せる。
 * 通知は端末の中だけで予約する（AlarmManager）。
 */
@Composable
fun ManageScreen(
    state: AppUiState,
    onReminder: (String, Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onComplete: (String, Boolean) -> Unit,
    onDismiss: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Reminders.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val askPermission = {
        if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Scaffold(topBar = { BackTopBar("通知と履歴", onBack) }) { padding ->
        ResultContent(state.catalog, onRetry, Modifier.padding(padding)) {
            val evaluation = state.evaluation ?: return@ResultContent
            val today = remember { LocalDate.now() }
            val reminders = RuleEngine.upcomingReminders(evaluation, today)
            val completed = evaluation.items.filter { it.procedure.id in state.completed }.sortedByDescending { state.completed[it.procedure.id] }
            val dismissed = evaluation.items.filter { it.dismissed }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding),
            ) {
                item { SectionTitle("通知") }
                item { PrivacyNote("期限の前の朝9時に、この端末から通知します。予定はサーバーに送りません。") }
                if (state.remindersOn.isNotEmpty() && !allowed) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("通知が許可されていません", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = askPermission) { Text("許可する") }
                            }
                        }
                    }
                }
                if (reminders.isEmpty()) {
                    item { Text("いまの手続きには、通知できる期限がありません。", style = MaterialTheme.typography.bodyMedium) }
                }
                items(reminders, key = { "r:" + it.id }) { r ->
                    ReminderRow(r, on = r.id in state.remindersOn) { on ->
                        if (on) askPermission()
                        onReminder(r.id, on)
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            askPermission()
                            onTestNotification()
                        },
                    ) { Text("テスト通知を送る") }
                }

                item { SectionTitle("完了した手続き（${completed.size}件）") }
                if (completed.isEmpty()) item { Empty("まだありません。") }
                items(completed, key = { "c:" + it.procedure.id }) { item ->
                    RecordRow(
                        title = item.procedure.title,
                        note = completedLabel(state.completed[item.procedure.id].orEmpty()),
                        action = "未完了に戻す",
                        onClick = { onOpen(item.procedure.id) },
                        onAction = { onComplete(item.procedure.id, false) },
                    )
                }

                item { SectionTitle("「関係ない」にした手続き（${dismissed.size}件）") }
                if (dismissed.isEmpty()) item { Empty("まだありません。") }
                items(dismissed, key = { "d:" + it.procedure.id }) { item ->
                    RecordRow(
                        title = item.procedure.title,
                        note = null,
                        action = "元に戻す",
                        onClick = { onOpen(item.procedure.id) },
                        onAction = { onDismiss(item.procedure.id, false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
}

/** 通知1つ: 期限の名前、通知する日と期限、手続きの名前、オン・オフ。 */
@Composable
private fun ReminderRow(r: ReminderView, on: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.reminder.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${r.notifyOn.monthValue}月${r.notifyOn.dayOfMonth}日に通知（期限 ${r.due.monthValue}月${r.due.dayOfMonth}日）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(r.procedure.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = onChange)
        }
        HorizontalDivider()
    }
}

/** 履歴1つ: 手続きの名前、補足、元に戻すボタン。押すと手続きの画面を開く。 */
@Composable
private fun RecordRow(title: String, note: String?, action: String, onClick: () -> Unit, onAction: () -> Unit) {
    Column {
        Row(Modifier.clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
            TextButton(onClick = onAction) { Text(action) }
        }
        HorizontalDivider()
    }
}
