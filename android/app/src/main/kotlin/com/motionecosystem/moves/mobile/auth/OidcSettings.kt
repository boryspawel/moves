package com.motionecosystem.moves.mobile.auth

import java.net.URI

/** Public OIDC configuration; never contains a client secret. */
data class OidcSettings(val issuer: String, val clientId: String, val redirectUri: String, val allowLocalHttp: Boolean) {
    fun validate() {
        val uri = URI.create(issuer)
        require(uri.scheme == "https" || (allowLocalHttp && uri.scheme == "http")) { "OIDC issuer must use HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(clientId.isNotBlank())
        val redirect = URI.create(redirectUri)
        require(redirect.scheme?.startsWith("com.motionecosystem.moves.mobile") == true &&
            redirect.host == null && redirect.path == "/oauth2redirect" &&
            redirect.query == null && redirect.fragment == null) { "Invalid Android redirect URI" }
    }
}
