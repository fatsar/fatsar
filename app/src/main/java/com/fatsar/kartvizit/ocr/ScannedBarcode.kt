package com.fatsar.kartvizit.ocr

import com.fatsar.kartvizit.model.TypedPhone

/** Karekod/barkod içindeki yapılandırılmış kişi bilgisi (vCard/MECARD). */
data class ScannedContact(
    val name: String = "",
    val title: String = "",
    val org: String = "",
    val phones: List<TypedPhone> = emptyList(),
    val emails: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val address: String = ""
)

/**
 * Görüntüde bulunan bir karekod/barkod. [contact] varsa yapılandırılmış kişi
 * bilgisidir; [url] tek bir bağlantı içeren kodlar içindir; [rawValue] ham
 * metindir. Kutu koordinatları, çoklu kartvizitte kodu doğru karta atamak
 * için kullanılır.
 */
data class ScannedBarcode(
    val rawValue: String,
    val url: String? = null,
    val contact: ScannedContact? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
