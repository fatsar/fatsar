package com.fatsar.kartvizit.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactRecordTest {

    @Test
    fun `tip destekli telefonlar json ile korunur`() {
        val record = ContactRecord(
            name = "Ahmet Yılmaz",
            phones = listOf(
                TypedPhone("0532 111 22 33", PhoneType.MOBILE),
                TypedPhone("0212 555 44 34", PhoneType.FAX)
            ),
            category = "Müşteriler"
        )

        val restored = ContactRecord.fromJson(JSONObject(record.toJson().toString()))

        assertEquals(record.name, restored.name)
        assertEquals(record.category, restored.category)
        assertEquals(2, restored.phones.size)
        assertEquals(PhoneType.MOBILE, restored.phones[0].type)
        assertEquals("0212 555 44 34", restored.phones[1].number)
        assertEquals(PhoneType.FAX, restored.phones[1].type)
    }

    @Test
    fun `eski bicim (duz metin telefonlar) geriye donuk okunur`() {
        val legacy = JSONObject(
            """{"name":"Ali Veli","phones":["0532 111 22 33","0212 555 44 33"]}"""
        )

        val record = ContactRecord.fromJson(legacy)

        assertEquals(2, record.phones.size)
        assertEquals("0532 111 22 33", record.phones[0].number)
        assertEquals(PhoneType.OTHER, record.phones[0].type)
    }

    @Test
    fun `rehber gonderim durumu json ile korunur`() {
        val record = ContactRecord(
            name = "Ahmet Yılmaz",
            addedToContacts = true,
            lastSentAt = 1_700_000_000_000L
        )

        val restored = ContactRecord.fromJson(JSONObject(record.toJson().toString()))

        assertEquals(true, restored.addedToContacts)
        assertEquals(1_700_000_000_000L, restored.lastSentAt)
    }

    @Test
    fun `gonderim alani olmayan eski kayit varsayilanla okunur`() {
        val legacy = JSONObject("""{"name":"Ali Veli","addedToContacts":true}""")

        val record = ContactRecord.fromJson(legacy)

        assertEquals(true, record.addedToContacts)
        assertEquals(0L, record.lastSentAt)
    }

    @Test
    fun `phonesOf ture gore filtreler`() {
        val record = ContactRecord(
            phones = listOf(
                TypedPhone("1", PhoneType.MOBILE),
                TypedPhone("2", PhoneType.WORK),
                TypedPhone("3", PhoneType.MOBILE)
            )
        )
        assertEquals(listOf("1", "3"), record.phonesOf(PhoneType.MOBILE))
        assertEquals(listOf("2"), record.phonesOf(PhoneType.WORK))
    }
}
