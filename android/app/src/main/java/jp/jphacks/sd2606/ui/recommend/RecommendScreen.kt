package jp.jphacks.sd2606.ui.recommend

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Labels
import jp.jphacks.sd2606.data.Recommendation
import jp.jphacks.sd2606.data.RecommendationResponse
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

@Composable
fun RecommendScreen(
    result: UiResult<RecommendationResponse>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(topBar = { BackTopBar("あなたに関係がありそうな手続き", onBack) }) { padding ->
        ResultContent(result, onRetry, Modifier.padding(padding)) { data ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                data.overallAdvice?.let { advice ->
                    item { AdviceCard(advice) }
                }
                if (data.items.isEmpty()) {
                    item { Text("いま特に必要な手続きは見つかりませんでした。") }
                }
                items(data.items, key = { it.procedureId }) { item ->
                    RecommendationCard(item, onClick = { onOpen(item.procedureId) })
                }
            }
        }
    }
}

@Composable
private fun AdviceCard(advice: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "AI のアドバイス")
            Spacer(Modifier.width(12.dp))
            Text(advice, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecommendationCard(item: Recommendation, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onClick, label = { Text(Labels.category[item.category] ?: item.category) })
                Spacer(Modifier.width(8.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium)
            }
            item.deadline?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = "期限", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            item.reasons.forEach { reason ->
                Text("・$reason", style = MaterialTheme.typography.bodyMedium)
            }
            item.personalNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecommendScreenPreview() {
    AppTheme { RecommendScreen(UiResult.Success(SampleData.recommendations), onRetry = {}, onBack = {}, onOpen = {}) }
}
