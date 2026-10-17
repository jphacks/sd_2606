package jp.jphacks.sd2606.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.Labels
import jp.jphacks.sd2606.data.UserProfile
import jp.jphacks.sd2606.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onChange: ((UserProfile) -> UserProfile) -> Unit,
    onSubmit: () -> Unit,
    onOpenMeter: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("あなたの状況を教えてください") },
                actions = {
                    IconButton(onClick = onOpenMeter) { Icon(Icons.Filled.Savings, contentDescription = "バイト代メーター") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("年齢", profile.age, "歳", Modifier.weight(1f)) { v -> onChange { it.copy(age = v) } }
                NumberField("年収", profile.annualIncome / 10_000, "万円", Modifier.weight(1f)) { v ->
                    onChange { it.copy(annualIncome = v * 10_000) }
                }
            }

            Section("職業") {
                SingleChoice(Labels.occupation, profile.occupation) { v -> onChange { it.copy(occupation = v) } }
            }

            Section("いま入っている健康保険") {
                SingleChoice(Labels.healthInsurance, profile.healthInsurance) { v -> onChange { it.copy(healthInsurance = v) } }
            }

            Section("家族") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("配偶者がいる", modifier = Modifier.weight(1f))
                    Switch(checked = profile.hasSpouse, onCheckedChange = { v -> onChange { it.copy(hasSpouse = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扶養している家族", modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange { it.copy(dependents = (it.dependents - 1).coerceAtLeast(0)) } }) { Text("−") }
                    Text("${profile.dependents} 人")
                    TextButton(onClick = { onChange { it.copy(dependents = it.dependents + 1) } }) { Text("＋") }
                }
            }

            Section("最近あったこと（複数選択可）") {
                MultiChoice(Labels.lifeEvent, profile.lifeEvents) { v -> onChange { it.copy(lifeEvents = v) } }
            }

            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                Text("必要な手続きを調べる")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun NumberField(label: String, value: Int, suffix: String, modifier: Modifier, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> onValue(text.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChoice(options: Map<String, String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChoice(options: Map<String, String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isOn = value in selected
            FilterChip(
                selected = isOn,
                onClick = { onChange(if (isOn) selected - value else selected + value) },
                label = { Text(label) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    AppTheme { ProfileScreen(UserProfile(lifeEvents = listOf("resign")), onChange = {}, onSubmit = {}, onOpenMeter = {}) }
}
