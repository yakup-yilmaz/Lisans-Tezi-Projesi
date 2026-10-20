import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useMemo, memo } from 'react'
import { tooltipStyle } from '../components/chartTheme'
import { MapCanvas } from '../components/MapCanvas'
import {
  Card, ChartCard, DataTable, EmptyState, FleetDistributionBar,
  MiniStat, Panel, SectionLabel, SidebarLayout, StatGrid, StatusBadge,
} from '../components/ui'

// ─── Memoized Helper Function ──────────────────────────────────────────────────

function progressByCategory(products, orders) {
  const totals    = { electronics: 0, medical: 0, food: 0, documents: 0 }
  const completed = { electronics: 0, medical: 0, food: 0, documents: 0 }
  const productMap = Object.fromEntries(products.map((p) => [p.id, p]))

  for (const order of orders) {
    const bucket = new Set()
    for (const item of order.items || []) {
      const cat = productMap[item.productId]?.category
      if (cat === 'ELECTRONICS') bucket.add('electronics')
      else if (cat === 'MEDICAL') bucket.add('medical')
      else if (cat === 'FOOD') bucket.add('food')
      else bucket.add('documents')
    }
    if (!bucket.size) bucket.add('documents')
    for (const key of bucket) {
      totals[key] += 1
      if (order.status === 'COMPLETED') completed[key] += 1
    }
  }

  return Object.entries(totals).map(([key, total]) => ({
    key,
    label: { electronics: 'Electronics', medical: 'Medical', food: 'Food', documents: 'Documents' }[key],
    total,
    completed: completed[key],
    pct: total ? Math.round((completed[key] / total) * 100) : 0,
  }))
}

const tooltipSt = {
  ...tooltipStyle,
  backgroundColor: '#111620',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 10,
}

// ─── Sidebar ─────────────────────────────────────────────────────────────────

const OverviewSidebar = memo(function OverviewSidebar({ progress, nodes, edges, vehicles, orders, replenishments, summary, kpis, products }) {
  const idle      = useMemo(() => vehicles.filter((v) => v.status === 'IDLE').length, [vehicles])
  const enRoute   = useMemo(() => vehicles.filter((v) => v.status === 'EN_ROUTE').length, [vehicles])
  const loading   = useMemo(() => vehicles.filter((v) => v.status === 'LOADING').length, [vehicles])
  const returning = useMemo(() => vehicles.filter((v) => v.status === 'RETURNING').length, [vehicles])
  const total     = vehicles.length || 1

  const openRepl      = useMemo(() => replenishments.filter((r) => r.status !== 'COMPLETED').length, [replenishments])
  const completedRepl = useMemo(() => replenishments.filter((r) => r.status === 'COMPLETED').length, [replenishments])

  const categoryProgress = useMemo(() => progressByCategory(products || [], orders), [products, orders])
  const deliveryPct = useMemo(() => 
    kpis.totalOrders > 0 ? Math.round((kpis.completedOrders / kpis.totalOrders) * 100) : 0,
    [kpis.totalOrders, kpis.completedOrders]
  )

  return (
    <>
      {/* Today's Delivery Progress — referans görseli karşılığı */}
      <div className="rounded-[16px] border border-white/[0.07] bg-[#0e1118] p-4">
        <div className="flex items-center justify-between mb-3">
          <SectionLabel>Günlük Teslimat</SectionLabel>
          <span className="text-[11px] text-orange-400 font-semibold">{deliveryPct}%</span>
        </div>

        {/* Kategori pill'leri — referanstaki gibi */}
        <div className="flex flex-wrap gap-2 mb-4">
          {[
            { label: 'Electronics', color: 'bg-sky-500/20 text-sky-300' },
            { label: 'Medical',     color: 'bg-emerald-500/20 text-emerald-300' },
            { label: 'Food',        color: 'bg-orange-500/20 text-orange-300' },
            { label: 'Documents',   color: 'bg-slate-700/50 text-slate-400' },
          ].map(({ label, color }) => {
            const found = categoryProgress.find((c) => c.label === label)
            return (
              <span key={label} className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${color}`}>
                {label} {found?.completed ?? 0}
              </span>
            )
          })}
        </div>

        {/* Ana progress bar */}
        <div className="h-2 overflow-hidden rounded-full bg-[#1c2230] mb-2">
          <div
            className="h-full rounded-full bg-gradient-to-r from-orange-500 to-amber-400 transition-all duration-700"
            style={{ width: `${Math.max(deliveryPct, 2)}%` }}
          />
        </div>
        <div className="flex justify-between text-[11px] text-slate-600">
          <span>0%</span>
          <span className="text-slate-400">{kpis.completedOrders}/{kpis.totalOrders} siparis</span>
          <span>100%</span>
        </div>
      </div>

      {/* Fleet Distribution — referans görseli */}
      <div className="rounded-[16px] border border-white/[0.07] bg-[#0e1118] p-4">
        <div className="flex items-center justify-between mb-3">
          <SectionLabel>Filo Dagilimi</SectionLabel>
          <span className="text-[11px] text-slate-500">{vehicles.length} arac</span>
        </div>
        <div className="space-y-1">
          <FleetDistributionBar label="Aktif"    count={enRoute + loading} total={total} icon="🚛" />
          <FleetDistributionBar label="Bosta"    count={idle}              total={total} icon="🅿" />
          <FleetDistributionBar label="Yukleme"  count={loading}           total={total} icon="📦" />
          <FleetDistributionBar label="Donus"    count={returning}         total={total} icon="↩" />
        </div>
      </div>

      {/* Network stats */}
      <div className="rounded-[16px] border border-white/[0.07] bg-[#0e1118] p-4">
        <SectionLabel className="mb-3">Ag & Operasyon</SectionLabel>
        <div className="mt-3 space-y-0">
          <MiniStat label="Node" value={nodes.length} />
          <MiniStat label="Yol baglantisi" value={edges.length} />
          <MiniStat label="Parali yol" value={edges.filter((e) => Number(e.tollCost) > 0).length} tone="orange" />
          <MiniStat label="Simulasyon ilerlemesi" value={`%${progress}`} tone="sky" />
          <MiniStat label="Acik ikmal talebi" value={openRepl} tone={openRepl > 0 ? 'orange' : 'default'} />
          <MiniStat label="Tamamlanan ikmal" value={completedRepl} tone="emerald" />
          <MiniStat label="Multi-stop sefer" value={summary.multiStopTrips ?? 0} />
          <MiniStat label="Single-stop sefer" value={summary.singleStopTrips ?? 0} />
          <MiniStat label="Kullanim orani" value={`%${Number(summary.vehicleUtilizationPercentage || 0).toFixed(1)}`} tone="sky" />
        </div>
      </div>

      {/* Live Operations Summary */}
      <div className="rounded-[16px] border border-orange-500/20 bg-orange-950/20 p-4">
        <SectionLabel>Canli Sistem Durumu</SectionLabel>
        <div className="mt-3 space-y-3">
           <div className="flex justify-between items-center bg-black/20 p-2 rounded-lg">
             <span className="text-[12px] font-bold text-slate-400">Acik İkmal</span>
             <span className={`text-[14px] font-bold ${openRepl > 0 ? 'text-orange-400' : 'text-slate-500'}`}>{openRepl}</span>
           </div>
           <div className="flex justify-between items-center bg-black/20 p-2 rounded-lg">
             <span className="text-[12px] font-bold text-slate-400">Ortalama Maliyet</span>
             <span className="text-[14px] font-bold text-sky-400">
               {kpis.totalOrders > 0 ? Number((summary.totalCost || 0) / kpis.totalOrders).toFixed(1) : 0} ₺/Sp.
             </span>
           </div>
           <div className="flex justify-between items-center bg-black/20 p-2 rounded-lg">
             <span className="text-[12px] font-bold text-slate-400">Aktif Sefer Sayisi</span>
             <span className="text-[14px] font-bold text-emerald-400">{enRoute + loading}</span>
           </div>
           <div className="pt-2 border-t border-orange-500/20">
             <p className="text-[10px] text-orange-400/80 leading-relaxed italic">
               Sistem {progress}% tamamlanma oraninda ilerliyor. Multi-stop algoritmasi devrede.
             </p>
           </div>
        </div>
      </div>
    </>
  )
})

// ─── Main Content ─────────────────────────────────────────────────────────────

export const OverviewScreen = memo(function OverviewScreen({
  status, progress, nodes, edges, vehicles, vehicleAssignments,
  orders, replenishments, reports, kpis, routePreview,
  products, timeline, summary, simulationMsPerTick, lastStatusSyncAt,
}) {
  const deliveryPct = kpis.totalOrders > 0
    ? Math.round((kpis.completedOrders / kpis.totalOrders) * 100)
    : 0

  const stats = [
    { label: 'Siparis Verimliligi',  value: `${deliveryPct}%`, tone: 'sky' },
    { label: 'Toplam Maliyet',  value: `${Number(summary.totalCost || 0).toFixed(0)}₺`,  tone: 'amber' },
    { label: 'Kargo Değeri',    value: `${Number(summary.totalCargoValue || 0).toLocaleString('tr-TR')} ₺`, tone: 'emerald' },
    { label: 'Sürdürülebilirlik', value: `-${Number(summary.co2Saved || 0).toFixed(1)}kg`, tone: 'sky' },
  ]

  const reportChart = reports.map((r) => ({ id: r.vehicleId, km: r.totalDistanceKm, cost: r.totalCost }))
  const loadChart   = vehicles.map((v) => {
    const val = v?.capacityVolume ? (Number(v?.currentLoad) || 0) / Number(v?.capacityVolume) : 0
    return { id: v.id, load: Math.round(val * 100) }
  })
  const recentOrders = orders.slice(-8).reverse()
  const latestEvents = [...(timeline || [])].slice(-12).reverse()

  const sidebar = (
    <OverviewSidebar
      status={status} progress={progress} nodes={nodes} edges={edges}
      vehicles={vehicles} orders={orders} replenishments={replenishments}
      summary={summary} kpis={kpis} products={products}
    />
  )

  return (
    <section className="mt-4 space-y-4">
      {/* KPI row */}
      <StatGrid items={stats} />

      <SidebarLayout sidebar={sidebar}>
        <div className="space-y-4">
          {/* Harita */}
          <Card title="Canlı Sevkiyat Haritası" subtitle="Araçlar, depolar ve aktif rotalar gerçek zamanlı izleniyor.">
            <MapCanvas
              nodes={nodes} edges={edges} vehicles={vehicles}
              vehicleAssignments={vehicleAssignments} routePreview={routePreview}
              simulationMsPerTick={simulationMsPerTick}
              lastStatusSyncAt={lastStatusSyncAt}
              status={status}
              compact
            />
          </Card>

          {/* Orders + Replenishments yan yana */}
          <div className="grid gap-4 xl:grid-cols-[1.1fr,0.9fr]">
            {/* Orders tablosu — referanstaki gibi */}
            <Card title="Son Siparişler" subtitle="Mikro depoya atama ve durum takibi.">
              <DataTable
                columns={['Sipariş', 'Müşteri', 'Mikro Depo', 'Hacim', 'Durum']}
                rows={recentOrders.map((order) => [
                  <span className="font-mono text-[12px] text-orange-300">{order.id}</span>,
                  order.customerNodeId,
                  order.microDepotNodeId || <span className="text-slate-600">—</span>,
                  <span className="tabular-nums">{Number(order.demandVolume || 0).toFixed(2)} m³</span>,
                  <StatusBadge status={order.status} />,
                ])}
                emptyMessage="Henüz sipariş yok."
              />
            </Card>

            {/* Replenishment özeti */}
            <Card title="İkmal Akışı" subtitle="Ana depodan mikro depolara sevkiyat talepleri.">
              <div className="space-y-2 max-h-[340px] overflow-auto pr-1">
                {!replenishments.length && <EmptyState message="Henüz ikmal talebi yok." />}
                {replenishments.slice(-6).reverse().map((item) => (
                  <div key={item.id} className="rounded-[10px] border border-white/[0.06] bg-[#111620] p-3">
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-mono text-[12px] font-semibold text-orange-300">{item.id}</p>
                      <StatusBadge status={item.status} />
                    </div>
                    <p className="mt-1 text-[12px] text-slate-400">
                      {item.routeSummary || `${item.sourceDepotId} → ${item.targetDepotId}`}
                    </p>
                    {item.cargoSummary && (
                      <p className="mt-1.5 text-[11px] uppercase tracking-[0.14em] text-orange-400/70">{item.cargoSummary}</p>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          </div>

          {/* Karar açıklaması + Timeline yan yana */}
          <div className="grid gap-4 xl:grid-cols-2">
            {/* Aktif araç kararları */}
            <Card title="Karar Açıklama Paneli" subtitle="Sistemin verdiği operasyon kararları okunabilir biçimde.">
              <div className="space-y-3 max-h-[360px] overflow-auto pr-1">
                {!vehicleAssignments.length && <EmptyState message="Aktif araç görevi yok." />}
                {vehicleAssignments.map((a) => {
                  const vehicle = vehicles.find((v) => v.id === a.vehicleId)
                  return (
                    <div key={a.vehicleId} className="rounded-[12px] border border-white/[0.06] bg-[#111620] p-4">
                      <div className="flex items-center justify-between gap-3 mb-2">
                        <p className="text-[14px] font-bold text-white">{a.vehicleId}</p>
                        {vehicle && <StatusBadge status={vehicle.status} />}
                      </div>
                      <p className="text-[12px] text-slate-500 mb-2">{a.routeSummary}</p>
                      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[12px]">
                        <span className="text-slate-500">Yük</span>
                        <span className="text-white tabular-nums">{Number(a.totalVolume || 0).toFixed(1)} / {Number(a.capacityVolume || 0).toFixed(1)} m³</span>
                        <span className="text-slate-500">Mesafe</span>
                        <span className="text-white tabular-nums">{Number(a.distanceKm || 0).toFixed(1)} km</span>
                        <span className="text-slate-500">Kalan</span>
                        <span className="text-white tabular-nums">{a.remainingTotalTicks} dk</span>
                      </div>
                      {a.decisionReason && (
                        <p className="mt-2 text-[12px] text-slate-500 leading-relaxed border-t border-white/[0.04] pt-2">
                          {a.decisionReason}
                        </p>
                      )}
                      {a.comparisonSummary && (
                        <p className="mt-1 text-[12px] text-orange-400/80">{a.comparisonSummary}</p>
                      )}
                    </div>
                  )
                })}
              </div>
            </Card>

            {/* Timeline */}
            <Card title="Zaman Akışı" subtitle="Kritik eventler simülasyon saatiyle.">
              <div className="space-y-2 max-h-[360px] overflow-auto pr-1">
                {!latestEvents.length && <EmptyState message="Henüz event yok." />}
                {latestEvents.map((event, i) => (
                  <div key={event.id || i} className="flex gap-3 rounded-[10px] border border-white/[0.05] bg-[#111620] p-3">
                    <div className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-orange-500/15 text-[10px] text-orange-400">
                      ⚡
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-[13px] font-semibold text-white truncate">{event.title}</p>
                        <span className="shrink-0 text-[10px] text-slate-600 tabular-nums">{event.timeLabel}</span>
                      </div>
                      <p className="mt-0.5 text-[12px] text-slate-400 leading-relaxed">{event.detail}</p>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          </div>

          {/* Grafikler ve Analiz */}
          <div className="grid gap-4 xl:grid-cols-2">
            <Card title="Finansal Analiz (Maliyet Kırılımı)" subtitle="Operasyonel giderlerin yakıt, gişe ve işçilik bazlı dağılımı.">
              <div className="grid grid-cols-[1fr,160px] items-center gap-4">
                <ChartCard height="h-48">
                  <PieChart width={200} height={200}>
                    <Pie
                      data={[
                        { name: 'Yakit', value: summary.totalFuelCost || 0, color: '#f97316' },
                        { name: 'Gise', value: summary.totalTollCost || 0, color: '#0ea5e9' },
                        { name: 'Iscilik', value: summary.totalLaborCost || 0, color: '#8b5cf6' },
                        { name: 'Penalti', value: summary.totalPenaltyCost || 0, color: '#ef4444' },
                      ]}
                      innerRadius={55}
                      outerRadius={75}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {[
                        { color: '#f97316' },
                        { color: '#0ea5e9' },
                        { color: '#8b5cf6' },
                        { color: '#ef4444' },
                      ].map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={tooltipSt} />
                  </PieChart>
                </ChartCard>
                <div className="space-y-2">
                  {[
                    { label: 'Yakit', val: summary.totalFuelCost, col: 'bg-orange-500' },
                    { label: 'Gise', val: summary.totalTollCost, col: 'bg-sky-500' },
                    { label: 'Iscilik', val: summary.totalLaborCost, col: 'bg-violet-500' },
                    { label: 'Penalti', val: summary.totalPenaltyCost, col: 'bg-red-500' },
                  ].map(item => (
                    <div key={item.label} className="flex items-center gap-2">
                      <div className={`h-2 w-2 rounded-full ${item.col}`} />
                      <span className="text-[11px] text-slate-500">{item.label}</span>
                      <span className="ml-auto text-[11px] font-bold text-white tabular-nums">{Number(item.val || 0).toFixed(0)}₺</span>
                    </div>
                  ))}
                </div>
              </div>
            </Card>

            <Card title="Çevresel Etki (Sustainability)" subtitle="Elektrikli araç kullanımı sayesinde engellenen karbon salımı.">
               <div className="flex flex-col h-full justify-between">
                  <div className="grid grid-cols-2 gap-4">
                     <div className="rounded-[12px] border border-white/[0.05] bg-[#111620] p-4">
                        <p className="text-[11px] uppercase tracking-widest text-slate-600 mb-1">Toplam CO2</p>
                        <p className="text-[24px] font-bold text-white tabular-nums">{Number(summary.co2Emissions || 0).toFixed(1)} <span className="text-[12px] font-normal text-slate-500">kg</span></p>
                     </div>
                     <div className="rounded-[12px] border border-emerald-500/10 bg-emerald-500/5 p-4">
                        <p className="text-[11px] uppercase tracking-widest text-emerald-600 mb-1">Engellenen CO2</p>
                        <p className="text-[24px] font-bold text-emerald-400 tabular-nums">-{Number(summary.co2Saved || 0).toFixed(1)} <span className="text-[12px] font-normal text-emerald-600/60">kg</span></p>
                     </div>
                  </div>
                  <div className="mt-4 p-3 rounded-[10px] bg-slate-900/50 border border-white/[0.03]">
                     <p className="text-[12px] text-slate-400 italic">
                        * Elektrikli araç kullanımı, dizel muadillerine göre karbon ayak izini %{summary.co2Emissions > 0 ? Math.round((summary.co2Saved / (summary.co2Emissions + summary.co2Saved)) * 100) : 0} oranında azaltmıştır.
                     </p>
                  </div>
               </div>
            </Card>
          </div>

          {/* Filo Yetkinlikleri */}
          <Card title="Filo Teknik Özellikleri & Yetkinlikler" subtitle="Araç bazlı ekipman, yakıt tipi ve operasyonel kapasite detayları.">
            <DataTable
              columns={['Araç ID', 'Plaka / Sürücü', 'Yakıt', 'Kapasite', 'Ekipman & Yetkinlikler']}
              rows={vehicles.map((v) => [
                <div key={v.id} className="flex flex-col">
                  <span className="font-bold text-white">{v.id}</span>
                  {v.dedicatedManual && <span className="text-[10px] text-orange-400 font-bold uppercase tracking-tighter">MANUEL DESTEK</span>}
                </div>,
                <div key={v.id} className="flex flex-col">
                  <span className="text-[12px] text-slate-300">{v.plateNumber}</span>
                  <span className="text-[11px] text-slate-500">{v.driverName}</span>
                </div>,
                <StatusBadge status={v.fuelType} />,
                <div key={v.id} className="flex flex-col">
                  <span className="text-white font-mono">{v.capacityVolume} m³</span>
                  <div className="w-16 h-1 bg-slate-800 rounded-full mt-1 overflow-hidden">
                    <div className="h-full bg-orange-500" style={{ width: `${Math.round((v.currentLoad / v.capacityVolume) * 100)}%` }} />
                  </div>
                </div>,
                <div key={v.id} className="flex flex-wrap gap-1">
                  {(v.capabilities || []).map(cap => (
                    <span key={cap} className="px-1.5 py-0.5 rounded bg-sky-500/10 border border-sky-500/20 text-[10px] text-sky-300 font-medium">
                      {cap.replace('_', ' ')}
                    </span>
                  ))}
                </div>
              ])}
            />
          </Card>

          <div className="grid gap-4 xl:grid-cols-2">
            <Card title="Filo Mesafe Analizi" subtitle="Araç bazlı toplam sevkiyat mesafesi.">
              <ChartCard height="h-48">
                <BarChart data={reportChart} barSize={24}>
                  <CartesianGrid stroke="rgba(148,163,184,0.08)" vertical={false} />
                  <XAxis dataKey="id" stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 11 }} />
                  <YAxis stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 11 }} />
                  <Tooltip contentStyle={tooltipSt} cursor={{ fill: 'rgba(249,115,22,0.06)' }} />
                  <Bar dataKey="km" fill="#f97316" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ChartCard>
            </Card>

            <Card title="Filo Verimlilik Raporu" subtitle="Multi-stop vs Single-stop maliyet karşılaştırması ve doluluk analizi.">
              <div className="space-y-4">
                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-[10px] border border-white/[0.06] bg-[#111620] p-3">
                    <p className="text-[10px] uppercase tracking-widest text-slate-600">Multi-stop Ort.</p>
                    <p className="mt-1 text-[18px] font-bold text-white tabular-nums">
                      {Number(summary.multiStopAverageCost || 0).toFixed(1)}₺
                    </p>
                  </div>
                  <div className="rounded-[10px] border border-white/[0.06] bg-[#111620] p-3">
                    <p className="text-[10px] uppercase tracking-widest text-slate-600">Single-stop Ort.</p>
                    <p className="mt-1 text-[18px] font-bold text-slate-400 tabular-nums">
                      {Number(summary.singleStopAverageCost || 0).toFixed(1)}₺
                    </p>
                  </div>
                  <div className="rounded-[10px] border border-emerald-500/10 bg-emerald-500/5 p-3">
                    <p className="text-[10px] uppercase tracking-widest text-emerald-600">Net Tasarruf</p>
                    <p className="mt-1 text-[18px] font-bold text-emerald-400 tabular-nums">
                      {Number(summary.totalSavings || 0).toFixed(0)}₺
                    </p>
                  </div>
                </div>
                
                <div className="rounded-[10px] border border-white/[0.05] bg-[#111620] p-3">
                  <p className="text-[11px] text-slate-500 mb-2 font-semibold">ARAC DOLULUK TRENDİ (%)</p>
                  <div className="h-[80px]">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={loadChart}>
                        <Line type="monotone" dataKey="load" stroke="#22c55e" strokeWidth={2} dot={false} />
                        <Tooltip contentStyle={tooltipSt} />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
            </Card>
          </div>
        </div>
      </SidebarLayout>
    </section>
  )
})
