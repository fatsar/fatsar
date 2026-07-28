package com.fatsar.kartvizit.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.util.Log
import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.ocr.TextNormalizer

/** Kayıtları telefon rehberine (kişilere) ekler. */
object DeviceContacts {

    private const val TAG = "DeviceContacts"

    fun insert(context: Context, record: ContactRecord): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        fun data(mimeType: String): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)

        // Kişinin adı ve soyadı ayrı alanlar olarak eklenir; firma adı yalnızca
        // isim hiç yoksa görünen ad olarak kullanılır.
        if (record.name.isNotBlank()) {
            val (givenName, familyName) = TextNormalizer.splitName(record.name)
            ops.add(
                data(StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.GIVEN_NAME, givenName)
                    .withValue(StructuredName.FAMILY_NAME, familyName)
                    .build()
            )
        } else if (record.company.isNotBlank()) {
            ops.add(
                data(StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, record.company)
                    .build()
            )
        }

        if (record.company.isNotBlank() || record.title.isNotBlank()) {
            ops.add(
                data(Organization.CONTENT_ITEM_TYPE)
                    .withValue(Organization.COMPANY, record.company)
                    .withValue(Organization.TITLE, record.title)
                    .withValue(Organization.TYPE, Organization.TYPE_WORK)
                    .build()
            )
        }

        record.phones.forEach { phone ->
            ops.add(
                data(Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone.number)
                    .withValue(Phone.TYPE, phoneContactType(phone.type))
                    .build()
            )
        }

        record.emails.forEach { email ->
            ops.add(
                data(Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, email)
                    .withValue(Email.TYPE, Email.TYPE_WORK)
                    .build()
            )
        }

        if (record.website.isNotBlank()) {
            ops.add(
                data(Website.CONTENT_ITEM_TYPE)
                    .withValue(Website.URL, record.website)
                    .withValue(Website.TYPE, Website.TYPE_WORK)
                    .build()
            )
        }

        if (record.address.isNotBlank()) {
            ops.add(
                data(StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(StructuredPostal.FORMATTED_ADDRESS, record.address)
                    .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
                    .build()
            )
        }

        val noteText = listOf(record.category, record.notes)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (noteText.isNotBlank()) {
            ops.add(data(Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, noteText).build())
        }

        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Rehbere eklenemedi", e)
            false
        }
    }

    private fun phoneContactType(type: PhoneType): Int = when (type) {
        PhoneType.MOBILE -> Phone.TYPE_MOBILE
        PhoneType.FAX -> Phone.TYPE_FAX_WORK
        PhoneType.HOME -> Phone.TYPE_HOME
        PhoneType.WORK -> Phone.TYPE_WORK
        PhoneType.OTHER -> Phone.TYPE_OTHER
    }
}
