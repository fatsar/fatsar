package com.fatsar.hermes

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 *
 * Ekran boyutu sıradan bir telefona sabitlenir (411×891 dp, xhdpi): Robolectric'in
 * varsayılanı 320×470 dp'lik çok eski bir ekrandır ve hiçbir güncel cihazı temsil
 * etmez. Yine de uzun ekranlarda içerik kaydırılabildiği için aşağıdaki yardımcılar
 * bir öğeye dokunmadan/bakmadan önce gerekiyorsa ona kaydırır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ArayuzAkisiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun kurulum_ekrani_agent_baglantisi_ister() {
        gorunur("Hermes Bot Konsol")
        gorunur("1. Sunucunuzda agent'ı başlatın")
        gorunur("2. Eşleştirme kodunu yapıştırın")
        gorunur("Önce bir bakayım")
    }

    @Test
    fun kurulum_atlanip_bot_listesine_gecilebilir() {
        tikla("Önce bir bakayım")
        gorunur("Botlarım")
        gorunur("Henüz botunuz yok")
    }

    /** Asıl iş: şablondan bot oluşturup sohbet ekranına ulaşmak. */
    @Test
    fun sablondan_bot_olusturulup_sohbet_ekrani_acilir() {
        tikla("Önce bir bakayım")

        adim("boş liste botu oluşturmaya yönlendiriyor") { tikla("İlk botu oluştur") }
        adim("şablon listesi açıldı") { gorunur("Nasıl bir bot istiyorsunuz?") }

        tikla("Sohbet Botu")
        adim("bot düzenleme ekranı açıldı") { gorunur("Görev tanımı") }

        adim("kaydet düğmesi etkin") { kaydir("Kaydet").assertIsEnabled() }
        tikla("Kaydet")
        adim("sohbet ekranı açıldı") { gorunur("Mesaj yazın…") }

        // Geri dönünce bot listede duruyor mu?
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        adim("bot listede") { rule.onAllNodesWithText("Sohbet Botu").onFirst().assertIsDisplayed() }
    }

    /**
     * Eşleştirme kodu ile agent bağlantısı eklenir. Kod, bağlantının anında
     * reddedildiği yerel bir adres taşır: test ağa çıkmaz.
     */
    @Test
    fun eslestirme_kodu_ile_agent_baglantisi_eklenir() {
        val kod = Pairing.encode("http://127.0.0.1:1", "test-token", "Test VPS")

        kaydir("Eşleştirme kodu").performTextInput(kod)
        rule.waitForIdle()
        adim("bağlan düğmesi etkin") { kaydir("Bağlan").assertIsEnabled() }

        tikla("Bağlan")
        adim("bot listesine geçildi") { gorunur("Botlarım") }

        // Sunucular ekranında eklenen agent görünüyor mu?
        tikla("🛰")
        adim("agent listede") { rule.onAllNodesWithText("Test VPS").onFirst().assertIsDisplayed() }
    }

    /** Gerekiyorsa öğeye kaydırır; kaydırılamayan (ör. üst çubuk) öğelerde sessizce geçer. */
    private fun kaydir(metin: String): SemanticsNodeInteraction {
        val dugum = rule.onNodeWithText(metin)
        runCatching { dugum.performScrollTo() }
        return dugum
    }

    private fun gorunur(metin: String) = kaydir(metin).assertIsDisplayed()

    private fun tikla(metin: String) {
        kaydir(metin).performClick()
        rule.waitForIdle()
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
