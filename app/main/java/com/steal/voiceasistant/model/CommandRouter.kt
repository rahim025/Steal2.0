package com.steal.voiceassistant.model

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.steal.voiceassistant.service.UnlockAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.Locale

/**
 * Point d'entrée unique : reçoit une commande déjà analysée (ParsedCommand)
 * et l'exécute en s'appuyant sur les bons composants Android.
 */
class CommandRouter(private val context: Context) {

    private var tts: TextToSpeech? = TextToSpeech(context) { }
    private val contactResolver = ContactResolver(context)
    private val routerScope = CoroutineScope(Dispatchers.Main)

    fun execute(command: ParsedCommand) {
        when (command) {
            is ParsedCommand.OpenApp -> openApp(command.appName)
            is ParsedCommand.SendMessage -> sendMessage(command)
            is ParsedCommand.Call -> call(command.contact)
            is ParsedCommand.SystemAction -> systemAction(command.action)
            is ParsedCommand.ScreenAction -> screenAction(command)
            ParsedCommand.Unknown -> speak("Je n'ai pas compris cette commande.")
        }
    }

    private fun openApp(appWord: String) {
        val parser = CommandParser()
        val packageName = parser.packageNameFor(appWord) ?: return speak("Application inconnue.")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            speak("$appWord n'est pas installé.")
        }
    }

    private fun sendMessage(command: ParsedCommand.SendMessage) {
        val profile = MessagingAppProfiles.forAppWord(command.appName)
        if (profile == null) {
            speak("Envoi de message non supporté pour ${command.appName}.")
            return
        }
        openApp(command.appName)
        // On attend un court instant que l'app soit lancée avant de démarrer
        // l'enchaînement d'actions (qui, lui, attend le chargement de chaque
        // écran suivant via waitForScreenChange).
        routerScope.launch {
            kotlinx.coroutines.delay(1200)
            val service = UnlockAccessibilityService.instance
            if (service == null) {
                speak("Service d'accessibilité non actif.")
                return@launch
            }
            val success = service.sendMessage(profile, command.contact, command.message)
            if (success) {
                speak("Message envoyé à ${command.contact}")
            } else {
                speak("Échec de l'envoi. Le profil de l'application a peut-être changé.")
            }
        }
    }

    private fun call(contact: String) {
        val number = contactResolver.resolvePhoneNumber(contact)
        if (number == null) {
            speak("Contact $contact introuvable.")
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            speak("Permission d'appel non accordée.")
            return
        }
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
        speak("Appel de $contact")
    }

    private fun systemAction(action: SystemActionType) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action) {
            SystemActionType.VOLUME_UP ->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            SystemActionType.VOLUME_DOWN ->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            SystemActionType.GO_HOME -> UnlockAccessibilityService.instance?.performGoHome()
            SystemActionType.GO_BACK -> UnlockAccessibilityService.instance?.performGoBack()
            SystemActionType.OPEN_NOTIFICATIONS -> UnlockAccessibilityService.instance?.openNotifications()
            SystemActionType.FLASHLIGHT_ON, SystemActionType.FLASHLIGHT_OFF ->
                speak("Contrôle de la lampe torche à implémenter via CameraManager.")
            SystemActionType.WIFI_ON, SystemActionType.WIFI_OFF ->
                speak("Sur Android récent, le WiFi doit être activé via les réglages rapides pour raisons de sécurité.")
        }
    }

    private fun screenAction(command: ParsedCommand.ScreenAction) {
        val service = UnlockAccessibilityService.instance ?: return speak("Service d'accessibilité non actif.")
        when (command.action) {
            ScreenActionType.CLICK -> command.target?.let { service.clickByLabel(it) }
            ScreenActionType.SCROLL_DOWN -> speak("Défilement vers le bas à implémenter (ACTION_SCROLL_FORWARD).")
            ScreenActionType.SCROLL_UP -> speak("Défilement vers le haut à implémenter (ACTION_SCROLL_BACKWARD).")
            ScreenActionType.TYPE_TEXT -> {
                if (command.target != null && command.text != null) {
                    service.clickByLabel(command.target)
                }
            }
            ScreenActionType.READ_SCREEN -> speak(service.collectScreenText())
        }
    }

    private fun speak(text: String) {
        tts?.language = Locale.FRENCH
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        tts?.shutdown()
        routerScope.cancel()
    }
}
