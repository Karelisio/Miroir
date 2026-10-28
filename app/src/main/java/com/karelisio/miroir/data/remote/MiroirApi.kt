package com.karelisio.miroir.data.remote

import com.karelisio.miroir.data.prefs.PairCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val error: ErrorBody) : IOException("HTTP $status ${error.error}")

/** Client du Worker. Authentification : secret de paire (Bearer) + identifiant de paire + membre. */
class MiroirApi(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pair(c: PairCredentials, body: PairRequest) = post(c, "/pair", body, PairRequest.serializer(), PairResponse.serializer())
    suspend fun unpair(c: PairCredentials) = call(c, "/pair", "DELETE", null, RevResponse.serializer())
    suspend fun state(c: PairCredentials, body: StateRequest) = post(c, "/state", body, StateRequest.serializer(), StateResponse.serializer())
    suspend fun answer(c: PairCredentials, body: AnswerRequest) = post(c, "/answer", body, AnswerRequest.serializer(), DayResponse.serializer())
    suspend fun skip(c: PairCredentials, body: SkipRequest) = post(c, "/skip", body, SkipRequest.serializer(), DayResponse.serializer())
    suspend fun react(c: PairCredentials, body: ReactRequest) = post(c, "/react", body, ReactRequest.serializer(), DayResponse.serializer())
    suspend fun customQuestion(c: PairCredentials, body: CustomQuestionRequest) =
        post(c, "/custom-question", body, CustomQuestionRequest.serializer(), RevResponse.serializer())
    suspend fun settings(c: PairCredentials, body: SettingsRequest) = post(c, "/settings", body, SettingsRequest.serializer(), SettingsResponse.serializer())
    suspend fun profile(c: PairCredentials, body: ProfileRequest) = post(c, "/profile", body, ProfileRequest.serializer(), RevResponse.serializer())
    suspend fun token(c: PairCredentials, body: TokenRequest) = post(c, "/token/update", body, TokenRequest.serializer(), RevResponse.serializer())
    suspend fun nudge(c: PairCredentials) = call(c, "/nudge", "POST", null, RevResponse.serializer())

    private suspend fun <I, O> post(c: PairCredentials, path: String, body: I, inS: KSerializer<I>, outS: KSerializer<O>): O =
        call(c, path, "POST", json.encodeToString(inS, body), outS)

    private suspend fun <O> call(c: PairCredentials, path: String, method: String, body: String?, outS: KSerializer<O>): O =
        withContext(Dispatchers.IO) {
            val base = c.serverUrl.trimEnd('/')
            if (base.isBlank()) throw IOException("Adresse du serveur non configurée")
            val request = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer ${c.secret}")
                .header("X-Pair-Id", c.pairId)
                .header("X-Member", c.member)
                .method(method, (body ?: "{}").toRequestBody(mediaType))
                .build()
            http.newCall(request).execute().use { res ->
                val text = res.body.string()
                if (!res.isSuccessful) {
                    val err = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrElse { ErrorBody("http_${res.code}") }
                    throw ApiException(res.code, err)
                }
                json.decodeFromString(outS, text)
            }
        }

    companion object {
        fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
