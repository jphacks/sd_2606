package jp.jphacks.sd2606.ui.meter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import jp.jphacks.sd2606.data.meter.Income
import jp.jphacks.sd2606.data.meter.Job
import jp.jphacks.sd2606.data.meter.MeterDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import jp.jphacks.sd2606.data.meter.IncomeWall

/** 今のペースで12月まで働いたときの見込み。 */
data class Projection(
    /** 12月末の年収の見込み */
    val yearEnd: Long,
    /** 次の壁を超えそうな月（超えない見込みなら null） */
    val crossMonth: Int?,
    /** 次の壁を超えないための、残りの月の1か月あたりの上限（残りの月が無ければ null） */
    val monthlyLimit: Long?,
)

data class MeterUiState(
    val year: Int,
    val jobs: List<Job> = emptyList(),
    /** 表示中の年の給料（新しい順） */
    val incomes: List<Income> = emptyList(),
) {
    val total: Long get() = incomes.sumOf { it.amount }

    /** ビーカーに積む層（支給日の古い順） */
    val layers: List<BeakerLayer>
        get() = incomes.asReversed().map { BeakerLayer(it.amount, jobColor(colorIndexOf(it.jobId))) }

    /** バイト先ごとの合計（この年に給料があるものだけ） */
    val jobTotals: List<Pair<Job, Long>>
        get() = jobs.map { job -> job to incomes.filter { it.jobId == job.id }.sumOf { it.amount } }.filter { it.second > 0 }

    /** 支給月ごと・バイト先ごとの合計（index 0 が1月。中身は 色の番号 to 金額） */
    val monthlyByJob: List<List<Pair<Int, Long>>>
        get() = (1..12).map { month ->
            val inMonth = incomes.filter { it.paidOn.substring(5, 7).toInt() == month }
            jobs.mapNotNull { job ->
                val sum = inMonth.filter { it.jobId == job.id }.sumOf { it.amount }
                if (sum > 0) job.colorIndex to sum else null
            }
        }

    /** 「あと何時間」の計算に使う時給。直近の給料のバイト先を優先する */
    val hourlyWage: Int?
        get() = incomes.firstNotNullOfOrNull { income -> jobs.firstOrNull { it.id == income.jobId }?.hourlyWage }
            ?: jobs.lastOrNull { it.hourlyWage != null }?.hourlyWage

    /** 新しいバイト先に使う色（まだ使っていない色を優先） */
    val nextColorIndex: Int
        get() = JobPalette.indices.firstOrNull { i -> jobs.none { it.colorIndex == i } } ?: jobs.size

    /**
     * 今年の見込み。最初に給料が出た月から最後に出た月までの平均月収で、12月まで続けたと仮定する。
     * 今年以外の年や、記録が無いときは null。
     */
    fun projection(next: IncomeWall?, today: LocalDate = LocalDate.now()): Projection? {
        if (year != today.year || incomes.isEmpty()) return null
        val months = incomes.map { it.paidOn.substring(5, 7).toInt() }
        val last = months.max()
        val average = total / (last - months.min() + 1)
        val remaining = 12 - last
        val yearEnd = total + average * remaining
        if (next == null || remaining == 0) return Projection(yearEnd, crossMonth = null, monthlyLimit = null)
        val crossMonth = (last + 1..12).firstOrNull { month -> total + average * (month - last) >= next.amount }
        return Projection(yearEnd, crossMonth, monthlyLimit = (next.amount - 1 - total) / remaining)
    }

    fun jobName(jobId: Long): String = jobs.firstOrNull { it.id == jobId }?.name ?: "（不明）"

    fun colorIndexOf(jobId: Long): Int = jobs.firstOrNull { it.id == jobId }?.colorIndex ?: 0
}

/** バイト代メーターの ViewModel。データは端末内の DB にだけ保存する。 */
class MeterViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = MeterDatabase.get(app).meterDao()
    private val year = MutableStateFlow(LocalDate.now().year)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MeterUiState> = year
        .flatMapLatest { y ->
            combine(dao.jobs(), dao.incomesBetween("$y-01-01", "$y-12-31")) { jobs, incomes -> MeterUiState(y, jobs, incomes) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterUiState(year.value))

    fun previousYear() = year.update { it - 1 }

    fun nextYear() = year.update { it + 1 }

    /** バイト先は名前で探し、無ければ作る。時給・色が変わっていればバイト先も更新する。 */
    fun addIncome(jobName: String, hourlyWage: Int?, colorIndex: Int, paidOn: LocalDate, amount: Long) {
        viewModelScope.launch {
            val name = jobName.trim()
            val existing = state.value.jobs.firstOrNull { it.name == name }
            val jobId = if (existing == null) {
                dao.insertJob(Job(name = name, hourlyWage = hourlyWage, colorIndex = colorIndex))
            } else {
                val updated = existing.copy(hourlyWage = hourlyWage ?: existing.hourlyWage, colorIndex = colorIndex)
                if (updated != existing) dao.updateJob(updated)
                existing.id
            }
            dao.insertIncome(Income(jobId = jobId, paidOn = paidOn.toString(), amount = amount))
        }
    }

    fun deleteIncome(income: Income) {
        viewModelScope.launch { dao.deleteIncome(income) }
    }
}
