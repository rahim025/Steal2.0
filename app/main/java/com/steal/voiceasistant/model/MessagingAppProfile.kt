package com.steal.voiceassistant.model

/**
 * Identifiants des éléments d'interface d'une app de messagerie, utilisés par
 * l'AccessibilityService pour automatiser la recherche d'un contact et
 * l'envoi d'un message.
 *
 * ATTENTION : ces resource-id peuvent changer à chaque mise à jour de l'app
 * concernée. Pour les retrouver après une mise à jour :
 *   1. Ouvrir l'app sur le téléphone
 *   2. Lancer `uiautomatorviewer` (fourni avec Android Studio / Android SDK)
 *   3. Cliquer sur l'élément (ex: le bouton recherche) pour voir son "resource-id"
 *   4. Mettre à jour les constantes correspondantes ci-dessous
 *
 * Les valeurs actuelles sont des points de départ plausibles à vérifier et
 * corriger avec uiautomatorviewer avant utilisation réelle — seul WhatsApp a
 * été documenté avec un peu plus de certitude, les autres sont à confirmer.
 */
data class MessagingAppProfile(
    val packageName: String,
    val searchButtonId: String,
    val searchInputId: String,
    val conversationRowId: String,
    val messageInputId: String,
    val sendButtonId: String
)

object MessagingAppProfiles {
    val WHATSAPP = MessagingAppProfile(
        packageName = "com.whatsapp",
        searchButtonId = "com.whatsapp:id/menuitem_search",
        searchInputId = "com.whatsapp:id/search_src_text",
        conversationRowId = "com.whatsapp:id/contact_row_container",
        messageInputId = "com.whatsapp:id/entry",
        sendButtonId = "com.whatsapp:id/send"
    )

    // À VÉRIFIER avec uiautomatorviewer avant usage réel.
    val MESSENGER = MessagingAppProfile(
        packageName = "com.facebook.orca",
        searchButtonId = "com.facebook.orca:id/search_src_text",
        searchInputId = "com.facebook.orca:id/search_src_text",
        conversationRowId = "com.facebook.orca:id/thread_list_item",
        messageInputId = "com.facebook.orca:id/message_input",
        sendButtonId = "com.facebook.orca:id/send_button"
    )

    // À VÉRIFIER avec uiautomatorviewer avant usage réel.
    val INSTAGRAM = MessagingAppProfile(
        packageName = "com.instagram.android",
        searchButtonId = "com.instagram.android:id/action_bar_search_edit_text",
        searchInputId = "com.instagram.android:id/action_bar_search_edit_text",
        conversationRowId = "com.instagram.android:id/direct_thread_row_container",
        messageInputId = "com.instagram.android:id/row_thread_composer_edittext",
        sendButtonId = "com.instagram.android:id/row_thread_composer_button_send"
    )

    // À VÉRIFIER avec uiautomatorviewer avant usage réel.
    // TikTok : les messages se trouvent dans l'onglet "Boîte de réception".
    val TIKTOK = MessagingAppProfile(
        packageName = "com.zhiliaoapp.musically",
        searchButtonId = "com.zhiliaoapp.musically:id/dmv_search_icon",
        searchInputId = "com.zhiliaoapp.musically:id/dmv_search_edit_text",
        conversationRowId = "com.zhiliaoapp.musically:id/dmv_conversation_item",
        messageInputId = "com.zhiliaoapp.musically:id/dmv_input_field",
        sendButtonId = "com.zhiliaoapp.musically:id/dmv_send_button"
    )

    // À VÉRIFIER avec uiautomatorviewer avant usage réel.
    // Facebook : vise l'onglet "Messages" intégré à l'app (icône Messenger
    // dans la barre de navigation). Si cet onglet redirige vers l'app
    // Messenger séparée sur l'appareil, utiliser le profil MESSENGER
    // ci-dessus à la place — comportement qui varie selon les versions.
    val FACEBOOK = MessagingAppProfile(
        packageName = "com.facebook.katana",
        searchButtonId = "com.facebook.katana:id/messaging_search_icon",
        searchInputId = "com.facebook.katana:id/search_src_text",
        conversationRowId = "com.facebook.katana:id/inbox_thread_row",
        messageInputId = "com.facebook.katana:id/message_compose_input",
        sendButtonId = "com.facebook.katana:id/message_send_button"
    )

    fun forAppWord(appWord: String): MessagingAppProfile? = when (appWord) {
        "whatsapp" -> WHATSAPP
        "messenger" -> MESSENGER
        "instagram" -> INSTAGRAM
        "tiktok" -> TIKTOK
        "facebook" -> FACEBOOK
        else -> null
    }
}
