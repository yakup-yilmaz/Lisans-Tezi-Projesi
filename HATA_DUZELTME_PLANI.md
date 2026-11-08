# 🛠️ LogiSim-DSS: Kapsamlı Hata Düzeltme Planı ve Öncelik Yol Haritası

> [!IMPORTANT]
> Bu doküman, projede tespit edilen **27 bulguyu** birbirine olan bağımlılıklarına ve kritiklik derecesine göre **çözüm sırasıyla (Adım #1 → Adım #27)** listelemektedir.
> Hiçbir kod dosyası henüz değiştirilmemiştir. Sen **"Tercih Sorularını"** ve **"Adım/Faz numaralarını"** onayladıkça sırayla düzeltmeleri uygulayacağız.

---

## 🎯 Bölüm 1: Başlamadan Önce 3 Mimari Tercih Sorusu ve Önerilerimiz

Düzeltmelere geçmeden önce aşağıdaki 3 tasarım kararında senin için en uygun **Önerilen** yaklaşımları belirledik:

### Tercih 1: Yol ve Gişe Mesafeleri (`DISTANCE_SCALE = 8.0`)
* **🔴 Şu An Ne Oluyor?**
  [README.md](README.md) tablosunda Kuzey Otoyolu `3.0 km`, Boğaz Köprüsü `2.8 km` yazarken, `DevDataSeeder.java:30` içinde `DISTANCE_SCALE = 8.0` sabiti ile tüm kenarlar 8 kat büyütülerek (`24.0 km`, `22.4 km`) grafa ekleniyor.
* **💡 Önerimiz (ÖNERİLEN — Seçenek A):**
  **Kodda `DISTANCE_SCALE = 8.0` korunmalı.** Çünkü simülasyonda `1 tick = 1 dakika` ve araç hızı `35 km/s` (`~0.58 km/dk`). Eğer yollar `1.5 - 3.0 km` olursa araçlar tüm şehri 2-3 tick içinde geçer, haritada LERP animasyonu izlenemez ve yakıt maliyeti gişe ücretinin yanında önemsiz kalır. Kodu bozmak yerine `README.md` içindeki tabloya simülasyon ölçek çarpanı (`×8.0 ölçekli mesafe`) notunu düşmek en sağlıklı çözümdür.

---

### Tercih 2: Canlı Simülasyon Hızı (`stepTicks(5)` vs `stepTicks(1)`)
* **🔴 Şu An Ne Oluyor?**
  `SimulationEngine.executeRunLoop` konsola `"1 dakika = 1000ms"` yazmasına rağmen her saniye `stepTicks(5)` çağırıyor. Yani "Başlat" butonuna basıldığında simülasyon saniyede 1 dakika yerine **5 dakika birden sıçrayarak** ilerliyor; bu da haritadaki araçların ara düğümleri atlayarak ışınlanmasına yol açıyor.
* **💡 Önerimiz (ÖNERİLEN — Seçenek A):**
  **Canlı döngüde her saniye `stepTicks(1)` (1 tick = 1 dakika) ilerlemeli.** Böylece canlı haritada araçlar her düğümü ve eğriyi 60 FPS LERP animasyonuyla pürüzsüz geçer. SLA (3 saat / 5 saat) testlerini hızlandırmak istediğinde ise üst bardaki **"+10 Tick"** ve **"+60 Tick"** butonlarıyla zamanı tek tıkla ileri sarabilirsin.

---

### Tercih 3: `(s, S)` Stok Eşikleri (`40 / 80`) — ✅ KARAR VERİLDİ
* **📌 Karar:** `reorderPoint = 40` ve `targetLevel = 80` sabit eşikleri **hiç bozulmayacak**, olduğu gibi korunacak. Dinamik baseline hesaplamasına dokunulmayacak.

---

## 🛠️ Bölüm 2: Çözüm Sırasına Göre Tüm Hatalar (Şu An Ne Oluyor? → Düzeltince Ne Olacak?)

Düzeltmeleri **4 Faz** halinde, en kritik çekirdek hatalardan başlayarak sıraladık:

---

### 📦 FAZ 1: Backend Simülasyon Çekirdeği, Rotalama ve Karar Motoru (Adım #1 – #10) [TAMAMLANDI & DOĞRULANDI ✅ - 59/59 Test Başarılı]

#### Öncelik #1 [🔴 KRİTİK] — Route Cache Anahtarına Araç Profilinin (`RoutingProfile`) Eklenmesi
* **Dosya:** `RoutingService.java:75-114, 240-253`, `GraphController.java:92-150`, `OrderGenerator.java:23`
* **🔴 Şu An Ne Oluyor?**
  Önbellek anahtarı yalnızca `source|target|trafficGeneration` tutuyor. Aynı tick içinde önce elektrikli küçük araç (`2.40 ₺/km`, gişe çarpanı `1.0`) rota hesaplarsa, arkasından gelen ağır tır (`8.55 ₺/km`, gişe çarpanı `1.35`) kendi maliyeti yerine elektrikli aracın önbellekteki rotasını kullanıyor. Ayrıca gişe ücreti güncellendiğinde (`PUT /api/graph/edges/toll`) ve `OrderGenerator` içinde önbellek hiç temizlenmiyor.
* **🟢 Düzeltince Ne Olacak?**
  Önbellek anahtarına `RoutingProfile` (`distanceCostMultiplier`, `tollPenalty`, `tollWeightMultiplier`) dahil edilecek; her araç tipi kendi maliyet ve ağır vasıta çarpanına göre gerçek en ucuz rotayı bulacak. Gişe/kenar güncellemelerinde ve `reset()` işleminde önbellek anında geçersiz kılınacak; `OrderGenerator` paylaşılan `RoutingService` bean'ini kullanacak.

---

#### Öncelik #2 [🔴 KRİTİK] — Yoldaki Stokların (`incomingQuantity` + `activeReplenishmentQuantity`) Çift Sayımının Kaldırılması
* **Dosya:** `SimulationEngine.java:549-558, 669-671, 703-711`, `ReplenishmentService.java:69-77`
* **🔴 Şu An Ne Oluyor?**
  Hem `replenishmentService.getIncomingQuantity` hem de `activeReplenishmentQuantityForProduct` durumu `COMPLETED` olmayan açık ikmal taleplerini sayıyor ve `computeDemandDrivenRequestQuantity` bu iki değeri birbiriyle topluyor (`incomingQuantity + activeReplenishmentQuantity`). Yolda 20 adet ürün varken sistem 40 adet var sanıp eksik ikmal siparişi (`under-ordering`) çıkarıyor.
* **🟢 Düzeltince Ne Olacak?**
  Açık/yoldaki ikmal miktarı tekilleştirilerek yalnızca **1 kez** düşülecek (`Talep = max(0, S - (Mevcut Stok + Yoldaki Stok))`). Mikro depolar tam olarak hedef stok seviyesine ($S$) tamamlanacak.

---

#### Öncelik #3 [⏭️ ATLANDI / SABİT KORUNACAK] — `(s, S)` Stok Eşikleri (`40 / 80`) Sabit Bırakılacak
* **Karar:** Kullanıcı tercihi doğrultusunda `s = 40` ve `S = 80` eşiklerine dokunulmayacak, mevcut haliyle korunacak.

---

#### Öncelik #4 [🔴 KRİTİK] — SLA Onay Matrisinde `180 dk` / `300 dk` Çakışmasının ve Finansal Zarar Uyarısının Düzeltilmesi
* **Dosya:** `SimulationEngine.java:883-933`
* **🔴 Şu An Ne Oluyor?**
  Araçlarda `maxWaitTicks = 180` tanımlı olduğu için `if (maxWaitTimerElapsed)` koşulu 180. dakikada `true` oluyor ve altındaki `else if (overdueTicks > 0L)` (300 dk Grace Period, `Aging >= 600 / 800 / 1000` ve `< %10` dolulukta `isFinancialWarning = true`) bloğuna **hiçbir zaman girilmiyor**.
* **🟢 Düzeltince Ne Olacak?**
  `%50` altındaki düşük doluluklu araçlarda `180 dk` kuralı yerine `README.md` tablosundaki **300 dakikalık (5 saat) Grace Period** ve karesel tırmanan `delayedAgingScore` (`600`, `800`, `1000`) eşikleri devreye girecek; `< %10` kritik düşük yüklerde **Finansal Zarar Uyarısı (`FINANCIAL_DISPATCH_ALERT`)** sorunsuz tetiklenecek.

---

#### Öncelik #5 [🔴 KRİTİK] — `%50 - %74` ve `< %50` Bekleme Sürelerinin İlk Yük Atanmasından İtibaren Sayılması ve Yeni Yük Eklenince Sıfırlanmadan Devam Etmesi
* **Dosya:** `SimulationEngine.java:873-974, 1503-1533, 1626-1657`
* **🔴 Şu An Ne Oluyor?**
  `< %50` dolulukta zaten `maxRequestWaitTicks` (araca düşen ilk yükten itibaren geçen süre) kullanılıyor. Ama `%50 - %74` arasındaki 180 dakikalık sayaçta (`waitedTicks`) kod yanlışlıkla aracın hiç yük yokken ana depoda boş beklediği süreyi de sayıyor. 3 saat boş bekleyen bir araca `%50` dolulukta ilk yük düştüğü saniyede 180 dakika dolmuş sanıp 0 dakika konsolidasyonla çıkıyor.
* **🟢 Düzeltince Ne Olacak?**
  `%50 - %74` arasındaki 180 dakikalık sayaç da **ilk yükün araca atanmasından/oluşmasından itibaren (`maxRequestWaitTicks`)** sayacak şekilde eşitlenecek; böylece her iki durumda da (`< %50` ve `%50 - %74`) süre **ilk yük araca düştüğü andan itibaren** başlayacak ve sonradan yeni yükler eklendikçe sayaç sıfırlanmadan ilk yükün süresinden devam edecek.

---

#### Öncelik #6 [🔴 KRİTİK] — `%50 - %74` Bandında 90. Dakika Erken Onay Eşiği, Konsolidasyona Devam Edebilme ve 5 Dakikalık İptal Döngüsünün Çözümü
* **Dosya:** `SimulationEngine.java:961-962, 985-987, 1626-1657, 2708-2759`
* **🔴 Şu An Ne Oluyor?**
  1. `%50-%74` doluluktaki araç 90. dakika yerine **0. dakikada** `WAITING_FOR_APPROVAL` durumuna geçiyor.
  2. `WAITING_FOR_APPROVAL` durumuna geçen araç yeni gelen siparişleri üzerine ekleyemiyor (konsolidasyon kilitleniyor).
  3. `< %50` doluluklu araç onaya düştüğünde `approvalWindow = 5` dk oluyor; 5 dk içinde onaylanmazsa iptal edilip tekrar onaya düşerek sonsuz döngüye giriyor.
* **🟢 Düzeltince Ne Olacak?**
  `%50-%74` dolulukta erken onay `90. dakikada` (`waitedTicks >= 90`) açılacak, araç otonom çıkışa kadar yeni yüklerle konsolide olmaya devam edebilecek (%75'e ulaşırsa anında otonom çıkacak) ve SLA onayı bekleyen araçlar 5 dakikada bir sıfırlanmayacak.

---

#### Öncelik #7 [🔴 KRİTİK] — `consolidateTowardsMinimumVolume` İçinde Manuel (`SQUARE-MANUAL-1`) ve Otonom Araç Ayrımının Sağlanması
* **Dosya:** `SimulationEngine.java:1028, 1109, 1178-1221`
* **🔴 Şu An Ne Oluyor?**
  `consolidateTowardsMinimumVolume` metoduna filtrelenmiş `eligible` liste yerine tüm `pending` listesi verildiği için, `%50` altındaki normal otonom bir araç `SQUARE-MANUAL-1` için oluşturulmuş manuel talepleri (`isManual = true`) kendi planına katıyor ve eşikleri bypass ederek kalkıyor.
* **🟢 Düzeltince Ne Olacak?**
  `consolidateTowardsMinimumVolume` içine `vehicle.isDedicatedManual() == request.isManual()` filtresi eklenecek; manuel talepler yalnızca `SQUARE-MANUAL-1` aracına, otonom talepler yalnızca otonom filoya atanacak.

---

#### Öncelik #8 [🔴 KRİTİK] — Anti-Starvation (`claimedRequestIds`) Mekanizmasının Gerçek Sevk Döngüsüne ve Best-Fit Sırasına Entegre Edilmesi
* **Dosya:** `SimulationEngine.java:780-828, 853-866`
* **🔴 Şu An Ne Oluyor?**
  Gerçek sevk döngüsünde (`selectBestActionableVehiclePlan`) `claimedRequestIds` kullanılmıyor; bekleyen araçlar döngüsünde ise araçlar Best-Fit sırasına göre değil `vehicles` listesindeki sabit sıraya göre gezilerek koridor sahipleniliyor.
* **🟢 Düzeltince Ne Olacak?**
  Bir koridordaki yük konsolidasyon beklerken o koridoru en uygun (Best-Fit) araç sahiplenecek (`claimedRequestIds`), diğer boş araçlar ise bloke olmadan diğer koridorlardaki (örn. M2/M3) hazır yükleri paralel olarak sevk edebilecek.

---

#### Öncelik #9 [✅ KARAR VERİLDİ: KİLO EKLENMEYECEK — SADECE BEST-FIT KONTROLÜ] — Hacim Bazlı Best-Fit Atamasının Bekleyen Yüklerde de Doğrulanması
* **Dosya:** `SimulationEngine.java:801-827, 853-866, 1447-1454`
* **📌 Karar:** Araçlara ağırlık (kilo) kapasitesi **eklenmeyecek**, sadece mevcut hacim (`m³`) bazlı yapı korunacak.
* **🔴 Best-Fit Şu An Nasıl Çalışıyor?**
  * **Hemen Yola Çıkan Araçlarda (`selectBestActionableVehiclePlan`):** Best-Fit **doğru çalışıyor** (`wastePenalty = (Kapasite - Yük) * 0.25` ile küçük yüke küçük araç seçiliyor).
  * **Henüz Dolmamış / Konsolidasyon Bekleyen Araçlarda (`satır 801-827`):** Araçlar Best-Fit sırasına göre değil, `vehicles` listesindeki sabit sıraya göre (`EXPRESS-MASTER 50m³` -> `ECO-TRANSIT 35m³` -> `HEAVY-CARRIER 85m³`) geziliyor. Bu yüzden 10 m³ küçük bir yük geldiğinde `35 m³`'lük en küçük araç yerine listedeki ilk araç (`50 m³`) yükü sahipleniyor.
* **🟢 Düzeltince Ne Olacak?**
  Bekleyen yüklerin dağıtıldığı döngüde de araçlar kapasite/Best-Fit uyumuna göre sıralanacak; böylece küçük yükleri hem kalkışta hem de beklerken her zaman **en küçük uygun araç (Best-Fit)** alacak.

---

#### Öncelik #10 [🔴 KRİTİK] — Çoklu Durak (Multi-Stop) Sıralamasında Dönüş Yolu ve Aynı Depo Birleştirmesi
* **Dosya:** `SimulationEngine.java:1241-1254, 2233-2262`
* **📌 Karar:** `%35` sapma kontrolünün mevcut kabul edilmiş rotaya (`M2 + M3` birlikte değerlendirilerek bir sonraki durağın eklenmesi) göre çalışması lojistik açıdan doğru olduğu için **korunacak, değiştirilmeyecek**.
* **🔴 Şu An Ne Oluyor? (Kalan 2 Nokta):**
  1. **Dönüş Yolu Unutulmuş:** Araç 2-3 depoya uğrayacağı zaman *"Önce hangisine gideyim?"* diye hesaplarken son uğradığı depodan **Ana Depoya (`MAIN`) dönüş mesafesini** hesaba katmıyor.
  2. **Aynı Depoya Çoklu Manuel Siparişte Donma Riski:** Şehirde toplam 5 mikro depo (`M1-M5`) var; ancak aynı depoya birden fazla manuel sipariş girildiğinde kod bazen bunları tek durakta birleştirmeyip 10-12 ayrı durak gibi sıralamaya çalışıyor ($12! = 479\text{ milyon}$ ihtimal denediği için bilgisayar donabiliyor).
* **🟢 Düzeltince Ne Olacak?**
  1. En kısa durak sırası seçilirken son duraktan `MAIN` ana depoya dönüş yolu da hesaba katılacak.
  2. Aynı mikro depoya giden yükler tek durakta birleştirilecek (en fazla 5 durak = 120 ihtimal), böylece sistem hiçbir zaman donmayacak.

---

### 💰 FAZ 2: Finansal Metrikler, REST API, Doğrulama ve Eşzamanlılık (Adım #11 – #18)

#### Öncelik #11 [🔴 KRİTİK] — `RETURN` (Boş Dönüş) Seferlerinin Maliyet, Sefer Sayısı ve Kargo Değerini 2 Katına Çıkarmasının Engellenmesi
* **Dosya:** `SimulationEngine.java:1377-1380, 2031-2088`, `SimulationController.java:201-257`, `VehicleReportService.java:72-112`
* **🔴 Şu An Ne Oluyor?**
  `REPLENISHMENT` görevi zaten gidiş+dönüş maliyetini kaydediyor; ancak araç ana depoya dönerken oluşturulan `RETURN` görevi bittiğinde `recordTrip` ikinci kez çağrılıyor. Bu yüzden:
  * Her sevkiyat **2 sefer** sayılıyor,
  * Dönüş maliyeti **2 kez** toplanıyor,
  * `currentMissionCargoValue` sıfırlanmadığı için toplam kargo değeri **2 kat** raporlanıyor,
  * Boş dönüşler `stopCount = 0 (<= 1)` olduğu için **"Tek Duraklı Sefer"** sayılarak Çoklu Durak Tasarruf (ROI) yüzdesini bozuyor.
* **🟢 Düzeltince Ne Olacak?**
  Raporlamada yalnızca gerçek teslimat seferleri (`REPLENISHMENT` veya tekilleştirilmiş tur) sayılacak, dönüşte kargo değeri iki kez yazılmayacak ve `singleStopTrips` yalnızca `stopCount == 1` olan gerçek sevkiyatları sayacak. Aday plan denenirken `Vehicle` nesnesinin üzerindeki aktif görev alanları kirletilmeyecek.

---

#### Öncelik #12 [🔴 KRİTİK] — Gişe Maliyeti (`tollCost`), İşçilik (`totalLaborCost`), Ceza (`totalPenaltyCost`) ve CO2 Hesaplarının Düzeltilmesi
* **Dosya:** `SimulationEngine.java:1346, 1385-1391, 1951-1990, 2032-2033`, `SimulationController.java:215-230`
* **🔴 Şu An Ne Oluyor?**
  1. `tollCost = routeCost - fuelCost` yapıldığı için gişesiz yollarda bile trafik çarpanı (`trafficFactor > 1.0`) kaynaklı maliyet farkı "Gişe Ücreti" olarak raporlanıyor.
  2. `requestDelayPenalty`, `stockoutPenaltyForDepot` ve işçilik/zaman maliyeti metodları hiç çağrılmadığı için `totalLaborCost` ve `totalPenaltyCost` her zaman **`0.0 ₺`** dönüyor.
  3. CO2 tasarrufu yalnızca elektrikli araçları sayıyor, benzinli araçların dizel baz çizgisine (`0.8 kg/km`) göre `0.1 kg/km` tasarrufunu atlıyor.
* **🟢 Düzeltince Ne Olacak?**
  Gişe maliyeti doğrudan geçilen kenarların gerçek `tollCost * vehicleTollMultiplier` toplamından hesaplanacak; işçilik (`LABOR_RATE_PER_TICK = 0.55`, `TIME_COST_WEIGHT = 0.35`) ve gecikme/stoksuzluk cezaları (`DELAY_PENALTY_WEIGHT`, `STOCKOUT_PENALTY_WEIGHT`) `DeliveryPlan` ve özet raporuna dahil edilecek; Genel Bakış ekranındaki pasta grafiği 4 maliyet kalemini de gerçek verilerle gösterecek.

---

#### Öncelik #13 [🔴 KRİTİK] — `MetricsEventHandler` Filo Kullanım Oranı (`utilizationPerVehicle`) Çift Sayımının Kaldırılması
* **Dosya:** `MetricsEventHandler.java:57`
* **🔴 Şu An Ne Oluyor?**
  `SimulationEngine` her tick'te aktif araçlara `+1 tick` kullanım süresi eklerken, `MetricsEventHandler` da teslim edilen her müşteri siparişinin depoda beklediği toplam süreyi (`duration`) aracın çalışma süresine ekliyor ve filo kullanım yüzdesini şişiriyor.
* **🟢 Düzeltince Ne Olacak?**
  `MetricsEventHandler` içindeki mükerrer `utilizationPerVehicle` eklemesi kaldırılacak; araç kullanım oranı yalnızca gerçek aktif tick süresi üzerinden doğru hesaplanacak.

---

#### Öncelik #14 [🔴 KRİTİK] — Eşzamanlılık (`ConcurrentModificationException`) ve Kilit (`ReentrantLock`) Açıklarının Kapatılması
* **Dosya:** `VehicleMovementService.java:25, 75-76, 301`, `SimulationEngine.java:105-140, 1505, 2641, 2698`, `ReplenishmentService.java:11`, `CityGraph.java:24-25`, `InventoryService.java:243`
* **🔴 Şu An Ne Oluyor?**
  Simülasyon arka planda çalışırken Frontend saniyede 15 GET isteği attığında `assignmentsByVehicle` (`HashMap`), `segmentPlans` (`ArrayDeque`), `ReplenishmentService.requests` (`ArrayList`), `dispatchReadinessByVehicle` (`HashMap`) ve `approvalPendingPlansByVehicle` üzerinde kilitsiz iterasyon yapılıyor; bu durum `ConcurrentModificationException` (`500 Internal Server Error`) riski üretiyor. Ayrıca `InventoryService.consume` stok düşümünü atomik yapmıyor.
* **🟢 Düzeltince Ne Olacak?**
  Paylaşılan koleksiyonlar thread-safe yapılara (`ConcurrentHashMap`, `CopyOnWriteArrayList` / anlık snapshot kopyaları) dönüştürülecek, `updateStagedPlanScores` ve `approveDispatch` kilit kapsamına alınacak ve `InventoryService` stok güncellemeleri atomik (`compute`) hale getirilecek.

---

#### Öncelik #15 [🔴 KRİTİK] — Geçersiz `depotId`, `locationNodeId`, `productId` ve `consumeManually` Rezerve Stok Doğrulamaları
* **Dosya:** `InventoryController.java:50-69`, `ReplenishmentController.java:87-137`, `VehicleController.java:51-85`, `InventoryService.java:260-272`
* **🔴 Şu An Ne Oluyor?**
  1. Var olmayan bir `depotId` ile ikmal veya var olmayan bir `locationNodeId` ile araç eklendiğinde API `200 OK` dönüyor, ancak bir sonraki tick'te simülasyon motoru `NoSuchElementException` / `IllegalArgumentException` ile **çöküyor (`ERROR` fazı)**.
  2. `consumeManually` toplam stoğu kontrol ettiği için bekleyen siparişlerin rezerve stoğunu eziyor ve sipariş tamamlanırken motoru çökertiyor.
* **🟢 Düzeltince Ne Olacak?**
  Geçersiz `depotId`, `productId`, `locationNodeId` veya negatif `ticks` gönderildiğinde API anlamlı `400 Bad Request` (`BadRequestException`) dönecek; `consumeManually` yalnızca kullanılabilir (`getAvailableStock`) stoktan düşüm yapacak ve simülasyon motoru hiçbir hatalı girdide çökmeyecek.

---

#### Öncelik #16 [🟠 YÜKSEK] — `DomainToApiMapper.toReplenishmentDto` İçinde `awaitingApproval` Alanının Düzeltilmesi
* **Dosya:** `DomainToApiMapper.java:124`, `ReplenishmentController.java`
* **🔴 Şu An Ne Oluyor?**
  `request.getStatus().name().equals("CREATED") && request.getAssignedVehicleId() != null` kontrolü yapılıyor; oysa araç atandığında status `ASSIGNED` olduğu için bu ifade **her zaman `false`** dönüyor.
* **🟢 Düzeltince Ne Olacak?**
  Talebin atandığı aracın onay bekleyip beklemediği (`WAITING_FOR_APPROVAL` veya `dispatchReadiness` durumu) doğru kontrol edilerek `awaitingApproval` alanı gerçek durumu yansıtacak.

---

#### Öncelik #17 [🟠 YÜKSEK] — `GraphController` ve `SimulationController` Endpoint Uyumlulukları (`traffic`, `tolls`, `kpis`)
* **Dosya:** `GraphController.java:122-150`, `SimulationController.java`, `MetricsController.java:63-85`
* **🔴 Şu An Ne Oluyor?**
  `README.md`'de yazan `GET /api/graph/traffic` uç noktası hiç yok; `PUT /api/graph/traffic`, `PUT /api/graph/tolls` ve `GET /api/simulation/kpis` yolları ile kod/Frontend yolları arasında farklılıklar var.
* **🟢 Düzeltince Ne Olacak?**
  Hem Frontend'in kullandığı mevcut yollar (`/api/graph/edges/traffic`, `/api/graph/edges/toll`, `/api/metrics/kpis`) korunacak hem de `README.md`'de belgelenen alias uç noktaları (`GET /api/graph/traffic`, `PUT /api/graph/traffic`, `PUT /api/graph/tolls`, `GET /api/simulation/kpis`) eklenecek; böylece hem Frontend hem dokümantasyon %100 çalışır olacak.

---

#### Öncelik #18 [🟡 ORTA] — Ağır Vasıta Gişe Eşiğinin (`HEAVY_VEHICLE_CAPACITY_THRESHOLD`) Kamyon/Tırlarla (`>= 60 m³`) Sınırlandırılması
* **Dosya:** `SimulationEngine.java:75`, `FleetScreen.jsx:15`, `MapScreen.jsx:643`
* **🔴 Şu An Ne Oluyor?**
  Eşik `46.0 m³` olduğu için `50 m³` kamyonetler (`EXPRESS-MASTER`, `FLEX-DELIVERY`) de ağır vasıta ($\times 1.35$ gişe) sayılıyor; `MapScreen.jsx:643` ise `>= 25 m³` yazıyor.
* **🟢 Düzeltince Ne Olacak?**
  Backend ve Frontend genelinde ağır vasıta gişe çarpanı ($\times 1.35$) tutarlı hale getirilecek ve `MapScreen` açıklaması gerçek eşikle eşitlenecek.

---

### 🖥️ FAZ 3: Frontend (React, SVG Harita, LERP Motoru ve Operasyon Ekranları) (Adım #19 – #25)

#### Öncelik #19 [🔴 KRİTİK] — `ManuelControlPanel.jsx` Varsayılan Ürün Seçimi (`selectedProduct`) Hatasının Düzeltilmesi
* **Dosya:** `ManuelControlPanel.jsx:9-98`
* **🔴 Şu An Ne Oluyor?**
  `selectedProduct` boş string (`''`) ile başlıyor ama ekranda `P1 - Su Paketi` seçili gibi görünüyor. Kullanıcı dropdown'a dokunmadan butona bastığında stok `0 / 100` görünüyor, tüketimde **"Yetersiz stok!"** hatası veriyor, ikmalde ise boş `productId: ""` gönderip `400 Bad Request` alıyor. Ayrıca ikmal sonrası `loadAll()` çağrılmıyor.
* **🟢 Düzeltince Ne Olacak?**
  Ürün listesi yüklendiğinde otomatik olarak ilk ürün (`P1`) seçilecek, gerçek stok (`80 / 100`) anında görünecek, tek tıkla tüketim ve özel ikmal hatasız çalışacak ve işlem sonunda `loadAll()` ile ekran anında güncellenecek.

---

#### Öncelik #20 [🔴 KRİTİK] — `OverviewScreen` Haritasında Zoom/Sürükleme Yapıldığında Çökmenin (`setTransform is not a function`) Önlenmesi
* **Dosya:** `MapCanvas.jsx:163, 184-199`, `OverviewScreen.jsx:209-216`
* **🔴 Şu An Ne Oluyor?**
  `OverviewScreen` içindeki kompakt haritaya `setTransform` prop'u verilmediği için, kullanıcı harita üzerinde fare tekerleğini çevirdiği veya sürüklediği anda uygulama `Uncaught TypeError: setTransform is not a function` hatasıyla çöküyor.
* **🟢 Düzeltince Ne Olacak?**
  `MapCanvas` içinde dahili fallback transform state'i ve `setTransform?.(...)` koruması eklenecek; Genel Bakış haritasında da zoom/pan çökmeden pürüzsüz çalışacak.

---

#### Öncelik #21 [🔴 KRİTİK] — `OperationsScreen.jsx` İçinde `< %50` Doluluklu Araçlarda Onay Butonunun ve Finansal Zarar Uyarısının Gösterilmesi
* **Dosya:** `OperationsScreen.jsx:373, 390-485`
* **🔴 Şu An Ne Oluyor?**
  `const needsApproval = group.hasApprovalPending && group.loadFraction >= 0.5 && group.loadFraction < 0.75` koşulu yüzünden, doluluğu `%50`'nin altında olup 5 saat sonunda operatör onayına düşen (`%5`, `%15`, `%35` vb.) hiçbir araç için **"SEVKİYATI ONAYLA"** butonu çıkmıyor ve Finansal Zarar Uyarısı metni (`decisionReason`) gösterilmiyor.
* **🟢 Düzeltince Ne Olacak?**
  `group.hasApprovalPending` (veya `awaitingApproval`) olan tüm araçlar için doluluk oranına bakılmaksızın onay butonu aktif olacak; `< %10` dolulukta **Finansal Zarar Uyarısı** kırmızı uyarı kutusuyla operatöre gösterilecek.

---

#### Öncelik #22 [🟠 YÜKSEK] — `MapCanvas.jsx` Araçların Eğrisel Yol (Quadratic Bézier) Üzerinden Gitmesi ve Çift Yönlü Eğri Simetrisi
* **Dosya:** `MapCanvas.jsx:25-50, 559-635`, `TrafficHeatMap.jsx:31-51`
* **🔴 Şu An Ne Oluyor?**
  1. Yollar Quadratic Bézier eğrisi (`Q cx cy`) olarak çizilirken, araçların LERP koordinatları iki düğüm arasında düz çizgi üzerinde hesaplandığı için araçlar virajlı yollarda yolun dışından (boşluktan) gidiyor.
  2. `getEdgePath(A, B)` ile `getEdgePath(B, A)` farklı eğriler ürettiği için ters yönde giden aracın mavi rota çizgisi yolun dışına taşıyor.
* **🟢 Düzeltince Ne Olacak?**
  1. `getEdgePath` kanonik düğüm sırasına göre simetrik kontrol noktası `(cx, cy)` üretecek (`A -> B` ve `B -> A` tam olarak aynı yol eğrisini izleyecek).
  2. Araçların `(rawPosX, rawPosY)` koordinatı ve teğet açısı (`angle`) Quadratic Bézier denklemi $B(t) = (1-t)^2 P_0 + 2(1-t)t P_1 + t^2 P_2$ ile hesaplanacak; araçlar milimetrik olarak çizilen yolun tam üzerinden akacak.

---

#### Öncelik #23 [🟠 YÜKSEK] — `MapCanvas.jsx` LERP Geriye Sıçrama (500ms vs 1000ms), `RETURNING` Araçlar ve Boşta Render Optimizasyonu
* **Dosya:** `MapCanvas.jsx:166, 269, 363-377, 561, 621`, `App.jsx:24-25`
* **🔴 Şu An Ne Oluyor?**
  1. Polling 1000 ms iken LERP döngüsü 500 ms olduğu için araçlar her saniye ileri gidip 500. ms'de geriye sıçrayıp tekrar ileri gidiyor.
  2. `RETURNING` (ana depoya dönen) araçlar LERP animasyonuna dahil edilmiyor.
  3. Simülasyon duraklatılmışken bile `setInterval(10ms)` saniyede 100 kez tüm SVG haritasını yeniden render ediyor.
* **🟢 Düzeltince Ne Olacak?**
  LERP enterpolasyon süresi polling periyodu (1000 ms) ile tam senkronize olacak, `RETURNING` araçlar da pürüzsüz hareket edecek ve simülasyon durduğunda (`!running`) animasyon timer'ı durdurularak CPU kullanımı `%0`'a indirilecek.

---

#### Öncelik #24 [🟠 YÜKSEK] — `App.jsx` Polling Koruma Kilidi, Login Öncesi Polling ve Gereksiz 500ms Re-render Temizliği
* **Dosya:** `App.jsx:117, 161-303, 372-373`
* **🔴 Şu An Ne Oluyor?**
  1. Hiç kullanılmayan `currentTimeMs` state'i her 500 ms'de bir güncellenerek tüm uygulamayı saniyede 2 kez boş yere re-render ediyor.
  2. `loadAll` içinde eşzamanlı istek kilidi (`isFetchingRef`) yok, kullanıcı Login ekranındayken bile arka planda polling çalışıyor ve `handleReset` sonrasında bildirim hafızası (`seenOrderIds` vb.) sıfırlanmıyor.
* **🟢 Düzeltince Ne Olacak?**
  Kullanılmayan `currentTimeMs` kaldırılacak, polling yalnızca giriş yapıldıktan sonra ve önceki istek tamamlandığında çalışacak, `Reset` butonuna basıldığında tüm toast/bildirim geçmişi temizlenecek.

---

#### Öncelik #25 [🟡 ORTA] — Ekranlardaki Görsel ve Metrik Düzeltmeleri (`ScoreBreakdown`, Mikro Depo Kapasitesi, Lejant, Kategori Grafiği)
* **Dosya:** `MapScreen.jsx:10, 98, 441-475, 815`, `MapCanvas.jsx:679-711`, `OverviewScreen.jsx:12-40, 114-118`, `FleetScreen.jsx:232`
* **🔴 Şu An Ne Oluyor?**
  * `MapScreen` `ScoreBreakdown` bileşeni `planCost` yerine `routeCost` okuduğu için maliyet `0.0 ₺` görünüyor.
  * Mikro depo kapasitesi 10 ürün toplamı için `100 m³` sabit alındığından `%448` doluluk ve `-348.0 m³` boş alan gösteriyor.
  * Harita lejantındaki yazılar (`x="220"`) `width="220"` olan kutunun dışına taşıyor ve araç/düğümlerde hover `<title>` tooltip'i bulunmuyor.
  * `OverviewScreen` kategori çubukları `'FULFILLED_FROM_MICRO'` siparişlerini saymıyor ve `LOADING` araçları çift sayıyor.
  * `FleetScreen` altındaki Seans Operasyon Raporu tablosu `xl:grid-cols-2` yüzünden ekranın sol yarısına sıkışıyor.
* **🟢 Düzeltince Ne Olacak?**
  Tüm kartlar doğru DTO alanlarını (`planCost`, `awaitingApproval`, `FULFILLED_FROM_MICRO`) okuyacak, mikro depo kapasitesi ürün sayısı × maksimum stok hacmi üzerinden (`%0 - %100`, pozitif boş alan) hesaplanacak, lejant kutusu genişletilecek, araç/düğümlere hover tooltip eklenecek ve rapor tablosu tam genişlikte (`col-span-full`) ferah görünecek.

---

### 🧪 FAZ 4: Birim Testleri, Lint ve Build Doğrulaması (Adım #26 – #27)

#### Öncelik #26 [🟠 YÜKSEK] — Birim ve Entegrasyon Testlerinin Güçlendirilmesi (`src/test/java`)
* **Dosya:** `SimulationEngineTest.java`, `DynamicBaselineInventoryTest.java`, `RoutingServiceTest.java`
* **🔴 Şu An Ne Oluyor?**
  Bazı testler boş araç listesiyle ikmal oluşmadığını doğruluyor veya eşzamanlı stok düşümünde kalan net stoğu (`99000`) kontrol etmiyor.
* **🟢 Düzeltince Ne Olacak?**
  Tüm mevcut 55 test yeni düzeltmelerle birlikte yeşil geçecek, ayrıca Route Cache profil ayrımı, gerçek 100-tick Dinamik Baseline ve SLA onay akışı için doğrulamalar tam kapsamlı hale gelecek.

---

#### Öncelik #27 [🟢 DOĞRULAMA] — Uçtan Uca `mvn test`, `npm run lint` ve `npm run build` Kontrolü
* **🔴 Şu An Ne Oluyor?**
  Kod değişiklikleri sonrası regresyon olmaması garanti altına alınmalı.
* **🟢 Düzeltince Ne Olacak?**
  Backend (`mvn test`) ve Frontend (`npm run lint`, `npm run build`) **0 hata ve 0 uyarı** ile doğrulanmış olarak teslim edilecek.

