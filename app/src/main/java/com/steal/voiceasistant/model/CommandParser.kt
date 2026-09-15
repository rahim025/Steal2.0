package com.steal.voiceassistant.model

/**
 * Une commande comprise, prête à être exécutée par le CommandRouter.
 */
sealed class ParsedCommand {
    data class OpenApp(val appName: String) : ParsedCommand()
    data class SendMessage(val appName: String, val contact: String, val message: String) : ParsedCommand()
    data class Call(val contact: String) : ParsedCommand()
    data class SystemAction(val action: SystemActionType) : ParsedCommand()
    data class ScreenAction(val action: ScreenActionType, val target: String? = null, val text: String? = null) : ParsedCommand()
    object Unknown : ParsedCommand()
}

enum class SystemActionType { VOLUME_UP, VOLUME_DOWN, WIFI_ON, WIFI_OFF, FLASHLIGHT_ON, FLASHLIGHT_OFF, GO_HOME, GO_BACK, OPEN_NOTIFICATIONS }
enum class ScreenActionType { CLICK, SCROLL_DOWN, SCROLL_UP, TYPE_TEXT, READ_SCREEN }

/**
 * Analyseur de commandes très simple à base de mots-clés, à améliorer avec le temps
 * (ex: modèle NLU embarqué) mais suffisant pour démarrer 100% offline.
 */
class CommandParser {

    // Apps supportées avec leur nom de package. À compléter au fil du projet.
    private val knownApps = mapOf(
        "whatsapp" to "com.whatsapp",
        "messenger" to "com.facebook.orca",
        "facebook" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically"
    )

    fun parse(rawText: String): ParsedCommand {
        val text = rawText.lowercase().trim()

        // "envoie un message [app] à [contact] : [message]"
        val sendRegex = Regex("envoie (?:un )?message (\\w+) à (\\w+)\\s*[:\\-]\\s*(.+)")
        sendRegex.find(text)?.let { match ->
            val (appWord, contact, message) = match.destructured
            knownApps[appWord]?.let {
                return ParsedCommand.SendMessage(appWord, contact, message)
            }
        }

        // "appelle [contact]"
        Regex("appelle (\\w+)").find(text)?.let {
            return ParsedCommand.Call(it.groupValues[1])
        }

        // "ouvre [app]"
        Regex("ouvre (\\w+)").find(text)?.let { match ->
            val appWord = match.groupValues[1]
            if (knownApps.containsKey(appWord)) {
                return ParsedCommand.OpenApp(appWord)
            }
        }

        // Actions système simples
        when {
            text.contains("monte le volume") -> return ParsedCommand.SystemAction(SystemActionType.VOLUME_UP)
            text.contains("baisse le volume") -> return ParsedCommand.SystemAction(SystemActionType.VOLUME_DOWN)
            text.contains("allume la lampe") || text.contains("allume la torche") ->
                return ParsedCommand.SystemAction(SystemActionType.FLASHLIGHT_ON)
            text.contains("éteins la lampe") || text.contains("éteins la torche") ->
                return ParsedCommand.SystemAction(SystemActionType.FLASHLIGHT_OFF)
            text.contains("reviens en arrière") -> return ParsedCommand.SystemAction(SystemActionType.GO_BACK)
            text.contains("va à l'accueil") || text.contains("accueil") ->
                return ParsedCommand.SystemAction(SystemActionType.GO_HOME)
        }

        // Actions génériques sur l'écran affiché
        Regex("clique sur (.+)").find(text)?.let {
            return ParsedCommand.ScreenAction(ScreenActionType.CLICK, target = it.groupValues[1])
        }
        if (text.contains("défile vers le bas")) return ParsedCommand.ScreenAction(ScreenActionType.SCROLL_DOWN)
        if (text.contains("défile vers le haut")) return ParsedCommand.ScreenAction(ScreenActionType.SCROLL_UP)
        if (text.contains("qu'est-ce qu'il y a à l'écran")) return ParsedCommand.ScreenAction(ScreenActionType.READ_SCREEN)

        Regex("tape (.+) dans (.+)").find(text)?.let {
            return ParsedCommand.ScreenAction(ScreenActionType.TYPE_TEXT, target = it.groupValues[2], text = it.groupValues[1])
        }

        return ParsedCommand.Unknown
    }

    fun packageNameFor(appWord: String): String? = knownApps[appWord]
}
