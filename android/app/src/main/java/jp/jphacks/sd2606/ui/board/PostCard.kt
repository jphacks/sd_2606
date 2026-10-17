package jp.jphacks.sd2606.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.BoardMeta
import jp.jphacks.sd2606.data.BoardSummary
import jp.jphacks.sd2606.data.Post

/** 体験談1件。一覧では本文を3行まで、詳細（full）では全文を出す。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(post: Post, meta: BoardMeta, helpful: Int, modifier: Modifier = Modifier, full: Boolean = false, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KindBadge(post.kind, meta)
                if (post.sample) Badge("サンプル", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(post.postedOn(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(post.title, style = MaterialTheme.typography.titleMedium)
            meta.attributes(post).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Ratings(post.difficulty, post.minutes?.let(meta::durationLabel))
            Text(
                post.body.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (full) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    post.tags.forEach { Text("#${meta.tagLabel(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                }
                if (!full && helpful > 0) {
                    Icon(Icons.Default.ThumbUp, contentDescription = "役に立った", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("$helpful", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { content() }
    else Card(modifier = modifier.fillMaxWidth()) { content() }
}

@Composable
fun KindBadge(kind: String, meta: BoardMeta) {
    val trouble = kind == "trouble"
    Badge(
        meta.kindLabel(kind),
        if (trouble) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        if (trouble) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 大変さ（★）とかかった時間。どちらも無ければ何も出さない。 */
@Composable
fun Ratings(difficulty: Int?, duration: String?) {
    if (difficulty == null && duration == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (difficulty != null) {
            Text("大変さ ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stars(difficulty.toDouble(), Modifier.size(14.dp))
            Spacer(Modifier.width(12.dp))
        }
        if (duration != null) {
            Icon(Icons.Default.Schedule, contentDescription = "かかった時間", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(duration, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Stars(value: Double, iconModifier: Modifier) {
    Row {
        (1..5).forEach { i ->
            Icon(
                if (value >= i - 0.25) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = Color(0xFFF2A900),
                modifier = iconModifier,
            )
        }
    }
}

/** 投稿フォームの大変さの入力。同じ星をもう一度押すと未選択に戻る。 */
@Composable
fun StarInput(value: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { i ->
            IconButton(onClick = { onChange(if (value == i) null else i) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if ((value ?: 0) >= i) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "大変さ $i",
                    tint = Color(0xFFF2A900),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when (value) {
                null -> "選ばない"
                1 -> "かんたん"
                2 -> "少し手間"
                3 -> "ふつう"
                4 -> "大変"
                else -> "とても大変"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 「12件・大変さ ★★★☆☆ 3.4・だいたい 30分くらい」 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryLine(summary: BoardSummary, meta: BoardMeta) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${summary.count}件", style = MaterialTheme.typography.titleSmall)
        summary.avgDifficulty?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("大変さ ", style = MaterialTheme.typography.labelMedium)
                Stars(it, Modifier.size(16.dp))
                Text(" ${"%.1f".format(it)}", style = MaterialTheme.typography.labelMedium)
            }
        }
        summary.medianMinutes?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = "かかった時間", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("だいたい${meta.durationLabel(it)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
