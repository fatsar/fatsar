package com.fatsar.hermes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.fatsar.hermes.core.logic.Pairing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uygulamayı emülatörsüz (JVM üzerinde, Robolectric ile) gerçekten çalıştırır.
 * Böylece her derlemede arayüzün açıldığı ve ana akışın yürüdüğü doğrulanır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArayuzAkisiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun kurulum_ekrani_agent_baglantisi_ister() {
        rule.onNodeWithText("Hermes Bot Konsol").assertIsDisplayed()
        rule.onNodeWithText("1. Sunucunuzda agent'ı başlatın").assertIsDisplayed()
        rule.onNodeWithText("2. Eşleştirme kodunu yapıştırın").assertIsDisplayed()
        rule.onNodeWithText("Önce bir bakayım").assertIsDisplayed()
    }

    @Test
    fun kurulum_atlanip_bot_listesine_gecilebilir() {
        rule.onNodeWithText("Önce bir bakayım").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Botlarım").assertIsDisplayed()
        rule.onNodeWithText("Henüz botunuz yok").assertIsDisplayed()
    }

    /** Asıl iş: şablondan bot oluşturup sohbet ekranına ulaşmak. */
    @Test
    fun sablondan_bot_olusturulup_sohbet_ekrani_acilir() {
        rule.onNodeWithText("Önce bir bakayım").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("İlk botu oluştur").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nasıl bir bot istiyorsunuz?").assertIsDisplayed()

        rule.onNodeWithText("Sohbet Botu").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Görev tanımı").assertIsDisplayed()
        rule.onNodeWithText("Kaydet").assertIsEnabled()

        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mesaj yazın…").assertIsDisplayed()

        // Geri dönünce bot listede duruyor mu?
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Sohbet Botu").onFirst().assertIsDisplayed()
    }

    /**
     * Eşleştirme kodu ile agent bağlantısı eklenir. Kod, bağlantının anında
     * reddedildiği yerel bir adres taşır: test ağa çıkmaz.
     */
    @Test
    fun eslestirme_kodu_ile_agent_baglantisi_eklenir() {
        val kod = Pairing.encode("http://127.0.0.1:1", "test-token", "Test VPS")

        rule.onNodeWithText("Eşleştirme kodu").performTextInput(kod)
        rule.waitForIdle()
        adim("kaydet düğmesi etkin") { rule.onNodeWithText("Bağlan").assertIsEnabled() }

        rule.onNodeWithText("Bağlan").performClick()
        rule.waitForIdle()
        adim("bot listesine geçildi") { rule.onNodeWithText("Botlarım").assertIsDisplayed() }

        // Sunucular ekranında eklenen agent görünüyor mu?
        rule.onNodeWithText("🛰").performClick()
        rule.waitForIdle()
        adim("agent listede") { rule.onAllNodesWithText("Test VPS").onFirst().assertIsDisplayed() }
    }

    /** Hata çıkarsa hangi adımda olduğunu ve ekranda ne olduğunu mesaja koyar. */
    private fun adim(aciklama: String, govde: () -> Unit) {
        try {
            govde()
        } catch (e: Throwable) {
            val agac = runCatching { rule.onRoot().printToString(maxDepth = 12) }
                .getOrElse { "(ekran ağacı okunamadı)" }
            throw AssertionError("ADIM BAŞARISIZ: $aciklama\n${e.message}\n--- EKRAN ---\n$agac", e)
        }
    }
}
