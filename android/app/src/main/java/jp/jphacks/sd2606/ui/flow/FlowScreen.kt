package jp.jphacks.sd2606.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.FlowStep
import jp.jphacks.sd2606.data.Procedure
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

/** 手続きの流れを縦のタイムラインで表示する画面。 */
@Composable
fun FlowScreen(result: UiResult<Procedure>, onRetry: () -> Unit, onBack: () -> Unit) {
    val title = (result as? UiResult.Success)?.data?.title ?: "手続きの流れ"
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        ResultContent(result, onRetry, Modifier.padding(padding)) { proc ->
            val uriHandler = LocalUriHandler.current
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                item { Header(proc) }
                itemsIndexed(proc.steps) { index, step ->
                    StepRow(index + 1, step, isLast = index == proc.steps.lastIndex)
                }
                items(proc.links.size) { i ->
                    val link = proc.links[i]
                    TextButton(onClick = { uriHandler.openUri(link.url) }) { Text("🔗 ${link.label}") }
                }
            }
        }
    }
}

@Composable
private fun Header(proc: Procedure) {
    Card(Modifier.padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(proc.summary, style = MaterialTheme.typography.bodyMedium)
            proc.deadline?.let { Text("期限: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            proc.office?.let { Text("窓口: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRow(number: Int, step: FlowStep, isLast: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        // 左側: 番号の丸と、次のステップへの線
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(
            Modifier
                .padding(start = 8.dp, bottom = 24.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(step.title, style = MaterialTheme.typography.titleSmall)
            Text(step.detail, style = MaterialTheme.typography.bodyMedium)
            step.where?.let { Text("📍 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (step.documents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    step.documents.forEach { doc -> SuggestionChip(onClick = {}, label = { Text(doc) }) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlowScreenPreview() {
    AppTheme { FlowScreen(UiResult.Success(SampleData.procedure), onRetry = {}, onBack = {}) }
}
