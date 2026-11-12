package com.motionecosystem.moves.mobile.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

fun interface TokenProvider { fun token(): String? }
object NoTokenProvider : TokenProvider { override fun token(): String? = null }

class BearerTokenInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val token = tokenProvider.token()
        val request = if (token.isNullOrBlank()) chain.request() else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        return chain.proceed(request)
    }
}

/** Mutation callers must supply the idempotency key to this request only; reads never use it. */
fun Request.withIdempotencyKey(idempotencyKey: String): Request = newBuilder().header("Idempotency-Key", idempotencyKey).build()

sealed interface RemoteResult<out T> { data class Success<T>(val value: T) : RemoteResult<T>; data class Failure(val error: RemoteError) : RemoteResult<Nothing> }
sealed interface RemoteError {
    data object Unauthorized : RemoteError
    data object Forbidden : RemoteError
    data object Conflict : RemoteError
    data class Http(val status: Int, val problem: ProblemDetails?) : RemoteError
    data class Network(val cause: IOException) : RemoteError
    data class Serialization(val cause: Throwable) : RemoteError
}

class RemoteCall(private val json: Json) {
    suspend fun <T> execute(block: suspend () -> Response<T>): RemoteResult<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) RemoteResult.Success(body) else RemoteResult.Failure(error(response))
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (network: IOException) { RemoteResult.Failure(RemoteError.Network(network))
    } catch (failure: Throwable) { RemoteResult.Failure(RemoteError.Serialization(failure)) }

    private fun error(response: Response<*>): RemoteError = when (response.code()) {
        401 -> RemoteError.Unauthorized
        403 -> RemoteError.Forbidden
        409 -> RemoteError.Conflict
        else -> RemoteError.Http(response.code(), response.errorBody()?.string()?.let { runCatching { json.decodeFromString(ProblemDetails.serializer(), it) }.getOrNull() })
    }
}

fun createMovesApi(baseUrl: String, tokenProvider: TokenProvider = NoTokenProvider): MovesApi {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    return Retrofit.Builder().baseUrl(baseUrl).client(OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor(tokenProvider)).build()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(MovesApi::class.java)
}
