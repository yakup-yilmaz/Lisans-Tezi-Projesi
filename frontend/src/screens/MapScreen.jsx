import { useMemo, useState, memo } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, XAxis, YAxis, Tooltip } from 'recharts'
import {
  ActionButton, Card, ChartCard, EmptyState, InfoList, Input,
  Panel, RouteSteps, Select, SectionLabel, StatusBadge,
} from '../components/ui'
import { MapCanvas } from '../components/MapCanvas'
import { TrafficHeatMap } from '../components/TrafficHeatMap'

const nodeTypes = ['MAIN_DEPOT', 'MICRO_DEPOT', 'INTERSECTION', 'CUSTOMER']
const MICRO_DEPOT_CAP_M3 = 100

function formatTickLabel(tick) {
  const base = new Date('2026-03-29T08:00:00')
  base.setMinutes(base.getMinutes() + Number(tick || 0))
  return base.toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function ReadinessBadge({ readiness }) {
  if (readiness.awaitingApproval) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-500/15 px-2.5 py-0.5 text-[11px] font-semibold text-amber-300">
        <span className="h-1.5 w-1.5 rounded-full bg-amber-400 animate-pulse" />
        Onay Bekliyor
      </span>
    )
  }
  if (readiness.readyToDispatch) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/15 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-300">
        <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
        Hazır
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-[#1c2230] px-2.5 py-0.5 text-[11px] font-semibold text-slate-400">
      Bekliyor
    </span>
  )
}

function LoadBar({ fraction, threshold }) {
  const pct = Math.round((fraction || 0) * 100)
  const thresholdPct = Math.round((threshold || 0) * 100)
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-[11px]">
        <span className="text-slate-500">Doluluk</span>
        <span className="font-semibold text-white tabular-nums">%{pct}</span>
      </div>
      <div className="relative h-1.5 overflow-hidden rounded-full bg-[#1c2230]">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{
            width: `${Math.max(pct, 2)}%`,
            background: pct >= thresholdPct ? '#22c55e' : '#f97316',
          }}
        />
        {/* threshold marker */}
        <div
          className="absolute top-0 h-full w-px bg-white/30"
          style={{ left: `${thresholdPct}%` }}
        />
      </div>
      <div className="flex justify-between text-[10px] text-slate-600">
        <span>0%</span>
        <span>Eşik: %{thresholdPct}</span>
        <span>100%</span>
      </div>
    </div>
  )
}

function formatMetric(value, digits = 1) {
  return Number(value || 0).toFixed(digits)
}

function ScoreBreakdown({ item, showThresholds = false }) {
  if (!item) return null

  const stopCount = Number(item.stopCount ?? item.plannedStopCount ?? 0)
  const hasScore = item.planCost != null || item.planBenefit != null || item.netScore != null

  return (
    <div className="space-y-2 rounded-[10px] border border-white/[0.05] bg-[#111620] p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[10px] uppercase tracking-[0.14em] text-slate-600">Skor mantigi</p>
        <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${stopCount > 1 ? 'bg-emerald-500/15 text-emerald-300' : 'bg-[#1c2230] text-slate-400'}`}>
          {stopCount > 1 ? `Multi-stop (${stopCount})` : `Single-stop (${stopCount || 1})`}
        </span>
      </div>

      {/* Hatali formul satiri kaldirildi, LES ve Maliyet alt tarafta kutucuklarda zaten mevcut */}


      <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-[11px] border-t border-white/[0.05] pt-3">
        <div className="flex flex-col">
          <span className="text-slate-500 uppercase text-[9px] font-bold tracking-wider">Tahmini Yakıt Maliyeti</span>
          <span className="text-orange-400 font-bold tabular-nums text-[13px]">{formatMetric(item.routeCost)} ₺</span>
        </div>
        <div className="flex flex-col">
          <span className="text-slate-500 uppercase text-[9px] font-bold tracking-wider">Verimlilik (LES)</span>
          <span className="text-sky-400 font-bold tabular-nums text-[13px]">{formatMetric(item.planBenefit, 0)} Puan</span>
        </div>
      </div>
      
      {item.cargoSummary && (
        <div className="mt-2 rounded-lg bg-emerald-500/5 border border-emerald-500/10 p-2">
           <p className="text-[10px] text-emerald-400/80 font-medium uppercase tracking-tight leading-tight">
             {item.cargoSummary}
           </p>
        </div>
      )}

      {item.comparisonSummary && (
        <div className="mt-2 rounded-lg bg-sky-500/5 border border-sky-500/10 p-2">
           <p className="text-[10px] text-sky-400 font-bold uppercase tracking-tight leading-tight">
             {item.comparisonSummary}
           </p>
        </div>
      )}

      {showThresholds ? (
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 border-t border-white/[0.05] pt-2 text-[11px]">
          <span className="text-slate-500">Min cikis esigi</span>
          <span className="text-white tabular-nums">%{Math.round(Number(item.minimumDepartureLoadFraction || 0) * 100)}</span>
          <span className="text-slate-500">Aninda cikis esigi</span>
          <span className="text-white tabular-nums">%{Math.round(Number(item.immediateDepartureLoadFraction || 0) * 100)}</span>
        </div>
      ) : null}
    </div>
  )
}

function VehicleDetailCard({ vehicle, assignment, onClose }) {
  if (!vehicle) return null
  const isActive = vehicle.status !== 'IDLE'

  return (
    <Card
      title={`Araç Detayı: ${vehicle.id}`}
      subtitle={`${vehicle.plateNumber || 'Plakasız'} · ${vehicle.fuelType}`}
      action={
        <button
          onClick={onClose}
          className="rounded-lg border border-white/10 px-2.5 py-1 text-[11px] font-medium text-slate-400 hover:border-white/20 hover:text-white"
        >
          Kapat
        </button>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
             <div className="h-12 w-12 flex items-center justify-center rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400">
                <span className="text-[18px]">🚛</span>
             </div>
             <div>
                <p className="text-[11px] font-bold text-slate-500 uppercase tracking-widest">Sürücü</p>
                <p className="text-[15px] font-bold text-white">{vehicle.driverName || 'Atanmamış'}</p>
             </div>
          </div>
          <StatusBadge status={vehicle.status} />
        </div>

        <LoadBar 
           fraction={vehicle.capacityVolume ? (Number(vehicle.currentLoad) || 0) / Number(vehicle.capacityVolume) : 0} 
           threshold={vehicle.minimumDepartureLoadFraction || 0.7} 
        />

        <div className="grid grid-cols-2 gap-3">
           <div className="rounded-xl border border-white/5 bg-[#111620] p-3">
              <p className="text-[10px] text-slate-500 uppercase font-bold mb-1">Mevcut Konum</p>
              <p className="text-[16px] font-bold text-white tabular-nums">{vehicle.locationNodeId}</p>
           </div>
           <div className="rounded-xl border border-white/5 bg-[#111620] p-3">
              <p className="text-[10px] text-slate-500 uppercase font-bold mb-1">Hız (Ort.)</p>
              <p className="text-[16px] font-bold text-sky-400 tabular-nums">{Number(vehicle.averageSpeedKmPerHour || 0).toFixed(0)} <span className="text-[11px] font-normal text-slate-500">km/h</span></p>
           </div>
        </div>

        {assignment ? (
          <div className="space-y-3 border-t border-white/10 pt-4">
             <SectionLabel>Aktif Görev</SectionLabel>
             <div className="rounded-xl border border-orange-500/20 bg-orange-500/5 p-3">
                <p className="text-[13px] font-semibold text-white mb-1">{assignment.routeSummary}</p>
                <div className="flex justify-between text-[11px]">
                   <span className="text-slate-500">Kalan Mesafe</span>
                   <span className="font-bold text-orange-400">{Number(assignment.distanceKm || 0).toFixed(1)} km</span>
                </div>
                <div className="mt-2 text-[12px] text-slate-400 leading-relaxed italic">
                   "{assignment.statusDetail || 'Görecek devam ediyor...'}"
                </div>
             </div>
             <ScoreBreakdown item={assignment} />
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-white/10 p-4 text-center">
             <p className="text-[12px] text-slate-500">Bu araç şu an bir görev icra etmiyor.</p>
          </div>
        )}
      </div>
    </Card>
  )
}

export const MapScreen = memo(function MapScreen({
  api, doAction, nodes, edges, vehicles,
  vehicleAssignments, dispatchReadiness, routePreview, routeForm, setRouteForm, loadRoute,
  simulationMsPerTick, lastStatusSyncAt, status,
  inventory = [],
  products = [],
}) {
  const routeSteps = routePreview?.nodeIds || []

  const [selectedMicroDepotNode, setSelectedMicroDepotNode] = useState(null)
  const [selectedVehicleId, setSelectedVehicleId] = useState(null)
  const [mapTransform, setMapTransform] = useState({ x: 0, y: 0, k: 0.8 })
  const [activePanel, setActivePanel] = useState('route') // 'route' | 'manage' | 'readiness' | 'assignments'
   const [trafficForm, setTrafficForm] = useState({ fromNodeId: '', toNodeId: '', trafficFactor: 1.4, locked: true, bidirectional: false })
  const [tollForm, setTollForm] = useState({ fromNodeId: '', toNodeId: '', tollCost: 2, bidirectional: false })

  const defaultFrom = nodes[0]?.id || ''
  const defaultTo   = nodes[Math.min(1, Math.max(0, nodes.length - 1))]?.id || defaultFrom

   const resolvedTraffic = {
    ...trafficForm,
    fromNodeId: trafficForm.fromNodeId || defaultFrom,
    toNodeId: trafficForm.toNodeId || defaultTo,
  }
  const resolvedToll = {
    ...tollForm,
    fromNodeId: tollForm.fromNodeId || defaultFrom,
    toNodeId: tollForm.toNodeId || defaultTo,
  }

  const mapMeta = useMemo(() => [
    { label: 'Toplam nokta',   value: nodes.length },
     { label: 'Toplam yol',     value: edges.length },
    { label: 'Paralı yol',     value: edges.filter((e) => Number(e.tollCost) > 0).length },
    { label: 'Kilitli trafik', value: edges.filter((e) => e.trafficLocked).length },
    { label: 'Aktif araç',     value: vehicles.filter((v) => v.status !== 'IDLE').length },
  ], [nodes, edges, vehicles])

   const topEdges = useMemo(
    () => [...edges]
      .sort((a, b) => (Number(b.trafficFactor) + Number(b.tollCost)) - (Number(a.trafficFactor) + Number(a.tollCost)))
      .slice(0, 4),
    [edges],
  )

  const microDepotData = useMemo(() => {
    if (!selectedMicroDepotNode?.id || !inventory.length) return { rows: [], totalM3: 0, categoryDist: [] }
    const rows = inventory
      .filter((item) => item.depotId === selectedMicroDepotNode.id)
      .map((item) => {
        const p = products.find((x) => x.id === item.productId)
        const unitVol = Number(p?.unitVolume ?? 0)
        const qty = Number(item.quantity ?? 0)
        return {
          productId: item.productId,
          name: p?.name || item.productId,
          category: p?.category || 'GENERAL',
          quantity: qty,
          lineM3: unitVol * qty,
          financialValue: p?.financialValue || 0,
        }
      })
    const totalM3 = rows.reduce((s, r) => s + r.lineM3, 0)
    
    const catMap = {}
    rows.forEach(r => {
      catMap[r.category] = (catMap[r.category] || 0) + r.lineM3
    })
    const categoryDist = Object.entries(catMap).map(([name, value]) => ({ name, value }))
    
    return { rows, totalM3, categoryDist }
  }, [selectedMicroDepotNode, inventory, products])

  const catColors = {
    FOOD: '#f97316',
    MEDICAL: '#10b981',
    ELECTRONICS: '#0ea5e9',
    CLEANING: '#a855f7',
    GENERAL: '#64748b'
  }

   const submitTraffic = () => doAction('Trafik güncellendi', () => api.put('/api/graph/edges/traffic', { ...resolvedTraffic, trafficFactor: Number(resolvedTraffic.trafficFactor) }))
  const submitToll    = () => doAction('Toll güncellendi', () => api.put('/api/graph/edges/toll', { ...resolvedToll, tollCost: Number(resolvedToll.tollCost) }))

  const approveDispatch = (r) => {
    if (!r?.awaitingApproval) return
    if (!window.confirm(`${r.vehicleId} aracı yola çıksın mı?`)) return
    doAction(`${r.vehicleId} dispatch başlatıldı`, () =>
      api.post('/api/simulation/dispatch-ready', null, { params: { vehicleId: r.vehicleId } }),
    )
  }

  const panelTabs = [
    { id: 'route',       label: 'Rota' },
    { id: 'readiness',   label: `Hazırlık${dispatchReadiness?.length ? ` (${dispatchReadiness.length})` : ''}` },
    { id: 'assignments', label: `Rotalar${vehicleAssignments?.length ? ` (${vehicleAssignments.length})` : ''}` },
    { id: 'manage',      label: 'Sistem Ayarlari' },
  ]

  return (
    <section className="relative mt-4 grid gap-4 xl:grid-cols-[1fr,380px]">
      {/* ── Sol: Harita ── */}
      <div className="space-y-4">
        <Card
          title="Canlı Harita"
          subtitle="Yollar, depolar, araçlar ve aktif rotalar."
          action={
            <div className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-[11px] font-medium text-emerald-400">Canlı</span>
            </div>
          }
        >
          <MapCanvas
            nodes={nodes} edges={edges} vehicles={vehicles}
            vehicleAssignments={vehicleAssignments} routePreview={routePreview}
            simulationMsPerTick={simulationMsPerTick}
            lastStatusSyncAt={lastStatusSyncAt}
            status={status}
            onSelectMicroDepotNode={setSelectedMicroDepotNode}
            onSelectVehicle={setSelectedVehicleId}
            selectedVehicleId={selectedVehicleId}
            transform={mapTransform}
            setTransform={setMapTransform}
          />
        </Card>

        {/* ── Filo İndeks Tablosu (Fleet Legend) ── */}
        <Card 
          title="Filo İndeks Listesi" 
          subtitle="Haritadaki numaraların detaylı araç dökümü."
          action={
            <div className="flex items-center gap-2 text-[10px] text-slate-500 uppercase font-bold">
               <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Aktif Filo
            </div>
          }
        >
          <div className="overflow-auto max-h-[400px]">
            <table className="w-full text-left text-[12px] border-collapse">
              <thead>
                <tr className="border-b border-white/5 text-slate-500 uppercase tracking-wider">
                  <th className="py-2 px-3">#</th>
                  <th className="py-2 px-3">Araç ID</th>
                  <th className="py-2 px-3">Sürücü</th>
                  <th className="py-2 px-3">Doluluk</th>
                  <th className="py-2 px-3">Durum</th>
                  <th className="py-2 px-3 text-right">İşlem</th>
                </tr>
              </thead>
              <tbody>
                {vehicles.map((v, idx) => {
                   const assignment = vehicleAssignments.find(a => a.vehicleId === v.id)
                   const loadPct = v.capacityVolume ? Math.round(((Number(v.currentLoad) || 0) / Number(v.capacityVolume)) * 100) : 0
                   const isSelected = selectedVehicleId === v.id

                   const handleFocus = (vehicle) => {
                      setSelectedVehicleId(vehicle.id)
                      const assignment = vehicleAssignments.find(a => a.vehicleId === vehicle.id)
                      
                      let targetX, targetY
                      
                      if (assignment?.currentFromNodeId && assignment?.currentToNodeId && vehicle.status === 'EN_ROUTE') {
                        // Hareket halindeyse, gerçek pozisyona zoom yap
                        const fromNode = nodes.find(n => n.id === assignment.currentFromNodeId)
                        const toNode = nodes.find(n => n.id === assignment.currentToNodeId)
                        
                        if (fromNode && toNode) {
                          const totalTicks = assignment.segmentTotalTicks || 1
                          const remainingTicks = assignment.segmentRemainingTicks || 0
                          const progress = (totalTicks - remainingTicks) / totalTicks
                          
                          targetX = fromNode.x + (toNode.x - fromNode.x) * progress
                          targetY = fromNode.y + (toNode.y - fromNode.y) * progress
                        }
                      } else {
                        // Duruşta ise, düğüm konumuna zoom yap
                        const node = nodes.find(n => n.id === vehicle.locationNodeId)
                        if (node) {
                          targetX = node.x
                          targetY = node.y
                        }
                      }
                      
                      if (targetX !== undefined && targetY !== undefined) {
                        // Zoom yap
                        const scale = 0.95
                        const offsetX = 50
                        const mapX = offsetX + targetX * scale
                        const mapY = offsetX + targetY * scale
                        setMapTransform({ x: 550 - mapX * 2, y: 500 - mapY * 2, k: 2 })
                      }
                   }

                   return (
                     <tr 
                       key={v.id} 
                       className={`border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors cursor-pointer ${isSelected ? 'bg-orange-500/10' : ''}`}
                       onClick={() => handleFocus(v)}
                     >
                        <td className="py-3 px-3">
                           <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#1c2230] font-bold text-white border border-white/5">
                              {idx + 1}
                           </span>
                        </td>
                        <td className="py-3 px-3 font-bold text-white">{v.id}</td>
                        <td className="py-3 px-3 text-slate-400">{v.driverName || '—'}</td>
                        <td className="py-3 px-3 min-w-[120px]">
                            <div className="flex flex-col gap-1">
                               <div className="h-1 w-full bg-[#1c2230] rounded-full overflow-hidden">
                                  <div className="h-full bg-emerald-500" style={{ width: `${loadPct}%` }} />
                               </div>
                               <span className="text-[10px] text-slate-500">%{loadPct}</span>
                            </div>
                        </td>
                        <td className="py-3 px-3"><StatusBadge status={v.status} /></td>
                        <td className="py-3 px-3 text-right">
                           <button className="text-sky-400 hover:text-white text-[10px] font-bold uppercase tracking-tighter">ODAKLA</button>
                        </td>
                     </tr>
                   )
                })}
              </tbody>
            </table>
          </div>
        </Card>

        {selectedVehicleId && (
          <VehicleDetailCard
            vehicle={vehicles.find(v => v.id === selectedVehicleId)}
            assignment={vehicleAssignments.find(a => a.vehicleId === selectedVehicleId)}
            onClose={() => setSelectedVehicleId(null)}
          />
        )}

        {selectedMicroDepotNode && (
          <Card
            title={`Depo Analizi: ${selectedMicroDepotNode.id}`}
            subtitle={`Kapasite Kullanimi: ${microDepotData.totalM3.toFixed(1)} / ${MICRO_DEPOT_CAP_M3} m³`}
            action={
              <button
                type="button"
                className="rounded-lg border border-white/10 px-2.5 py-1 text-[11px] font-medium text-slate-400 hover:border-white/20 hover:text-white"
                onClick={() => setSelectedMicroDepotNode(null)}
              >
                Kapat
              </button>
            }
          >
            <div className="grid gap-6 md:grid-cols-[1fr,200px]">
               {/* Linear Capacity Bar */}
               <div className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex justify-between text-[12px]">
                       <span className="text-slate-500 font-bold uppercase tracking-wider">Doluluk Orani</span>
                       <span className="text-white font-bold">%{Math.round((microDepotData.totalM3 / MICRO_DEPOT_CAP_M3) * 100)}</span>
                    </div>
                    <div className="h-4 overflow-hidden rounded-full bg-[#1c2230] border border-white/5 p-0.5">
                       <div 
                         className="h-full rounded-full bg-gradient-to-r from-sky-600 to-emerald-500 shadow-[0_0_12px_rgba(14,165,233,0.4)] transition-all duration-700" 
                         style={{ width: `${Math.max(2, (microDepotData.totalM3 / MICRO_DEPOT_CAP_M3) * 100)}%` }}
                       />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                     <div className="rounded-[12px] bg-[#111620] p-3 border border-white/5">
                        <p className="text-[10px] text-slate-500 uppercase font-bold mb-1">Kullanilan</p>
                        <p className="text-[18px] font-bold text-white tabular-nums">{microDepotData.totalM3.toFixed(1)} <span className="text-[12px] text-slate-500 font-normal">m³</span></p>
                     </div>
                     <div className="rounded-[12px] bg-[#111620] p-3 border border-white/5">
                        <p className="text-[10px] text-slate-500 uppercase font-bold mb-1">Bos Alan</p>
                        <p className="text-[18px] font-bold text-sky-400 tabular-nums">{(MICRO_DEPOT_CAP_M3 - microDepotData.totalM3).toFixed(1)} <span className="text-[12px] text-slate-500 font-normal">m³</span></p>
                     </div>
                  </div>
               </div>

               {/* Category Donut */}
               <div className="flex flex-col items-center justify-center border-l border-white/5 pl-4">
                  <div className="h-28 w-28 relative">
                     <ResponsiveContainer>
                        <PieChart>
                          <Pie 
                            data={microDepotData.categoryDist} 
                            innerRadius={30} 
                            outerRadius={45} 
                            paddingAngle={3} 
                            dataKey="value"
                          >
                            {microDepotData.categoryDist.map((entry, index) => (
                              <Cell key={`cell-${index}`} fill={catColors[entry.name] || '#334155'} />
                            ))}
                          </Pie>
                        </PieChart>
                     </ResponsiveContainer>
                     <div className="absolute inset-0 flex items-center justify-center text-[9px] font-bold text-slate-500 uppercase">
                        Stok
                     </div>
                  </div>
                  <div className="mt-2 flex flex-wrap justify-center gap-x-3 gap-y-1">
                     {microDepotData.categoryDist.map(d => (
                       <div key={d.name} className="flex items-center gap-1">
                          <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: catColors[d.name] }} />
                          <span className="text-[9px] font-bold text-slate-400 uppercase tracking-tighter">{d.name}</span>
                       </div>
                     ))}
                  </div>
               </div>
            </div>
            
            {/* Minimal Detailed Table Toggle or List */}
            <div className="mt-6 border-t border-white/5 pt-4">
               <SectionLabel className="mb-3">Urun Bazli Detay</SectionLabel>
               <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {microDepotData.rows.map(row => (
                    <div key={row.productId} className="flex flex-col rounded-lg bg-[#111620]/50 px-3 py-2 border border-white/[0.03]">
                       <div className="flex justify-between items-center">
                          <span className="text-[11px] text-slate-400 font-mono">{row.productId}</span>
                          <span className="text-[12px] font-bold text-white tabular-nums">{row.quantity}</span>
                       </div>
                       <div className="mt-1 flex justify-between items-center border-t border-white/5 pt-1">
                          <span className="text-[9px] text-slate-600 uppercase font-bold">Deger</span>
                          <span className="text-[10px] font-bold text-sky-400 tabular-nums">{Number(row.financialValue).toFixed(1)} ₺</span>
                       </div>
                    </div>
                  ))}
               </div>
            </div>
          </Card>
        )}

        {/* Harita özet istatistikleri */}
        <div className="grid grid-cols-5 gap-3">
          {mapMeta.map((item) => (
            <div key={item.label} className="rounded-[12px] border border-white/[0.07] bg-[#0e1118] p-3 text-center">
              <p className="text-[20px] font-bold text-white tabular-nums">{item.value}</p>
              <p className="mt-0.5 text-[10px] uppercase tracking-[0.14em] text-slate-600">{item.label}</p>
            </div>
          ))}
        </div>

        {/* Yüksek maliyetli edge'ler */}
        <Card title="Yüksek Maliyetli Yollar" subtitle="Trafik ve toll katsayısına göre sıralı.">
          <div className="grid gap-2 md:grid-cols-2">
            {topEdges.map((edge) => (
              <div key={`${edge.fromNodeId}-${edge.toNodeId}`} className="rounded-[10px] border border-white/[0.06] bg-[#111620] p-3">
                <div className="flex items-center justify-between gap-2 mb-1.5">
                  <p className="text-[13px] font-semibold text-white">
                    {edge.fromNodeId} <span className="text-slate-600">→</span> {edge.toNodeId}
                  </p>
                  {edge.trafficLocked && (
                    <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-[10px] font-medium text-amber-300">KİLİTLİ</span>
                  )}
                </div>
                <div className="grid grid-cols-3 gap-2 text-[11px]">
                  <div>
                    <p className="text-slate-600">Mesafe</p>
                    <p className="font-semibold text-slate-200 tabular-nums">{Number(edge.distanceKm).toFixed(1)} km</p>
                  </div>
                  <div>
                    <p className="text-slate-600">Trafik</p>
                    <p className={`font-semibold tabular-nums ${Number(edge.trafficFactor) > 1.5 ? 'text-rose-400' : Number(edge.trafficFactor) > 1.2 ? 'text-amber-400' : 'text-slate-200'}`}>
                      ×{Number(edge.trafficFactor).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-slate-600">Toll</p>
                    <p className={`font-semibold tabular-nums ${Number(edge.tollCost) > 0 ? 'text-orange-400' : 'text-slate-600'}`}>
                      {Number(edge.tollCost) > 0 ? `${Number(edge.tollCost).toFixed(1)}₺` : '—'}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>

         {/* Trafik Yoğunluk Haritası */}
         <TrafficHeatMap nodes={nodes} edges={edges} />

         {/* Trafik & Toll Açıklama Kartı */}
         <Card title="Sistem Açıklaması" subtitle="Trafik katsayısı ve ücretli yolların rota kararlarına etkisi.">
           <div className="space-y-4">
             {/* Trafik Katsayısı Açıklaması */}
             <div className="rounded-[12px] border border-emerald-500/15 bg-emerald-500/[0.04] p-4">
               <div className="flex items-center gap-2 mb-3">
                 <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-500/15 text-[14px]">🚦</span>
                 <p className="text-[13px] font-bold text-emerald-300">Trafik Katsayısı (trafficFactor)</p>
               </div>
               <div className="space-y-2 text-[12px] text-slate-400 leading-relaxed">
                 <p>
                   Her yol segmentinin bir <span className="text-white font-semibold">trafik katsayısı</span> vardır.
                   <span className="text-emerald-300 font-mono"> 1.0</span> = normal trafik,
                   <span className="text-amber-300 font-mono"> 2.0</span> = 2 kat yoğun,
                   <span className="text-rose-300 font-mono"> 0.7</span> = gece boş.
                 </p>
                 <p>
                   <span className="text-white font-semibold">Etkisi:</span> Dijkstra algoritmasında yol maliyeti{' '}
                   <span className="font-mono text-sky-300">mesafe × trafikKatsayısı</span> formülüyle hesaplanır.
                   Yoğun yollar otomatik olarak pahalılaşır ve araçlar alternatif rotaları tercih eder.
                 </p>
                 <p>
                   <span className="text-white font-semibold">Seyahat süresi:</span> Araç hızı{' '}
                   <span className="font-mono text-sky-300">baseHız / trafikKatsayısı</span> ile efektif hıza dönüşür.
                   ×2.5 katsayıda 60 km/h araç efektif 24 km/h'ye düşer.
                 </p>
               </div>
               <div className="mt-3 grid grid-cols-3 gap-2">
                 <div className="rounded-lg bg-emerald-500/10 p-2 text-center">
                   <p className="text-[16px] font-bold text-emerald-400">×0.7</p>
                   <p className="text-[9px] text-slate-500 uppercase">Gece (00-05)</p>
                 </div>
                 <div className="rounded-lg bg-amber-500/10 p-2 text-center">
                   <p className="text-[16px] font-bold text-amber-400">×2.5</p>
                   <p className="text-[9px] text-slate-500 uppercase">Sabah Zirvesi</p>
                 </div>
                 <div className="rounded-lg bg-rose-500/10 p-2 text-center">
                   <p className="text-[16px] font-bold text-rose-400">×2.0</p>
                   <p className="text-[9px] text-slate-500 uppercase">Akşam Zirvesi</p>
                 </div>
               </div>
             </div>

             {/* Toll Açıklaması */}
             <div className="rounded-[12px] border border-amber-500/15 bg-amber-500/[0.04] p-4">
               <div className="flex items-center gap-2 mb-3">
                 <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-500/15 text-[14px]">💰</span>
                 <p className="text-[13px] font-bold text-amber-300">Ücretli Yol (Toll Cost)</p>
               </div>
               <div className="space-y-2 text-[12px] text-slate-400 leading-relaxed">
                 <p>
                   Bazı yollar <span className="text-amber-300 font-semibold">otoyol/köprü geçiş ücreti</span> taşır.
                   Haritada <span className="text-amber-300">altın rengi çift çizgi</span> ile gösterilir.
                 </p>
                 <p>
                   <span className="text-white font-semibold">Rota maliyetine etkisi:</span>{' '}
                   <span className="font-mono text-sky-300">toplam = (mesafe × trafik × birimMaliyet) + (toll × tollÇarpan)</span>.
                   Dijkstra bu toplam maliyeti minimize eder.
                 </p>
                 <p>
                   <span className="text-white font-semibold">Ağır araç çarpanı:</span> Kapasitesi ≥ 25m³ olan araçlar
                   toll ücretini <span className="text-rose-300 font-mono">×1.35</span> çarpanla öder.
                   Bu, küçük araçları paralı yollara, büyük araçları alternatif rotalara yönlendirir.
                 </p>
               </div>
             </div>

             {/* Formül */}
             <div className="rounded-[12px] border border-sky-500/15 bg-sky-500/[0.04] p-4">
               <div className="flex items-center gap-2 mb-3">
                 <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-sky-500/15 text-[14px]">📐</span>
                 <p className="text-[13px] font-bold text-sky-300">Dijkstra Maliyet Formülü</p>
               </div>
               <div className="rounded-lg bg-[#0a1018] p-3 font-mono text-[13px] text-center leading-loose">
                 <span className="text-orange-300">EdgeCost</span>{' '}
                 <span className="text-slate-500">=</span>{' '}
                 <span className="text-sky-300">mesafe</span>{' '}
                 <span className="text-slate-500">×</span>{' '}
                 <span className="text-emerald-300">trafikKatsayısı</span>{' '}
                 <span className="text-slate-500">×</span>{' '}
                 <span className="text-purple-300">birimMaliyet</span>{' '}
                 <span className="text-slate-500">+</span>{' '}
                 <span className="text-amber-300">tollÜcreti</span>{' '}
                 <span className="text-slate-500">×</span>{' '}
                 <span className="text-rose-300">tollÇarpan</span>
               </div>
               <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px]">
                 <span className="text-slate-500">mesafe</span>
                 <span className="text-white">Yol uzunluğu (km)</span>
                 <span className="text-slate-500">trafikKatsayısı</span>
                 <span className="text-white">Rush-hour modeline göre 0.5–3.0</span>
                 <span className="text-slate-500">birimMaliyet</span>
                 <span className="text-white">Araç yakıt tipine göre (0.30–0.75)</span>
                 <span className="text-slate-500">tollÜcreti</span>
                 <span className="text-white">Yolun sabit geçiş ücreti (₺)</span>
                 <span className="text-slate-500">tollÇarpan</span>
                 <span className="text-white">Ağır araç: ×1.35, hafif: ×1.0</span>
               </div>
             </div>
           </div>
         </Card>
      </div>

      {/* ── Sağ: Panel ── */}
      <div className="space-y-4">
        {/* Tab switcher */}
        <div className="flex gap-1.5 rounded-[12px] border border-white/[0.07] bg-[#0e1118] p-1.5">
          {panelTabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setActivePanel(t.id)}
              className={`flex-1 rounded-[8px] py-2 text-[11px] font-semibold transition-all ${
                activePanel === t.id
                  ? 'bg-orange-500 text-white shadow-[0_0_12px_rgba(249,115,22,0.3)]'
                  : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* ── Rota paneli ── */}
        {activePanel === 'route' && (
          <Card title="Rota Önizleme" subtitle="Ağırlıklı en kısa yol hesabı.">
            <div className="space-y-3">
              <Select
                label="Çıkış noktası"
                value={routeForm.sourceNodeId}
                onChange={(v) => setRouteForm((s) => ({ ...s, sourceNodeId: v }))}
                options={nodes.map((n) => n.id)}
              />
              <Select
                label="Varış noktası"
                value={routeForm.targetNodeId}
                onChange={(v) => setRouteForm((s) => ({ ...s, targetNodeId: v }))}
                options={nodes.map((n) => n.id)}
              />
              <ActionButton onClick={loadRoute}>Rotayı Hesapla</ActionButton>

              {/* Rota sonucu */}
              <div className="rounded-[12px] border border-white/[0.06] bg-[#111620] p-3">
                <div className="flex items-center justify-between gap-2 mb-3">
                  <p className="text-[13px] font-semibold text-white">
                    {routeSteps.length ? `${routeSteps.length} nokta` : 'Rota bekleniyor'}
                  </p>
                  {routePreview && (
                    <div className="text-right">
                      <p className="text-[11px] text-slate-500">Toplam mesafe</p>
                      <p className="text-[13px] font-bold text-orange-400 tabular-nums">
                        {Number(routePreview.totalDistanceKm || 0).toFixed(2)} km
                      </p>
                    </div>
                  )}
                </div>
                <RouteSteps steps={routeSteps} />
              </div>
            </div>
          </Card>
        )}

        {/* ── Araç Hazırlık ── */}
        {activePanel === 'readiness' && (
          <div className="space-y-3">
            {!dispatchReadiness?.length && <EmptyState message="Çıkış için hazırlanan araç yok." />}
            {dispatchReadiness?.map((r) => (
              <div key={r.vehicleId} className="rounded-[14px] border border-white/[0.07] bg-[#0e1118] p-4 space-y-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-[14px] font-bold text-white">{r.vehicleId}</p>
                    <p className="text-[12px] text-slate-500 mt-0.5">{r.routeSummary}</p>
                  </div>
                  <ReadinessBadge readiness={r} />
                </div>

                <LoadBar fraction={r.loadFraction} threshold={r.minimumDepartureLoadFraction} />

                <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-[12px]">
                  <span className="text-slate-500">Hacim</span>
                  <span className="text-white tabular-nums">{Number(r.totalVolume || 0).toFixed(1)} / {Number(r.capacityVolume || 0).toFixed(1)} m³</span>
                  <span className="text-slate-500">Bekleme</span>
                  <span className="text-white tabular-nums">{r.waitTicks} / {r.maxWaitTicks} dk</span>
                  <span className="text-slate-500">Durak</span>
                  <span className="text-white tabular-nums">{r.stopCount}</span>
                  <span className="text-slate-500">İlk talep</span>
                  <span className="text-white">{formatTickLabel(r.firstRequestTick)}</span>
                  <span className="text-slate-500">Son güncelleme</span>
                  <span className="text-white">{formatTickLabel(r.lastRequestUpdateTick)}</span>
                </div>

                <div className="rounded-[8px] border border-white/[0.05] bg-[#111620] p-2.5 text-[12px] text-slate-400 leading-relaxed">
                  {r.decisionReason}
                </div>


                <ScoreBreakdown item={r} showThresholds />

                {r.awaitingApproval && (
                  <ActionButton onClick={() => approveDispatch(r)} variant="accent">
                    ✓ Aracı Hareket Ettir
                  </ActionButton>
                )}
              </div>
            ))}
          </div>
        )}

        {/* ── Aktif Rotalar ── */}
        {activePanel === 'assignments' && (
          <div className="space-y-3">
            {!vehicleAssignments?.length && <EmptyState message="Aktif görev atanmış araç yok." />}
            {vehicleAssignments?.map((a) => {
              const vehicle = vehicles.find((v) => v.id === a.vehicleId)
              return (
                <div key={a.vehicleId} className="rounded-[14px] border border-white/[0.07] bg-[#0e1118] p-4 space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-[14px] font-bold text-white">{a.vehicleId}</p>
                    {vehicle && <StatusBadge status={vehicle.status} />}
                  </div>

                  <p className="text-[12px] text-slate-500">{a.routeSummary}</p>

                  {/* Progress: kalan süre */}
                  <div className="space-y-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-500">Kalan süre</span>
                      <span className="font-semibold text-white tabular-nums">{a.remainingTotalTicks} dk</span>
                    </div>
                    <div className="h-1 overflow-hidden rounded-full bg-[#1c2230]">
                      <div
                        className="h-full rounded-full bg-sky-500 transition-all duration-500"
                        style={{
                          width: `${Math.max(2, 100 - Math.round((a.remainingTotalTicks / Math.max(a.remainingTotalTicks + (a.totalLoadingTicks || 0) + (a.totalUnloadingTicks || 0), 1)) * 100))}%`
                        }}
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[12px]">
                    <span className="text-slate-500">Tip</span>
                    <span className="text-white">{a.routeType}</span>
                    <span className="text-slate-500">Kalan durak</span>
                    <span className="text-white tabular-nums">{a.remainingStopCount}</span>
                    <span className="text-slate-500">Planlanan durak</span>
                    <span className="text-white tabular-nums">{a.plannedStopCount}</span>
                    <span className="text-slate-500">Yük</span>
                    <span className="text-white tabular-nums">{Number(a.totalVolume || 0).toFixed(1)} m³</span>
                    <span className="text-slate-500">Mesafe</span>
                    <span className="text-white tabular-nums">{Number(a.distanceKm || 0).toFixed(1)} km</span>
                    <span className="text-slate-500">Yükleme</span>
                    <span className="text-white tabular-nums">{a.totalLoadingTicks ?? 0} dk</span>
                    <span className="text-slate-500">Boşaltma</span>
                    <span className="text-white tabular-nums">{a.totalUnloadingTicks ?? 0} dk</span>
                  </div>

                  <div className="space-y-1.5 text-[12px] text-slate-500 border-t border-white/[0.04] pt-2">
                    <p>{a.statusDetail}</p>
                    {a.decisionReason && <p className="text-slate-500">{a.decisionReason}</p>}
                    {a.comparisonSummary && <p className="text-orange-400/70">{a.comparisonSummary}</p>}
                  </div>

                  <ScoreBreakdown item={a} />

                  {/* x = v.t Kanıtı */}
                  {a.currentFromNodeId && a.currentToNodeId && a.segmentRemainingTicks > 0 && (() => {
                    const activeEdge = edges.find(e => e.fromNodeId === a.currentFromNodeId && e.toNodeId === a.currentToNodeId)
                    if (!activeEdge || !vehicle) return null
                    
                    const x = Number(activeEdge.distanceKm)
                    const t = Number(a.segmentTotalTicks) / 60 // saat cinsinden toplam süre
                    const v = t > 0 ? x / t : 0
                    
                    const targetNode = a.remainingNodeIds && a.remainingNodeIds.length > 0 ? a.remainingNodeIds[a.remainingNodeIds.length - 1] : a.currentToNodeId
                    const expectedArrival = formatTickLabel(a.expectedCompletionTick)
                    const estimatedRemainingX = v * (Number(a.remainingTotalTicks) / 60)
                    
                    return (
                      <div className="rounded-[8px] border border-emerald-500/20 bg-emerald-500/[0.04] p-3 space-y-3">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-emerald-500/20 text-[12px]">🧮</span>
                          <p className="text-[12px] font-bold text-emerald-400">Canlı Fizik Kanıtı (x = v × t)</p>
                        </div>
                        
                        {/* Anlık Segment */}
                        <div>
                          <p className="text-[10px] text-slate-500 uppercase tracking-widest mb-1.5 font-semibold">Anlık İlerleme ({a.currentFromNodeId} → {a.currentToNodeId})</p>
                          <div className="grid grid-cols-3 gap-2 text-[11px] text-center bg-[#0a1018] p-2 rounded-lg font-mono border border-white/[0.02]">
                            <div>
                              <p className="text-slate-500 mb-0.5">Mesafe (x)</p>
                              <p className="text-emerald-300 font-semibold">{x.toFixed(2)} km</p>
                            </div>
                            <div>
                              <p className="text-slate-500 mb-0.5">Hız (v)</p>
                              <p className="text-emerald-300 font-semibold">{v.toFixed(1)} km/s</p>
                            </div>
                            <div>
                              <p className="text-slate-500 mb-0.5">Süre (t)</p>
                              <p className="text-emerald-300 font-semibold">{(t * 60).toFixed(0)} dk</p>
                            </div>
                          </div>
                        </div>

                        {/* Varış Projeksiyonu */}
                        <div className="border-t border-emerald-500/10 pt-2">
                          <p className="text-[10px] text-slate-500 uppercase tracking-widest mb-1.5 font-semibold">Varış Projeksiyonu (Hedef: {targetNode})</p>
                          <div className="text-[11px] text-slate-300 leading-relaxed bg-[#0a1018] p-2 rounded-lg border border-white/[0.02]">
                            <p>Aracın güncel ortalama hızı <strong>{v.toFixed(1)} km/s</strong> olarak korunursa;</p>
                            <p className="mt-1">Kalan <strong>{a.remainingTotalTicks} dakika</strong> yolculuk için (x = {v.toFixed(1)} × {(a.remainingTotalTicks / 60).toFixed(2)}) tahmini <strong>{estimatedRemainingX.toFixed(1)} km</strong> mesafe kat edilecek ve hedefe <strong className="text-amber-300">{expectedArrival}</strong> saatinde varılacaktır.</p>
                          </div>
                        </div>
                      </div>
                    )
                  })()}

                  {/* Rota */}
                  <div className="rounded-[8px] border border-white/[0.05] bg-[#111620] p-2.5">
                    <p className="text-[10px] uppercase tracking-[0.14em] text-slate-600 mb-1">Kalan rota</p>
                    <p className="text-[11px] text-amber-300/80 leading-relaxed font-mono">
                      {(a.remainingNodeIds || []).join(' → ') || '—'}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* ── Sistem Ayarları ── */}
        {activePanel === 'manage' && (
          <div className="space-y-4">
            <div className="rounded-[16px] border border-sky-500/20 bg-sky-500/5 p-4">
               <SectionLabel className="mb-2 text-sky-400">Canli Kontrol Merkezi</SectionLabel>
               <p className="text-[12px] text-slate-400 leading-relaxed font-medium">Bu panel üzerinden ağ parametrelerini gerçek zamanlı olarak manipüle edebilirsiniz.</p>
            </div>

             <Card title="Trafik Kontrolü" subtitle="Yol yoğunluklarını dinamik olarak belirleyin.">
              <div className="space-y-3">
                <div className="grid gap-2 grid-cols-2">
                  <Select label="Başlangıç" value={resolvedTraffic.fromNodeId} onChange={(v) => setTrafficForm((s) => ({ ...s, fromNodeId: v }))} options={nodes.map((n) => n.id)} />
                  <Select label="Bitiş" value={resolvedTraffic.toNodeId} onChange={(v) => setTrafficForm((s) => ({ ...s, toNodeId: v }))} options={nodes.map((n) => n.id)} />
                </div>
                <Input label="Trafik Katsayısı (1.0 = Normal)" type="number" value={resolvedTraffic.trafficFactor} onChange={(v) => setTrafficForm((s) => ({ ...s, trafficFactor: v }))} />
                <div className="flex gap-2">
                   <label className="flex flex-1 items-center gap-2 rounded-[10px] border border-white/[0.07] bg-[#111620] px-3 py-2 text-[12px] text-slate-300">
                      <input type="checkbox" checked={resolvedTraffic.locked} onChange={(e) => setTrafficForm((s) => ({ ...s, locked: e.target.checked }))} />
                      Katsayiyi Kilitle
                   </label>
                   <ActionButton onClick={submitTraffic} variant="accent">Guncelle</ActionButton>
                </div>
              </div>
            </Card>

            <Card title="Otoyol Ücretlendirme" subtitle="Paralı yolların maliyet yönetimi.">
              <div className="space-y-3">
                <div className="grid gap-2 grid-cols-2">
                   <Select label="Başlangıç" value={resolvedToll.fromNodeId} onChange={(v) => setTollForm((s) => ({ ...s, fromNodeId: v }))} options={nodes.map((n) => n.id)} />
                   <Select label="Bitiş" value={resolvedToll.toNodeId} onChange={(v) => setTollForm((s) => ({ ...s, toNodeId: v }))} options={nodes.map((n) => n.id)} />
                </div>
                <Input label="Toll Ücreti (₺)" type="number" value={resolvedToll.tollCost} onChange={(v) => setTollForm((s) => ({ ...s, tollCost: v }))} />
                <div className="flex gap-2">
                   <label className="flex flex-1 items-center gap-2 rounded-[10px] border border-white/[0.07] bg-[#111620] px-3 py-2 text-[12px] text-slate-300">
                      <input type="checkbox" checked={resolvedToll.bidirectional} onChange={(e) => setTollForm((s) => ({ ...s, bidirectional: e.target.checked }))} />
                      Cift Yonlu
                   </label>
                   <ActionButton onClick={submitToll} variant="secondary">Kaydet</ActionButton>
                </div>
              </div>
            </Card>


            {/* Rush-Hour Trafik Modeli Açıklaması */}
            <Card title="Rush-Hour Trafik Modeli" subtitle="Simülasyon saatine göre otomatik trafik güncellemesi.">
              <div className="space-y-3">
                <p className="text-[12px] text-slate-400 leading-relaxed">
                  Simülasyon, <span className="text-white font-semibold">1440 tick = 24 saat</span> döngüsüyle çalışır.
                  Her tick'te tüm yolların trafik katsayısı, günün saatine göre otomatik güncellenir.
                  Kilitlenmiş yollar bu güncellemeden muaftır.
                </p>
                <div className="overflow-hidden rounded-[10px] border border-white/[0.06]">
                  <table className="w-full text-[11px]">
                    <thead>
                      <tr className="border-b border-white/[0.06] bg-[#111620]">
                        <th className="py-2 px-3 text-left text-slate-500 font-bold uppercase">Saat</th>
                        <th className="py-2 px-3 text-left text-slate-500 font-bold uppercase">Dönem</th>
                        <th className="py-2 px-3 text-right text-slate-500 font-bold uppercase">Katsayı</th>
                      </tr>
                    </thead>
                    <tbody className="text-[11px]">
                      {[
                        { saat: '00:00–05:00', donem: 'Gece', katsayi: '×0.7', color: 'text-emerald-400' },
                        { saat: '05:00–08:00', donem: 'Sabah Erken', katsayi: '0.7→2.5', color: 'text-yellow-400' },
                        { saat: '08:00–10:00', donem: 'Sabah Zirvesi', katsayi: '×2.5', color: 'text-rose-400' },
                        { saat: '10:00–17:00', donem: 'Öğleden Sonra', katsayi: '×1.0', color: 'text-sky-400' },
                        { saat: '17:00–19:00', donem: 'Akşam Zirvesi', katsayi: '×2.0', color: 'text-orange-400' },
                        { saat: '19:00–24:00', donem: 'Gece Geç', katsayi: '2.0→0.9', color: 'text-emerald-300' },
                      ].map(row => (
                        <tr key={row.saat} className="border-b border-white/[0.03]">
                          <td className="py-1.5 px-3 text-slate-300 font-mono">{row.saat}</td>
                          <td className="py-1.5 px-3 text-slate-400">{row.donem}</td>
                          <td className={`py-1.5 px-3 text-right font-bold tabular-nums ${row.color}`}>{row.katsayi}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="text-[11px] text-slate-500 italic">
                  Her tick'te ±15% rastgele jitter uygulanır. Rota cache'i trafik değiştiğinde otomatik invalidate edilir.
                </p>
              </div>
            </Card>
          </div>
        )}
      </div>
    </section>
  )
})
