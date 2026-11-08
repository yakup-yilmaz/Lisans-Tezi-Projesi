# ✅ LogiSim-DSS: Problem Çözümleri ve Teknik Uygulama Raporu (`PROBLEM_COZUMLERI.md`)

Bu doküman, [HATA_DUZELTME_PLANI.md](HATA_DUZELTME_PLANI.md) kapsamında yapılan tüm düzeltmelerin **hangi dosyada, hangi satırlarda ve nasıl çözüldüğünü** detaylı olarak kayıt altına almaktadır.

---

## 📦 FAZ 1: Backend Simülasyon Çekirdeği, Rotalama ve Karar Motoru Çözümleri (TAMAMLANDI & DOĞRULANDI ✅)

### 🧪 Test Doğrulama Durumu
* **Çalıştırılan Test Sayısı:** 59
* **Başarısız (Failures):** 0
* **Hata (Errors):** 0
* **Atlanan (Skipped):** 0
* **Maven Durumu:** `BUILD SUCCESS` (59/59 test başarıyla geçti)

---

### 🛠️ Çözüm #1: Route Cache Anahtarına Araç Profilinin (`RoutingProfile`) Eklenmesi ve Önbellek Senkronizasyonu
* **İlgili Dosyalar:**
  * `RoutingService.java:91-93` ve `satır 251-257`
  * `OrderGenerator.java:21-52`
  * `SimApp.java:98-110`
  * `GraphController.java:110-155`
  * `SimulationEngine.java:2882-2884`
* **Problem Neydi?**
  `RoutingService.buildCacheKey` yalnızca `sourceNodeId + "|" + targetNodeId + "|" + generation` üretiyordu. Aynı tick içinde önce hafif/elektrikli araç (`costPerKm = 2.40`, gişe çarpanı `1.0`) bir rota sorguladığında sonuç önbelleğe yazılıyor, hemen ardından aynı kaynak-hedef için sorgu yapan ağır vasıta (`costPerKm = 8.55`, gişe çarpanı `1.35`) önbellekten hafif aracın rotasını alıyordu. Ayrıca `OrderGenerator` kendi izole `new RoutingService()` nesnesini kullandığı için trafik değişimlerinden haberdar olmuyordu.
* **Nasıl Çözüldü?**
  1. `RoutingService.buildCacheKey` metoduna `RoutingProfile` parametreleri (`distanceCostMultiplier`, `tollPenalty`, `tollWeightMultiplier`, `fuelCostMultiplier`) eklendi:
     ```java
     private String buildCacheKey(String sourceNodeId, String targetNodeId, long generation, RoutingProfile routingProfile) {
       return sourceNodeId + "|" + targetNodeId + "|" + generation + "|"
           + routingProfile.distanceCostMultiplier() + "|"
           + routingProfile.tollPenalty() + "|"
           + routingProfile.tollWeightMultiplier() + "|"
           + routingProfile.fuelCostMultiplier();
     }
     ```
  2. `OrderGenerator` sınıfına paylaşılan `RoutingService` bean'ini alan constructor eklendi ve `SimApp.java` içinde merkezi `RoutingService` enjekte edildi.
  3. `GraphController` içindeki `upsertEdge`, `updateTraffic`, `updateToll` uç noktalarına ve `SimulationEngine.restoreInitialState` (`reset`) metoduna `routingService.invalidateRouteCache()` çağrısı eklendi.

---

### 🛠️ Çözüm #2: Yoldaki Stokların (`incomingQuantity` ve `activeReplenishmentQuantity`) Çift Sayımının Kaldırılması
* **İlgili Dosya:** `SimulationEngine.java:546-556, 667-672`
* **Problem Neydi?**
  Hem `replenishmentService.getIncomingQuantity` hem de `activeReplenishmentQuantityForProduct` durumu `COMPLETED` olmayan açık talepleri topluyordu. `computeDemandDrivenRequestQuantity` içinde `incomingQuantity + activeReplenishmentQuantity` yapıldığı için aynı açık ikmal siparişi **2 kez** düşülüyor ve eksik ikmal siparişi (`under-ordering`) oluşuyordu.
* **Nasıl Çözüldü?**
  Açık ikmal miktarları toplanmak yerine `int totalPendingStock = Math.max(incomingQuantity, activeReplenishmentQuantity);` ile tekilleştirildi:
  ```java
  int totalPendingStock = Math.max(incomingQuantity, activeReplenishmentQuantity);
  int effectiveGap = Math.max(0, targetLevel - (currentStock + totalPendingStock));
  ```
  Böylece `s = 40`, `S = 80` sabit eşikleri korunarak mikro depoya tam olarak eksik kalan miktar (`80 - (mevcut + yoldaki)`) kadar sipariş açılması sağlandı.

---

### 🛠️ Çözüm #3: `(s, S)` Stok Eşikleri (`40 / 80`) — Karar Doğrultusunda Korundu
* **Açıklama:** Kararımız doğrultusunda `reorderPoint = 40` (`AUTO_REPLENISH_TRIGGER`) ve `targetLevel = 80` (`AUTO_REPLENISH_TARGET`) sabit eşiklerine dokunulmadı, mevcut çalışma mantığı birebir korundu.

---

### 🛠️ Çözüm #4, #5 ve #6: SLA Onay Matrisi (`180 dk` / `300 dk` Ayrımı), İlk Yükten İtibaren Süre Sayımı ve Onayda Konsolidasyon Devamı
* **İlgili Dosya:** `SimulationEngine.java:750-760, 850-985, 1040-1050, 1545-1615, 2800-2810`
* **Problem Neydi?**
  1. `DevDataSeeder`'da araçlara `maxWaitTicks = 180` verildiği için, `< %50` doluluktaki araçlarda `if (maxWaitTimerElapsed)` koşulu 180. dakikada `true` oluyor ve altındaki `else if (overdueTicks > 0L)` (300 dk Grace Period + `Aging >= 600 / 800 / 1000` + `< %10` Finansal Zarar Uyarısı) bloğuna hiç girmiyordu.
  2. `< %50` dolulukta süre ilk yükten itibaren (`maxRequestWaitTicks`) sayılırken, `%50 - %74` arasındaki 180 dakikalık sayaçta ve onay dispatch işlemlerinde aracın hiç yük yokken ana depoda boş beklediği süre (`computeWaitedTicks`) sayılıyordu.
  3. Onay bekleyen (`WAITING_FOR_APPROVAL`) bir araca sonradan yeni uygun yük geldiğinde araç bu yükü üzerine ekleyemiyordu ve `< %50` SLA onayında `approvalWindow = Math.max(5L, 180L - waitedTicks) = 5` dk olduğu için 5 dakikada bir iptal döngüsüne giriyordu.
* **Nasıl Çözüldü?**
  1. **300 dk Grace Period & Aging (600/800/1000) Ayrımı:** `< %50` doluluk (`!minimumLoadReached`) bloğundaki `if (maxWaitTimerElapsed)` engeli kaldırılarak doğrudan `if (overdueTicks > 0L)` bloğunun çalışması sağlandı. Artık `< %50` doluluklu araçlar ilk 300 dk (5 saat) konsolidasyon bekler, ardından karesel aging puanı `600` (`%20-%49`), `800` (`%10-%19`) ve `1000` (`< %10` + Finansal Zarar Uyarısı) olduğunda operatör onayına düşer.
  2. **İlk Yükten İtibaren Sayma (`computePlanRequestWaitTicks`):** Tek bir merkezi yardımcı metod (`computePlanRequestWaitTicks`) tanımlandı. Hem `%50 - %74` hem de `< %50` için yük bekleme süresi araca atanan **ilk (en eski) yükten itibaren** sayılır. Sonradan yeni yük eklendiğinde `.max()` sayesinde sayaç sıfırlanmaz, ilk yükün süresinden devam eder. (Aracın boş bekleme süresi `vehicleIdleTicks` ise yalnızca 10 puan tavanlı Best-Fit `agingDiscount` hesabında tutuldu).
  3. **Onay Beklerken Konsolidasyon ve Timeout Koruması:** `updateStagedPlanScores` içinde onay bekleyen araçların yeni gelen uygun talepleri de planlarına ekleyebilmesi (ve doluluk `%75`'e ulaşırsa anında otonom çıkabilmesi) sağlandı; `< %50` SLA onay penceresi 5 dk yerine `240 dk` yapıldı.

---

### 🛠️ Çözüm #7: `consolidateTowardsMinimumVolume` İçinde Manuel (`SQUARE-MANUAL-1`) ve Otonom Araç Ayrımı
* **İlgili Dosya:** `SimulationEngine.java:1224`
* **Problem Neydi?**
  `chooseBestPlanForVehicle` başında `vehicle.isDedicatedManual() == request.isManual()` filtresi varken, `consolidateTowardsMinimumVolume` metodunda bu filtre unutulduğu için `%50` altındaki otonom araçlar manuel talepleri (`isManual = true`) kendi planlarına katabiliyordu.
* **Nasıl Çözüldü?**
  `consolidateTowardsMinimumVolume` aday filtreleme akışına `.filter(request -> vehicle.isDedicatedManual() == request.isManual())` eklendi.

---

### 🛠️ Çözüm #8 ve #9: Anti-Starvation (`claimedRequestIds`) ve Bekleyen Yüklerde Best-Fit Sıralaması
* **İlgili Dosya:** `SimulationEngine.java:799-905`
* **Problem Neydi?**
  1. `selectBestActionableVehiclePlan` içinde bir aracın ilk seçtiği talep henüz çıkacak dolulukta değilse, arkadaki hazır koridor talebine geçemiyordu.
  2. Konsolidasyon bekleyen yüklerin dağıtıldığı döngü (`claimedRequestIds`) araçları Best-Fit maliyetine göre değil, `vehicles` listesindeki sabit sıraya göre geziyordu.
* **Nasıl Çözüldü?**
  1. `evaluateActionableVehiclePlan` eklendi; bir araç için ilk aday talep henüz çıkmaya hazır değilse, o talebi atlayıp kalan koridorlarda hemen çıkabilecek (`readyToDispatch` / `awaitingApproval`) bir yük olup olmadığını kontrol eder.
  2. Bekleyen yüklerin dağıtıldığı `claimedRequestIds` döngüsü de her adımda en düşük `selectionCost()` (en iyi Best-Fit hacim uyumu) değerine sahip aracı seçecek şekilde güncellendi. (Kararımız doğrultusunda kilo/ağırlık eklenmedi, tamamen hacim bazlı Best-Fit korundu).

---

### 🛠️ Çözüm #10: Çoklu Durak (Multi-Stop TSP) Sıralamasında Ana Depoya Dönüş Yolu ve Aynı Depo Birleştirmesi
* **İlgili Dosya:** `SimulationEngine.java:1281, 1514-1520, 2339-2368`
* **Problem Neydi?**
  1. TSP permütasyonları (`generatePermutations`) arasından en kısa sıra seçilirken son duraktan `MAIN` ana depoya dönüş maliyeti toplama dahil edilmiyordu.
  2. Aynı depoya ait parçalı taleplerde kayan nokta (`1e-6`) farkı veya çoklu parçalı duraklarda permütasyon patlaması riski vardı.
* **Nasıl Çözüldü?**
  1. TSP sıra maliyeti hesabına `currentSequenceCost += scoreToTarget(vehicle, tempNode, mainDepot.getNodeId());` satırı eklenerek gidiş+dönüş tam turu en kısa olan durak sırasının seçilmesi sağlandı.
  2. `mergeRequestsByDepot` içinde `depotVolume <= vehicle.getCapacityVolume() + 1e-6` toleransı ve `generatePermutations` içinde güvenli sınır (`size <= 7`) eklendi. (`%35` sapmanın mevcut kabul edilmiş `M2+M3` rotasına göre çalışması mantığı korundu).
