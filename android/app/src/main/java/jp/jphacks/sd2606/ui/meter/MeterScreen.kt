package jp.jphacks.sd2606.ui.meter

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import jp.jphacks.sd2606.data.RuleEngine
import jp.jphacks.sd2606.ui.AppBottomBar
import jp.jphacks.sd2606.ui.Tab
import jp.jphacks.sd2606.ui.theme.AppTheme
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material3.Button

/**
 * バイト代メーター。今年の給料を容器に積み、次の年収の壁までの残りを表示する。
 * 壁は表示している年の12月31日時点の年齢で選ぶ（生年月日が無いときは19〜22歳の特例を使わない）。
 */
@Composable
fun MeterScreen(
    birthDate: LocalDate?,
    /** 制度データにある手続き（id → 名前）。壁や診断から手続きの画面に飛ぶリンクに使う */
    procedures: Map<String, String>,
    onOpenProcedure: (String) -> Unit,
    onTab: (Tab) -> Unit,
    vm: MeterViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // 編集している記録の id（null なら編集していない）
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val age = birthDate?.let { RuleEngine.ageOn(it, LocalDate.of(state.year, 12, 31)) } ?: 0

    MeterContent(
        state = state,
        age = age,
        // 勤労学生控除の壁は、年末の見込みか今の合計が163万円を超えそうなときだけ出す
        walls = incomeWallsFor(age, state.workingStudent, maxOf(state.total, state.projection(null)?.yearEnd ?: 0L)),
        onTab = onTab,
        onPreviousYear = vm::previousYear,
        onNextYear = vm::nextYear,
        onAdd = { showAdd = true },
        onDelete = vm::deleteIncome,
        onEdit = { editingId = it.id },
        procedures = procedures,
        onOpenProcedure = onOpenProcedure,
        onWorkingStudentChange = vm::setWorkingStudent,
    )
    if (showAdd) {
        IncomeDialog(
            // 一覧から外したバイト先は出さない（記録とメーターには残る）
            jobs = state.jobs.filterNot { it.hidden },
            allJobs = state.jobs,
            last = state.incomes.firstOrNull(),
            newJobColorIndex = state.nextColorIndex,
            onHideJob = vm::hideJob,
            onDismiss = { showAdd = false },
            onSave = { name, wage, colorIndex, date, amount, withheldTax ->
                vm.addIncome(name, wage, colorIndex, date, amount, withheldTax)
                showAdd = false
            },
        )
    }
    val editing = editingId?.let { id -> state.incomes.firstOrNull { it.id == id } }
    if (editing != null) {
        // key で、別の記録を開いたときに入力欄を作り直す
        key(editing.id) {
            IncomeDialog(
                jobs = state.jobs.filterNot { it.hidden },
                allJobs = state.jobs,
                last = null,
                newJobColorIndex = state.nextColorIndex,
                onHideJob = vm::hideJob,
                onDismiss = { editingId = null },
                onSave = { name, wage, colorIndex, date, amount, withheldTax ->
                    vm.updateIncome(editing, name, wage, colorIndex, date, amount, withheldTax)
                    editingId = null
                },
                editing = editing,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterContent(
    state: MeterUiState,
    /** 表示している年の12月31日時点の年齢（税金の目安で 19〜22歳かどうかを見る） */
    age: Int,
    walls: List<IncomeWall>,
    onTab: (Tab) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Income) -> Unit,
    onEdit: (Income) -> Unit = {},
    procedures: Map<String, String> = emptyMap(),
    onOpenProcedure: (String) -> Unit = {},
    onWorkingStudentChange: (Boolean) -> Unit = {},
) {
    // ビーカーの長押しで出す選択肢と詳細は、画面全体の上に重ねる
    val menu = rememberWallMenuState()
    var showDiagnosis by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlayWidth by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOrigin = it.positionInRoot()
                overlayWidth = it.size.width.toFloat()
            },
    ) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("バイト代メーター", fontWeight = FontWeight.Bold) }) },
            bottomBar = { AppBottomBar(Tab.METER, onTab) },
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
                        onDiagnose = { showDiagnosis = true },
                        onShowTodo = { position ->
                            // 長押しと同じ十字を出すが、指を離しているので出したままにしてタップで選ばせる
                            menu.show(walls.sortedBy { it.amount }, position - overlayOrigin, pinned = true, screenWidth = overlayWidth)
                            scope.launch { menu.animateIn() }
                        },
                    )
                }
                item { CumulativeChart(state, walls) }
                item {
                    Column {
                        Text("給料の記録", style = MaterialTheme.typography.titleMedium)
                        if (state.incomes.isNotEmpty()) {
                            Text("タップすると直せます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.incomes.isEmpty()) {
                    item { Text("まだ記録がありません。右下のボタンから追加してください。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.incomes, key = { it.id }) { income ->
                    IncomeRow(
                        income, state.jobName(income.jobId), jobColor(state.colorIndexOf(income.jobId)),
                        onEdit = { onEdit(income) },
                        onDelete = { onDelete(income) },
                    )
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
            procedures = procedures,
            // 手続きの画面に移るときは、重ねている詳細を閉じる
            onOpenProcedure = { id ->
                scope.launch { menu.close() }
                onOpenProcedure(id)
            },
            onClose = { scope.launch { menu.close() } },
        )
        if (showDiagnosis) {
            DiagnosisSheet(
                state = state,
                age = age,
                next = next,
                procedures = procedures,
                onWorkingStudentChange = onWorkingStudentChange,
                onOpenProcedure = onOpenProcedure,
                onDismiss = { showDiagnosis = false },
            )
        }
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
    onDiagnose: () -> Unit,
    /** 「やること」ボタン: 押した位置（画面上）を中心に壁の選択肢を出す */
    onShowTodo: (Offset) -> Unit,
) {
    val total = state.total
    val next = walls.firstOrNull { total < it.amount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.year}年の合計", style = MaterialTheme.typography.labelLarge)
                    Text(yen(total), style = MaterialTheme.typography.headlineMedium)
                }
                // 判定・範囲・税金の目安は、診断シートにまとめて重ねて出す
                FilledTonalButton(onClick = onDiagnose, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Outlined.QueryStats, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("診断")
                }
            }
            if (next != null) {
                state.projection(next)?.let { ProjectionNote(it, next, onShowTodo) }
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
private fun ProjectionNote(projection: Projection, next: IncomeWall, onShowTodo: (Offset) -> Unit) {
    val month = projection.crossMonth
    if (month == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "このペースなら、今年は「${next.shortTitle}」の壁を超えない見込みです（年末に約${projection.yearEnd / 10_000}万円）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TodoButton(onShowTodo)
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Column(Modifier.weight(1f)) {
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
        TodoButton(onShowTodo)
    }
}

/**
 * 「やること」ボタン（白地に、アプリの基本色の青緑の文字と枠。灰色のカードの上でも、淡い赤の警告の上でもなじむように）。押すと、ボタンの位置を中心に壁の選択肢を半円に出す（ビーカーの長押しと同じ選択肢）。
 * 押した位置を知らせるため、ボタンの真ん中の画面上の位置を覚えておく。
 */
@Composable
private fun TodoButton(onShowTodo: (Offset) -> Unit) {
    var center by remember { mutableStateOf(Offset.Zero) }
    Button(
        onClick = { onShowTodo(center) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TodoColor),
        border = BorderStroke(1.dp, TodoColor.copy(alpha = 0.5f)),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.onGloballyPositioned { center = it.boundsInRoot().center },
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("やること")
    }
}

private val TodoColor = Color(0xFF00796B)

@Composable
private fun IncomeRow(income: Income, jobName: String, color: Color, onEdit: () -> Unit, onDelete: () -> Unit) {
    // 行をタップすると編集する
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(start = 4.dp),
    ) {
        Text(income.paidOn.substring(5).replace('-', '/'), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
        ColorDot(color)
        Text(jobName, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(yen(income.amount), style = MaterialTheme.typography.titleSmall)
            if (income.withheldTax > 0) {
                Text("所得税 ${yen(income.withheldTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

/**
 * 給料を追加・編集するダイアログ。editing を渡すと、その記録の内容を入れた編集の画面になる。
 * jobs は選ぶチップに出すバイト先（一覧から外したものは出さない）、allJobs は記録のバイト先を探すのに使う。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IncomeDialog(
    jobs: List<Job>,
    allJobs: List<Job>,
    last: Income?,
    newJobColorIndex: Int,
    onHideJob: (Job) -> Unit,
    onDismiss: () -> Unit,
    onSave: (jobName: String, hourlyWage: Int?, colorIndex: Int, paidOn: LocalDate, amount: Long, withheldTax: Long) -> Unit,
    editing: Income? = null,
) {
    // 追加のときは、毎月の入力が数秒で済むよう、前回のバイト先と金額を最初から入れておく
    val source = editing ?: last
    val sourceJob = allJobs.firstOrNull { it.id == source?.jobId } ?: jobs.lastOrNull()
    var jobName by rememberSaveable { mutableStateOf(sourceJob?.name.orEmpty()) }
    var wage by rememberSaveable { mutableStateOf(sourceJob?.hourlyWage?.toString().orEmpty()) }
    var colorIndex by rememberSaveable { mutableIntStateOf(sourceJob?.colorIndex ?: newJobColorIndex) }
    var amount by rememberSaveable { mutableStateOf(source?.amount?.toString().orEmpty()) }
    // 天引きの所得税は月ごとに変わるので、追加のときは前回の値を入れない
    var withheld by rememberSaveable { mutableStateOf(editing?.withheldTax?.takeIf { it > 0 }?.toString().orEmpty()) }
    var date by rememberSaveable { mutableStateOf(editing?.paidOn ?: LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    val amountValue = amount.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "給料を編集" else "給料を追加") },
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
                        colorIndex = allJobs.firstOrNull { it.name == text.trim() }?.colorIndex ?: newJobColorIndex
                    },
                    label = { Text("バイト先") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = wage,
                    onValueChange = { wage = it.filter(Char::isDigit).take(5) },
                    label = { Text("時給（任意）") },
                    suffix = { Text("円") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = withheld,
                    onValueChange = { withheld = it.filter(Char::isDigit).take(7) },
                    label = { Text("所得税（給与明細の天引き額・任意）") },
                    supportingText = { Text("入れると、戻ってくる額の目安が出ます") },
                    suffix = { Text("円") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    amountValue?.let { onSave(jobName, wage.toIntOrNull(), colorIndex, LocalDate.parse(date), it, withheld.toLongOrNull() ?: 0L) }
                },
                enabled = jobName.isNotBlank() && amountValue != null,
            ) { Text(if (editing != null) "保存" else "追加") }
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
        MeterContent(MeterUiState(2026, jobs, incomes), age = 20, walls = incomeWallsFor(20), onTab = {}, onPreviousYear = {}, onNextYear = {}, onAdd = {}, onDelete = {})
    }
}
