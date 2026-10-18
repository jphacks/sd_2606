package jp.jphacks.sd2606.data.meter

/** 診断の判定。 */
enum class Verdict {
    /** 働き損ゾーンに入らない見込み */
    OK,

    /** 今は入っていないが、このままだと年末までに入りそう */
    CAUTION,

    /** 働き損ゾーンに入っている */
    LOSS,

    /** 働き損ゾーンを抜けている（扶養を外れても、自分の手取りは扶養の範囲より多い） */
    BEYOND,
}

/**
 * 年収の働き方の診断。
 * 「働き損ゾーン」は、健康保険の扶養から外れる年収（safeLimit）から、
 * 自分の手取りが扶養の範囲で働いたとき（safeLimit の直前）に追いつく年収（breakEven）まで。
 * 親の税金はこれとは別に、parentFrom を超えると増える（19〜22歳は parentUntil まで少しずつ、それ以外は一度に）。
 */
data class Diagnosis(
    val verdict: Verdict,
    /** 今の合計 */
    val total: Long,
    /** 判定に使った額（今年なら年末の見込み、それ以外の年はその年の合計） */
    val amount: Long,
    /** ここ未満なら働き損ゾーンに入らない（健康保険の扶養の基準） */
    val safeLimit: Long,
    /** ここ以上なら、扶養を外れても自分の手取りは扶養の範囲より多い */
    val breakEven: Long,
    /** 親の税金が増え始める年収 */
    val parentFrom: Long,
    /** 親の税金の増え方が止まる年収（19〜22歳）。それ以外の年齢は一度に増えるので null */
    val parentUntil: Long?,
    /** 働き損ゾーンに入らないための、残りの月の1か月あたりの上限（今年でなければ null） */
    val monthlyLimit: Long?,
)

/**
 * total: 今の合計、yearEnd: 今年の年末の見込み（今年でなければ null）、
 * remainingMonths: 今年の残りの月数（最後に給料が出た月の翌月から12月まで）。
 */
fun diagnose(total: Long, yearEnd: Long?, remainingMonths: Int, age: Int): Diagnosis {
    val safeLimit = healthInsuranceLimit(age)
    val breakEven = breakEvenOf(safeLimit, age)
    val amount = yearEnd ?: total
    val verdict = when {
        amount >= breakEven -> Verdict.BEYOND
        total >= safeLimit -> Verdict.LOSS
        amount >= safeLimit -> Verdict.CAUTION
        else -> Verdict.OK
    }
    val monthlyLimit = if (yearEnd != null && remainingMonths > 0 && total < safeLimit) {
        (safeLimit - 1 - total) / remainingMonths
    } else {
        null
    }
    val student = age in 19..22
    return Diagnosis(
        verdict = verdict,
        total = total,
        amount = amount,
        safeLimit = safeLimit,
        breakEven = breakEven,
        parentFrom = if (student) 1_590_000L else 1_360_000L,
        parentUntil = if (student) 1_970_000L else null,
        monthlyLimit = monthlyLimit,
    )
}

/** 自分の手取りが、扶養の基準の直前で働いたときに追いつく年収（1万円刻みで探す）。 */
private fun breakEvenOf(safeLimit: Long, age: Int): Long {
    val baseline = takeHome(safeLimit - 1, age)
    var annual = safeLimit
    while (annual < 5_000_000L && takeHome(annual, age) < baseline) annual += 10_000L
    return annual
}
