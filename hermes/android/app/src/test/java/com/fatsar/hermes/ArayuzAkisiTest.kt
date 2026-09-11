package com.fatsar.hermes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uygulamayı emülatörsüz (JVM üzerinde, Robolectric ile) gerçekten çalıştırır:
 * açılış → API bağlantısı ekleme → şablondan bot oluşturma → sohbet ekranı.
 * Böylece her derlemede arayüzün açıldığı ve akışın yürüdüğü doğrulanır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArayuzAkisiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun kurulum_ekrani_acilir() {
        rule.onNodeWithText("Hermes Bot Konsol").assertIsDisplayed()
        rule.onNodeWithText("Sunucumda Hermes Agent var").assertIsDisplayed()
        rule.onNodeWithText("Doğrudan API kullanacağım").assertIsDisplayed()
    }

    @Test
    fun baglanti_eklenip_bot_olusturulabilir() {
        // 1) Kurulum: doğrudan API
        rule.onNodeWithText("Doğrudan API kullanacağım").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("API anahtarı").performTextInput("test-anahtari-123")
        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()

        // 2) Bot listesi boş durumu
        rule.onNodeWithText("Henüz botunuz yok").assertIsDisplayed()
        rule.onNodeWithText("İlk botu oluştur").performClick()
        rule.waitForIdle()

        // 3) Şablon seçimi
        rule.onNodeWithText("Nasıl bir bot istiyorsunuz?").assertIsDisplayed()
        rule.onNodeWithText("Sohbet Botu").performClick()
        rule.waitForIdle()

        // 4) Şablon bot tanımını doldurdu mu?
        rule.onNodeWithText("Görev tanımı").assertIsDisplayed()
        rule.onAllNodesWithText("Sohbet Botu").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()

        // 5) Sohbet ekranı geldi mi?
        rule.onNodeWithText("Mesaj yazın…").assertIsDisplayed()
    }

    @Test
    fun kurulum_atlanip_bot_listesine_gecilebilir() {
        rule.onNodeWithText("Önce bir bakayım").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Botlarım").assertIsDisplayed()
    }
}
