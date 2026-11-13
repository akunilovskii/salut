package com.liskovsoft.youtubeapi.notifications

import android.app.Application
import com.google.gson.Gson
import com.liskovsoft.youtubeapi.common.models.gen.NotificationStateItem
import com.liskovsoft.youtubeapi.common.models.impl.NotificationStateImpl
import com.liskovsoft.youtubeapi.notifications.gen.NotificationsResult
import com.liskovsoft.youtubeapi.notifications.gen.getItems
import com.liskovsoft.youtubeapi.notifications.gen.getVideoId
import okhttp3.Request
import okio.Timeout
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PhoneNotificationPreferenceTest {
    @Before fun useSyntheticWebCredentials() {
        PhoneNotificationSupport.setWebAuthorizationProvider { owner -> if (owner == "test-account") mapOf("Authorization" to "SAPISIDHASH synthetic", "Cookie" to "SAPISID=synthetic") else emptyMap() }
    }
    @After fun resetPhoneMode() {
        PhoneNotificationSupport.setWebAuthorizationProvider(null)
        PhoneNotificationSupport::class.java.getDeclaredField("enabled").apply { isAccessible = true }.setBoolean(null, false)
    }

    @Test fun failedWriteKeepsThePreviouslyConfirmedMode() {
        PhoneNotificationSupport.enable()
        val states = states()
        var calls = 0
        val service = service(api { calls++; throw IOException("offline") })
        try { service.modifyNotification(states[1]); fail("Expected network failure") } catch (_: Exception) { }
        assertEquals(1, calls)
        assertTrue(states[0].isSelected)
        assertFalse(states[1].isSelected)
    }

    @Test fun confirmedWriteSendsOneRequestWithoutResubscribingAndUpdatesSelection() {
        PhoneNotificationSupport.enable()
        val states = states()
        var calls = 0
        var query: String? = null
        val service = service(api { calls++; Response.success(null) }) { query = it; it }
        service.modifyNotification(states[1])
        assertEquals(1, calls)
        assertEquals("mode-1", query)
        assertFalse(states[0].isSelected)
        assertTrue(states[1].isSelected)
    }

    @Test fun phoneSelectionUsesServerStateWithoutConsultingLocalLikeHeuristics() {
        PhoneNotificationSupport.enable()
        val value = NotificationStateImplWrapper(item(0), 0, "channel", null, true)
        assertTrue(value.isSelected)
    }

    @Test fun inboxReadsEveryNotificationSectionAndAction() {
        val json = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"sections":[
            {"multiPageMenuNotificationSectionRenderer":{"items":[{"notificationRenderer":{"navigationEndpoint":{"watchEndpoint":{"videoId":"abcdefghijk"}}}}]}},
            {"multiPageMenuNotificationSectionRenderer":{"items":[{"notificationRenderer":{"navigationEndpoint":{"watchEndpoint":{"videoId":"lmnopqrstuv"}}}}]}}
        ]}}}}]}"""
        val result = Gson().fromJson(json, NotificationsResult::class.java)
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), result.getItems()?.map { it?.getVideoId() })
    }

    @Test fun missingWebSessionNeverSendsTheUnsupportedTVBearer() {
        PhoneNotificationSupport.enable()
        PhoneNotificationSupport.setWebAuthorizationProvider(null)
        var calls = 0
        val states = states()
        val service = service(api { calls++; Response.success(null) })
        try { service.modifyNotification(states[1]); fail("Expected web sign-in requirement") }
        catch (_: NotificationWebAuthorizationRequired) { }
        assertEquals(0, calls)
        assertTrue(states[0].isSelected)
    }

    @Test fun aWebSessionForOneAccountCannotAuthorizeAnotherAccount() {
        PhoneNotificationSupport.enable()
        assertTrue(PhoneNotificationSupport.isWebAuthorizationAvailable("test-account"))
        assertFalse(PhoneNotificationSupport.isWebAuthorizationAvailable("another-account"))
    }

    @Test fun aDisabledPhoneIntegrationDoesNotReportWebAuthorizationAvailable() {
        resetPhoneMode()
        assertFalse(PhoneNotificationSupport.isWebAuthorizationAvailable("test-account"))
    }

    private fun service(api: NotificationsApi, query: (String) -> String = { it }) =
        NotificationsServiceInt(api, query) { PhoneNotificationSupport.requestHeaders("test-account") }

    private fun item(index: Int): NotificationStateItem = Gson().fromJson("""{"stateId":$index,"inlineMenuButton":{"buttonRenderer":{"serviceEndpoint":{"modifyChannelNotificationPreferenceEndpoint":{"params":"mode-$index"}}}}}""", NotificationStateItem::class.java)
    private fun states(): List<NotificationStateImpl> = (0..2).map { NotificationStateImpl(item(it), 0, "channel", null, true) }
        .also { values -> values.forEach { it.allStates = values } }

    private fun api(execute: () -> Response<Void>): NotificationsApi = object : NotificationsApi {
        override fun getNotifications(notificationsQuery: String?, headers: Map<String, String>): Call<NotificationsResult?>? = throw UnsupportedOperationException()
        override fun getHideNotification(hideNotificationQuery: String?, headers: Map<String, String>): Call<Void> = throw UnsupportedOperationException()
        override fun getModifyNotification(modifyNotificationQuery: String?, headers: Map<String, String>): Call<Void> = object : Call<Void> {
            override fun execute(): Response<Void> = execute.invoke()
            override fun enqueue(callback: Callback<Void>) { throw UnsupportedOperationException() }
            override fun isExecuted() = false
            override fun cancel() { }
            override fun isCanceled() = false
            override fun clone(): Call<Void> = this
            override fun request(): Request = Request.Builder().url("https://www.youtube.com/youtubei/v1/notification/modify_channel_preference").build()
            override fun timeout() = Timeout()
        }
    }
}
