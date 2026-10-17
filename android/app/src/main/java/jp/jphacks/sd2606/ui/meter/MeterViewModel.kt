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
