# LogiSim-DSS | Proje Kontrol ve Teknik Doğrulama Raporu

Bu döküman, projenin akademik gereksinimleri (Tez kriterleri) ile kaynak kodun (Implementation) tam uyumunu belgelemektedir.

---

## 1. Teknik Doğrulama (Kod Eşleşmeleri)

### 1.1. Operasyonel Maliyet Modeli ($C_{total}$)
- **Kod Konumu:** `SimulationEngine.java` (Satır 1134-1144)
- **Kapsam:** Yakıt maliyeti, işçilik, zaman maliyeti, durak cezaları ve stoksuz kalma cezaları dinamik olarak hesaplanır.
- **Katsayılar:** `DispatchConstants.java` içinde Elektrik (0.10), Dizel (0.18) ve Benzin (0.26) olarak set edilmiştir.

### 1.2. Karar Verme Puanlaması (Net Score)
- **Kod Konumu:** `SimulationEngine.java` (Satır 1164)
- **Mantık:** `netScore = planBenefit + trafficEscapeBonus - operationalCost`. Bir operasyonun başlatılması için bu değerin pozitif olması veya maksimum bekleme süresinin dolması gerekir.

### 1.3. Dinamik Sapma Toleransı (Multi-Stop Deviation)
- **Kod Konumu:** `SimulationEngine.java` (Satır 1271-1275)
- **Formül:** `(Tur_Mesafesi * 0.35) + (Kargo_Değeri / 30.0) + 25.0`
- **İspat:** Kodda `MULTI_STOP_DEVIATION_RATIO`, `CARGO_VALUE_DIVISOR` ve `BASE_BYPASS_KM` sabitleri üzerinden hesaplama yapılmaktadır.

---

## 2. Sistem Çalışma Mantığı

### 2.1. Simülasyon Döngüsü (Tick-Based)
Sistem gerçek zamanlı bir saat mekanizması (`Tick`) ile çalışır.
1.  **Sipariş Akışı:** `OrderGenerator` her tick'te depoların stok durumunu bozar.
2.  **Envanter Zekası (s,S):** Stoklar kritik eşik (`s`) altına indiğinde sistem otomatik ikmal planı hazırlar.
3.  **Optimizasyon:** Birden fazla deponun talebi varsa, sistem bunları en verimli (en kısa yol ve en düşük maliyet) rotada birleştirir.

### 2.2. Akıllı Trafik Modeli
- **Pik Saatler:** Sabah (08:00-10:00) ve Akşam (17:00-19:00) saatlerinde trafik katsayısı otomatik artar.
- **Dinamik Rota:** Dijkstra algoritması, yoğun yolların maliyetini yüksek hesaplayarak araçları alternatif (arka sokak/açık yol) rotalara yönlendirir.

### 2.3. Hibrit Kontrol (Otonom & Manuel)
- **Otonom Araçlar (V1-V6):** Belirlenen matematiksel kurallar ve doluluk oranlarına göre hareket eder.
- **Manuel Araç (V7 - Kare):** Kullanıcının "İkmal Görevi" emriyle anında hareket eder, doluluk kuralına takılmaz. Jürinin sistemi test etmesi için özel olarak tasarlanmıştır.

---

## 3. KPI ve Sürdürülebilirlik İzleme
Dashboard üzerinde canlı olarak takip edilen metriklerin hesaplanma mantığı:
- **Net Tasarruf:** Multi-stop rotaların, klasik tekil taşımacılığa göre sağladığı finansal kazanç.
- **Kurtarılan CO2:** Elektrikli araçların (Electric Fuel Type), standart dizel araçlara göre önlediği karbon salınımı (Dizel baseline: 0.8 kg/km).
- **Utilization:** Filodaki araçların boş beklemek yerine ne kadar süre aktif taşıma yaptığı.

---

## 4. Sonuç
Proje, **finalraporu.md** dosyasında iddia edilen tüm lojistik optimizasyon modellerini, backend tarafındaki Java algoritmalarıyla %100 oranında gerçekleştirmektedir.
