import { useMemo, memo } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { tooltipStyle } from '../components/chartTheme'
import { ActionButton, Card, ChartCard, DataTable, Input, ProgressBar, SectionLabel, Select, StatusBadge } from '../components/ui'

const tooltipSt = {
  ...tooltipStyle,
  backgroundColor: '#111620',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 10,
}

const VehicleCard = memo(function VehicleCard({ vehicle, assignment }) {
  const isActive = vehicle.status !== 'IDLE'
  const isHeavy = (vehicle.capacityVolume || 0) >= 46

  return (
    <div
      className={`rounded-[14px] border bg-[#0e1118] p-4 transition-all duration-300 ${
        isActive ? 'border-orange-500/25 ring-1 ring-orange-500/10' : 'border-white/[0.07]'
      }`}
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className={`flex h-9 w-9 items-center justify-center rounded-[10px] text-[11px] font-bold ${
              isActive ? 'bg-orange-500/20 text-orange-300' : 'bg-[#161b24] text-slate-400'
            }`}
          >
            {isHeavy ? 'HGV' : 'LGV'}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <p className="text-[14px] font-bold leading-tight text-white">{vehicle.id}</p>
              <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wider ${isHeavy ? 'bg-amber-500/10 text-amber-500' : 'bg-sky-500/10 text-sky-500'}`}>
                {isHeavy ? 'HEAVY' : 'LIGHT'}
              </span>
            </div>
            <p className="text-[11px] text-slate-500">
              {vehicle.plateNumber || 'Plakasiz'} · {vehicle.driverName || 'Surucu yok'}
            </p>
          </div>
        </div>
        <StatusBadge status={vehicle.status} />
      </div>

      <ProgressBar
        value={vehicle.currentLoad || 0}
        max={vehicle.capacityVolume || 1}
        tone={isActive ? 'orange' : 'sky'}
      />

      <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1.5 text-[12px]">
        <span className="text-slate-500">Konum</span>
        <span className="font-medium text-white">{vehicle.locationNodeId}</span>
        <span className="text-slate-500">Yakit</span>
        <span className="font-medium text-white">{vehicle.fuelType}</span>
        <span className="text-slate-500">Maliyet</span>
        <span className="font-medium tabular-nums text-white">{Number(vehicle.costPerKm || 0).toFixed(2)} ₺/km {isHeavy && <span className="text-[10px] text-amber-500/80">(×1.35 Gise)</span>}</span>
        <span className="text-slate-500">Kapasite</span>
        <span className="font-medium tabular-nums text-white">{vehicle.capacityVolume} m³</span>
      </div>

      {assignment && (
        <div className="mt-3 space-y-2 border-t border-white/[0.05] pt-3 text-[12px]">
          <div className="flex items-center justify-between">
            <span className="text-slate-500 font-semibold uppercase tracking-wider text-[10px]">Aktif Görev Detayı</span>
            <span className="font-bold text-sky-400 tabular-nums text-[11px]">{assignment.routeType}</span>
          </div>
          <div className="grid grid-cols-2 gap-y-1 bg-white/[0.02] p-2 rounded-lg">
             <span className="text-slate-500">Kalan Sure</span>
             <span className="text-right font-bold tabular-nums text-white">{assignment.remainingTotalTicks} dk</span>
             <span className="text-slate-500">Mesafe</span>
             <span className="text-right font-medium tabular-nums text-white">{Number(assignment.distanceKm || 0).toFixed(1)} km</span>
             <span className="text-slate-500">Yüklenme</span>
             <span className="text-right font-medium tabular-nums text-emerald-400">%{Math.round((assignment.totalVolume / vehicle.capacityVolume) * 100)}</span>
          </div>
          
          {assignment.netScore != null && (
            <div className="rounded-[10px] border border-white/[0.05] bg-[#111620] p-2.5">
              <p className="text-[10px] uppercase tracking-[0.12em] text-slate-600">Optimizasyon Skoru</p>
              <div className="flex items-end justify-between mt-1">
                 <p className="text-[11px] text-slate-400">
                    Net = {Number(assignment.planBenefit || 0).toFixed(0)} - {Number(assignment.planCost || 0).toFixed(0)}
                 </p>
                 <span className={`text-[14px] font-bold tabular-nums ${Number(assignment.netScore || 0) >= 0 ? 'text-emerald-300' : 'text-rose-300'}`}>
                   {Number(assignment.netScore || 0).toFixed(1)}
                 </span>
              </div>
            </div>
          )}
          {assignment.decisionReason && (
            <p className="leading-relaxed text-slate-500 text-[11px] italic bg-white/[0.01] p-2 rounded">
               "{assignment.decisionReason}"
            </p>
          )}
        </div>
      )}
    </div>
  )
})

export const FleetScreen = memo(function FleetScreen({ vehicles, vehicleAssignments, reports, summary }) {
  const safeReports = useMemo(() => Array.isArray(reports) ? reports : [], [reports])
  const safeVehicles = useMemo(() => Array.isArray(vehicles) ? vehicles : [], [vehicles])
  const safeAssignments = useMemo(() => Array.isArray(vehicleAssignments) ? vehicleAssignments : [], [vehicleAssignments])

  const reportChart = useMemo(() => safeReports.map((item) => ({ 
    id: item?.vehicleId || '?', 
    km: Number(item?.totalDistanceKm || 0), 
    cost: Number(item?.totalCost || 0) 
  })), [safeReports])
  
  const loadChart = useMemo(() => safeVehicles.map((vehicle) => {
    const val = vehicle?.capacityVolume ? (Number(vehicle?.currentLoad) || 0) / Number(vehicle?.capacityVolume) : 0
    return { id: vehicle?.id || '?', load: Math.round(val * 100) }
  }), [safeVehicles])
  
  const activeCount = useMemo(() => safeVehicles.filter((vehicle) => vehicle?.status !== 'IDLE').length, [safeVehicles])
  const totalCapacity = useMemo(() => safeVehicles.reduce((sum, vehicle) => sum + (Number(vehicle?.capacityVolume) || 0), 0), [safeVehicles])

  return (
    <section className="mt-4 space-y-6">
      {/* ── PHASE 1: Real-time Monitor Header ── */}
      <div className="grid gap-4 xl:grid-cols-2">
         {/* Live Trends */}
         <Card title="Operasyonel Trendler" subtitle="Arac bazli mesafe ve doluluk grafigi.">
          <div className="grid gap-4 md:grid-cols-2">
            <ChartCard title="Mesafe (KM)" height="h-44">
              <BarChart data={reportChart} barSize={16}>
                <CartesianGrid stroke="rgba(14,165,233,0.05)" vertical={false} />
                <XAxis dataKey="id" stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 9 }} />
                <YAxis stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 9 }} />
                <Tooltip contentStyle={tooltipSt} cursor={{ fill: 'rgba(56,189,248,0.06)' }} />
                <Bar dataKey="km" fill="#0ea5e9" radius={[4, 4, 0, 0]} isAnimationActive={false} />
              </BarChart>
            </ChartCard>

            <ChartCard title="Doluluk Orani (%)" height="h-44">
              <LineChart data={loadChart}>
                <CartesianGrid stroke="rgba(14,165,233,0.05)" vertical={false} />
                <XAxis dataKey="id" stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 9 }} />
                <YAxis stroke="#475569" tickLine={false} axisLine={false} tick={{ fontSize: 9 }} domain={[0, 100]} />
                <Tooltip contentStyle={tooltipSt} />
                <Line type="monotone" dataKey="load" stroke="#34d399" strokeWidth={2} dot={{ r: 3, fill: '#34d399' }} isAnimationActive={false} />
              </LineChart>
            </ChartCard>
          </div>
        </Card>

        {/* Live Occupancy Grid */}
        <Card title="Canli Filo Takibi" subtitle="Tum araclarin anlik doluluk yuzdeleri.">
           <div className="grid grid-cols-4 sm:grid-cols-5 gap-3">
              {safeVehicles.map(v => {
                const vid = String(v?.id || '')
                const pct = v?.capacityVolume ? Math.round(((Number(v?.currentLoad) || 0) / Number(v?.capacityVolume)) * 100) : 0
                const data = [{ value: pct }, { value: 100 - pct }]
                const isActive = v?.status !== 'IDLE'
                
                return (
                  <div key={vid} className="flex flex-col items-center">
                    <div className="relative h-14 w-14 flex items-center justify-center">
                      <PieChart width={56} height={56}>
                        <Pie data={data} innerRadius={18} outerRadius={26} paddingAngle={0} dataKey="value" startAngle={90} endAngle={-270} isAnimationActive={false}>
                           <Cell fill={isActive ? '#0ea5e9' : '#1e293b'} />
                           <Cell fill="rgba(255,255,255,0.05)" />
                        </Pie>
                      </PieChart>
                      <div className="absolute inset-0 flex items-center justify-center text-[10px] font-bold text-white pointer-events-none">
                        %{pct}
                      </div>
                    </div>
                    <span className="mt-1 text-[9px] font-bold text-slate-500 uppercase tracking-tighter truncate w-full text-center">
                      {vid}
                    </span>
                  </div>
                )
              })}
           </div>
           <div className="mt-4 flex gap-4 border-t border-white/[0.05] pt-3">
              <div className="text-center flex-1">
                 <p className="text-[18px] font-bold text-white">{safeVehicles.length}</p>
                 <p className="text-[9px] uppercase tracking-widest text-slate-500">Filo</p>
              </div>
              <div className="text-center flex-1">
                 <p className="text-[18px] font-bold text-sky-400">{activeCount}</p>
                 <p className="text-[9px] uppercase tracking-widest text-slate-500">Aktif</p>
              </div>
              <div className="text-center flex-1">
                 <p className="text-[18px] font-bold text-emerald-400">{totalCapacity.toFixed(0)}</p>
                 <p className="text-[9px] uppercase tracking-widest text-slate-500">m³ Kap.</p>
              </div>
           </div>
        </Card>
      </div>

      {/* ── PHASE 2: Detailed Cards & Management ── */}
      <div className="grid gap-4 xl:grid-cols-[1fr,300px]">
        {/* Vehicle Grid */}
        <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
          {safeVehicles.map((vehicle) => (
            <VehicleCard
              key={vehicle.id}
              vehicle={vehicle}
              assignment={safeAssignments.find((assignment) => assignment.vehicleId === vehicle.id)}
            />
          ))}
        </div>

           <div className="rounded-[14px] border border-white/[0.05] bg-[#0e1118] p-4 h-fit">
              <SectionLabel className="mb-3">Operasyonel Kurallar</SectionLabel>
              <div className="space-y-3">
                {[
                  { l: 'Doluluk <50%', v: 'Engellendi', d: 'Araçlar %50 doluluğuna ulaşmadan yola çıkmaz.' },
                  { l: 'Doluluk 50-74%', v: '180 dk', d: 'Maksimum 3 saat bekleme, sonra mevcut yükle çıkar.' },
                  { l: 'Doluluk ≥75%', v: 'Anlık', d: 'Öncelikli eşik geçildiğinde derhal çıkış yapar.' },
                  { l: 'Max Durak', v: '3', d: 'Sabit kapasite yönetimi için 3 nokta ile sınırlı.' },
                  { l: 'Ölü KM (Deadhead)', v: 'İzole', d: 'Dönüş mesafesi çoklu durak sapma hesabından muaf tutulur.' },
                ].map(rule => (
                  <div key={rule.l}>
                    <div className="flex justify-between text-[11px] mb-1">
                      <span className="text-slate-400 font-bold uppercase">{rule.l}</span>
                      <span className="text-sky-400 font-bold">{rule.v}</span>
                    </div>
                    <p className="text-[10px] text-slate-500 leading-tight">{rule.d}</p>
                  </div>
                ))}
              </div>
           </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">


        <Card title="Seans Operasyon Raporu" subtitle="Simülasyon başlangıcından itibaren operasyon özeti ve finansal analiz.">
          <div className="mb-4 grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[
              { label: 'Toplam Maliyet', value: `${Number(summary.totalCost || 0).toFixed(1)} TL`, tone: 'text-orange-400' },
              { label: 'Toplam Kargo Değeri', value: `${Number(summary.totalCargoValue || 0).toLocaleString('tr-TR')} ₺`, tone: 'text-sky-400' },
              { label: 'Genel ROI (Verimlilik)', value: `${(Number(summary.totalCargoValue || 0) / Math.max(1, Number(summary.totalCost || 0))).toFixed(2)}x`, tone: 'text-emerald-400' },
              { label: 'Araç Kullanım Oranı', value: `%${Number(summary.vehicleUtilizationPercentage || 0).toFixed(1)}`, tone: 'text-white' },
            ].map(({ label, value, tone }) => (
              <div key={label} className="rounded-[10px] border border-white/[0.06] bg-[#111620] p-3">
                <p className="text-[10px] uppercase tracking-[0.14em] text-slate-600">{label}</p>
                <p className={`mt-1.5 text-[18px] font-bold tabular-nums ${tone}`}>{value}</p>
              </div>
            ))}
          </div>

          {summary.comparisonSummary && (
            <div className="mb-4 rounded-[10px] border border-orange-500/20 bg-orange-500/8 p-3 text-[12px] leading-relaxed text-orange-200 italic">
              " {summary.comparisonSummary} "
            </div>
          )}

          <DataTable
            columns={['Arac', 'Sefer', 'Multi', 'Single', 'Maliyet', 'LES (Puan)', 'ROI', 'Kargo Değeri']}
            rows={safeReports.map((report) => [
              <span className="font-semibold text-white">{report?.vehicleId}</span>,
              <span className="tabular-nums">{report?.tripCount}</span>,
              <span className="tabular-nums text-amber-400">{report?.multiStopTrips}</span>,
              <span className="tabular-nums">{report?.singleStopTrips}</span>,
              <span className="tabular-nums text-orange-400">{Number(report?.totalCost || 0).toFixed(1)} ₺</span>,
              <span className={`font-semibold tabular-nums text-emerald-400`}>
                {Number(report?.averageNetScore || 0).toFixed(1)} P
              </span>,
              <span className="tabular-nums text-sky-400 font-bold">
                {(Number(report?.totalCargoValue || 0) / Math.max(1, Number(report?.totalCost || 0))).toFixed(2)}x
              </span>,
              <span className="tabular-nums text-white font-medium">{Number(report?.totalCargoValue || 0).toLocaleString('tr-TR')} ₺</span>,
            ])}
            emptyMessage="Henüz tamamlanmış sefer verisi yok."
          />
        </Card>
      </div>
    </section>
  )
})
