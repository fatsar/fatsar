#!/usr/bin/env python3
"""Satınalma Teklif Toplama ve Mukayese Programı.

Akış:
  1) ornek   : Örnek talep ve tedarikçi Excel şablonlarını oluşturur.
  2) gonder  : Talepteki her stok için onaylı tedarikçilere Outlook'tan
               teklif e-postası + doldurulacak Excel formu gönderir.
  3) topla   : Outlook gelen kutusundan cevapları ve formları toplar.
  4) rapor   : Toplanan teklifleri tek Excel'de mukayese eder.

Parametresiz çalıştırılırsa etkileşimli menü açılır.
"""

from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import os
import random
import string
import sys

from rfq import eposta, form, rapor, veri

KAYIT_KLASORU = "kayitlar"
GELEN_KLASORU = "gelen_teklifler"
FORM_KLASORU = "gonderilen_formlar"


def _rfq_no_uret() -> str:
    ek = "".join(random.choices(string.ascii_uppercase + string.digits, k=4))
    return f"TEKLIF-{dt.date.today():%Y%m%d}-{ek}"


def _kayit_yolu(rfq_no: str) -> str:
    return os.path.join(KAYIT_KLASORU, f"{rfq_no}.json")


def _kayit_yaz(rfq_no: str, kayit: dict) -> None:
    os.makedirs(KAYIT_KLASORU, exist_ok=True)
    with open(_kayit_yolu(rfq_no), "w", encoding="utf-8") as f:
        json.dump(kayit, f, ensure_ascii=False, indent=2)


def _kayit_oku(rfq_no: str) -> dict:
    with open(_kayit_yolu(rfq_no), encoding="utf-8") as f:
        return json.load(f)


def _son_rfq_no() -> str | None:
    dosyalar = sorted(glob.glob(os.path.join(KAYIT_KLASORU, "TEKLIF-*.json")))
    if not dosyalar:
        return None
    return os.path.splitext(os.path.basename(dosyalar[-1]))[0]


def komut_ornek(args) -> None:
    """Doldurulacak örnek Excel şablonlarını üretir."""
    from ornekler.olustur import ornekleri_olustur

    ornekleri_olustur(".")
    print("Örnek dosyalar oluşturuldu:")
    print("  - ornek_talep.xlsx        (planlamadan gelen talep bu düzende girilir)")
    print("  - ornek_tedarikciler.xlsx (stok kodu başına onaylı tedarikçiler)")


def komut_gonder(args) -> None:
    kalemler = veri.talep_oku(args.talep)
    eslem = veri.tedarikci_oku(args.tedarikciler)
    tedarikciler, eksikler = veri.tedarikcilere_dagit(kalemler, eslem)

    if eksikler:
        print("UYARI — Aşağıdaki kalemler için tedarikçi listesinde kayıt yok:")
        for k in eksikler:
            print(f"  {k.kod}  {k.isim}")
        print()

    if not tedarikciler:
        print("Gönderilecek tedarikçi bulunamadı; tedarikçi listesini kontrol edin.")
        return

    rfq_no = _rfq_no_uret()
    print(f"Teklif No: {rfq_no}")
    print(f"{len(kalemler)} kalem, {len(tedarikciler)} tedarikçiye gönderilecek.\n")

    outlook = eposta.outlook_baglan()
    os.makedirs(FORM_KLASORU, exist_ok=True)

    gonderilenler = []
    for ted in tedarikciler.values():
        guvenli_ad = "".join(c if c.isalnum() else "_" for c in ted.ad)[:40]
        form_dosyasi = os.path.join(FORM_KLASORU, f"{rfq_no}__{guvenli_ad}.xlsx")
        form.teklif_formu_olustur(form_dosyasi, rfq_no, ted, ted.kalemler)
        konu = f"Fiyat Teklif Talebi [{rfq_no}] - {len(ted.kalemler)} kalem"
        govde = eposta.govde_html(rfq_no, ted, ted.kalemler, args.son_cevap or "")
        eposta.eposta_gonder(outlook, ted.eposta, konu, govde, form_dosyasi, taslak=args.taslak)
        durum = "taslak oluşturuldu" if args.taslak else "gönderildi"
        print(f"  {ted.ad} <{ted.eposta}> — {len(ted.kalemler)} kalem — {durum}")
        gonderilenler.append({"ad": ted.ad, "eposta": ted.eposta, "kalem_sayisi": len(ted.kalemler)})

    _kayit_yaz(
        rfq_no,
        {
            "rfq_no": rfq_no,
            "tarih": dt.datetime.now().isoformat(timespec="seconds"),
            "talep_dosyasi": os.path.abspath(args.talep),
            "kalemler": [k.sozluk() for k in kalemler],
            "tedarikciler": gonderilenler,
            "taslak": args.taslak,
        },
    )
    print(f"\nKayıt: {_kayit_yolu(rfq_no)}")
    if args.taslak:
        print("E-postalar Outlook TASLAKLAR klasörüne kaydedildi; kontrol edip gönderin.")
    print(f"Cevaplar için: python mukayese.py topla --rfq {rfq_no}")


def _rfq_sec(arg_rfq: str | None) -> str:
    rfq_no = arg_rfq or _son_rfq_no()
    if not rfq_no:
        print("Henüz kayıtlı bir teklif turu yok. Önce 'gonder' çalıştırın.")
        sys.exit(1)
    if not os.path.exists(_kayit_yolu(rfq_no)):
        print(f"'{rfq_no}' için kayıt bulunamadı ({_kayit_yolu(rfq_no)}).")
        sys.exit(1)
    return rfq_no


def komut_topla(args) -> None:
    rfq_no = _rfq_sec(args.rfq)
    hedef = os.path.join(GELEN_KLASORU, rfq_no)
    outlook = eposta.outlook_baglan()
    bulunanlar = eposta.cevaplari_topla(outlook, rfq_no, hedef)
    if not bulunanlar:
        print(f"Gelen kutusunda '{rfq_no}' içeren e-posta bulunamadı.")
        return
    print(f"{len(bulunanlar)} cevap bulundu:")
    for b in bulunanlar:
        print(f"  {b['gonderen']} — {len(b['dosyalar'])} dosya kaydedildi")
    print(f"\nDosyalar: {hedef}{os.sep}")
    print(f"Rapor için: python mukayese.py rapor --rfq {rfq_no}")


def komut_rapor(args) -> None:
    rfq_no = _rfq_sec(args.rfq)
    kayit = _kayit_oku(rfq_no)
    kalemler = [veri.TalepKalemi(**k) for k in kayit["kalemler"]]

    klasor = args.klasor or os.path.join(GELEN_KLASORU, rfq_no)
    dosyalar = sorted(
        glob.glob(os.path.join(klasor, "*.xlsx"))
        + glob.glob(os.path.join(klasor, "*.xlsm"))
    )
    if not dosyalar:
        print(f"'{klasor}' içinde teklif formu (.xlsx) yok. Önce 'topla' çalıştırın")
        print("veya elle kaydettiğiniz formları bu klasöre koyun.")
        return

    teklifler = []
    for dosya in dosyalar:
        try:
            teklif = form.teklif_formu_oku(dosya)
        except Exception as hata:
            print(f"  Atlandı: {os.path.basename(dosya)} ({hata})")
            continue
        if not teklif.kalemler:
            print(f"  Atlandı: {os.path.basename(dosya)} (fiyat girilmemiş)")
            continue
        if not teklif.tedarikci:
            teklif.tedarikci = os.path.splitext(os.path.basename(dosya))[0]
        teklifler.append(teklif)
        print(f"  Okundu: {teklif.tedarikci} — {len(teklif.kalemler)} kalem fiyatı")

    if not teklifler:
        print("Okunabilir teklif bulunamadı.")
        return

    cikti = args.cikti or f"mukayese_{rfq_no}.xlsx"
    rapor.mukayese_raporu(cikti, rfq_no, kalemler, teklifler)
    print(f"\nMukayese raporu hazır: {cikti}")


def _menu() -> None:
    print("=" * 56)
    print("  SATINALMA TEKLİF TOPLAMA VE MUKAYESE PROGRAMI")
    print("=" * 56)
    print("""
 1) Örnek Excel şablonlarını oluştur
 2) Tedarikçilere teklif e-postası gönder
 3) Outlook'tan gelen cevapları topla
 4) Mukayese raporu oluştur
 0) Çıkış
""")
    secim = input("Seçiminiz: ").strip()
    if secim == "1":
        komut_ornek(argparse.Namespace())
    elif secim == "2":
        talep = input("Talep dosyası [ornek_talep.xlsx]: ").strip() or "ornek_talep.xlsx"
        ted = input("Tedarikçi listesi [ornek_tedarikciler.xlsx]: ").strip() or "ornek_tedarikciler.xlsx"
        taslak = input("Önce taslak olarak mı kaydedilsin? (E/h): ").strip().lower() != "h"
        son = input("Son cevap tarihi (örn. 13.06.2026, boş geçilebilir): ").strip()
        komut_gonder(argparse.Namespace(talep=talep, tedarikciler=ted, taslak=taslak, son_cevap=son))
    elif secim == "3":
        rfq = input(f"Teklif no [{_son_rfq_no() or '-'}]: ").strip() or None
        komut_topla(argparse.Namespace(rfq=rfq))
    elif secim == "4":
        rfq = input(f"Teklif no [{_son_rfq_no() or '-'}]: ").strip() or None
        komut_rapor(argparse.Namespace(rfq=rfq, klasor=None, cikti=None))
    elif secim == "0":
        return
    else:
        print("Geçersiz seçim.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    alt = parser.add_subparsers(dest="komut")

    alt.add_parser("ornek", help="Örnek Excel şablonlarını oluştur")

    p_gonder = alt.add_parser("gonder", help="Tedarikçilere teklif e-postası gönder")
    p_gonder.add_argument("--talep", required=True, help="Talep Excel dosyası")
    p_gonder.add_argument("--tedarikciler", required=True, help="Onaylı tedarikçi listesi Excel dosyası")
    p_gonder.add_argument("--taslak", action="store_true", help="Göndermek yerine Outlook taslağı oluştur")
    p_gonder.add_argument("--son-cevap", default="", help="Tedarikçiden istenen son cevap tarihi")

    p_topla = alt.add_parser("topla", help="Outlook'tan cevapları topla")
    p_topla.add_argument("--rfq", help="Teklif no (boşsa en son tur)")

    p_rapor = alt.add_parser("rapor", help="Mukayese raporu oluştur")
    p_rapor.add_argument("--rfq", help="Teklif no (boşsa en son tur)")
    p_rapor.add_argument("--klasor", help="Teklif formlarının bulunduğu klasör")
    p_rapor.add_argument("--cikti", help="Rapor dosya adı")

    args = parser.parse_args()
    if args.komut is None:
        _menu()
    else:
        {"ornek": komut_ornek, "gonder": komut_gonder, "topla": komut_topla, "rapor": komut_rapor}[args.komut](args)


if __name__ == "__main__":
    main()
