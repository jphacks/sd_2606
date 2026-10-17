package jp.jphacks.sd2606.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.Catalog
import jp.jphacks.sd2606.data.SampleData
import jp.jphacks.sd2606.ui.PrivacyNote
import jp.jphacks.sd2606.ui.QuestionInput
import jp.jphacks.sd2606.ui.ResultContent
import jp.jphacks.sd2606.ui.UiResult
import jp.jphacks.sd2606.ui.theme.AppTheme

/** 最初の画面。おすすめが大きく変わる質問（initial: 働き方・生年月日）だけを聞く。それ以外は判定に必要になったときに聞く。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    catalog: UiResult<Catalog>,
    answers: Map<String, Answer>,
    onAnswer: (String, Answer?) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("あなたの状況を教えてください") }) }) { padding ->
        ResultContent(catalog, onRetry, Modifier.padding(padding)) { data ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PrivacyNote("答えた内容はサーバーに送信されません。")

                data.questions.filter { it.initial }.forEach { q ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(q.text, style = MaterialTheme.typography.titleMedium)
                        QuestionInput(q, answers[q.field]) { onAnswer(q.field, it) }
                    }
                }

                Text(
                    "年齢と働き方から、関係する手続きを関係の強い順に並べます。年収や扶養の状況などは、手続きの判定に必要になったときだけ、1問ずつお聞きします。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                    Text("必要な手続きを調べる")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(UiResult.Success(SampleData.catalog), SampleData.answers, onAnswer = { _, _ -> }, onRetry = {}, onSubmit = {})
    }
}
