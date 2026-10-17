package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ユーザーの回答。端末の中にだけ保存し、サーバーには送らない。
 * 未回答の項目は Map に入れない（RuleEngine は「まだ分からない」として扱う）。
 */
@Serializable
sealed interface Answer {
    @Serializable
    @SerialName("single")
    data class Single(val value: String) : Answer

    @Serializable
    @SerialName("multi")
    data class Multi(val values: List<String>) : Answer

    /** 数値そのものではなく、選んだ範囲だけを持つ（例: 年齢 20〜59歳）。max が null なら上限なし */
    @Serializable
    @SerialName("range")
    data class Range(val min: Long, val max: Long?) : Answer

    /** 数値（年収は円、扶養家族は人数）。 */
    @Serializable
    @SerialName("number")
    data class Number(val value: Long) : Answer

    /** 日付（生年月日など）。ISO 形式（2000-04-01）で持つ */
    @Serializable
    @SerialName("date")
    data class Date(val value: String) : Answer {
        constructor(date: LocalDate) : this(date.toString())

        fun toLocalDate(): LocalDate = LocalDate.parse(value)
    }
}

fun Choice.toAnswer(): Answer = if (value != null) Answer.Single(value) else Answer.Range(min ?: 0, max)

fun Question.isSelected(choice: Choice, answer: Answer?): Boolean = when (answer) {
    is Answer.Single -> answer.value == choice.value
    is Answer.Multi -> choice.value in answer.values
    is Answer.Range -> answer.min == choice.min && answer.max == choice.max
    is Answer.Number, is Answer.Date, null -> false
}

/** 回答を画面に出す文字列にする（「あなたの回答」の一覧用）。 */
fun Question.describe(answer: Answer): String = when (answer) {
    is Answer.Multi -> choices.filter { it.value in answer.values }.joinToString("、") { it.label }.ifEmpty { "なし" }
    is Answer.Date -> answer.describe()
    is Answer.Number -> "%,d%s".format(answer.value / scale, unit)
    else -> choices.firstOrNull { isSelected(it, answer) }?.label ?: "（選択肢が変わりました）"
}

/** 「2000年4月1日（26歳）」 */
fun Answer.Date.describe(today: LocalDate = LocalDate.now()): String {
    val d = toLocalDate()
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日（${RuleEngine.ageOn(d, today)}歳）"
}
