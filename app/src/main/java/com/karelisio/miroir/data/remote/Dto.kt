package com.karelisio.miroir.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class PairRequest(val action: String, val profile: String? = null, val fcmToken: String? = null)

@Serializable
data class PairResponse(val pairId: String, val member: String, val partnerJoined: Boolean, val rev: Long)

@Serializable
data class TodayProposal(val day: String, val tag: String, val ref: String)

@Serializable
data class StateRequest(val since: Long, val today: TodayProposal? = null)

@Serializable
data class StateResponse(
    val rev: Long,
    val serverTime: Long,
    val me: String,
    val members: List<MemberDto> = emptyList(),
    val settings: SettingsDto = SettingsDto(),
    val days: List<DayDto> = emptyList(),
    val customQuestions: List<CustomQuestionDto> = emptyList(),
)

@Serializable
data class MemberDto(val member: String, val profile: String? = null, val joinedAt: Long = 0)

@Serializable
data class SettingsDto(val payload: String? = null, val version: Int = 0)

@Serializable
data class DayDto(
    val day: String,
    val tag: String,
    val ref: String,
    val skips: Int = 0,
    val skippedRef: String? = null,
    val rev: Long = 0,
    val answers: Map<String, AnswerDto> = emptyMap(),
    val reactions: Map<String, ReactionDto> = emptyMap(),
)

@Serializable
data class AnswerDto(val answered: Boolean = true, val payload: String? = null, val at: Long = 0)

@Serializable
data class ReactionDto(val payload: String, val at: Long = 0)

@Serializable
data class CustomQuestionDto(
    val id: String,
    val author: String,
    val payload: String,
    val deleted: Boolean = false,
    val createdAt: Long = 0,
    val rev: Long = 0,
)

@Serializable
data class AnswerRequest(val day: String, val tag: String, val ref: String, val payload: String)

@Serializable
data class SkipRequest(val day: String, val expectedSkips: Int, val tag: String, val ref: String)

@Serializable
data class ReactRequest(val day: String, val payload: String)

@Serializable
data class CustomQuestionRequest(val id: String, val payload: String, val deleted: Boolean = false)

@Serializable
data class SettingsRequest(val payload: String, val baseVersion: Int)

@Serializable
data class ProfileRequest(val profile: String)

@Serializable
data class TokenRequest(val fcmToken: String?)

@Serializable
data class DayResponse(val rev: Long, val day: DayDto? = null)

@Serializable
data class SettingsResponse(val rev: Long, val settings: SettingsDto = SettingsDto())

@Serializable
data class RevResponse(val rev: Long = 0)

@Serializable
data class ErrorBody(val error: String = "unknown", val day: DayDto? = null, val settings: SettingsDto? = null)
