package jp.jphacks.sd2606.data

import java.time.LocalDate

/**
 * 手続きに出すお金の目安1つ。
 * - label: 「住民税（来年6月から）」など短い名前
 * - value: 「年 約5,000円」など
 * - note: 次の基準など、短い補足（無ければ null）
 * - breakdown: 計算の内訳（手続きの画面で「計算の内訳」を開くと出す）
 */
data class AmountView(val label: String, val value: String, val note: String? = null, val breakdown: List<String> = emptyList())

/**
 * お金の目安の計算（端末内で回答から計算し、送らない）。2026年（令和8年分）のアルバイト収入（給与）だけがある学生を想定した目安。
 * 社会保険料控除など、本人に聞いていない控除は含めない。
 *
 * 基準の出典（2026年10月に確認）:
 * - 所得税の基礎控除104万円・給与所得控除の最低74万円・特定親族特別控除の表: 国税庁「令和8年4月 源泉所得税の改正のあらまし」
 *   https://www.nta.go.jp/publication/pamph/gensen/2026kaisei.pdf
 * - 住民税の非課税（単身で総所得45万円以下）・基礎控除43万円: 総務省「個人住民税の基礎控除等について」（2026年5月29日）
 *   https://www.soumu.go.jp/main_content/001075462.pdf 、税率・均等割は東京都主税局「個人住民税」
 * - 勤労学生控除（所得税27万円・住民税26万円、合計所得89万円以下）: 国税庁 No.1175、財務省 令和8年度税制改正の大綱
 * - 国民年金保険料 月17,920円（令和8年度）: 日本年金機構 https://www.nenkin.go.jp/faq/kokunen/seido/hokenryo/seidosetsumei/20150331.html
 */
object Amounts {
    /** 給与所得控除の最低額（令和8年分の所得税・令和9年度の住民税） */
    const val SALARY_DEDUCTION_MIN = 740_000L

    /** 所得税の基礎控除（合計所得132万円以下） */
    const val INCOME_TAX_BASIC = 1_040_000L
    const val INCOME_TAX_BASIC_LIMIT = 1_320_000L

    /** 住民税: 基礎控除・非課税になる所得（単身）・均等割（森林環境税1,000円を含む） */
    const val RESIDENT_TAX_BASIC = 430_000L
    const val RESIDENT_TAX_EXEMPT_INCOME = 450_000L
    const val RESIDENT_TAX_PER_CAPITA = 5_000L

    /** 勤労学生控除（合計所得89万円以下の学生） */
    const val WORKING_STUDENT_INCOME_TAX = 270_000L
    const val WORKING_STUDENT_RESIDENT_TAX = 260_000L
    const val WORKING_STUDENT_LIMIT = 890_000L

    /** 国民年金保険料（令和8年度: 2026年4月〜2027年3月） */
    const val NENKIN_MONTHLY = 17_920L

    /** 特定親族特別控除（19〜22歳の子の合計所得 → 親の控除額）。62万円以下は特定扶養控除63万円 */
    private val SPECIFIC_RELATIVE = listOf(
        850_000L to 630_000L, 900_000L to 610_000L, 950_000L to 510_000L, 1_000_000L to 410_000L,
        1_050_000L to 310_000L, 1_100_000L to 210_000L, 1_150_000L to 110_000L, 1_200_000L to 60_000L, 1_230_000L to 30_000L,
    )
    const val DEPENDENT_INCOME_LIMIT = 620_000L

    /** 手続きの amounts（一覧のカードの代表的な金額）のうち、この人に当てはまるものを計算する。 */
    fun of(proc: ProcedureDef, rawAnswers: Map<String, Answer>, today: LocalDate = LocalDate.now()): List<Pair<AmountSpec, AmountView>> =
        of(proc.amounts, rawAnswers, today)

    /** 金額の指定のうち、この人に当てはまるもの（when をすべて満たす）を計算する。計算できないものは出さない。 */
    fun of(specs: List<AmountSpec>, rawAnswers: Map<String, Answer>, today: LocalDate = LocalDate.now()): List<Pair<AmountSpec, AmountView>> {
        val answers = RuleEngine.withDerived(rawAnswers, today)
        return specs
            .filter { spec -> spec.conditions.all { RuleEngine.check(it, answers[it.field]) == Tri.TRUE } }
            .mapNotNull { spec -> compute(spec.kind, answers)?.let { spec to it } }
    }

    fun compute(kind: String, answers: Map<String, Answer>): AmountView? {
        val income = (answers["annual_income"] as? Answer.Number)?.value
        val student = true // アプリの対象は学生（勤労学生控除を受けられる前提）
        return when (kind) {
            "juminzei" -> income?.let { residentTax(it, student) }
            "shotokuzei" -> income?.let { incomeTax(it, student) }
            "parent_deduction" -> {
                val age = (answers["age_at_year_end"] as? Answer.Range)?.min?.toInt()
                if (income == null || age == null) null else parentDeduction(income, age)
            }
            "nenkin" -> nenkin()
            "nenkin_tokurei" -> nenkinTokurei()
            else -> null
        }
    }

    /** 給与所得（年収 − 給与所得控除）。 */
    fun salaryIncome(income: Long): Long = (income - salaryDeduction(income)).coerceAtLeast(0)

    /** 給与所得控除。令和8年分は最低74万円（それより多くなる年収では通常の計算）。 */
    fun salaryDeduction(income: Long): Long {
        val normal = when {
            income <= 1_900_000 -> 650_000
            income <= 3_600_000 -> income * 30 / 100 + 80_000
            income <= 6_600_000 -> income * 20 / 100 + 440_000
            income <= 8_500_000 -> income * 10 / 100 + 1_100_000
            else -> 1_950_000
        }
        return maxOf(SALARY_DEDUCTION_MIN, normal).coerceAtMost(income)
    }

    /** 住民税の計算の途中の値（数字の関数と、内訳を出す表示の関数で共通に使う）。 */
    private class ResidentTaxCalc(income: Long, student: Boolean) {
        val salary = salaryIncome(income)
        val exempt = salary <= RESIDENT_TAX_EXEMPT_INCOME
        val workingStudent = student && salary <= WORKING_STUDENT_LIMIT
        val deductions = RESIDENT_TAX_BASIC + if (workingStudent) WORKING_STUDENT_RESIDENT_TAX else 0
        val taxable = floorTo(salary - deductions, 1_000).coerceAtLeast(0)

        // 調整控除: 所得税との人的控除の差（基礎控除5万円、勤労学生控除1万円）の5%（課税所得200万円以下の場合）
        private val personalGap = 50_000L + if (workingStudent) 10_000 else 0
        val adjust = if (taxable <= 2_000_000) minOf(personalGap, taxable) * 5 / 100 else maxOf((personalGap - (taxable - 2_000_000)) * 5 / 100, 2_500)
        val incomeLevy = floorTo((taxable * 10 / 100 - adjust).coerceAtLeast(0), 100)
        val total = if (exempt) 0L else incomeLevy + RESIDENT_TAX_PER_CAPITA
    }

    /** 本人の住民税の額（かからなければ 0）。 */
    fun residentTaxYen(income: Long, student: Boolean): Long = ResidentTaxCalc(income, student).total

    /** 本人の住民税（2026年の収入に対して2027年度に課税）。 */
    fun residentTax(income: Long, student: Boolean): AmountView {
        val calc = ResidentTaxCalc(income, student)
        val salary = calc.salary
        val label = "住民税（来年6月から）"
        if (calc.exempt) {
            return AmountView(
                label, "かかりません",
                note = "年収119万円を超えるとかかります",
                breakdown = listOf("給与所得 ${yen(salary)}（年収 − 給与所得控除 ${yen(salaryDeduction(income))}）が45万円以下"),
            )
        }
        val workingStudent = calc.workingStudent
        val deductions = calc.deductions
        val taxable = calc.taxable
        val adjust = calc.adjust
        val incomeLevy = calc.incomeLevy
        val total = calc.total
        return AmountView(
            label, "年 約${yen(total)}",
            note = if (workingStudent && incomeLevy == 0L) "勤労学生控除を申告すると均等割だけ" else null,
            breakdown = listOfNotNull(
                "給与所得 ${yen(salary)}（年収 − 給与所得控除 ${yen(salaryDeduction(income))}）",
                "控除 ${yen(deductions)}（基礎控除43万円" + (if (workingStudent) "・勤労学生控除26万円" else "") + "）",
                "所得割 ${yen(incomeLevy)}（課税所得 ${yen(taxable)} × 10% − 調整控除 ${yen(adjust)}）",
                "均等割 ${yen(RESIDENT_TAX_PER_CAPITA)}（森林環境税を含む。市区町村で違う）",
            ),
        )
    }

    /** 本人の所得税の額（かからなければ 0）。合計所得132万円（年収206万円）を超える人は計算しない（null）。 */
    fun incomeTaxYen(income: Long, student: Boolean): Long? {
        val salary = salaryIncome(income)
        if (salary > INCOME_TAX_BASIC_LIMIT) return null
        val taxable = incomeTaxable(salary, student)
        return floorTo(taxable * 5_105 / 100_000, 100) // 税率5%と復興特別所得税（2.1%）
    }

    private fun incomeTaxDeductions(salary: Long, student: Boolean): Long =
        INCOME_TAX_BASIC + if (student && salary <= WORKING_STUDENT_LIMIT) WORKING_STUDENT_INCOME_TAX else 0

    private fun incomeTaxable(salary: Long, student: Boolean): Long =
        floorTo(salary - incomeTaxDeductions(salary, student), 1_000).coerceAtLeast(0)

    /** 本人の所得税（2026年分）。合計所得132万円（年収206万円）を超える人は目安を出さない。 */
    fun incomeTax(income: Long, student: Boolean): AmountView? {
        val tax = incomeTaxYen(income, student) ?: return null
        val salary = salaryIncome(income)
        val label = "所得税（今年分）"
        val workingStudent = student && salary <= WORKING_STUDENT_LIMIT
        val deductions = incomeTaxDeductions(salary, student)
        val taxable = incomeTaxable(salary, student)
        if (taxable == 0L) {
            return AmountView(label, "かかりません", note = "年収178万円を超えるとかかります")
        }
        return AmountView(
            label, "年 約${yen(tax)}",
            note = "引かれすぎた分は年末調整か確定申告で戻ります",
            breakdown = listOf(
                "給与所得 ${yen(salary)}（年収 − 給与所得控除 ${yen(salaryDeduction(income))}）",
                "控除 ${yen(deductions)}（基礎控除104万円" + (if (workingStudent) "・勤労学生控除27万円" else "") + "）",
                "課税所得 ${yen(taxable)} × 5.105%（復興特別所得税を含む）",
            ),
        )
    }

    /** 親が受ける控除の額（所得税、2026年分）。16歳未満・70歳以上は null。 */
    fun parentDeductionYen(income: Long, ageAtYearEnd: Int): Long? {
        val salary = salaryIncome(income)
        return when (ageAtYearEnd) {
            in 19..22 -> if (salary <= DEPENDENT_INCOME_LIMIT) 630_000L else SPECIFIC_RELATIVE.firstOrNull { salary <= it.first }?.second ?: 0L
            in 16..18, in 23..69 -> if (salary <= DEPENDENT_INCOME_LIMIT) 380_000L else 0L
            else -> null
        }
    }

    /** 親が受ける控除（所得税、2026年分）。子の年末の年齢と合計所得で決まる。16歳未満・70歳以上は出さない。 */
    fun parentDeduction(income: Long, ageAtYearEnd: Int): AmountView? {
        val salary = salaryIncome(income)
        val label = "親の控除（所得税）"
        return when (ageAtYearEnd) {
            in 19..22 -> {
                val (amount, name) = when {
                    salary <= DEPENDENT_INCOME_LIMIT -> 630_000L to "特定扶養控除"
                    else -> (SPECIFIC_RELATIVE.firstOrNull { salary <= it.first }?.second ?: 0L) to "特定親族特別控除"
                }
                val note = when {
                    income <= 1_360_000 -> "136万円を超えたら、親は申告書を切り替えます"
                    income <= 1_590_000 -> "159万円を超えると減り始めます"
                    amount > 0 -> "197万円を超えるとなくなります"
                    else -> null
                }
                AmountView(
                    label, if (amount > 0) man(amount) else "なし",
                    note = note,
                    breakdown = listOf("${name}（あなたの合計所得 ${yen(salary)} で決まる）"),
                )
            }
            in 16..18, in 23..69 -> {
                val ok = salary <= DEPENDENT_INCOME_LIMIT
                AmountView(
                    label, if (ok) "38万円" else "なし",
                    note = if (ok) "136万円を超えるとなくなります" else null,
                    breakdown = listOf("扶養控除（あなたの合計所得 ${yen(salary)} が62万円以下なら受けられる）"),
                )
            }
            else -> null
        }
    }

    /** 国民年金保険料（令和8年度）。 */
    fun nenkin(): AmountView = AmountView(
        "国民年金保険料",
        "月 ${yen(NENKIN_MONTHLY)}",
        breakdown = listOf("1年で ${yen(NENKIN_MONTHLY * 12)}（2026年4月〜2027年3月）。前納すると割引あり"),
    )

    /** 学生納付特例を使うときの保険料（在学中は0円、後から納めるなら月額）。 */
    fun nenkinTokurei(): AmountView = AmountView(
        "在学中に納める保険料",
        "0円",
        note = "後から納めるなら月 ${yen(NENKIN_MONTHLY)}（10年以内）",
    )

    private fun floorTo(v: Long, unit: Long): Long = if (v <= 0) 0 else v / unit * unit

    fun yen(v: Long): String = "%,d円".format(v)

    private fun man(v: Long): String = "${v / 10_000}万円"
}
