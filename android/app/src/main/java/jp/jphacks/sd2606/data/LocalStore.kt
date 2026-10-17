package jp.jphacks.sd2606.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 端末の中だけに保存するデータ。
 * - 回答: アプリ専用の領域に保存し、サーバーには送らない（allowBackup=false なのでクラウドのバックアップにも載らない）
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

    fun clearAnswers() {
        prefs.edit { remove(KEY_ANSWERS) }
    }

    fun loadCatalog(): Catalog? = runCatching { json.decodeFromString<Catalog>(catalogFile.readText()) }.getOrNull()

    fun saveCatalog(catalog: Catalog) {
        runCatching { catalogFile.writeText(json.encodeToString(catalog)) }
    }

    private companion object {
        const val KEY_ANSWERS = "answers"
    }
}
