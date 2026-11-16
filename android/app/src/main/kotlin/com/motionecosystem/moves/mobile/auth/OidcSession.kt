package com.motionecosystem.moves.mobile.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.motionecosystem.moves.mobile.BuildConfig
import com.motionecosystem.moves.mobile.api.TokenProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import kotlin.coroutines.resume

sealed interface SessionStatus {
    data object SignedOut : SessionStatus
    data object Authorizing : SessionStatus
    data object SignedIn : SessionStatus
    data class Failed(val message: String) : SessionStatus
}

/** A transport/temporary identity-provider failure: session survives but PHI must stay gated.
 * Intentionally has no message containing token or endpoint data.
 */
class TokenRefreshTemporarilyUnavailable : Exception("Identity provider temporarily unavailable")

/** OAuth token rejections prove an unusable session; network/5xx/unrecognized failures do not. */
internal fun isDefinitiveTokenRejection(error: AuthorizationException): Boolean =
    error.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
        error.error in setOf(
            "invalid_grant", "invalid_client", "unauthorized_client",
            "invalid_request", "unsupported_grant_type", "invalid_scope"
        )

/** Volatile session: tokens are never stored on disk; process restart requires login. */
class OidcSession private constructor(context: Context, private val settings: OidcSettings) : TokenProvider {
    private val connectionBuilder: ConnectionBuilder =
        if (settings.allowLocalHttp) ConnectionBuilder { uri ->
            (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
        } else DefaultConnectionBuilder.INSTANCE

    private val service = AuthorizationService(
        context.applicationContext,
        AppAuthConfiguration.Builder().setConnectionBuilder(connectionBuilder)
            .setSkipIssuerHttpsCheck(settings.allowLocalHttp).build()
    )
    private val mutableStatus = MutableStateFlow<SessionStatus>(SessionStatus.SignedOut)
    val status: StateFlow<SessionStatus> = mutableStatus
    @Volatile private var active: AuthState? = null

    override fun token(): String? {
        val state = active ?: return null
        val expiry = state.accessTokenExpirationTime ?: return null
        return state.accessToken?.takeIf { expiry > System.currentTimeMillis() + 10_000 }
    }

    suspend fun loginIntent(): Intent {
        settings.validate()
        mutableStatus.value = SessionStatus.Authorizing
        try {
            val config = withTimeout(20_000) {
                suspendCancellableCoroutine<AuthorizationServiceConfiguration> { continuation ->
                    AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(settings.issuer), { result, error ->
                        if (!continuation.isActive) return@fetchFromIssuer
                        if (error != null || result == null) continuation.cancel()
                        else continuation.resume(result)
                    }, connectionBuilder)
                }
            }
            require(config.discoveryDoc?.issuer?.toString() == settings.issuer) { "Issuer mismatch" }
            val request = AuthorizationRequest.Builder(
                config, settings.clientId, ResponseTypeValues.CODE, Uri.parse(settings.redirectUri)
            ).setScope("openid profile").build() // AppAuth generates S256 PKCE, state and nonce.
            return service.getAuthorizationRequestIntent(request)
        } catch (_: Exception) {
            mutableStatus.value = SessionStatus.Failed("Nie można rozpocząć logowania. Sprawdź adres Keycloak.")
            throw IllegalStateException("OIDC initialization failed")
        }
    }

    suspend fun completeLogin(data: Intent?) {
        val response = data?.let(AuthorizationResponse::fromIntent)
        if (response == null) {
            mutableStatus.value = SessionStatus.SignedOut
            return
        }
        val state = AuthState(response, null)
        try {
            val tokens = withTimeout(20_000) {
                suspendCancellableCoroutine<TokenResponse> { continuation ->
                    service.performTokenRequest(response.createTokenExchangeRequest()) { result, error ->
                        if (!continuation.isActive) return@performTokenRequest
                        if (error != null || result == null) continuation.cancel()
                        else continuation.resume(result)
                    }
                }
            }
            state.update(tokens, null)
            require(!state.accessToken.isNullOrBlank())
            active = state
            mutableStatus.value = SessionStatus.SignedIn
        } catch (_: Exception) {
            clear()
            mutableStatus.value = SessionStatus.Failed("Logowanie nie zostało ukończone.")
        }
    }

    /** Only definitive OAuth token rejections terminate the volatile session.
     * Transient refresh failures are signaled to the access gate, never treated as logout.
     */
    suspend fun freshAccessToken(): String? {
        val state = active ?: return null
        // AppAuth reports a local client error when an expiring access token has no refresh
        // grant. This is not a network outage and requires a fresh interactive login.
        if (state.getNeedsTokenRefresh() && state.refreshToken.isNullOrBlank()) {
            if (state === active) clear()
            return null
        }
        return try {
            val (value, error) = withTimeout(20_000) {
                suspendCancellableCoroutine<Pair<String?, AuthorizationException?>> { continuation ->
                    state.performActionWithFreshTokens(service) { access, _, failure ->
                        if (continuation.isActive) continuation.resume(access to failure)
                    }
                }
            }
            if (state !== active) return null // Logout occurred during refresh.
            if (error != null) {
                if (isDefinitiveTokenRejection(error)) {
                    clear()
                    return null
                }
                throw TokenRefreshTemporarilyUnavailable()
            }
            if (value.isNullOrBlank()) {
                clear() // No usable access token or refresh grant.
                null
            } else value
        } catch (_: TimeoutCancellationException) {
            throw TokenRefreshTemporarilyUnavailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (temporary: TokenRefreshTemporarilyUnavailable) {
            throw temporary
        } catch (_: Exception) {
            // Fail closed while retaining the in-memory session for a manual retry.
            throw TokenRefreshTemporarilyUnavailable()
        }
    }

    /** Local sign-out is immediate; browser end-session is best effort. */
    fun logoutIntent(): Intent? {
        val previous = active
        clear()
        val config = previous?.authorizationServiceConfiguration ?: return null
        val hint = previous.idToken ?: return null
        if (config.endSessionEndpoint == null) return null
        return runCatching {
            service.getEndSessionRequestIntent(
                EndSessionRequest.Builder(config).setIdTokenHint(hint)
                    .setPostLogoutRedirectUri(Uri.parse(settings.redirectUri)).build()
            )
        }.getOrNull()
    }

    fun clear() {
        active = null
        mutableStatus.value = SessionStatus.SignedOut
    }

    companion object {
        @Volatile private var instance: OidcSession? = null
        fun get(context: Context): OidcSession = instance ?: synchronized(this) {
            instance ?: OidcSession(
                context.applicationContext,
                OidcSettings(BuildConfig.OIDC_ISSUER, BuildConfig.OIDC_CLIENT_ID,
                    BuildConfig.OIDC_REDIRECT_URI, BuildConfig.FLAVOR == "dev")
            ).also { instance = it }
        }
    }
}
