package jp.jphacks.sd2606.ui.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import jp.jphacks.sd2606.data.meter.Income
import jp.jphacks.sd2606.data.meter.IncomeWall
import jp.jphacks.sd2606.data.meter.Job
import jp.jphacks.sd2606.data.meter.incomeWallsFor
import jp.jphacks.sd2606.ui.BackTopBar
import jp.jphacks.sd2606.ui.theme.AppTheme
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch

/** バイト代メーター。今年の給料を容器に積み、次の年収の壁までの残りを表示する。 */
@Composable
fun MeterScreen(age: Int, onBack: () -> Unit, vm: MeterViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    MeterContent(
        state = state,
        walls = incomeWallsFor(age),
        onBack = onBack,
        onPreviousYear = vm::previousYear,
        onNextYear = vm::nextYear,
        onAdd = { showAdd = true },
        onDelete = vm::deleteIncome,
    )
    if (showAdd) {
        AddIncomeDialog(
            // 一覧から外したバイト先は出さない（記録とメーターには残る）
            jobs = state.jobs.filterNot { it.hidden },
            last = state.incomes.firstOrNull(),
            newJobColorIndex = state.nextColorIndex,
            onHideJob = vm::hideJob,
            onDismiss = { showAdd = false },
            onSave = { name, wage, colorIndex, date, amount ->
                vm.addIncome(name, wage, colorIndex, date, amount)
                showAdd = false
            },
        )
    }
}

@Composable
private fun MeterContent(
    state: MeterUiState,
    walls: List<IncomeWall>,
    onBack: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Income) -> Unit,
) {
    // ビーカーの長押しで出す選択肢と詳細は、画面全体の上に重ねる
    val menu = rememberWallMenuState()
    val scope = rememberCoroutineScope()
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        Scaffold(
            topBar = { BackTopBar("バイト代メーター", onBack) },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("給料を追加") })
            },
        ) { padding ->
            LazyColumn(
                // 下は FAB に隠れないように空ける
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                item { YearSelector(state.year, onPreviousYear, onNextYear) }
                item {
                    SummaryCard(
                        state = state,
                        walls = walls,
                        onPeekStart = { position ->
                            // 選択肢はすべての壁（金額の小さい順）
                            val options = walls.sortedBy { it.amount }
                            if (options.isNotEmpty()) {
                                menu.show(options, position - overlayOrigin)
                                scope.launch { menu.animateIn() }
                            }
                        },
                        onPeekDrag = menu::dragBy,
                        onPeekEnd = { scope.launch { menu.release() } },
                    )
                }
                item { CumulativeChart(state, walls) }
                item { Text("給料の記録", style = MaterialTheme.typography.titleMedium) }
                if (state.incomes.isEmpty()) {
                    item { Text("まだ記録がありません。右下のボタンから追加してください。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.incomes, key = { it.id }) { income ->
                    IncomeRow(income, state.jobName(income.jobId), jobColor(state.colorIndexOf(income.jobId)), onDelete = { onDelete(income) })
                }
                item {
                    Text(
                        "※ 金額は目安です。年収は1〜12月に支給された額（交通費を除く）で数えます。正確な判定は勤務先・税務署・自治体に確認してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val next = walls.firstOrNull { state.total < it.amount }
        WallMenuOverlay(
            menu = menu,
            total = state.total,
            projection = next?.let { state.projection(it) },
            nextWall = next,
            onClose = { scope.launch { menu.close() } },
        )
    }
}

@Composable
private fun YearSelector(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の年") }
        Text("${year}年", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の年") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(
    state: MeterUiState,
    walls: List<IncomeWall>,
    onPeekStart: (Offset) -> Unit,
    onPeekDrag: (Offset) -> Unit,
    onPeekEnd: () -> Unit,
) {
    val total = state.total
    val next = walls.firstOrNull { total < it.amount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("${state.year}年の合計", style = MaterialTheme.typography.labelLarge)
                Text(yen(total), style = MaterialTheme.typography.headlineMedium)
            }
            if (next != null) {
                state.projection(next)?.let { ProjectionNote(it, next) }
            }
            Beaker(
                layers = state.layers,
                walls = walls,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                onPeekStart = onPeekStart,
                onPeekDrag = onPeekDrag,
                onPeekEnd = onPeekEnd,
            )
            Text(
                "長押しすると壁が十字に広がります。指をその方向に動かして離すと詳しく見られます",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 凡例（バイト先ごとの合計）
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.jobTotals.forEach { (job, sum) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorDot(jobColor(job.colorIndex))
                        Text("${job.name} ${sum / 10_000}万", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (next != null) {
                val remaining = next.amount - total
                val hours = state.hourlyWage?.let { remaining / it }
                Column {
                    Text("次の壁「${next.title}」まで", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "あと ${yen(remaining)}" + (hours?.let { "（約${it}時間）" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (next.detail.isNotEmpty()) {
                        Text(next.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("入れすぎて入れ物が割れました！すべての壁を超えています。必要な手続きを確認しましょう。", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** このペースで働き続けたときの見込み。超えそうなら警告として目立たせる。 */
@Composable
private fun ProjectionNote(projection: Projection, next: IncomeWall) {
    val month = projection.crossMonth
    if (month == null) {
        Text(
            "このペースなら、今年は「${next.shortTitle}」の壁を超えない見込みです（年末に約${projection.yearEnd / 10_000}万円）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Column {
            Text(
                "このペースだと${month}月に「${next.shortTitle}」の壁を超えそう",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            projection.monthlyLimit?.let {
                Text(
                    "12月まで月${yen(it)}以内なら超えません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun IncomeRow(income: Income, jobName: String, color: Color, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(income.paidOn.substring(5).replace('-', '/'), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
        ColorDot(color)
        Text(jobName, modifier = Modifier.weight(1f))
        Text(yen(income.amount), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "削除") }
    }
}

@Composable
private fun ColorDot(color: Color, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddIncomeDialog(
    jobs: List<Job>,
    last: Income?,
    newJobColorIndex: Int,
    onHideJob: (Job) -> Unit,
    onDismiss: () -> Unit,
    onSave: (jobName: String, hourlyWage: Int?, colorIndex: Int, paidOn: LocalDate, amount: Long) -> Unit,
) {
    // 毎月の入力が数秒で済むよう、前回のバイト先と金額を最初から入れておく
    val lastJob = jobs.firstOrNull { it.id == last?.jobId } ?: jobs.lastOrNull()
    var jobName by rememberSaveable { mutableStateOf(lastJob?.name.orEmpty()) }
    var wage by rememberSaveable { mutableStateOf(lastJob?.hourlyWage?.toString().orEmpty()) }
    var colorIndex by rememberSaveable { mutableIntStateOf(lastJob?.colorIndex ?: newJobColorIndex) }
    var amount by rememberSaveable { mutableStateOf(last?.amount?.toString().orEmpty()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    val amountValue = amount.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("給料を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("支給日: ${date.replace('-', '/')}")
                }
                OutlinedTextField(
                    value = jobName,
                    onValueChange = { text ->
                        jobName = text
                        // 新しい名前なら、まだ使っていない色にする
                        colorIndex = jobs.firstOrNull { it.name == text.trim() }?.colorIndex ?: newJobColorIndex
                    },
                    label = { Text("バイト先") },
                    singleLine = true,
                )
                if (jobs.isNotEmpty()) {
                    // タップで選ぶと横に × が出て、× を押すとそのバイト先を一覧から外す
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        jobs.forEach { job ->
                            val selected = job.name == jobName
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    jobName = job.name
                                    wage = job.hourlyWage?.toString().orEmpty()
                                    colorIndex = job.colorIndex
                                },
                                label = { Text(job.name) },
                                leadingIcon = { ColorDot(jobColor(job.colorIndex), size = 10) },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "「${job.name}」を一覧から外す",
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onHideJob(job)
                                                    jobName = ""
                                                    wage = ""
                                                    colorIndex = newJobColorIndex
                                                },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (jobs.any { it.name == jobName }) {
                        Text(
                            "もう追加しないバイト先は × で一覧から外せます（これまでの記録はメーターに残ります）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("色", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JobPalette.forEachIndexed { index, color ->
                        val selected = index == colorIndex
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { colorIndex = index },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(8) },
                    label = { Text("支給額（交通費を除く）") },
                    suffix = { Text("円") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = wage,
                    onValueChange = { wage = it.filter(Char::isDigit).take(5) },
                    label = { Text("時給（任意）") },
                    suffix = { Text("円") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amountValue?.let { onSave(jobName, wage.toIntOrNull(), colorIndex, LocalDate.parse(date), it) } },
                enabled = jobName.isNotBlank() && amountValue != null,
            ) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )

    if (pickDate) {
        // DatePicker は UTC の 0 時をミリ秒で扱う
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("キャンセル") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

internal fun yen(value: Long) = "%,d円".format(value)

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun MeterScreenPreview() {
    val jobs = listOf(
        Job(id = 1, name = "カフェ", hourlyWage = 1_100, colorIndex = 0),
        Job(id = 2, name = "塾講師", hourlyWage = 1_800, colorIndex = 1),
    )
    val incomes = listOf(
        Income(id = 5, jobId = 2, paidOn = "2026-05-25", amount = 60_000),
        Income(id = 4, jobId = 1, paidOn = "2026-04-25", amount = 95_000),
        Income(id = 3, jobId = 2, paidOn = "2026-03-25", amount = 54_000),
        Income(id = 2, jobId = 1, paidOn = "2026-02-25", amount = 85_000),
        Income(id = 1, jobId = 1, paidOn = "2026-01-25", amount = 78_000),
    )
    AppTheme {
        MeterContent(MeterUiState(2026, jobs, incomes), incomeWallsFor(20), onBack = {}, onPreviousYear = {}, onNextYear = {}, onAdd = {}, onDelete = {})
    }
}
