package com.nexora.git.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEndpointPolicyTest {

    @Test
    fun acceptsExactHttpsBrokerCallbackPair() {
        assertTrue(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://auth.example.com/oauth/callback",
            ),
        )
    }

    @Test
    fun acceptsExplicitMatchingHttpsPort() {
        assertTrue(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com:8443",
                githubCallbackUrl = "https://auth.example.com:8443/oauth/callback",
            ),
        )
    }

    @Test
    fun rejectsDifferentOriginOrUnexpectedCallbackShape() {
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://evil.example.com/oauth/callback",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://auth.example.com/other",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "http://auth.example.com",
                githubCallbackUrl = "http://auth.example.com/oauth/callback",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com?redirect=evil",
                githubCallbackUrl = "https://auth.example.com/oauth/callback",
            ),
        )
    }

    @Test
    fun acceptsExactHttpsAndroidAppLink() {
        assertTrue(
            isValidAppCallbackUri(
                brokerBaseUrl = "https://auth.example.com",
                appCallbackUri = "https://auth.example.com/oauth/android/callback",
            ),
        )
    }

    @Test
    fun rejectsCrossOriginOrCustomSchemeAndroidCallback() {
        assertFalse(
            isValidAppCallbackUri(
                brokerBaseUrl = "https://auth.example.com",
                appCallbackUri = "https://evil.example.com/oauth/android/callback",
            ),
        )
        assertFalse(
            isValidAppCallbackUri(
                brokerBaseUrl = "https://auth.example.com",
                appCallbackUri = "nexoragit://oauth/callback",
            ),
        )
        assertFalse(
            isValidAppCallbackUri(
                brokerBaseUrl = "https://auth.example.com",
                appCallbackUri = "https://auth.example.com/other",
            ),
        )
    }

    @Test
    fun rejectsCiSentinelRuntimeConfiguration() {
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.NexoraObfuscationSentinel",
                brokerBaseUrl = "https://obfuscation-sentinel.example",
            ),
        )
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.NexoraEphemeralCiOnly",
                brokerBaseUrl = "https://ci-signing.invalid",
            ),
        )
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.your-client-id",
                brokerBaseUrl = "https://your-nexora-git-domain.example",
            ),
        )
    }

    @Test
    fun rejectsReservedOrEmptyRuntimeConfiguration() {
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.0123456789abcdef",
                brokerBaseUrl = "https://auth.example.com",
            ),
        )
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.0123456789abcdef",
                brokerBaseUrl = "https://auth.nexoragit.test",
            ),
        )
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "",
                brokerBaseUrl = "https://auth.nexoragit.dev",
            ),
        )
        assertTrue(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.0123456789abcdef",
                brokerBaseUrl = "",
            ),
        )
    }

    @Test
    fun acceptsRealisticRuntimeConfiguration() {
        assertFalse(
            isPlaceholderRuntimeConfig(
                githubClientId = "Iv1.0123456789abcdef",
                brokerBaseUrl = "https://auth.nexoragit.dev",
            ),
        )
    }

}
