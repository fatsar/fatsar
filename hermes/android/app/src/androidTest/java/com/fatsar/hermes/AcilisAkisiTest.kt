package com.fatsar.hermes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gerçek cihaz/emülatörde uçtan uca akış:
 * uygulama açılır → API bağlantısı eklenir → şablondan bot oluşturulur → sohbet ekranı gelir.
 */
@RunWith(AndroidJUnit4::class)
class AcilisAkisiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun uygulama_acilir_ve_bot_olusturulabilir() {
        // 1) Kurulum ekranı
        rule.onNodeWithText("Hermes Bot Konsol").assertIsDisplayed()
        rule.onNodeWithText("Doğrudan API kullanacağım").performClick()
        rule.waitForIdle()

        // 2) API bilgileri
        rule.onNodeWithText("API anahtarı").performTextInput("test-anahtari-123")
        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()

        // 3) Bot listesi (boş durum)
        rule.onNodeWithText("Henüz botunuz yok").assertIsDisplayed()
        rule.onNodeWithText("İlk botu oluştur").performClick()
        rule.waitForIdle()

        // 4) Şablon seçimi
        rule.onNodeWithText("Nasıl bir bot istiyorsunuz?").assertIsDisplayed()
        rule.onNodeWithText("Sohbet Botu").performClick()
        rule.waitForIdle()

        // 5) Bot düzenleme ekranı şablonla doldu mu?
        rule.onAllNodesWithText("Sohbet Botu").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Görev tanımı").assertIsDisplayed()
        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()

        // 6) Sohbet ekranı açıldı; mesaj kutusu var
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Mesaj yazın…").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Mesaj yazın…").assertIsDisplayed()

        // 7) Geri dönünce bot listede görünüyor
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Sohbet Botu").onFirst().assertIsDisplayed()
    }

    @Test
    fun sunucu_ekranina_gidilebilir() {
        rule.onNodeWithText("Önce bir bakayım").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Botlarım")).assertIsDisplayed()
    }
}
