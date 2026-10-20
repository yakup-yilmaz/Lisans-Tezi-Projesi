import { useMemo, memo } from 'react'
import { Card, DataTable, StatusBadge } from '../components/ui'
import { ManuelControlPanel } from '../components/ManuelControlPanel'

function formatSimulationTime(tick) {
  const base = new Date('2026-03-29T08:00:00')
  base.setMinutes(base.getMinutes() + Number(tick || 0))
  return base.toLocaleString('tr-TR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function LoadIndicator({ fraction, volume, capacity }) {
  if (fraction == null || Number.isNaN(Number(fraction))) {
    return <div className="min-w-[132px] text-slate-600 text-[11px] italic">Hesaplaniyor...</div>
  }

  const pct = Math.max(0, Math.min(100, Math.round(Number(fraction) * 100)))
  // Dynamic color based on efficiency (75% is our optimal dispatch point)
  const barColor = pct >= 75 ? 'bg-gradient-to-r from-emerald-600 to-emerald-400' :
    pct >= 50 ? 'bg-gradient-to-r from-amber-500 to-orange-400' :
      'bg-slate-700'

  return (
    <div className="min-w-[140px] group">
      <div className="mb-1 flex items-center justify-between gap-2 text-[10px]">
        <span className={`font-bold tabular-nums ${pct >= 50 ? 'text-white' : 'text-slate-500'}`}>%{pct}</span>
        {volume != null && capacity != null ? (
          <span className="tabular-nums text-slate-500 font-medium">
            {Number(volume).toFixed(1)} / {Number(capacity).toFixed(1)} m³
          </span>
        ) : null}
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-white/[0.03] border border-white/[0.05]">
        <div
          className={`h-full rounded-full transition-all duration-1000 ease-out ${barColor}`}
          style={{ width: `${Math.max(pct, 4)}%` }}
        />
      </div>
    </div>
  )
}

export const OperationsScreen = memo(function OperationsScreen({
  nodes,
  products,
  inventory,
  orders,
  replenishments,
  vehicles = [],
  vehicleAssignments = [],
  dispatchReadiness = [],
  doAction,
  api,
}) {
  const inventoryByDepot = useMemo(() => {
    const map = {}
    inventory.forEach((item) => {
      map[item.depotId] ||= []
      map[item.depotId].push(item)
    })
    return map
  }, [inventory])
  const productMetaById = useMemo(
    () => new Map(products.map((product) => [product.id, product])),
    [products],
  )
  const vehicleById = useMemo(() => new Map(vehicles.map((vehicle) => [vehicle.id, vehicle])), [vehicles])
  const assignmentByVehicleId = useMemo(
    () => new Map(vehicleAssignments.map((assignment) => [assignment.vehicleId, assignment])),
    [vehicleAssignments],
  )
  const readinessByRequestId = useMemo(() => {
    const entries = []
    dispatchReadiness.forEach((readiness) => {
      ; (readiness.requestIds || []).forEach((requestId) => {
        entries.push([requestId, readiness])
      })
    })
    return new Map(entries)
  }, [dispatchReadiness])

  const enrichedReplenishments = useMemo(
    () =>
      replenishments.map((item) => {
        const readiness = readinessByRequestId.get(item.id)
        const resolvedVehicleId = item.assignedVehicleId || readiness?.vehicleId || null
        const assignment = resolvedVehicleId ? assignmentByVehicleId.get(resolvedVehicleId) : null
        const vehicle = resolvedVehicleId ? vehicleById.get(resolvedVehicleId) : null
        const loadFraction =
          readiness?.loadFraction ?? assignment?.loadFraction ?? vehicle?.loadFraction ?? null
        const capacityVolume =
          readiness?.capacityVolume ?? assignment?.capacityVolume ?? vehicle?.capacityVolume ?? null
        const currentVolume =
          readiness?.totalVolume ??
          assignment?.totalVolume ??
          (capacityVolume != null && loadFraction != null ? capacityVolume * loadFraction : null)

        return {
          ...item,
          createdLabel: formatSimulationTime(item.createdTick),
          resolvedVehicleId,
          resolvedRouteSummary:
            item.routeSummary || readiness?.routeSummary || assignment?.routeSummary || '',
          resolvedComparisonSummary:
            item.comparisonSummary || readiness?.comparisonSummary || assignment?.comparisonSummary || '',
          resolvedCargoSummary:
            item.cargoSummary || readiness?.cargoSummary || assignment?.cargoSummary || '',
          resolvedNetScore:
            item.netScore != null && Number(item.netScore) !== 0
              ? item.netScore
              : assignment?.netScore ?? null,
          loadFraction,
          capacityVolume,
          currentVolume,
          awaitingApproval: readiness?.awaitingApproval || false,
        }
      }),
    [replenishments, readinessByRequestId, assignmentByVehicleId, vehicleById],
  )

  // Vehicle-wise replenishment grouping para ikmal takibi
  const activeReplsByVehicle = useMemo(() => {
    const grouped = {}
    const activeRepls = enrichedReplenishments.filter((item) => item.status !== 'COMPLETED')

    activeRepls.forEach((repl) => {
      if (!repl.resolvedVehicleId) return
      if (!grouped[repl.resolvedVehicleId]) {
        grouped[repl.resolvedVehicleId] = []
      }
      grouped[repl.resolvedVehicleId].push(repl)
    })

    return Object.entries(grouped).map(([vehicleId, reps]) => {
      const totalVolume = reps.reduce((sum, r) => sum + Number(r.totalVolume || 0), 0)
      const totalItems = reps.reduce((sum, r) => sum + (r.items?.length || 0), 0)
      const loadFraction = reps[0]?.loadFraction || 0
      const capacityVolume = reps[0]?.capacityVolume || 0
      const hasApprovalPending = reps.some((r) => r.awaitingApproval)
      const hasInTransit = reps.some((r) => r.status === 'IN_TRANSIT')

      return {
        vehicleId,
        replenishments: reps,
        totalVolume,
        totalItems,
        loadFraction,
        capacityVolume,
        hasApprovalPending,
        hasInTransit,
      }
    })
  }, [enrichedReplenishments])





  const openRepl = enrichedReplenishments.filter((item) => item.status !== 'COMPLETED').length
  const completedRepl = enrichedReplenishments.filter((item) => item.status === 'COMPLETED').length
  const totalVolume = enrichedReplenishments.reduce((sum, item) => sum + Number(item.totalVolume || 0), 0)

  return (
    <section className="mt-4 space-y-4">
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Urun Katalogu" subtitle="Sistemdeki tanimli urunler ve teknik ozellikleri.">
          <div className="mt-2">
            <DataTable
              columns={['Kod', 'Ad', 'Kategori', 'Hacim', 'Agirlik', 'Deger (TL)', 'Capability']}
              rows={products.map((product) => [
                <span className="font-mono text-[12px] text-orange-300">{product.id}</span>,
                product.name,
                <span className="rounded-full bg-[#1c2230] px-2 py-0.5 text-[11px] text-slate-300">{product.category}</span>,
                <span className="tabular-nums">{product.unitVolume}</span>,
                <span className="tabular-nums">{product.unitWeight}</span>,
                <span className="tabular-nums font-bold text-sky-400">{Number(product.financialValue || 0).toFixed(1)} ₺</span>,
                (product.requiredCapabilities || []).join(', '),
              ])}
              emptyMessage="Henuz urun eklenmedi."
            />
          </div>
        </Card>

        <div className="space-y-4">
          <ManuelControlPanel
            nodes={nodes}
            products={products}
            inventory={inventory}
            replenishments={replenishments}
            api={api}
            doAction={doAction}
          />

          <Card title="Stok ve Depo Monitörü" subtitle="Mikro depolardaki anlık stok seviyeleri ve kritik eşik takibi.">
            <div className="grid gap-3 md:grid-cols-3">
              {Object.entries(inventoryByDepot).map(([depotId, items]) => (
                <div key={depotId} className="rounded-[12px] border border-white/[0.06] bg-[#111620] p-4">
                  <div className="mb-3 flex items-center justify-between border-b border-white/[0.03] pb-2">
                    <p className="text-[14px] font-bold text-white tracking-tight">{depotId}</p>
                    <span className="text-[10px] uppercase font-bold text-slate-600 bg-white/[0.03] px-1.5 py-0.5 rounded">{items.length} SKU</span>
                  </div>
                  <div className="space-y-2">
                    {items.slice(0, 10).map((item) => {
                      const isCritical = item.quantity <= 10 // Visual threshold for demo
                      const isDanger = item.quantity <= 3
                      return (
                        <div
                          key={`${depotId}-${item.productId}`}
                          className={`flex items-center justify-between p-1.5 rounded-lg transition-all ${isDanger ? 'stock-critical-pulse bg-red-500/5' : ''}`}
                        >
                          <div className="flex flex-col">
                            <span className="font-mono text-[11px] font-bold text-slate-400">{item.productId}</span>
                            {isDanger && <span className="text-[8px] font-bold text-red-500 uppercase">Critical Low</span>}
                          </div>
                          <span
                            className={`text-[13px] font-bold tabular-nums ${item.quantity <= 0 ? 'text-rose-500' : isCritical ? 'text-amber-400' : 'text-emerald-400'
                              }`}
                          >
                            {Number(item.quantity).toFixed(0)}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Siparis Listesi" subtitle="Sistemdeki mevcut siparisler ve durumları.">
          <div className="mt-2">
            <DataTable
              columns={['Siparis', 'Musteri', 'Mikro Depo', 'Satirlar', 'Hacim', 'Durum']}
              rows={orders.map((order) => [
                <span className="font-mono text-[12px] text-orange-300">{order.id}</span>,
                order.customerNodeId,
                order.microDepotNodeId || <span className="text-slate-600">-</span>,
                <span className="text-[12px] text-slate-400">
                  {order.items?.map((item) => `${item.productId} x${item.quantity}`).join(', ') || '-'}
                </span>,
                <span className="tabular-nums">{Number(order.demandVolume || 0).toFixed(2)} m3</span>,
                <StatusBadge status={order.status} />,
              ])}
              emptyMessage="Henuz siparis yok."
            />
          </div>
        </Card>

        <Card title="Ikmal Takibi" subtitle="Mikro depolara acilan destek sevkiyatlari.">
          <div className="mb-4 grid grid-cols-3 gap-3">
            {[
              { label: 'Acik Talep', value: openRepl, tone: openRepl > 0 ? 'text-orange-400' : 'text-white' },
              { label: 'Tamamlanan', value: completedRepl, tone: 'text-emerald-400' },
              { label: 'Toplam Hacim', value: `${totalVolume.toFixed(1)} m3`, tone: 'text-white' },
            ].map(({ label, value, tone }) => (
              <div key={label} className="rounded-[12px] border border-white/[0.06] bg-[#111620] p-3 text-center">
                <p className={`text-[22px] font-bold tabular-nums ${tone}`}>{value}</p>
                <p className="mt-0.5 text-[10px] uppercase tracking-[0.14em] text-slate-600">{label}</p>
              </div>
            ))}
          </div>

          <DataTable
            columns={['Kod', 'Saat', 'Kaynak -> Hedef', 'Durum', 'Arac', 'Doluluk', 'Satirlar', 'Hacim']}
            rows={enrichedReplenishments.map((item) => [
              <span className="font-mono text-[12px] text-orange-300">{item.id}</span>,
              <span className="whitespace-nowrap text-[12px] text-slate-400">{item.createdLabel}</span>,
              <span className="text-[12px]">
                {item.sourceDepotId} {'->'} {item.targetDepotId}
              </span>,
              <StatusBadge status={item.status} />,
              item.resolvedVehicleId ? (
                <div className="min-w-[132px]">
                  <p className="text-[12px] font-semibold text-sky-300">{item.resolvedVehicleId}</p>
                  <p className="mt-0.5 text-[11px] text-slate-500">
                    {item.resolvedRouteSummary || 'Arac plani hazir'}
                  </p>
                  {item.resolvedNetScore != null ? (
                    <p className="mt-0.5 text-[11px] font-semibold text-emerald-300">
                      Skor: {Number(item.resolvedNetScore).toFixed(1)}
                    </p>
                  ) : null}
                  {item.totalFinancialValue > 0 ? (
                    <p className="mt-0.5 text-[11px] font-semibold text-sky-400">
                      Kargo: {Number(item.totalFinancialValue).toLocaleString('tr-TR')} ₺
                    </p>
                  ) : null}
                </div>
              ) : (
                <span className="text-slate-600">-</span>
              ),
              <LoadIndicator
                fraction={item.loadFraction}
                volume={item.currentVolume}
                capacity={item.capacityVolume}
              />,
              <span className="text-[12px] text-slate-400">
                {item.items
                  ?.map((line) => {
                    const product = productMetaById.get(line.productId)
                    const category = product?.category ? ` (${product.category})` : ''
                    return `${line.productId}${category} x${line.quantity}`
                  })
                  .join(', ') || '-'}
              </span>,
              <span className="tabular-nums">{Number(item.totalVolume || 0).toFixed(2)} m3</span>,
            ])}
            emptyMessage="Henuz ikmal talebi yok."
          />

          <div className="mt-4 space-y-2 overflow-auto pr-1">
            {enrichedReplenishments
              .filter((item) => item.decisionReason)
              .slice(-5)
              .reverse()
              .map((item) => (
                <div key={item.id} className="rounded-[10px] border border-white/[0.05] bg-[#111620] p-3">
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="font-mono text-[11px] text-orange-300">{item.id}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-[11px] text-slate-500">{item.createdLabel}</span>
                      <StatusBadge status={item.status} />
                    </div>
                  </div>
                  <div className="mb-2 flex flex-wrap gap-2 text-[11px]">
                    <span className="rounded-full bg-[#1c2230] px-2 py-0.5 text-slate-400">
                      Arac: {item.resolvedVehicleId || '-'}
                    </span>
                    {item.loadFraction != null ? (
                      <span className="rounded-full bg-[#1c2230] px-2 py-0.5 text-slate-400">
                        Doluluk: %{Math.round(Number(item.loadFraction) * 100)}
                      </span>
                    ) : null}
                    {item.resolvedNetScore != null ? (
                      <span className="rounded-full bg-[#1c2230] px-2 py-0.5 text-emerald-300">
                        Net skor: {Number(item.resolvedNetScore).toFixed(1)}
                      </span>
                    ) : null}
                  </div>
                  <p className="text-[12px] leading-relaxed text-slate-400">{item.decisionReason}</p>
                  {item.resolvedComparisonSummary ? (
                    <p className="mt-1 text-[12px] leading-relaxed text-emerald-300/80">
                      {item.resolvedComparisonSummary}
                    </p>
                  ) : null}
                  {item.resolvedCargoSummary ? (
                    <p className="mt-1 text-[11px] uppercase tracking-[0.12em] text-orange-400/70">
                      {item.resolvedCargoSummary}
                    </p>
                  ) : null}
                </div>
              ))}
          </div>
        </Card>

        <Card title="Destek İkmal Takibi (Araç Bazlı)" subtitle="Araç başına konsolidasyonlar ve talep birleştirme durumu.">
          {activeReplsByVehicle.length === 0 ? (
            <div className="py-6 text-center">
              <p className="text-slate-500 text-[12px]">Şu anda atanmış ikmal talebi yok.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {activeReplsByVehicle.map((group) => {
                const vehicle = vehicleById.get(group.vehicleId)
                const pct = Math.round(group.loadFraction * 100)
                const needsApproval = group.hasApprovalPending && group.loadFraction >= 0.5 && group.loadFraction < 0.75
                const isInTransit = group.hasInTransit
                const isReady = group.loadFraction >= 0.75

                let bgColor = 'bg-[#0e1118]'
                let borderColor = 'border-white/[0.07]'
                if (needsApproval) {
                  bgColor = 'bg-amber-900/10'
                  borderColor = 'border-amber-500/30'
                } else if (isReady) {
                  bgColor = 'bg-emerald-900/10'
                  borderColor = 'border-emerald-500/30'
                } else if (isInTransit) {
                  bgColor = 'bg-blue-900/10'
                  borderColor = 'border-blue-500/30'
                }

                return (
                  <div key={group.vehicleId} className={`rounded-[12px] border ${borderColor} ${bgColor} p-4 transition-all`}>
                    {/* Vehicle Header with Status */}
                    <div className="mb-3 flex items-start justify-between gap-3">
                      <div className="flex-1">
                        <div className="mb-1 flex items-center gap-2">
                          <p className="text-[14px] font-bold text-white">{group.vehicleId}</p>
                          {needsApproval && (
                            <span className="rounded-full bg-amber-500/30 px-2 py-0.5 text-[10px] font-bold text-amber-300 uppercase">
                              🔔 Onay Bekliyor
                            </span>
                          )}
                          {isReady && (
                            <span className="rounded-full bg-emerald-500/30 px-2 py-0.5 text-[10px] font-bold text-emerald-300 uppercase">
                              ✓ Hazır
                            </span>
                          )}
                          {isInTransit && (
                            <span className="rounded-full bg-blue-500/30 px-2 py-0.5 text-[10px] font-bold text-blue-300 uppercase">
                              ➜ Yolda
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-slate-500">
                          {vehicle ? `${vehicle.plateNumber} · ${vehicle.driverName} · ${vehicle.fuelType}` : 'Araç bilgisi yok'}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-[13px] font-bold text-sky-400">{group.replenishments.length} talep</p>
                        <p className="text-[11px] text-slate-500">
                          {Number(group.totalVolume).toFixed(1)} m³ toplam
                        </p>
                      </div>
                    </div>

                    {/* Combined Load Indicator */}
                    <LoadIndicator
                      fraction={group.loadFraction}
                      volume={group.totalVolume}
                      capacity={group.capacityVolume}
                    />

                    {/* Consolidated Replenishments */}
                    <div className="mt-3 space-y-2 border-t border-white/[0.05] pt-3">
                      <p className="text-[11px] uppercase tracking-[0.12em] text-slate-600 font-bold">
                        {group.replenishments.length > 1 ? '📦 Birleştirilmiş Talepler' : 'Talep'}
                      </p>
                      {group.replenishments.map((rep) => (
                        <div key={rep.id} className={`text-[11px] flex items-center justify-between rounded-lg p-2.5 transition-all ${
                          rep.status === 'IN_TRANSIT' ? 'bg-blue-500/10 border border-blue-500/20' :
                          rep.awaitingApproval ? 'bg-amber-500/10 border border-amber-500/20' :
                          'bg-white/[0.02] border border-white/[0.05]'
                        }`}>
                          <div>
                            <span className="font-mono text-orange-300">{rep.id}</span>
                            <span className="mx-1 text-slate-600">→</span>
                            <span className="text-sky-300">{rep.targetDepotId}</span>
                            <span className="ml-2 text-slate-500">({rep.items?.length || 0} ürün)</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="tabular-nums font-semibold text-orange-300">
                              {Number(rep.totalVolume).toFixed(1)} m³
                            </span>
                            {rep.awaitingApproval && <span className="text-amber-400 text-xs">⏳</span>}
                            {rep.status === 'IN_TRANSIT' && <span className="text-blue-400 text-xs">➜</span>}
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Status Details & Manual Approval Button */}
                    {needsApproval && (
                      <div className="mt-4 border-t border-amber-500/30 pt-4 flex items-center justify-between gap-4">
                        <div className="flex-1">
                          <p className="text-[11px] font-bold text-amber-300 uppercase tracking-tight flex items-center gap-1.5">
                            <span className="relative flex h-2 w-2">
                              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                              <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500"></span>
                            </span>
                            Onay Bekliyor (%{pct} Doluluk)
                          </p>
                          <p className="mt-0.5 text-[10px] text-slate-400">
                            Sistem verimlilik için bekliyor. Manuel çıkış onayı verebilirsiniz.
                          </p>
                        </div>
                        <button
                          onClick={() => doAction(`Sevkiyat Onayı (${group.vehicleId})`, () => 
                            api.post('/api/simulation/approve-dispatch', null, { params: { vehicleId: group.vehicleId } })
                          )}
                          className="px-4 py-2 bg-gradient-to-r from-amber-600 to-orange-600 text-white text-[11px] font-bold rounded-lg shadow-[0_0_15px_rgba(217,119,6,0.3)] hover:shadow-[0_0_20px_rgba(217,119,6,0.5)] transition-all animate-pulse"
                        >
                          🚀 SEVKİYATI ONAYLA
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </Card>
      </div>
    </section>
  )
})
