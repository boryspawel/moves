package com.motionecosystem.moves.mobile.auth

import net.openid.appauth.AuthorizationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OidcRefreshPolicyTest {
    @Test fun invalidRefreshGrantAndClientAreDefinitiveRevocation() {
        val invalidGrant = AuthorizationException.fromTemplate(
            AuthorizationException.TokenRequestErrors.INVALID_GRANT, null)
        val invalidClient = AuthorizationException.fromTemplate(
            AuthorizationException.TokenRequestErrors.INVALID_CLIENT, null)
        assertTrue(isDefinitiveTokenRejection(invalidGrant))
        assertTrue(isDefinitiveTokenRejection(invalidClient))
    }

    @Test fun networkAndUnclassifiedRefreshErrorsAreNotAutomaticLogout() {
        val network = AuthorizationException.fromTemplate(
            AuthorizationException.GeneralErrors.NETWORK_ERROR, null)
        val malformedResponse = AuthorizationException.fromTemplate(
            AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR, null)
        val other = AuthorizationException.fromTemplate(
            AuthorizationException.TokenRequestErrors.OTHER, null)
        assertFalse(isDefinitiveTokenRejection(network))
        assertFalse(isDefinitiveTokenRejection(malformedResponse))
        assertFalse(isDefinitiveTokenRejection(other))
    }
}
