package com.nuvio.app.features.debrid

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * CineSnappa: AllDebrid API v4 client (https://docs.alldebrid.com).
 * Auth is the user's API key sent as `Authorization: Bearer <key>`.
 * Every response is wrapped as {"status":"success"|"error","data":{...},"error":{...}}.
 */
internal object AllDebridApiClient {
    private const val BASE_URL = "https://api.alldebrid.com"

    suspend fun validateApiKey(apiKey: String): Boolean {
        val response = request<AllDebridUserDataDto>("GET", "$BASE_URL/v4/user", apiKey.trim())
        return response.isSuccessful && response.body?.status == "success" && response.body.data?.user != null
    }

    suspend fun uploadMagnet(apiKey: String, magnet: String): DebridApiResponse<AllDebridEnvelopeDto<AllDebridUploadDataDto>> =
        request("POST", "$BASE_URL/v4/magnet/upload", apiKey, listOf("magnets[]" to magnet))

    suspend fun magnetFiles(apiKey: String, id: Long): DebridApiResponse<AllDebridEnvelopeDto<AllDebridFilesDataDto>> =
        request("POST", "$BASE_URL/v4/magnet/files", apiKey, listOf("id[]" to id.toString()))

    suspend fun unlockLink(apiKey: String, link: String): DebridApiResponse<AllDebridEnvelopeDto<AllDebridUnlockDto>> =
        request("POST", "$BASE_URL/v4/link/unlock", apiKey, listOf("link" to link))

    suspend fun deleteMagnet(apiKey: String, id: Long): DebridApiResponse<AllDebridEnvelopeDto<AllDebridMessageDto>> =
        request("POST", "$BASE_URL/v4/magnet/delete", apiKey, listOf("id" to id.toString()))

    private suspend inline fun <reified T> request(
        method: String,
        url: String,
        apiKey: String,
        fields: List<Pair<String, String>> = emptyList(),
    ): DebridApiResponse<T> {
        val body = fields.joinToString("&") { (key, value) ->
            "${encodeFormValue(key)}=${encodeFormValue(value)}"
        }
        val headers = buildMap {
            put("Authorization", "Bearer ${apiKey.trim()}")
            put("Accept", "application/json")
            if (fields.isNotEmpty()) put("Content-Type", "application/x-www-form-urlencoded")
        }
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
        )
        val decoded: T? = if (response.body.isBlank()) {
            null
        } else {
            try {
                DebridApiJson.json.decodeFromString<T>(response.body)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return DebridApiResponse(status = response.status, body = decoded, rawBody = response.body)
    }
}

@Serializable
internal data class AllDebridEnvelopeDto<T>(
    val status: String? = null,
    val data: T? = null,
    val error: AllDebridErrorDto? = null,
) {
    val isSuccess: Boolean get() = status == "success" && data != null
}

@Serializable
internal data class AllDebridErrorDto(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
internal data class AllDebridUserDataDto(
    val status: String? = null,
    val data: AllDebridUserWrapperDto? = null,
)

@Serializable
internal data class AllDebridUserWrapperDto(
    val user: AllDebridUserDto? = null,
)

@Serializable
internal data class AllDebridUserDto(
    val username: String? = null,
    val isPremium: Boolean? = null,
)

@Serializable
internal data class AllDebridUploadDataDto(
    val magnets: List<AllDebridUploadedMagnetDto> = emptyList(),
)

@Serializable
internal data class AllDebridUploadedMagnetDto(
    val id: Long? = null,
    val hash: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val ready: Boolean? = null,
    val error: AllDebridErrorDto? = null,
)

@Serializable
internal data class AllDebridFilesDataDto(
    val magnets: List<AllDebridMagnetFilesDto> = emptyList(),
)

@Serializable
internal data class AllDebridMagnetFilesDto(
    val id: String? = null,
    val files: List<AllDebridFileNodeDto> = emptyList(),
    val error: AllDebridErrorDto? = null,
)

/** A folder has `n` + `e` (children); a file has `n` (name), `s` (size) and `l` (link). */
@Serializable
internal data class AllDebridFileNodeDto(
    val n: String? = null,
    val s: Long? = null,
    val l: String? = null,
    val e: List<AllDebridFileNodeDto>? = null,
)

@Serializable
internal data class AllDebridUnlockDto(
    val link: String? = null,
    val filename: String? = null,
    val filesize: Long? = null,
    val delayed: Long? = null,
)

@Serializable
internal data class AllDebridMessageDto(
    val message: String? = null,
)

/** One downloadable file, flattened out of AllDebrid's folder tree. */
internal data class AllDebridFlatFile(
    val path: String,
    val size: Long?,
    val link: String,
)

internal fun List<AllDebridFileNodeDto>.flattenAllDebridFiles(prefix: String = ""): List<AllDebridFlatFile> =
    flatMap { node ->
        val name = node.n.orEmpty()
        val path = if (prefix.isEmpty()) name else "$prefix/$name"
        val children = node.e
        when {
            !children.isNullOrEmpty() -> children.flattenAllDebridFiles(path)
            !node.l.isNullOrBlank() -> listOf(AllDebridFlatFile(path = path, size = node.s, link = node.l))
            else -> emptyList()
        }
    }
