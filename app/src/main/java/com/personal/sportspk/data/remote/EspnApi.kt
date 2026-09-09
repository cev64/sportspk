package com.personal.sportspk.data.remote

import com.personal.sportspk.data.remote.dto.EspnScoreboardDto
import com.personal.sportspk.data.remote.dto.EspnStandingsDto
import com.personal.sportspk.data.remote.dto.EspnSummaryDto
import com.personal.sportspk.data.remote.dto.EspnTeamsDto
import com.personal.sportspk.domain.model.League
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** A network failure we can describe to the user without leaking implementation detail. */
sealed class EspnException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Offline(cause: Throwable) : EspnException("No network connection", cause)
    class Timeout(cause: Throwable) : EspnException("The scores service timed out", cause)
    class RateLimited : EspnException("The scores service is rate limiting requests")
    class Server(val code: Int) : EspnException("The scores service returned HTTP $code")
    class Malformed(cause: Throwable) : EspnException("The scores service returned unexpected data", cause)
}

/**
 * Thin HTTP client for ESPN's site API.
 *
 * Uses [HttpURLConnection] (backed by OkHttp inside Android) plus kotlinx.serialization rather
 * than pulling in Retrofit/OkHttp directly - per the dependency rule, the amount of code saved
 * did not justify the extra dependency for four GET endpoints.
 */
internal class EspnApi(
    private val ioDispatcher: CoroutineDispatcher,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    private val json = Json {
        // ESPN adds and removes fields without notice; unknown keys must never crash a parse.
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun scoreboard(league: League): EspnScoreboardDto =
        get(EspnEndpoints.scoreboard(league))

    suspend fun summary(league: League, eventId: String): EspnSummaryDto =
        get(EspnEndpoints.summary(league, eventId))

    suspend fun teams(league: League): EspnTeamsDto =
        get(EspnEndpoints.teams(league))

    suspend fun standings(league: League): EspnStandingsDto =
        get(EspnEndpoints.standings(league))

    private suspend inline fun <reified T> get(url: String): T = withContext(ioDispatcher) {
        val body = fetch(url)
        try {
            json.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw EspnException.Malformed(e)
        }
    }

    private fun fetch(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
            }

            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                429 -> throw EspnException.RateLimited()
                else -> throw EspnException.Server(code)
            }

            return connection.decodedStream(connection.inputStream)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: SocketTimeoutException) {
            throw EspnException.Timeout(e)
        } catch (e: EspnException) {
            throw e
        } catch (e: IOException) {
            throw EspnException.Offline(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun HttpURLConnection.decodedStream(stream: InputStream): InputStream =
        if (contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(stream) else stream
}
