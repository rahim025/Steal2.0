package com.steal.voiceassistant.model

import android.content.Context
import android.provider.ContactsContract

/**
 * Cherche le numéro de téléphone d'un contact à partir d'un nom prononcé,
 * en interrogeant le carnet de contacts du téléphone (nécessite la
 * permission READ_CONTACTS, déjà déclarée dans le manifeste).
 */
class ContactResolver(private val context: Context) {

    /**
     * @param spokenName nom tel que reconnu par la voix (peut être partiel,
     * ex: juste le prénom)
     * @return le premier numéro de téléphone trouvé pour un contact dont le
     * nom contient `spokenName`, ou null si aucun contact ne correspond
     */
    fun resolvePhoneNumber(spokenName: String): String? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$spokenName%")

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (cursor.moveToFirst() && numberIndex >= 0) {
                return cursor.getString(numberIndex)
            }
        }
        return null
    }
}
