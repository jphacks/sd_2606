package jp.jphacks.sd2606.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 端末の中だけに保存するデータ。
 * - 回答: アプリ専用の領域に保存し、サーバーには送らない（allowBackup=false なのでクラウドのバックアップにも載らない）
 * - 完了の記録: 手続きの id と完了した日（ISO 形式）。回答と同じく端末の中だけ
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

    /** 回答と完了の記録を消す（制度データの控えは個人の情報ではないので残す）。 */
    fun clearAll() {
        prefs.edit {
            remove(KEY_ANSWERS)
            remove(KEY_COMPLETED)
        }
    }

    fun loadCatalog(): Catalog? = runCatching { json.decodeFromString<Catalog>(catalogFile.readText()) }.getOrNull()

    fun saveCatalog(catalog: Catalog) {
        runCatching { catalogFile.writeText(json.encodeToString(catalog)) }
    }

    private companion object {
        const val KEY_ANSWERS = "answers"
        const val KEY_COMPLETED = "completed"
    }
}
