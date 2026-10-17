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
import retrofit2.http.Path

interface ApiService {
    @POST("api/recommendations")
    suspend fun recommendations(@Body profile: UserProfile): RecommendationResponse

    @GET("api/procedures/{id}")
    suspend fun procedure(@Path("id") id: String): Procedure
}

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // LLM の生成を待つので読み取りタイムアウトを長めにする
    private val http = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
