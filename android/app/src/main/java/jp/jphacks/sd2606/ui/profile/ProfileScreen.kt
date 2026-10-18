package jp.jphacks.sd2606.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Catalog
import jp.jphacks.sd2606.data.Question
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.QuestionInput
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

/**
 * 最初の画面。基本情報（required: 働き方・生年月日・扶養の状況・年収）は必ず答えてもらい、
 * それ以外は「もっと詳しく（任意）」に畳んでおく。任意の項目は、判定に必要になった手続きのカードでも聞く。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    catalog: UiResult<Catalog>,
    answers: Map<String, Answer>,
    onAnswer: (String, Answer?) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
) {
    var showOptional by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("あなたの状況を教えてください") }) }) { padding ->
        ResultContent(catalog, onRetry, Modifier.padding(padding)) { data ->
            val required = data.questions.filter { it.required }
            val optional = data.questions.filter { !it.required && !it.inline } // inline は手続きのカードの中でだけ聞く
            val missing = required.filter { it.field !in answers }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PrivacyNote("答えた内容はこの端末の中だけに保存し、サーバーには送りません。")

                required.forEach { q -> QuestionBlock(q, answers[q.field], required = true) { onAnswer(q.field, it) } }

                if (optional.isNotEmpty()) {
                    Surface(
                        onClick = { showOptional = !showOptional },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("もっと詳しく（任意）", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "入れると、必要かどうかが確定する手続きが増えます。入れなくても、必要になった手続きでお聞きします。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (showOptional) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showOptional) "閉じる" else "開く",
                            )
                        }
                    }
                    if (showOptional) {
                        optional.forEach { q -> QuestionBlock(q, answers[q.field], required = false) { onAnswer(q.field, it) } }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSubmit, enabled = missing.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("必要な手続きを調べる")
                    }
                    if (missing.isNotEmpty()) {
                        Text(
                            "未入力の必須項目: ${missing.joinToString("・") { it.name }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(question: Question, answer: Answer?, required: Boolean, onAnswer: (Answer?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            if (required) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "必須",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        // 最初の画面は並びが動かないので、数値は入力のたびに反映する（「決定」を押し忘れて未入力にならないように）
        QuestionInput(question, answer, onAnswer, live = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(UiResult.Success(SampleData.catalog), SampleData.answers, onAnswer = { _, _ -> }, onRetry = {}, onSubmit = {})
    }
}
