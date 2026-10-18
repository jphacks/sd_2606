package jp.jphacks.sd2606.data

import java.util.concurrent.TimeUnit
import jp.jphacks.sd2606.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * サーバーからは制度データを受け取る。ユーザーの回答を送るのは「AIに質問」（chat）だけで、
 * そのときも表示用に加工した情報（年齢など）だけを送り、サーバーには保存しない。
 */
interface ApiService {
    @GET("api/catalog")
    suspend fun catalog(): Catalog

    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatReply
}

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val service: ApiService = build(http)

    /** AI の返事は時間がかかる（数秒〜数十秒）ので、読み込みの待ち時間を長くした client を使う。 */
    val chatService: ApiService = build(http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build())

    private fun build(client: OkHttpClient): ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
