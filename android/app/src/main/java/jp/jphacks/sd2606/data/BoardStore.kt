package jp.jphacks.sd2606.data

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * 掲示板について端末にだけ保存するもの。
 * - client_id: 「役に立った」・通報の重複を防ぐためのランダムな ID（インストールごと。回答とは結び付けない）
 * - 自分の投稿の削除用トークン（サーバーはハッシュしか持たないので、消すとその投稿は削除できなくなる）
 * - 「役に立った」を押した投稿・通報した投稿
 */
class BoardStore(context: Context) {
    private val prefs = context.getSharedPreferences("board", Context.MODE_PRIVATE)

    val clientId: String = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_CLIENT_ID, it) }
    }

    fun myPosts(): Map<Int, String> = prefs.getStringSet(KEY_MINE, emptySet()).orEmpty()
        .mapNotNull { entry -> entry.split(":", limit = 2).takeIf { it.size == 2 }?.let { (id, token) -> id.toIntOrNull()?.let { it to token } } }
        .toMap()

    fun saveMyPost(id: Int, token: String) = editSet(KEY_MINE) { it + "$id:$token" }

    fun forgetMyPost(id: Int) = editSet(KEY_MINE) { set -> set.filterNot { it.startsWith("$id:") }.toSet() }

    fun helpful(): Set<Int> = ids(KEY_HELPFUL)

    fun setHelpful(id: Int, on: Boolean) = editSet(KEY_HELPFUL) { if (on) it + "$id" else it - "$id" }

    fun reported(): Set<Int> = ids(KEY_REPORTED)

    fun addReported(id: Int) = editSet(KEY_REPORTED) { it + "$id" }

    private fun ids(key: String) = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun editSet(key: String, change: (Set<String>) -> Set<String>) {
        val next = change(prefs.getStringSet(key, emptySet()).orEmpty().toSet())
        prefs.edit { putStringSet(key, next) }
    }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_MINE = "my_posts"
        const val KEY_HELPFUL = "helpful"
        const val KEY_REPORTED = "reported"
    }
}
