package jp.jphacks.sd2606.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 端末の中だけに保存するデータ。
 * - 回答: アプリ専用の領域に保存し、サーバーには送らない（allowBackup=false なのでクラウドのバックアップにも載らない）
 * - 完了の記録: 手続きの id と完了した日（ISO 形式）。回答と同じく端末の中だけ
 * - 「自分には関係ない」にした手続きの id
 * - 通知: オンにした通知の id と、予約した通知（再起動したときに予約し直す）
 * - 制度データ: 通信できないときのために、前回受け取ったものを残しておく
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("answers", Context.MODE_PRIVATE)
    private val catalogFile = File(context.filesDir, "catalog.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAnswers(): Map<String, Answer> =
        runCatching { prefs.getString(KEY_ANSWERS, null)?.let { json.decodeFromString<Map<String, Answer>>(it) } }
            .getOrNull() ?: emptyMap()

    fun saveAnswers(answers: Map<String, Answer>) {
        prefs.edit { putString(KEY_ANSWERS, json.encodeToString(answers)) }
    }

    fun loadCompleted(): Map<String, String> =
        runCatching { prefs.getString(KEY_COMPLETED, null)?.let { json.decodeFromString<Map<String, String>>(it) } }
            .getOrNull() ?: emptyMap()

    fun saveCompleted(completed: Map<String, String>) {
        prefs.edit { putString(KEY_COMPLETED, json.encodeToString(completed)) }
    }

    fun loadDismissed(): Set<String> = prefs.getStringSet(KEY_DISMISSED, null)?.toSet() ?: emptySet()

    fun saveDismissed(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_DISMISSED, ids) }
    }

    fun loadRemindersOn(): Set<String> = prefs.getStringSet(KEY_REMINDERS, null)?.toSet() ?: emptySet()

    fun saveRemindersOn(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_REMINDERS, ids) }
    }

    fun loadScheduled(): List<ScheduledNotification> =
        runCatching { prefs.getString(KEY_SCHEDULED, null)?.let { json.decodeFromString<List<ScheduledNotification>>(it) } }
            .getOrNull() ?: emptyList()

    fun saveScheduled(items: List<ScheduledNotification>) {
        prefs.edit { putString(KEY_SCHEDULED, json.encodeToString(items)) }
    }

    /** 回答・完了の記録・「関係ない」の記録・通知の設定を消す（制度データの控えは個人の情報ではないので残す）。 */
    fun clearAll() {
        prefs.edit {
            remove(KEY_ANSWERS)
            remove(KEY_COMPLETED)
            remove(KEY_DISMISSED)
            remove(KEY_REMINDERS)
        }
    }

    fun loadCatalog(): Catalog? = runCatching { json.decodeFromString<Catalog>(catalogFile.readText()) }.getOrNull()

    fun saveCatalog(catalog: Catalog) {
        runCatching { catalogFile.writeText(json.encodeToString(catalog)) }
    }

    private companion object {
        const val KEY_ANSWERS = "answers"
        const val KEY_COMPLETED = "completed"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_REMINDERS = "reminders_on"
        const val KEY_SCHEDULED = "scheduled"
    }
}
