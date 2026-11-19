package com.nexora.git.core.auth

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthConfig @Inject constructor() {
    private val runtimeConfig = SecureRuntimeConfigNative.read()

    val githubClientId: String = runtimeConfig.githubClientId.trim()
    val brokerBaseUrl: String = runtimeConfig.authBrokerBaseUrl.trim().trimEnd('/')
    val githubCallbackUrl: String = runtimeConfig.githubCallbackUrl.trim()
    val appCallbackUri: String = runtimeConfig.appCallbackUri

    val isConfigured: Boolean
        get() = githubClientId.isNotBlank() &&
            !isPlaceholderRuntimeConfig(
                githubClientId = githubClientId,
                brokerBaseUrl = brokerBaseUrl,
            ) &&
            isValidAuthEndpointPair(
                brokerBaseUrl = brokerBaseUrl,
                githubCallbackUrl = githubCallbackUrl,
            ) &&
            isValidAppCallbackUri(
                brokerBaseUrl = brokerBaseUrl,
                appCallbackUri = appCallbackUri,
            )
}

/**
 * CI builds intentionally use inert placeholders so that the full build, native
 * configuration encoding and packaging pipeline can be exercised without real OAuth
 * credentials. Those builds must never be handed to a user for sign-in: GitHub answers
 * an unknown `client_id` with its 404 page, which looks like a broken application.
 *
 * This guard only rejects values that cannot be a working production configuration
 * (reserved example/invalid/test hosts and the project's own CI sentinels). It does not
 * try to validate the exact GitHub client-ID format.
 */
internal fun isPlaceholderRuntimeConfig(
    githubClientId: String,
    brokerBaseUrl: String,
): Boolean {
    val clientId = githubClientId.trim()
    if (clientId.isEmpty()) return true

    val normalizedId = clientId.lowercase()
    if (normalizedId in PLACEHOLDER_CLIENT_IDS) return true
    if (PLACEHOLDER_ID_FRAGMENTS.any(normalizedId::contains)) return true

    val host = runCatching { URI(brokerBaseUrl.trim()).host }
        .getOrNull()
        ?.lowercase()
        .orEmpty()
    if (host.isEmpty()) return true

    return host == "localhost" ||
        host == "example.com" ||
        host.endsWith(".example.com") ||
        host.endsWith(".example") ||
        host.endsWith(".invalid") ||
        host.endsWith(".test")
}

private val PLACEHOLDER_CLIENT_IDS = setOf(
    "iv1.nexoraobfuscationsentinel",
    "iv1.nexoraephemeralcionly",
    "iv1.example",
    "iv1.your-client-id",
)

private val PLACEHOLDER_ID_FRAGMENTS = listOf(
    "sentinel",
    "your-client-id",
    "example",
)

internal fun isValidAuthEndpointPair(
    brokerBaseUrl: String,
    githubCallbackUrl: String,
): Boolean {
    val broker = brokerBaseUrl.toStrictHttpsUri() ?: return false
    val callback = githubCallbackUrl.toStrictHttpsUri() ?: return false

    val brokerPath = broker.path.orEmpty()
    if (brokerPath.isNotEmpty() && brokerPath != "/") {
        return false
    }

    return callback.host.equals(broker.host, ignoreCase = true) &&
        effectivePort(callback) == effectivePort(broker) &&
        callback.path == "/oauth/callback"
}


internal fun isValidAppCallbackUri(
    brokerBaseUrl: String,
    appCallbackUri: String,
): Boolean {
    val broker = brokerBaseUrl.toStrictHttpsUri() ?: return false
    val callback = appCallbackUri.toStrictHttpsUri() ?: return false

    val brokerPath = broker.path.orEmpty()
    if (brokerPath.isNotEmpty() && brokerPath != "/") {
        return false
    }

    return callback.host.equals(broker.host, ignoreCase = true) &&
        effectivePort(callback) == effectivePort(broker) &&
        callback.path == "/oauth/android/callback"
}

private fun String.toStrictHttpsUri(): URI? =
    runCatching { URI(this) }
        .getOrNull()
        ?.takeIf { uri ->
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        }

private fun effectivePort(uri: URI): Int =
    if (uri.port >= 0) uri.port else 443
