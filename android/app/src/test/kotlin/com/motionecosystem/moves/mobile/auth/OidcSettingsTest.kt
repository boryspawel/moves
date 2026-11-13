package com.motionecosystem.moves.mobile.auth

import org.junit.Assert.assertThrows
import org.junit.Test

class OidcSettingsTest {
    private val valid = OidcSettings(
        "https://auth.example.org/realms/moves",
        "motion-android",
        "com.motionecosystem.moves.mobile:/oauth2redirect",
        false
    )

    @Test fun productionConfig() { valid.validate() }

    @Test fun productionRejectsHttpButDevelopmentAllowsIt() {
        assertThrows(IllegalArgumentException::class.java) { valid.copy(issuer = "http://auth.example.org/realms/moves").validate() }
        valid.copy(issuer = "http://10.0.2.2:8180/realms/motion-local", allowLocalHttp = true).validate()
    }

    @Test fun malformedConfigRejected() {
        assertThrows(IllegalArgumentException::class.java) { valid.copy(issuer = "https://user:password@auth.example.org/realms/moves").validate() }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(redirectUri = "com.motionecosystem.moves.mobile:/oauth2redirect#fragment").validate() }
    }
}
