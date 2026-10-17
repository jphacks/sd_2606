package jp.jphacks.sd2606.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// backend/app/board/schemas.py と対応させること。
// 体験談は本人が公開すると決めて送るもの。判定に使う回答（Answer）とは別で、回答は送らない。

@Serializable
data class BoardOption(val value: String, val label: String, val min: Int? = null, val max: Int? = null)

@Serializable
data class BoardDuration(val value: Int, val label: String)

@Serializable
data class BoardTag(val id: String, val label: String, val group: String, val procedures: List<String> = emptyList())

@Serializable
data class BoardMeta(
    val kinds: List<BoardOption>,
    @SerialName("age_bands") val ageBands: List<BoardOption>,
    val occupations: List<BoardOption>,
    val durations: List<BoardDuration>,
    @SerialName("tag_groups") val tagGroups: List<BoardOption>,
    val tags: List<BoardTag>,
) {
    fun kindLabel(v: String) = kinds.firstOrNull { it.value == v }?.label ?: v
    fun tagLabel(id: String) = tags.firstOrNull { it.id == id }?.label ?: id
    fun durationLabel(minutes: Int) = durations.firstOrNull { it.value == minutes }?.label ?: "${minutes}分"

    /** その手続きの画面に出すタグ（サーバーと同じ対応）。 */
    fun tagsFor(procedureId: String) = tags.filter { procedureId in it.procedures }

    /** 「20代前半・学生・2026年5月の話」 */
    fun attributes(post: Post): String = listOfNotNull(
        post.ageBand?.let { v -> ageBands.firstOrNull { it.value == v }?.label },
        post.occupation?.let { v -> occupations.firstOrNull { it.value == v }?.label },
        post.happenedYm?.let(::describeYearMonth),
    ).joinToString("・")

    /** 生年月日（端末内の回答）から年代を選ぶ。生年月日そのものは送らない。 */
    fun ageBandFor(birth: LocalDate, today: LocalDate = LocalDate.now()): String? {
        val age = RuleEngine.ageOn(birth, today)
        return ageBands.firstOrNull { age >= (it.min ?: 0) && (it.max == null || age <= it.max) }?.value
    }
}

@Serializable
data class Post(
    val id: Int,
    val kind: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    @SerialName("age_band") val ageBand: String? = null,
    val occupation: String? = null,
    @SerialName("happened_ym") val happenedYm: String? = null,
    val difficulty: Int? = null,
    val minutes: Int? = null,
    val helpful: Int = 0,
    /** 開発チームが書いたサンプル */
    val sample: Boolean = false,
    @SerialName("created_at") val createdAt: String,
) {
    /** 投稿日（端末のタイムゾーン）「10月17日」 */
    fun postedOn(): String = runCatching {
        val d = OffsetDateTime.parse(createdAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        if (d.year == LocalDate.now().year) "${d.monthValue}月${d.dayOfMonth}日" else "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }.getOrDefault("")
}

@Serializable
data class PostPage(val items: List<Post>, @SerialName("next_offset") val nextOffset: Int? = null)

@Serializable
data class NewPost(
    val kind: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    @SerialName("age_band") val ageBand: String? = null,
    val occupation: String? = null,
    @SerialName("happened_ym") val happenedYm: String? = null,
    val difficulty: Int? = null,
    val minutes: Int? = null,
)

@Serializable
data class CreatedPost(val post: Post, @SerialName("delete_token") val deleteToken: String)

@Serializable
data class BoardSummary(
    val count: Int,
    @SerialName("avg_difficulty") val avgDifficulty: Double? = null,
    @SerialName("median_minutes") val medianMinutes: Int? = null,
    val kinds: Map<String, Int> = emptyMap(),
)

@Serializable
data class Helpful(val helpful: Int)

/** 一覧の絞り込み。procedure を指定すると、その手続きに対応するタグの投稿だけになる。 */
data class PostQuery(
    val procedure: String? = null,
    val tag: String? = null,
    val kind: String? = null,
    val sort: String = "new",
)

/** 「2026-05」→「2026年5月の話」 */
fun describeYearMonth(ym: String): String {
    val (y, m) = ym.split("-").map { it.toIntOrNull() ?: return ym }
    return "${y}年${m}月の話"
}
