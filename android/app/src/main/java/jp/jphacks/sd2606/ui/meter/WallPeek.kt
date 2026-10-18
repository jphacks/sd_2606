package jp.jphacks.sd2606.ui.meter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.meter.IncomeWall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 壁の選択肢（十字）と詳細の状態。開き方は2つある。
 * - 「やること」ボタン: ボタンの位置を中心に選択肢が広がり、出したままにする。選択肢をタップすると詳細が開き、外をタップすると閉じる
 * - ビーカーの長押し（隠しコマンド）: 押した位置を中心に広がり、指をその方向に動かして離すと詳細が開く。選ばずに離すと消える
 */
@Stable
class WallMenuState(private val radiusPx: Float, private val minDragPx: Float, private val pinnedRadiusPx: Float) {
    /** 広げている選択肢（空なら何も出していない） */
    var options by mutableStateOf<List<IncomeWall>>(emptyList())
        private set

    /** 長押しした位置（オーバーレイの座標） */
    var origin by mutableStateOf(Offset.Zero)
        private set

    /** 指を向けている選択肢 */
    var selected by mutableStateOf<IncomeWall?>(null)
        private set

    private var drag = Offset.Zero

    /** ボタンで開いて、出したままにしているか（長押しのときは false） */
    var pinned by mutableStateOf(false)
        private set

    /** ボタンで開いたときの半円の向き（ボタンが画面の右半分にあれば左向き） */
    private var facingLeft = true

    /** 選択肢が広がるアニメーション（0→1） */
    val appear = Animatable(0f)

    /** 開いている詳細（null なら閉じている） */
    var opened by mutableStateOf<IncomeWall?>(null)
        private set

    /** 詳細が出てくる元の位置（選んだ選択肢の位置） */
    var openFrom by mutableStateOf(Offset.Zero)
        private set

    /** 詳細が開くアニメーション（0→1） */
    val openProgress = Animatable(0f)

    /**
     * i 番目の選択肢の向き（ラジアン）。選択肢は金額の小さい順に渡す。
     * ボタンで開いたとき: ボタンと重ならないよう、画面の内側に向けた半円に、下（いちばん低い壁）から上へ等間隔で並べる。
     * 長押しのとき: 4つなら十字に、下＝いちばん低い壁・左右＝真ん中の2つ（左が低いほう）・上＝いちばん高い壁に置く。
     * 4つ以外なら、上から時計回りに等間隔で並べる。
     */
    fun angleOf(index: Int): Float {
        if (pinned) {
            val step = if (options.size <= 1) 0.0 else PI / (options.size - 1)
            // 下（π/2）から始めて、左向きなら反時計回り、右向きなら時計回りに上（−π/2）まで
            val angle = if (facingLeft) PI / 2 + step * index else PI / 2 - step * index
            return angle.toFloat()
        }
        return if (options.size == 4) CrossAngles[index] else (-PI / 2 + 2 * PI * index / options.size).toFloat()
    }

    fun positionOf(index: Int): Offset =
        origin + Offset(cos(angleOf(index)), sin(angleOf(index))) * (if (pinned) pinnedRadiusPx else radiusPx)

    /** screenWidth は、ボタンで開くときに半円の向きを決めるのに使う */
    fun show(options: List<IncomeWall>, origin: Offset, pinned: Boolean = false, screenWidth: Float = 0f) {
        this.options = options
        this.origin = origin
        this.pinned = pinned
        facingLeft = origin.x > screenWidth / 2
        drag = Offset.Zero
        selected = null
    }

    /** 出したままの選択肢をタップした: その壁の詳細を開く */
    suspend fun pick(wall: IncomeWall) {
        selected = wall
        release()
    }

    /** 出したままの選択肢を、選ばずに閉じる */
    suspend fun dismissOptions() {
        selected = null
        release()
    }

    suspend fun animateIn() {
        appear.snapTo(0f)
        appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }

    /** 指の動いた向きにいちばん近い選択肢を選ぶ（少ししか動いていなければ何も選ばない） */
    fun dragBy(delta: Offset) {
        drag += delta
        selected = if (drag.getDistance() < minDragPx) {
            null
        } else {
            val angle = atan2(drag.y, drag.x)
            options.indices.minByOrNull { angleBetween(angle, angleOf(it)) }?.let(options::get)
        }
    }

    /** 指を離した: 選んでいればその壁の詳細を開き、選択肢はしまう */
    suspend fun release() {
        val choice = selected
        if (choice != null) {
            openFrom = positionOf(options.indexOf(choice))
            opened = choice
            openProgress.snapTo(0f)
        }
        selected = null
        coroutineScope {
            launch { appear.animateTo(0f, tween(150)) }
            if (choice != null) launch { openProgress.animateTo(1f, spring(dampingRatio = 0.75f)) }
        }
        if (appear.value == 0f) {
            options = emptyList()
            pinned = false
        }
    }

    suspend fun close() {
        openProgress.animateTo(0f, tween(180))
        opened = null
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs(a - b) % (2 * PI).toFloat()
        return min(d, (2 * PI).toFloat() - d)
    }
}

/** 十字に置くときの向き（下・左・右・上） */
private val CrossAngles = listOf((PI / 2).toFloat(), PI.toFloat(), 0f, (-PI / 2).toFloat())

@Composable
fun rememberWallMenuState(): WallMenuState {
    val density = LocalDensity.current
    return remember(density) {
        with(density) { WallMenuState(radiusPx = 96.dp.toPx(), minDragPx = 24.dp.toPx(), pinnedRadiusPx = 150.dp.toPx()) }
    }
}

/** 長押しで広がる選択肢と、選んだ壁の詳細。画面全体の上に重ねる。 */
@Composable
fun WallMenuOverlay(
    menu: WallMenuState,
    total: Long,
    projection: Projection?,
    nextWall: IncomeWall?,
    /** 制度データにある手続き（id → 名前）。ここに無い手続きはリンクを出さない */
    procedures: Map<String, String>,
    onOpenProcedure: (String) -> Unit,
    onClose: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(menu.selected) {
        if (menu.selected != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = menu.opened != null) { onClose() }
    BackHandler(enabled = menu.pinned && menu.opened == null) { scope.launch { menu.dismissOptions() } }

    Box(Modifier.fillMaxSize()) {
        if (menu.options.isNotEmpty()) {
            val appear = menu.appear.value
            // ボタンで出したままのときは、選択肢の外をタップすると閉じる
            if (menu.pinned) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { scope.launch { menu.dismissOptions() } } },
                )
            }
            // 押した位置の印
            Box(
                Modifier
                    .centeredAt { menu.origin }
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = appear.coerceIn(0f, 1f)
                        scaleX = appear
                        scaleY = appear
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
            )
            // 年末の見込み（見込みが無ければ今の合計）
            val yearEnd = maxOf(total, projection?.yearEnd ?: total)
            // 選択肢は押した位置から飛び出して、十字に並ぶ。
            // 色は、超えた・年末までに超えそうな壁は淡い赤、超えない見込みの壁は淡い青。
            // 長押しで指を向けている選択肢だけ濃い色にする
            menu.options.forEachIndexed { index, wall ->
                val isSelected = wall == menu.selected
                val crosses = yearEnd >= wall.amount
                val palette = when {
                    crosses && isSelected -> WallCrossStrong
                    crosses -> WallCross
                    isSelected -> WallSafeStrong
                    else -> WallSafe
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.container,
                    contentColor = palette.content,
                    border = palette.border?.let { BorderStroke(1.dp, it) },
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .centeredAt { lerp(menu.origin, menu.positionOf(index), appear) }
                        // ボタンで出したままのときは、タップで選ぶ
                        .then(
                            if (menu.pinned) {
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { scope.launch { menu.pick(wall) } }
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            val scale = appear * if (isSelected) 1.15f else 1f
                            scaleX = scale
                            scaleY = scale
                            alpha = appear.coerceIn(0f, 1f)
                        },
                ) {
                    val mark = if (total >= wall.amount) "✓ " else ""
                    Text(
                        "$mark${wall.amount / 10_000}万 ${wall.shortTitle}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val wall = menu.opened ?: return@Box
        val p = menu.openProgress.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * p.coerceIn(0f, 1f)))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        // 詳細は文字の分だけの大きさにして、選んだ選択肢の位置から膨らむように出す
        val placedCenter = remember { arrayOf(Offset.Zero) }
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .layout { measurable, constraints ->
                    val margin = 16.dp.roundToPx()
                    val width = constraints.maxWidth - margin * 2
                    val placeable = measurable.measure(
                        Constraints(minWidth = width, maxWidth = width, maxHeight = (constraints.maxHeight * 0.7f).roundToInt()),
                    )
                    // 選んだ位置の高さに出す（画面からはみ出さないように）
                    val y = (menu.openFrom.y - placeable.height / 2f)
                        .coerceIn(margin.toFloat(), (constraints.maxHeight - placeable.height - margin).toFloat().coerceAtLeast(margin.toFloat()))
                    placedCenter[0] = Offset(margin + width / 2f, y + placeable.height / 2f)
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(margin, y.roundToInt()) }
                }
                .graphicsLayer {
                    val from = menu.openFrom - placedCenter[0]
                    translationX = from.x * (1 - p)
                    translationY = from.y * (1 - p)
                    scaleX = 0.2f + 0.8f * p
                    scaleY = 0.2f + 0.8f * p
                    alpha = p.coerceIn(0f, 1f)
                },
        ) {
            WallDetail(wall, total, projection.takeIf { wall == nextWall }, procedures, onOpenProcedure, onClose)
        }
    }
}

/** 壁の選択肢の色（背景・文字・枠）。淡い青は手続きの画面の「税」タグと同じ色 */
private class ChipPalette(val container: Color, val content: Color, val border: Color? = null)

private val WallSafe = ChipPalette(Color(0xFFDCE7FF), Color(0xFF1B3A7A), Color(0xFFB8CCF5))
private val WallSafeStrong = ChipPalette(Color(0xFF1B3A7A), Color.White)
private val WallCross = ChipPalette(Color(0xFFFFDAD6), Color(0xFF8C1D18), Color(0xFFF2B8B5))
private val WallCrossStrong = ChipPalette(Color(0xFFB3261E), Color.White)

/** 中心が center になるように置く（親いっぱいの領域の中で、はみ出さないようにずらす）。 */
private fun Modifier.centeredAt(center: () -> Offset) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val c = center()
    val x = (c.x - placeable.width / 2f).coerceIn(0f, (constraints.maxWidth - placeable.width).toFloat().coerceAtLeast(0f))
    val y = (c.y - placeable.height / 2f).coerceIn(0f, (constraints.maxHeight - placeable.height).toFloat().coerceAtLeast(0f))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x.roundToInt(), y.roundToInt()) }
}

/** 壁の詳細。高さは文字の分だけ（長いときは中でスクロールする）。 */
@Composable
private fun WallDetail(
    wall: IncomeWall,
    total: Long,
    projection: Projection?,
    procedures: Map<String, String>,
    onOpenProcedure: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    ) {
        Text("${wall.amount / 10_000}万円の壁", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(wall.title, style = MaterialTheme.typography.headlineSmall)
        val remaining = wall.amount - total
        if (remaining > 0) {
            Text("あと ${yen(remaining)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Text("✓ ${yen(-remaining)} 超えています", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (wall.detail.isNotEmpty()) {
            Text(wall.detail, style = MaterialTheme.typography.bodyMedium)
        }
        projection?.crossMonth?.let { month ->
            Text("⚠ このペースだと${month}月にこの壁を超えそうです", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            projection.monthlyLimit?.let {
                Text("12月まで月${yen(it)}以内なら超えません", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "※ 金額は2026年の収入の基準による目安です。正確な判定は勤務先・税務署・自治体に確認してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // この壁を超えるときにやる手続き（手続きの画面に飛ぶ）
        val links = wall.procedureIds.filter { it in procedures }
        if (links.isNotEmpty()) {
            Text("この壁でやること", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            links.forEach { id ->
                OutlinedButton(onClick = { onOpenProcedure(id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(procedures.getValue(id), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("閉じる") }
    }
}
