package com.steal.voiceassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.steal.voiceassistant.model.MessagingAppProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Service central : c'est lui qui "voit" ce qui est affiché à l'écran
 * (via onAccessibilityEvent / rootInActiveWindow) et qui peut simuler des
 * clics, du scroll et de la saisie de texte dans N'IMPORTE QUELLE app,
 * y compris quand l'écran est verrouillé (dans la limite des restrictions
 * de sécurité Android — voir README pour le détail des limites).
 */
class UnlockAccessibilityService : AccessibilityService() {

    companion object {
        // Référence statique simple pour que le reste de l'app puisse
        // appeler ce service tant qu'il est actif. À remplacer par un
        // bus d'événements/binder si le projet grossit.
        var instance: UnlockAccessibilityService? = null
    }

    private var pendingScreenChangeCallback: (() -> Unit)? = null
    private val screenChangeTimeoutHandler = Handler(Looper.getMainLooper())

    /**
     * Suspend l'exécution jusqu'à ce qu'un changement d'écran/contenu soit
     * détecté, ou jusqu'au timeout — pour enchaîner les actions UI de façon
     * fiable au lieu de cliquer "en rafale" sans attendre le chargement.
     */
    suspend fun waitForScreenChange(timeoutMs: Long = 2000): Boolean = suspendCancellableCoroutine { cont ->
        pendingScreenChangeCallback = {
            if (cont.isActive) cont.resume(true)
        }
        screenChangeTimeoutHandler.postDelayed({
            if (pendingScreenChangeCallback != null) {
                pendingScreenChangeCallback = null
                if (cont.isActive) cont.resume(false)
            }
        }, timeoutMs)
        cont.invokeOnCancellation {
            pendingScreenChangeCallback = null
            screenChangeTimeoutHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On pourra ici détecter par ex. l'écran de verrouillage actif
        // via event.packageName == "com.android.systemui" pour déclencher
        // la tentative de déverrouillage vocal.

        // Utilisé par waitForScreenChange() pour savoir qu'un nouvel écran
        // (ou contenu) vient de s'afficher, avant d'exécuter l'étape suivante
        // d'un enchaînement d'actions.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            pendingScreenChangeCallback?.let { callback ->
                pendingScreenChangeCallback = null
                screenChangeTimeoutHandler.removeCallbacksAndMessages(null)
                callback()
            }
        }
    }

    override fun onInterrupt() {}

    // ---- Actions génériques sur l'écran ----

    fun performGoHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performGoBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Tente le déverrouillage (fonctionne seulement sans code/schéma actif). */
    fun attemptUnlock() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Swipe du bas vers le haut au centre de l'écran, geste standard
        // pour révéler l'écran d'accueil sur un verrouillage "glissement simple".
        val displayMetrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.8f)
            lineTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /** Cherche un noeud dont le texte ou la description contient `label` et clique dessus. */
    fun clickByLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, label) ?: return false
        return clickNode(node)
    }

    fun clickByResourceId(resourceId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        val node = nodes.firstOrNull() ?: return false
        return clickNode(node)
    }

    fun typeTextInField(resourceId: String, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        val node = nodes.firstOrNull() ?: return false
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByText(text)
        return matches.firstOrNull()
    }

    /**
     * Enchaîne les étapes pour envoyer un message dans une app de messagerie
     * donnée (WhatsApp, Messenger, Instagram, TikTok...), en attendant à
     * chaque étape que l'écran suivant soit réellement chargé avant de
     * continuer (via waitForScreenChange), au lieu d'enchaîner les clics
     * en rafale sans certitude que l'écran précédent a fini de s'afficher.
     *
     * @return true si toutes les étapes ont pu s'exécuter, false si une
     * étape a échoué (élément introuvable ou écran non chargé à temps) —
     * dans ce cas le profil de l'app (resource-id) est probablement à jour à vérifier.
     */
    suspend fun sendMessage(profile: MessagingAppProfile, contact: String, message: String): Boolean {
        if (!clickByResourceId(profile.searchButtonId)) return false
        waitForScreenChange()

        if (!typeTextInField(profile.searchInputId, contact)) return false
        waitForScreenChange(timeoutMs = 1500) // laisse le temps aux résultats de recherche de s'afficher

        if (!clickByResourceId(profile.conversationRowId)) return false
        waitForScreenChange()

        if (!typeTextInField(profile.messageInputId, message)) return false

        return clickByResourceId(profile.sendButtonId)
    }

    /** Lit à voix haute (à connecter au TTS) le contenu textuel visible à l'écran. */
    fun collectScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        collectText(root, builder)
        return builder.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let { builder.append(it).append(". ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, builder) }
        }
    }
}
