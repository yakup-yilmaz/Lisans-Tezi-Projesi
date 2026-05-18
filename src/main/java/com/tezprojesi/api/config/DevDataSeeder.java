package com.tezprojesi.api.config;

import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ProductCapability;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleCapability;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.simulation.InventoryService;
import com.tezprojesi.simulation.ProductCatalogService;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile({"default", "dev"})
public class DevDataSeeder {
  private static final double DISTANCE_SCALE = 8.0;

  @Bean
  @Order(1)
  public CommandLineRunner seedDemoData(
      CityGraph graph,
      List<Depot> depots,
      List<Vehicle> vehicles,
      VehicleRepository vehicleRepository,
      ProductCatalogService productCatalogService,
      InventoryService inventoryService,
      SimulationEngine simulationEngine) {
    return args -> {
      if (!graph.getNodes().isEmpty()) {
        return;
      }

      // ═══════════════════════════════════════════════════════════════════
      // ORGANIC CITY TOPOLOGY — Gerçekçi şehir haritası düzeni
      // Koordinat alanı: ~1000×1000, 1 KM ≈ 50 birim
      // ═══════════════════════════════════════════════════════════════════

      // ─── ANA LOJİSTİK MERKEZ (kuzeybatı — endüstri bölgesi) ───
      graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT, 80, 120));

      // ─── MİKRO DEPOLAR (asimetrik dağılım) ───
      graph.addNode(new Node("M1", NodeType.MICRO_DEPOT, 430, 145));   // Kuzey-merkez
      graph.addNode(new Node("M2", NodeType.MICRO_DEPOT, 560, 780));   // Güney
      graph.addNode(new Node("M3", NodeType.MICRO_DEPOT, 820, 390));   // Doğu
      graph.addNode(new Node("M4", NodeType.MICRO_DEPOT, 140, 620));   // Batı
      graph.addNode(new Node("M5", NodeType.MICRO_DEPOT, 490, 460));   // Merkez

      // ─── KAVŞAKLAR — Organik yerleşim (ızgara yok) ───

      // Kuzeybatı bölgesi (lojistik/endüstri)
      graph.addNode(new Node("J1",  NodeType.INTERSECTION, 155,  78));
      graph.addNode(new Node("J2",  NodeType.INTERSECTION, 218, 162));
      graph.addNode(new Node("J3",  NodeType.INTERSECTION, 128, 235));
      graph.addNode(new Node("J4",  NodeType.INTERSECTION, 208, 298));

      // Kuzey koridoru
      graph.addNode(new Node("J5",  NodeType.INTERSECTION, 308,  68));
      graph.addNode(new Node("J6",  NodeType.INTERSECTION, 388, 118));
      graph.addNode(new Node("J7",  NodeType.INTERSECTION, 478,  75));

      // Kuzey bağlantı
      graph.addNode(new Node("J8",  NodeType.INTERSECTION, 348, 202));
      graph.addNode(new Node("J9",  NodeType.INTERSECTION, 462, 238));
      graph.addNode(new Node("J10", NodeType.INTERSECTION, 538, 172));

      // Orta-kuzey halkası
      graph.addNode(new Node("J11", NodeType.INTERSECTION, 282, 278));
      graph.addNode(new Node("J12", NodeType.INTERSECTION, 378, 322));
      graph.addNode(new Node("J13", NodeType.INTERSECTION, 508, 292));
      graph.addNode(new Node("J14", NodeType.INTERSECTION, 593, 258));

      // Merkez bölge (yoğun)
      graph.addNode(new Node("J15", NodeType.INTERSECTION, 398, 398));
      graph.addNode(new Node("J16", NodeType.INTERSECTION, 533, 372));
      graph.addNode(new Node("J17", NodeType.INTERSECTION, 448, 518));
      graph.addNode(new Node("J18", NodeType.INTERSECTION, 573, 488));

      // Doğu koridoru
      graph.addNode(new Node("J19", NodeType.INTERSECTION, 643, 188));
      graph.addNode(new Node("J20", NodeType.INTERSECTION, 718, 252));
      graph.addNode(new Node("J21", NodeType.INTERSECTION, 768, 342));

      // Doğu bölgesi
      graph.addNode(new Node("J22", NodeType.INTERSECTION, 728, 418));
      graph.addNode(new Node("J23", NodeType.INTERSECTION, 843, 468));
      graph.addNode(new Node("J24", NodeType.INTERSECTION, 778, 528));

      // Güneydoğu
      graph.addNode(new Node("J25", NodeType.INTERSECTION, 688, 588));
      graph.addNode(new Node("J26", NodeType.INTERSECTION, 748, 658));
      graph.addNode(new Node("J27", NodeType.INTERSECTION, 658, 728));

      // Güney bölgesi
      graph.addNode(new Node("J28", NodeType.INTERSECTION, 513, 648));
      graph.addNode(new Node("J29", NodeType.INTERSECTION, 598, 693));
      graph.addNode(new Node("J30", NodeType.INTERSECTION, 643, 818));
      graph.addNode(new Node("J31", NodeType.INTERSECTION, 503, 762));

      // Güneybatı koridoru
      graph.addNode(new Node("J32", NodeType.INTERSECTION, 328, 588));
      graph.addNode(new Node("J33", NodeType.INTERSECTION, 258, 658));
      graph.addNode(new Node("J34", NodeType.INTERSECTION, 188, 742));

      // Batı bölgesi
      graph.addNode(new Node("J35", NodeType.INTERSECTION,  98, 548));
      graph.addNode(new Node("J36", NodeType.INTERSECTION, 213, 523));
      graph.addNode(new Node("J37", NodeType.INTERSECTION,  88, 698));

      // Batı koridoru
      graph.addNode(new Node("J38", NodeType.INTERSECTION, 138, 388));
      graph.addNode(new Node("J39", NodeType.INTERSECTION, 228, 438));
      graph.addNode(new Node("J40", NodeType.INTERSECTION, 318, 468));

      // Ek merkez düğümleri
      graph.addNode(new Node("J41", NodeType.INTERSECTION, 363, 513));
      graph.addNode(new Node("J42", NodeType.INTERSECTION, 428, 578));
      graph.addNode(new Node("J43", NodeType.INTERSECTION, 308, 358));

      // Derin bağlantı düğümleri
      graph.addNode(new Node("J44", NodeType.INTERSECTION, 623, 423));
      graph.addNode(new Node("J45", NodeType.INTERSECTION, 473, 683));
      graph.addNode(new Node("J46", NodeType.INTERSECTION, 343, 723));
      graph.addNode(new Node("J47", NodeType.INTERSECTION, 683, 483));
      graph.addNode(new Node("J48", NodeType.INTERSECTION, 553, 583));

      // Çıkmaz sokaklar (Dead-ends → T-kavşak oluşturur)
      graph.addNode(new Node("D1", NodeType.INTERSECTION,  58, 318));
      graph.addNode(new Node("D2", NodeType.INTERSECTION, 568,  52));
      graph.addNode(new Node("D3", NodeType.INTERSECTION, 888, 298));
      graph.addNode(new Node("D4", NodeType.INTERSECTION, 863, 558));
      graph.addNode(new Node("D5", NodeType.INTERSECTION, 278, 793));
      graph.addNode(new Node("D6", NodeType.INTERSECTION,  73, 778));
      graph.addNode(new Node("D7", NodeType.INTERSECTION, 713, 863));
      graph.addNode(new Node("D8", NodeType.INTERSECTION, 338, 152));

      // ─── MÜŞTERİLER (asimetrik offset ile dağıtılmış) ───
      graph.addNode(new Node("C1",  NodeType.CUSTOMER, 183, 110));   // J1 yakını
      graph.addNode(new Node("C2",  NodeType.CUSTOMER, 253, 192));   // J2 yakını
      graph.addNode(new Node("C3",  NodeType.CUSTOMER, 328, 108));   // J5 yakını
      graph.addNode(new Node("C4",  NodeType.CUSTOMER, 503, 133));   // J7/J10 arası
      graph.addNode(new Node("C5",  NodeType.CUSTOMER, 623, 218));   // J19 yakını
      graph.addNode(new Node("C6",  NodeType.CUSTOMER, 753, 283));   // J20 yakını
      graph.addNode(new Node("C7",  NodeType.CUSTOMER, 868, 423));   // J23 yakını
      graph.addNode(new Node("C8",  NodeType.CUSTOMER, 703, 448));   // J22/J47 arası
      graph.addNode(new Node("C9",  NodeType.CUSTOMER, 603, 553));   // J48 yakını
      graph.addNode(new Node("C10", NodeType.CUSTOMER, 553, 733));   // J31/M2 arası
      graph.addNode(new Node("C11", NodeType.CUSTOMER, 453, 618));   // J42 yakını
      graph.addNode(new Node("C12", NodeType.CUSTOMER, 273, 603));   // J32 yakını
      graph.addNode(new Node("C13", NodeType.CUSTOMER, 128, 583));   // J35 yakını
      graph.addNode(new Node("C14", NodeType.CUSTOMER, 178, 463));   // J39 yakını
      graph.addNode(new Node("C15", NodeType.CUSTOMER, 393, 438));   // J15 yakını
      graph.addNode(new Node("C16", NodeType.CUSTOMER, 513, 333));   // J13 yakını

      // ─── MÜŞTERİ BAĞLANTILARI ───
      addBoth(graph, "J1",  "C1",  1.0);
      addBoth(graph, "J2",  "C2",  1.2);
      addBoth(graph, "J5",  "C3",  1.1);
      addBoth(graph, "J10", "C4",  1.3);
      addBoth(graph, "J19", "C5",  1.0);
      addBoth(graph, "J20", "C6",  1.4);
      addBoth(graph, "J23", "C7",  1.2);
      addBoth(graph, "J22", "C8",  1.1);
      addBoth(graph, "J48", "C9",  1.3);
      addBoth(graph, "J31", "C10", 1.2);
      addBoth(graph, "J42", "C11", 1.0);
      addBoth(graph, "J32", "C12", 1.3);
      addBoth(graph, "J35", "C13", 1.1);
      addBoth(graph, "J39", "C14", 1.2);
      addBoth(graph, "J15", "C15", 1.0);
      addBoth(graph, "J13", "C16", 1.4);

      // ═══════════════════════════════════════════════════════════════════
      // YOL AĞI — Organik bağlantılar
      // ═══════════════════════════════════════════════════════════════════

      // --- Kuzeybatı (MAIN çevresi) ---
      addBoth(graph, "MAIN", "J1",  3.8);
      addBoth(graph, "MAIN", "J3",  4.5);
      addBoth(graph, "J1",   "J2",  2.4);
      addBoth(graph, "J1",   "J5",  3.0);
      addBoth(graph, "J2",   "J3",  2.2);
      addBoth(graph, "J2",   "J8",  2.8);
      addBoth(graph, "J2",   "J11", 2.6);
      addBoth(graph, "J3",   "J4",  2.0);
      addBoth(graph, "J3",   "J38", 3.2);
      addBoth(graph, "J4",   "J11", 2.1);
      addBoth(graph, "J4",   "J39", 3.4);

      // --- Kuzey koridoru ---
      addBoth(graph, "J5",   "J6",  2.2);
      addBoth(graph, "J5",   "D8",  1.8);
      addBoth(graph, "J6",   "J7",  2.0);
      addBoth(graph, "J6",   "M1",  1.6);
      addBoth(graph, "J6",   "J8",  2.1);
      addBoth(graph, "J7",   "J10", 1.8);
      addBoth(graph, "J7",   "D2",  1.9);

      // --- Kuzey bölgesi (M1 çevresi) ---
      addBoth(graph, "M1",   "J8",  2.3);
      addBoth(graph, "M1",   "J9",  1.9);
      addBoth(graph, "J8",   "J12", 2.7);
      addBoth(graph, "J9",   "J10", 1.8);
      addBoth(graph, "J9",   "J13", 2.2);
      addBoth(graph, "J10",  "J14", 2.1);
      addBoth(graph, "J10",  "J19", 2.5);

      // --- Orta-kuzey halkası ---
      addBoth(graph, "J11",  "J12", 2.1);
      addBoth(graph, "J11",  "J43", 2.0);
      addBoth(graph, "J12",  "J13", 2.8);
      addBoth(graph, "J12",  "J15", 2.2);
      addBoth(graph, "J13",  "J14", 2.0);
      addBoth(graph, "J13",  "J16", 2.1);
      addBoth(graph, "J14",  "J19", 1.8);

      // --- Merkez bölgesi (M5 çevresi) ---
      addBoth(graph, "J15",  "M5",  2.0);
      addBoth(graph, "J15",  "J41", 2.5);
      addBoth(graph, "J15",  "J43", 1.6);
      addBoth(graph, "J16",  "M5",  1.8);
      addBoth(graph, "J16",  "J18", 2.3);
      addBoth(graph, "J16",  "J44", 2.2);
      addBoth(graph, "J17",  "M5",  1.5);
      addBoth(graph, "J17",  "J41", 1.6);
      addBoth(graph, "J17",  "J28", 3.0);
      addBoth(graph, "J18",  "J25", 2.4);
      addBoth(graph, "J18",  "J48", 2.2);
      addBoth(graph, "J18",  "J47", 2.5);

      // --- Doğu koridoru ---
      addBoth(graph, "J19",  "J20", 2.0);
      addBoth(graph, "J20",  "J21", 2.1);
      addBoth(graph, "J21",  "M3",  1.8);
      addBoth(graph, "J21",  "D3",  2.4);

      // --- Doğu bölgesi (M3 çevresi) ---
      addBoth(graph, "M3",   "J22", 2.1);
      addBoth(graph, "M3",   "J23", 1.9);
      addBoth(graph, "J22",  "J24", 2.3);
      addBoth(graph, "J22",  "J47", 1.8);
      addBoth(graph, "J23",  "J24", 1.8);
      addBoth(graph, "J23",  "D4",  2.0);
      addBoth(graph, "J24",  "J47", 2.2);
      addBoth(graph, "J24",  "J25", 2.5);

      // --- Güneydoğu ---
      addBoth(graph, "J25",  "J26", 2.0);
      addBoth(graph, "J25",  "J48", 1.8);
      addBoth(graph, "J25",  "J29", 2.4);
      addBoth(graph, "J26",  "J27", 2.0);
      addBoth(graph, "J27",  "J30", 2.2);
      addBoth(graph, "J27",  "J29", 2.4);

      // --- Güney bölgesi (M2 çevresi) ---
      addBoth(graph, "J28",  "J29", 2.0);
      addBoth(graph, "J28",  "J42", 1.8);
      addBoth(graph, "J28",  "J32", 3.6);
      addBoth(graph, "J29",  "M2",  2.1);
      addBoth(graph, "J29",  "J48", 2.5);
      addBoth(graph, "J30",  "M2",  2.2);
      addBoth(graph, "J30",  "D7",  1.6);
      addBoth(graph, "J31",  "M2",  1.8);
      addBoth(graph, "J31",  "J45", 1.8);
      addBoth(graph, "J31",  "J46", 3.2);

      // --- Güneybatı ---
      addBoth(graph, "J32",  "J33", 2.0);
      addBoth(graph, "J32",  "J41", 2.2);
      addBoth(graph, "J33",  "J34", 2.1);
      addBoth(graph, "J33",  "M4",  2.5);
      addBoth(graph, "J34",  "D5",  1.8);
      addBoth(graph, "J34",  "J37", 2.3);

      // --- Batı bölgesi (M4 çevresi) ---
      addBoth(graph, "J35",  "M4",  1.8);
      addBoth(graph, "J35",  "J36", 2.1);
      addBoth(graph, "J35",  "J37", 2.8);
      addBoth(graph, "J36",  "J39", 2.0);
      addBoth(graph, "J36",  "J40", 2.3);
      addBoth(graph, "J37",  "M4",  2.0);
      addBoth(graph, "J37",  "D6",  1.6);

      // --- Batı koridoru ---
      addBoth(graph, "J38",  "J39", 2.0);
      addBoth(graph, "J38",  "D1",  1.8);
      addBoth(graph, "J38",  "J43", 2.8);
      addBoth(graph, "J38",  "J35", 3.2);
      addBoth(graph, "J39",  "J40", 2.0);
      addBoth(graph, "J40",  "J41", 1.8);
      addBoth(graph, "J40",  "J15", 2.6);

      // --- Ek merkez bağlantıları ---
      addBoth(graph, "J41",  "J42", 1.6);
      addBoth(graph, "J42",  "J45", 2.5);
      addBoth(graph, "J42",  "J48", 2.8);
      addBoth(graph, "J44",  "J47", 1.8);
      addBoth(graph, "J45",  "J46", 2.5);
      addBoth(graph, "J45",  "J48", 2.2);
      addBoth(graph, "J46",  "J33", 2.0);
      addBoth(graph, "J47",  "J25", 2.3);
      addBoth(graph, "J43",  "J12", 1.8);
      addBoth(graph, "J44",  "J22", 2.5);
      addBoth(graph, "J46",  "J34", 3.2);

      // ═══════════════════════════════════════════════════════════════════
      // ÜCRETLİ YOLLAR (TOLL ROADS)
      // Otoyol/köprü geçişleri — stratejik noktaları hızlı bağlar
      // ancak ücretli olduğu için Dijkstra maliyet hesabını etkiler.
      // ═══════════════════════════════════════════════════════════════════

      // Kuzey Otoyolu: MAIN → M1 direkt bağlantı (hızlı ama ücretli)
      addTollBoth(graph, "MAIN", "J5",  3.0, 60.0);   // Kuzey Otoyolu — 60₺

      // Doğu Çevre Yolu: J14 → J20 (uzun mesafe kısayol)
      addTollBoth(graph, "J14", "J20",  3.2, 85.0);    // Doğu Çevre — 85₺

      // Boğaz Köprüsü: J18 → J22 (doğu-merkez hızlı geçiş)
      addTollBoth(graph, "J18", "J22",  2.8, 145.0);   // Köprü — 145₺

      // Güney Ekspres: J28 → J45 (güney koridoru kısayol)
      addTollBoth(graph, "J28", "J45",  2.0, 55.0);    // Ekspres — 55₺

      // Batı Otoyolu: J38 → J36 (batı bölgesi hızlı erişim)
      addTollBoth(graph, "J38", "J36",  1.5, 25.0);    // Otoyol — 25₺

      // Merkez Tüneli: J12 → J16 (merkez kuzey-güney hızlı geçiş)
      addTollBoth(graph, "J12", "J16",  1.8, 45.0);    // Tünel — 45₺

      depots.add(new Depot("MAIN", DepotType.MAIN, "MAIN"));
      depots.add(new Depot("M1", DepotType.MICRO, "M1"));
      depots.add(new Depot("M2", DepotType.MICRO, "M2"));
      depots.add(new Depot("M3", DepotType.MICRO, "M3"));
      depots.add(new Depot("M4", DepotType.MICRO, "M4"));
      depots.add(new Depot("M5", DepotType.MICRO, "M5"));

      List<Product> products = List.of(
          new Product("P1", "Su Paketi", ProductCategory.FOOD, 0.2, 3.0, 25.0, Set.of(ProductCapability.STANDARD)),
          new Product("P2", "Dondurulmus Gida", ProductCategory.FOOD, 1.0, 1.2, 75.0, Set.of(ProductCapability.DEEP_FREEZE, ProductCapability.COOLED_BOX)),
          new Product("P3", "Temizlik Seti", ProductCategory.CLEANING, 0.8, 2.4, 45.0, Set.of(ProductCapability.STANDARD)),
          new Product("P4", "Ilac Kutusu", ProductCategory.MEDICAL, 0.1, 0.8, 950.0, Set.of(ProductCapability.MEDICAL_LOCK, ProductCapability.COOLED_BOX)),
          new Product("P5", "Tablet Kutu", ProductCategory.ELECTRONICS, 0.3, 0.5, 550.0, Set.of(ProductCapability.FRAGILE_RACK)),
          new Product("P6", "Powerbank", ProductCategory.ELECTRONICS, 0.2, 0.4, 85.0, Set.of(ProductCapability.STANDARD)),
          new Product("P7", "Bebek Mamasi", ProductCategory.FOOD, 0.5, 0.9, 60.0, Set.of(ProductCapability.COOLED_BOX)),
          new Product("P8", "Hijyen Paketi", ProductCategory.MEDICAL, 0.6, 1.0, 55.0, Set.of(ProductCapability.MEDICAL_LOCK)),
          new Product("P9", "Ofis Kutusu", ProductCategory.GENERAL, 0.4, 1.4, 75.0, Set.of(ProductCapability.STANDARD)),
          new Product("P10", "Cam Ev Aleti", ProductCategory.GENERAL, 1.5, 4.2, 180.0, Set.of(ProductCapability.FRAGILE_RACK)));
      products.forEach(productCatalogService::save);

      for (Product product : products) {
        inventoryService.setStock("MAIN", product.getId(), 5000);
        inventoryService.setStock("M1", product.getId(), 80);
        inventoryService.setStock("M2", product.getId(), 80);
        inventoryService.setStock("M3", product.getId(), 80);
        inventoryService.setStock("M4", product.getId(), 80);
        inventoryService.setStock("M5", product.getId(), 80);
      }

      // V1: EXPRESS-MASTER (Hiz)
      Vehicle v1 = new Vehicle("EXPRESS-MASTER", 50.0, "MAIN", "GASOLINE", 7.80, 
          Set.of(ProductCategory.MEDICAL, ProductCategory.ELECTRONICS, ProductCategory.FOOD), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.MEDICAL_LOCK, VehicleCapability.COOLED_BOX), 
          "34 EX 001", "Usta Sofor 1", 0.50, 3, 180, 50.0, 5.0, 4.0, 3, 2, false);
      vehicles.add(v1);
      vehicleRepository.save(v1);

      // V2: ECO-TRANSIT (Ekonomi)
      Vehicle v2 = new Vehicle("ECO-TRANSIT", 35.0, "MAIN", "ELECTRIC", 2.40, 
          Set.of(ProductCategory.FOOD, ProductCategory.GENERAL, ProductCategory.CLEANING, ProductCategory.ELECTRONICS), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.FRAGILE_RACK), 
          "34 EC 002", "Ekonomik Sofor 2", 0.50, 3, 180, 32.0, 3.0, 2.5, 3, 2, false);
      vehicles.add(v2);
      vehicleRepository.save(v2);

      // V3: HEAVY-CARRIER (Kapasite)
      Vehicle v3 = new Vehicle("HEAVY-CARRIER", 85.0, "MAIN", "DIESEL", 8.55, 
          Set.of(ProductCategory.FOOD, ProductCategory.CLEANING, ProductCategory.GENERAL, ProductCategory.MEDICAL), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.DEEP_FREEZER, VehicleCapability.COOLED_BOX), 
          "34 HV 003", "Yuk Soforu 3", 0.50, 3, 180, 35.0, 6.0, 5.0, 3, 2, false);
      vehicles.add(v3);
      vehicleRepository.save(v3);

      // V4: CITY-RUNNER (Hassas)
      Vehicle v4 = new Vehicle("CITY-RUNNER", 45.0, "MAIN", "GASOLINE", 7.20, 
          Set.of(ProductCategory.ELECTRONICS, ProductCategory.GENERAL, ProductCategory.FOOD), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.FRAGILE_RACK), 
          "34 CR 004", "Sehir Soforu 4", 0.50, 3, 180, 45.0, 4.5, 3.5, 3, 2, false);
      vehicles.add(v4);
      vehicleRepository.save(v4);

      // V5: STANDARD-HAULER (Denge)
      Vehicle v5 = new Vehicle("STANDARD-HAULER", 60.0, "MAIN", "DIESEL", 5.85, 
          Set.of(ProductCategory.FOOD, ProductCategory.CLEANING, ProductCategory.MEDICAL, ProductCategory.GENERAL), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.COOLED_BOX), 
          "34 SH 005", "Standart Sofor 5", 0.50, 3, 180, 40.0, 5.0, 4.0, 3, 2, false);
      vehicles.add(v5);
      vehicleRepository.save(v5);

      // V6: FLEX-DELIVERY (Cok Yonlu)
      Vehicle v6 = new Vehicle("FLEX-DELIVERY", 50.0, "MAIN", "GASOLINE", 6.60, 
          Set.of(ProductCategory.MEDICAL, ProductCategory.ELECTRONICS, ProductCategory.GENERAL), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.MEDICAL_LOCK, VehicleCapability.FRAGILE_RACK), 
          "34 FL 006", "Esnek Sofor 6", 0.50, 3, 180, 38.0, 4.0, 3.0, 3, 2, false);
      vehicles.add(v6);
      vehicleRepository.save(v6);

      // V7: SQUARE-MANUAL-1 (Özel Manuel İkmal)
      Vehicle v7 = new Vehicle("SQUARE-MANUAL-1", 100.0, "MAIN", "ELECTRIC", 4.50, 
          Set.of(ProductCategory.FOOD, ProductCategory.GENERAL, ProductCategory.CLEANING, ProductCategory.ELECTRONICS, ProductCategory.MEDICAL), 
          Set.of(VehicleCapability.STANDARD_BOX, VehicleCapability.COOLED_BOX, VehicleCapability.DEEP_FREEZER, VehicleCapability.FRAGILE_RACK, VehicleCapability.MEDICAL_LOCK), 
          "34 SQ 007", "Manuel Sofor 7", 0.0, 4, 0, 40.0, 15.0, 12.0, 1, 1, true);
      vehicles.add(v7);
      vehicleRepository.save(v7);
      simulationEngine.captureInitialState();
    };
  }

  private static void addBoth(CityGraph graph, String from, String to, double distanceKm) {
    double scaledDistance = distanceKm * DISTANCE_SCALE;
    graph.addEdge(new Edge(from, to, scaledDistance, 1.0));
    graph.addEdge(new Edge(to, from, scaledDistance, 1.0));
  }

  /**
   * Adds a bidirectional toll road.
   * @param tollCost Geçiş ücreti (₺) — Dijkstra maliyet hesabına eklenir.
   */
  private static void addTollBoth(CityGraph graph, String from, String to, double distanceKm, double tollCost) {
    double scaledDistance = distanceKm * DISTANCE_SCALE;
    graph.addEdge(new Edge(from, to, scaledDistance, 1.0, tollCost));
    graph.addEdge(new Edge(to, from, scaledDistance, 1.0, tollCost));
  }
}
