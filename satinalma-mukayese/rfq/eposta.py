"""Outlook (masaüstü) üzerinden e-posta gönderme ve cevap toplama.

Windows'ta kurulu Outlook uygulamasını COM arayüzüyle kullanır;
ek bir şifre ya da sunucu ayarı gerekmez.
"""

from __future__ import annotations

import os
import re

from .veri import TalepKalemi, Tedarikci

OLFOLDER_INBOX = 6


def outlook_baglan():
    try:
        import win32com.client  # type: ignore
    except ImportError as hata:
        raise RuntimeError(
            "Outlook bağlantısı için 'pywin32' paketi gerekli ve program "
            "Windows'ta çalışmalı. Kurulum: pip install pywin32"
        ) from hata
    try:
        return win32com.client.Dispatch("Outlook.Application")
    except Exception as hata:  # pragma: no cover - Outlook yoksa
        raise RuntimeError(
            "Outlook'a bağlanılamadı. Outlook'un kurulu ve açılabilir "
            "olduğundan emin olun."
        ) from hata


def tr_sayi(deger: float) -> str:
    """1234567.5 -> '1.234.567,50' (Türkçe sayı biçimi)."""
    return f"{deger:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def govde_html(rfq_no: str, tedarikci: Tedarikci, kalemler: list[TalepKalemi], son_cevap_tarihi: str = "") -> str:
    hitap = f"Sayın {tedarikci.yetkili}," if tedarikci.yetkili else f"Sayın {tedarikci.ad} yetkilisi,"
    satirlar = "".join(
        f"<tr><td>{k.kod}</td><td>{k.isim}</td>"
        f"<td style='text-align:right'>{tr_sayi(k.miktar)}</td>"
        f"<td>{k.birim}</td><td>{k.teslim_tarihi}</td></tr>"
        for k in kalemler
    )
    son_cevap = (
        f"<p>Teklifinizi en geç <b>{son_cevap_tarihi}</b> tarihine kadar iletmenizi rica ederiz.</p>"
        if son_cevap_tarihi
        else ""
    )
    return f"""
<p>{hitap}</p>
<p>Aşağıdaki malzemeler için fiyat teklifinizi rica ederiz.
Ekteki <b>teklif formunu</b> doldurup, konu satırını değiştirmeden bu e-postayı
yanıtlayarak formu ek olarak göndermenizi önemle rica ederiz.</p>
<table border="1" cellspacing="0" cellpadding="4" style="border-collapse:collapse">
  <tr style="background:#D9E1F2">
    <th>Stok Kodu</th><th>Stok Adı</th><th>Miktar</th><th>Birim</th><th>İstenen Teslim Tarihi</th>
  </tr>
  {satirlar}
</table>
<p>Belirtilen teslim tarihleri, malzemenin fabrikamızda olması gereken tarihlerdir.</p>
{son_cevap}
<p>Teklif No: <b>{rfq_no}</b><br>Saygılarımızla,</p>
"""


def eposta_gonder(
    outlook,
    kime: str,
    konu: str,
    html_govde: str,
    ek_dosya: str,
    taslak: bool = False,
) -> None:
    posta = outlook.CreateItem(0)  # olMailItem
    posta.To = kime
    posta.Subject = konu
    posta.HTMLBody = html_govde
    posta.Attachments.Add(os.path.abspath(ek_dosya))
    if taslak:
        posta.Save()  # Taslaklar klasörüne kaydeder, kontrol edip elle gönderilir
    else:
        posta.Send()


def cevaplari_topla(outlook, rfq_no: str, hedef_klasor: str) -> list[dict]:
    """Gelen kutusunda konusunda RFQ numarası geçen e-postaları tarar.

    Excel eklerini hedef klasöre kaydeder; ek yoksa gövde metnini .txt olarak
    saklar. Bulunan e-postaların özet listesi döner.
    """
    os.makedirs(hedef_klasor, exist_ok=True)
    ns = outlook.GetNamespace("MAPI")
    gelen_kutusu = ns.GetDefaultFolder(OLFOLDER_INBOX)
    bulunanlar = []
    for eleman in gelen_kutusu.Items:
        try:
            konu = str(eleman.Subject or "")
        except Exception:
            continue
        if rfq_no not in konu:
            continue
        gonderen = ""
        try:
            gonderen = str(eleman.SenderEmailAddress or eleman.SenderName or "")
        except Exception:
            pass
        kayit = {"gonderen": gonderen, "konu": konu, "dosyalar": []}
        ek_sayisi = 0
        try:
            ek_sayisi = eleman.Attachments.Count
        except Exception:
            pass
        for i in range(1, ek_sayisi + 1):
            ek = eleman.Attachments.Item(i)
            ad = str(ek.FileName)
            if not ad.lower().endswith((".xlsx", ".xlsm", ".xls")):
                continue
            guvenli_gonderen = re.sub(r"[^\w.@-]", "_", gonderen) or "bilinmeyen"
            hedef = os.path.join(hedef_klasor, f"{guvenli_gonderen}__{ad}")
            ek.SaveAsFile(os.path.abspath(hedef))
            kayit["dosyalar"].append(hedef)
        if not kayit["dosyalar"]:
            guvenli_gonderen = re.sub(r"[^\w.@-]", "_", gonderen) or "bilinmeyen"
            hedef = os.path.join(hedef_klasor, f"{guvenli_gonderen}__govde.txt")
            try:
                with open(hedef, "w", encoding="utf-8") as f:
                    f.write(str(eleman.Body or ""))
                kayit["dosyalar"].append(hedef)
            except Exception:
                pass
        bulunanlar.append(kayit)
    return bulunanlar
