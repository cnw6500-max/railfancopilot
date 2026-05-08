package com.railfancopilot.app.data.repository

import com.railfancopilot.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ── Retrofit service interfaces ───────────────────────────────────────────────

// Live Amtrak data: api.amtraker.com (public, no auth required)
// Scanner: Broadcastify deprecated — WebView player via RailroadRadio.net

// ── Anthropic Claude API ──────────────────────────────────────────────────────

interface AnthropicApiService {
    @POST("v1/messages")
    @Headers("anthropic-version: 2023-06-01")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String = BuildConfig.ANTHROPIC_API_KEY,
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

data class AnthropicRequest(
    val model: String = "claude-sonnet-4-20250514",
    val max_tokens: Int = 1024,
    val system: String? = null,
    val messages: List<AnthropicMessage>
)

data class AnthropicMessage(val role: String, val content: Any)

data class AnthropicResponse(
    val content: List<AnthropicContent> = emptyList()
)

data class AnthropicContent(val type: String = "", val text: String = "")

// ── Amtrak API (api.amtraker.com) ─────────────────────────────────────────────

interface AmtrakApiService {
    @GET("v3/trains")
    suspend fun getTrains(): Map<String, List<AmtrakTrainJson>>
}

data class AmtrakTrainJson(
    val trainNum: Int = 0,
    val routeName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val heading: Int = 0,
    val velocity: Int = 0,
    val trainTimely: String = "",
    val stations: List<AmtrakStationJson> = emptyList(),
    val trainState: String = ""
)

data class AmtrakStationJson(
    val code: String = "",
    val status: String = ""
)

// ── Nominatim geocoding (OpenStreetMap, free, no auth) ────────────────────────

interface NominatimService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 6,
        @Query("addressdetails") addressDetails: Int = 0
    ): List<NominatimResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 10   // city-level granularity
    ): NominatimReverseResult
}

data class NominatimResult(
    val place_id: Long = 0L,
    val display_name: String = "",
    val lat: String = "0",
    val lon: String = "0",
    val type: String? = null
)

data class NominatimReverseResult(
    val display_name: String = "",
    val address: NominatimAddress = NominatimAddress()
)

data class NominatimAddress(
    val road: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country_code: String? = null
)

// ── SEPTA Regional Rail (Southeastern Pennsylvania — free JSON, no auth) ──────

interface SeptaApiService {
    /** Returns all active Regional Rail train positions. */
    @GET("TrainView/")
    suspend fun getTrainView(): List<SeptaTrainJson>
}

data class SeptaTrainJson(
    val lat: String       = "0",
    val lon: String       = "0",
    val trainno: String   = "",
    val service: String   = "",
    val dest: String      = "",
    val currentstop: String = "",
    val nextstop: String  = "",
    val line: String      = "",
    val consist: String   = "",
    val heading: String   = "",   // cardinal direction: "N", "NE", "SW", etc.
    val late: Int         = 0,    // minutes late (0 = on time)
    val SOURCE: String    = ""
)

// ── MBTA v3 API (Massachusetts Bay Transportation Authority — free, no key) ───

interface MbtaApiService {
    /** Returns commuter rail vehicle positions (route_type=2). */
    @GET("vehicles")
    suspend fun getVehicles(
        @Query("filter[route_type]") routeType: Int = 2,
        @Query("fields[vehicle]") fields: String = "latitude,longitude,speed,bearing,label,current_status,updated_at"
    ): MbtaVehicleResponse
}

data class MbtaVehicleResponse(
    val data: List<MbtaVehicleData> = emptyList()
)

data class MbtaVehicleData(
    val id: String = "",
    val attributes: MbtaVehicleAttributes = MbtaVehicleAttributes(),
    val relationships: MbtaVehicleRelationships = MbtaVehicleRelationships()
)

data class MbtaVehicleAttributes(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double? = null,         // m/s
    val bearing: Int? = null,
    val label: String = "",            // coach/car number
    val current_status: String = "",   // IN_TRANSIT_TO / STOPPED_AT
    val updated_at: String = ""
)

data class MbtaVehicleRelationships(
    val trip: MbtaRelationshipWrapper = MbtaRelationshipWrapper(),
    val route: MbtaRelationshipWrapper = MbtaRelationshipWrapper()
)

data class MbtaRelationshipWrapper(
    val data: MbtaRelationshipData? = null
)

data class MbtaRelationshipData(
    val id: String = ""
)

// ── OkHttp / Retrofit builders ─────────────────────────────────────────────

object NetworkModule {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    val anthropicClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val anthropicApi: AnthropicApiService = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(anthropicClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AnthropicApiService::class.java)

    val amtrakApi: AmtrakApiService = Retrofit.Builder()
        .baseUrl("https://api.amtraker.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AmtrakApiService::class.java)

    val nominatimApi: NominatimService = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    // Nominatim requires a descriptive User-Agent
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                        .build()
                    chain.proceed(req)
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimService::class.java)

    val mbtaApi: MbtaApiService = Retrofit.Builder()
        .baseUrl("https://api-v3.mbta.com/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                        .build()
                    chain.proceed(req)
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MbtaApiService::class.java)

    val septaApi: SeptaApiService = Retrofit.Builder()
        .baseUrl("https://www3.septa.org/api/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                        .build()
                    chain.proceed(req)
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SeptaApiService::class.java)

    /** Shared OkHttpClient for raw GTFS-RT binary protobuf fetches (no Retrofit). */
    val gtfsRtClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                .build()
            chain.proceed(req)
        }
        .build()

}
