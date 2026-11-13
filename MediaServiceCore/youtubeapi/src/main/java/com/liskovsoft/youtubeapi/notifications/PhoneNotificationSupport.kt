package com.liskovsoft.youtubeapi.notifications

import com.liskovsoft.youtubeapi.common.helpers.AppClient
import java.util.function.Function

class NotificationWebAuthorizationRequired : IllegalStateException("YouTube notifications require web authorization")

/** Per-request web credentials never replace the application's TV OAuth credentials. */
object PhoneNotificationSupport {
    @Volatile var enabled: Boolean = false
        private set
    @Volatile private var webAuthorization: Function<String, Map<String, String>>? = null

    @JvmStatic fun enable() { enabled = true }
    @JvmStatic fun setWebAuthorizationProvider(provider: Function<String, Map<String, String>>?) { webAuthorization = provider }
    @JvmStatic fun isWebAuthorizationAvailable(account: String): Boolean {
        if (!enabled) return false
        return try {
            requestHeaders(account)
            true
        } catch (_: NotificationWebAuthorizationRequired) { false }
    }

    internal fun requestHeaders(account: String): Map<String, String> {
        if (!enabled) return emptyMap()
        val headers = webAuthorization?.apply(account)?.toMutableMap() ?: throw NotificationWebAuthorizationRequired()
        val authorization = headers["Authorization"].orEmpty()
        if (!authorization.contains("SAPISIDHASH") || headers["Cookie"].isNullOrBlank()) throw NotificationWebAuthorizationRequired()
        headers["Origin"] = "https://www.youtube.com"
        headers["Referer"] = "https://www.youtube.com/"
        headers["User-Agent"] = AppClient.WEB.userAgent
        headers["X-Youtube-Client-Name"] = "1"
        headers["X-Youtube-Client-Version"] = AppClient.WEB.clientVersion
        return headers
    }
}
