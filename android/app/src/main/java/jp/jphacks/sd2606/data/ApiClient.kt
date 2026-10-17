package jp.jphacks.sd2606.data

import java.util.concurrent.TimeUnit
import jp.jphacks.sd2606.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 判定に使う回答を送る API は無い。制度データは受け取るだけ。 */
interface ApiService {
    @GET("api/catalog")
    suspend fun catalog(): Catalog
}

/** 体験談の掲示板。送るのは、本人が公開すると決めた投稿だけ（匿名・アカウントなし）。 */
interface BoardApi {
    @GET("api/board/meta")
    suspend fun meta(): BoardMeta

    @GET("api/board/posts")
    suspend fun posts(
        @Query("procedure") procedure: String?,
        @Query("tag") tag: String?,
        @Query("kind") kind: String?,
        @Query("sort") sort: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = 20,
    ): PostPage

    @GET("api/board/posts/{id}")
    suspend fun post(@Path("id") id: Int): Post

    @GET("api/board/summary")
    suspend fun summary(@Query("procedure") procedure: String): BoardSummary

    @GET("api/board/counts")
    suspend fun counts(): Map<String, Int>

    @POST("api/board/posts")
    suspend fun create(@Body post: NewPost): CreatedPost

    @DELETE("api/board/posts/{id}")
    suspend fun delete(@Path("id") id: Int, @Header("X-Delete-Token") token: String): Response<Unit>

    @PUT("api/board/posts/{id}/helpful")
    suspend fun helpful(@Path("id") id: Int, @Header("X-Client-Id") clientId: String): Helpful

    @DELETE("api/board/posts/{id}/helpful")
    suspend fun unhelpful(@Path("id") id: Int, @Header("X-Client-Id") clientId: String): Helpful

    @POST("api/board/posts/{id}/report")
    suspend fun report(@Path("id") id: Int, @Header("X-Client-Id") clientId: String): Response<Unit>
}

object ApiClient {
    val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val service: ApiService = retrofit.create(ApiService::class.java)
    val board: BoardApi = retrofit.create(BoardApi::class.java)
}
