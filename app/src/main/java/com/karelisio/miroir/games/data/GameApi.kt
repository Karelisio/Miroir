package com.karelisio.miroir.games.data

import com.karelisio.miroir.data.prefs.PairCredentials
import com.karelisio.miroir.data.remote.MiroirApi
import kotlinx.serialization.Serializable

@Serializable
data class GameStateRequest(val since: Long, val wishesSince: Long)

@Serializable
data class GameAnswerDto(val member: String, val item: String, val payload: String? = null, val at: Long = 0)

@Serializable
data class GameSessionDto(
    val id: String,
    val kind: String,
    val reveal: String,
    val createdBy: String,
    val payload: String,
    val status: String = "open",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val rev: Long = 0,
    val answers: List<GameAnswerDto> = emptyList(),
)

@Serializable
data class GameSettingsDto(val member: String, val payload: String, val at: Long = 0)

@Serializable
data class GameCustomDto(val id: String, val author: String, val payload: String, val deleted: Boolean = false, val createdAt: Long = 0, val rev: Long = 0)

@Serializable
data class WishMineDto(val slot: String, val payload: String)

@Serializable
data class WishesDto(val mine: List<WishMineDto> = emptyList(), val matches: List<String> = emptyList(), val rev: Long = 0)

@Serializable
data class GameStateResponse(
    val rev: Long,
    val sessions: List<GameSessionDto> = emptyList(),
    val settings: List<GameSettingsDto> = emptyList(),
    val custom: List<GameCustomDto> = emptyList(),
    val wishes: WishesDto = WishesDto(),
)

@Serializable
data class CreateSessionRequest(val id: String, val kind: String, val reveal: String, val payload: String, val status: String = "open", val notify: Boolean = false)

@Serializable
data class SessionResponse(val rev: Long = 0, val created: Boolean = false, val session: GameSessionDto? = null)

@Serializable
data class GameAnswerRequest(val session: String, val item: String, val payload: String, val notify: Boolean = false)

@Serializable
data class SessionStatusRequest(val id: String, val status: String)

@Serializable
data class GameSettingsRequest(val payload: String)

@Serializable
data class GameCustomRequest(val id: String, val payload: String, val deleted: Boolean = false)

@Serializable
data class WishEntryDto(val slot: String, val t1: String, val t2: String, val mine: String)

@Serializable
data class WishesRequest(val entries: List<WishEntryDto>)

@Serializable
data class WishesResponse(val rev: Long = 0, val matches: List<String> = emptyList())

@Serializable
data class GameRevResponse(val rev: Long = 0)

/** Routes « /games/… » du Worker (même authentification que le reste de Miroir). */
class GameApi(private val api: MiroirApi) {
    suspend fun state(c: PairCredentials, body: GameStateRequest) =
        api.post(c, "/games/state", body, GameStateRequest.serializer(), GameStateResponse.serializer())

    suspend fun createSession(c: PairCredentials, body: CreateSessionRequest) =
        api.post(c, "/games/session", body, CreateSessionRequest.serializer(), SessionResponse.serializer())

    suspend fun status(c: PairCredentials, body: SessionStatusRequest) =
        api.post(c, "/games/session/status", body, SessionStatusRequest.serializer(), SessionResponse.serializer())

    suspend fun answer(c: PairCredentials, body: GameAnswerRequest) =
        api.post(c, "/games/answer", body, GameAnswerRequest.serializer(), SessionResponse.serializer())

    suspend fun settings(c: PairCredentials, body: GameSettingsRequest) =
        api.post(c, "/games/settings", body, GameSettingsRequest.serializer(), GameRevResponse.serializer())

    suspend fun custom(c: PairCredentials, body: GameCustomRequest) =
        api.post(c, "/games/custom", body, GameCustomRequest.serializer(), GameRevResponse.serializer())

    suspend fun wishes(c: PairCredentials, body: WishesRequest) =
        api.post(c, "/games/wishes", body, WishesRequest.serializer(), WishesResponse.serializer())
}
