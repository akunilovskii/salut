package com.liskovsoft.youtubeapi.notifications

import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import org.json.JSONObject
import java.util.Locale

internal object NotificationsApiHelper {
    private fun createQuery(data: String): String {
        if (!PhoneNotificationSupport.enabled) return PostDataHelper.createQueryTV(data)
        // Notification endpoints reject the TV browse context. In particular, no TV visitor
        // token or TV app fields belong to this web request.
        val client = JSONObject().put("clientName", AppClient.WEB.clientName)
            .put("clientVersion", AppClient.WEB.clientVersion)
        val locale = Locale.getDefault()
        if (locale.language.isNotEmpty()) client.put("hl", locale.language)
        if (locale.country.isNotEmpty()) client.put("gl", locale.country)
        return JSONObject("{$data}").put("context", JSONObject().put("client", client)).toString()
    }

    fun getNotificationsQuery(): String {
        return createQuery("\"notificationsMenuRequestType\":\"NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX\"")
    }

    fun getHideNotificationQuery(hideNotificationToken: String): String {
        return createQuery("\"serializedRecordNotificationInteractionsRequest\":\"$hideNotificationToken\"")
    }

    fun getModifyNotificationQuery(modifyNotificationParams: String): String {
        return createQuery("\"params\":\"$modifyNotificationParams\"")
    }
}