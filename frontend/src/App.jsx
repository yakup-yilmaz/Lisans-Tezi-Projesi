import { useCallback, useEffect, useMemo, useRef, useState, lazy, Suspense, memo } from 'react'
import axios from 'axios'
import { ActionButton, AlertTicker, ClockPanel, Notice, PageHeader, Shell, TabButton, ToastContainer, ScreenSkeleton } from './components/ui'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  timeout: 8000,
})

// Lazy load screens for better performance
const OverviewScreen = lazy(() => import('./screens/OverviewScreen').then(m => ({ default: m.OverviewScreen })))
const MapScreen = lazy(() => import('./screens/MapScreen').then(m => ({ default: m.MapScreen })))
const OperationsScreen = lazy(() => import('./screens/OperationsScreen').then(m => ({ default: m.OperationsScreen })))
import { FleetScreen } from './screens/FleetScreen';
import LoginScreen from './LoginScreen';

const tabs = [
  { id: 'overview', label: 'Genel Bakis', icon: '◉' },
  { id: 'map', label: 'Canli Harita', icon: '⬡' },
  { id: 'operations', label: 'Operasyonlar', icon: '⊞' },
  { id: 'fleet', label: 'Canli Izleme & Filo', icon: '⬙' },
]

const SIMULATION_MS_PER_TICK = 500
const SNAPSHOT_REFRESH_MS = 1000

const EMPTY_STATUS = {
  running: false,
  phase: 'READY',
  currentTick: 0,
  maxTicks: 0,
  errorMessage: '',
  failureTick: -1,
  reorderPointFraction: 0.3,
}

const EMPTY_SUMMARY = {
  totalCost: 0,
  totalOperationTicks: 0,
  vehicleUtilizationPercentage: 0,
  averageDeliveryTicks: 0,
  totalTrips: 0,
  multiStopTrips: 0,
  singleStopTrips: 0,
  multiStopAverageCost: 0,
  singleStopAverageCost: 0,
  totalSavings: 0,
  totalCargoValue: 0,
  co2Saved: 0,
  co2Emissions: 0,
  comparisonSummary: '',
}

const EMPTY_KPIS = {
  totalOrders: 0,
  completedOrders: 0,
  totalDistance: 0,
  avgDeliveryTime: 0,
}

const INITIAL_SIM_DATA = {
  status: EMPTY_STATUS,
  nodes: [],
  edges: [],
  products: [],
  inventory: [],
  orders: [],
  replenishments: [],
  vehicleReplenishments: [],
  vehicles: [],
  vehicleAssignments: [],
  dispatchReadiness: [],
  reports: [],
  timeline: [],
  summary: EMPTY_SUMMARY,
  kpis: EMPTY_KPIS,
}

function normalizeStatus(status = {}) {
  return {
    ...EMPTY_STATUS,
    ...status,
    phase: status.phase || (status.running ? 'RUNNING' : status.currentTick > 0 ? 'PAUSED' : 'READY'),
  }
}

function buildMicroAlert(order) {
  return {
    id: order.id,
    title: `Yeni Siparis ${order.id}`,
    subtitle: `${order.customerNodeId} -> ${order.microDepotNodeId}`,
    meta: `${Number(order.demandVolume || 0).toFixed(2)} m3 · ${order.status || 'OLUSTU'}`,
  }
}

function buildCapacityDispatchAlert(dr) {
  const vid = dr.vehicleId || 'ARAC'
  return {
    id: `cap-${vid}-${dr.referenceId || 'ref'}`,
    title: `Dikkat: ${vid} %50+ kapasite`,
    subtitle: 'Cikis icin onay bekleniyor',
    meta: `Doluluk: ${((dr.loadFraction || 0) * 100).toFixed(0)}% · WAITING_FOR_APPROVAL`,
  }
}

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [tab, setTab] = useState('overview')
  const [simData, setSimData] = useState(INITIAL_SIM_DATA)
  const [routePreview, setRoutePreview] = useState(null)
  const [routeForm, setRouteForm] = useState({ sourceNodeId: '', targetNodeId: '', tollPenalty: 1 })
  const [reportPeriod, setReportPeriod] = useState('daily')
  const [tickStep, setTickStep] = useState(60)
  const [microAlerts, setMicroAlerts] = useState([])
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [currentTimeMs, setCurrentTimeMs] = useState(() => Date.now())
  const [lastStatusSyncAt, setLastStatusSyncAt] = useState(() => Date.now())
  const seenOrderIds = useRef(new Set())
  const seenCapacityAlertKeys = useRef(new Set())
  const seenAssignmentsRef = useRef(new Set())
  const simDataRef = useRef(INITIAL_SIM_DATA)
  
  const [toasts, setToasts] = useState([])
  const removeToast = useCallback((id) => setToasts(t => t.filter(x => x.id !== id)), [])

  const {
    status,
    nodes,
    edges,
    products,
    inventory,
    orders,
    replenishments,
    vehicles,
    vehicleAssignments,
    dispatchReadiness,
    reports,
    timeline,
    summary,
    kpis,
  } = simData

  const defaultFrom = nodes[0]?.id || ''
  const defaultTo = nodes[Math.min(1, Math.max(0, nodes.length - 1))]?.id || defaultFrom
  const resolvedRouteForm = {
    ...routeForm,
    sourceNodeId: routeForm.sourceNodeId || defaultFrom,
    targetNodeId: routeForm.targetNodeId || defaultTo,
  }

  const registerMicroAlert = useCallback((order) => {
    if (!order?.id || !order?.microDepotNodeId || seenOrderIds.current.has(order.id)) {
      return
    }

    seenOrderIds.current.add(order.id)
    setMicroAlerts((current) => [buildMicroAlert(order), ...current].slice(0, 6))
  }, [])

  const loadAll = useCallback(async (period = reportPeriod) => {
    try {
      const [
        statusResponse,
        nodesResponse,
        edgesResponse,
        productsResponse,
        inventoryResponse,
        ordersResponse,
        replenishmentsResponse,
        vehicleReplenishmentsResponse,
        vehiclesResponse,
        kpisResponse,
        reportsResponse,
        assignmentsResponse,
        timelineResponse,
        summaryResponse,
        dispatchReadinessResponse,
      ] = await Promise.all([
        api.get('/api/simulation/status'),
        api.get('/api/graph/nodes'),
        api.get('/api/graph/edges'),
        api.get('/api/products'),
        api.get('/api/inventory'),
        api.get('/api/orders'),
        api.get('/api/replenishments'),
        api.get('/api/replenishments/by-vehicle'),
        api.get('/api/vehicles'),
        api.get('/api/metrics/kpis'),
        api.get('/api/vehicle-reports', { params: { period } }),
        api.get('/api/simulation/vehicle-assignments'),
        api.get('/api/simulation/timeline', { params: { limit: 80 } }),
        api.get('/api/simulation/summary'),
        api.get('/api/simulation/dispatch-readiness'),
      ])

      const nextStatus = normalizeStatus(statusResponse.data)
      const nextSimData = {
        status: nextStatus,
        nodes: [...nodesResponse.data].sort((left, right) => left.id.localeCompare(right.id)),
        edges: edgesResponse.data,
        products: productsResponse.data,
        inventory: inventoryResponse.data,
        orders: ordersResponse.data,
        replenishments: replenishmentsResponse.data,
        vehicleReplenishments: vehicleReplenishmentsResponse.data || [],
        vehicles: Array.isArray(vehiclesResponse.data) 
          ? [...vehiclesResponse.data].sort((a, b) => {
              if (!!a.dedicatedManual !== !!b.dedicatedManual) return a.dedicatedManual ? 1 : -1;
              return (a.id || "").localeCompare(b.id || "");
            })
          : [],
        vehicleAssignments: assignmentsResponse.data,
        dispatchReadiness: dispatchReadinessResponse.data,
        reports: reportsResponse.data,
        timeline: timelineResponse.data,
        summary: summaryResponse.data,
        kpis: kpisResponse.data,
      }

      const previousStatus = simDataRef.current.status
      const shouldResyncClock =
        previousStatus.currentTick !== nextStatus.currentTick ||
        previousStatus.running !== nextStatus.running

      if (shouldResyncClock) {
        const now = Date.now()
        setLastStatusSyncAt(now)
        setCurrentTimeMs(now)
      }

      simDataRef.current = nextSimData
      setSimData(nextSimData)

      setMicroAlerts((current) => {
        const nextAlerts = []
        for (const dr of nextSimData.dispatchReadiness || []) {
          if (!dr.capacityApprovalPending || !dr.vehicleId) {
            continue
          }
          const dedupeKey = `${dr.vehicleId}-${dr.referenceId || ''}`
          if (seenCapacityAlertKeys.current.has(dedupeKey)) {
            continue
          }
          seenCapacityAlertKeys.current.add(dedupeKey)
          nextAlerts.push(buildCapacityDispatchAlert(dr))
        }
        for (const order of nextSimData.orders) {
          if (seenOrderIds.current.has(order.id)) {
            continue
          }

          seenOrderIds.current.add(order.id)
          if (order.microDepotNodeId) {
            nextAlerts.push(buildMicroAlert(order))
          }
        }

        // --- NEW: Vehicle Assignment Toasts ---
        const newToasts = []
        for (const dr of nextSimData.vehicleAssignments || []) {
          // Track assignments we already toasted about
          if (!dr.referenceId || seenAssignmentsRef.current.has(dr.referenceId)) {
            continue
          }
          
          seenAssignmentsRef.current.add(dr.referenceId)
          newToasts.push({
            id: dr.referenceId,
            title: `⚡ ${dr.vehicleId} Yola Çıktı`,
            subtitle: `${dr.routeSummary || 'Teslimat Rotası'}`,
            meta: `Maliyet: ${Number(dr.planCost || 0).toFixed(2)}₺ | Kargo: ${dr.cargoSummary?.substring(0, 30) || 'Standart'}`
          })
        }
        
        if (newToasts.length > 0) {
          setToasts((t) => [...newToasts, ...t].slice(0, 4))
        }

        return [...nextAlerts.reverse(), ...current].slice(0, 8)
      })

      setError('')
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Veriler alinamadi')
    }
  }, [reportPeriod])

  useEffect(() => {
    simDataRef.current = simData
  }, [simData])

  useEffect(() => {
    void loadAll(reportPeriod)
    const timer = window.setInterval(() => void loadAll(reportPeriod), SNAPSHOT_REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [loadAll, reportPeriod])

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTimeMs(Date.now()), SIMULATION_MS_PER_TICK)
    return () => window.clearInterval(timer)
  }, [])

  const progress = useMemo(
    () => (status.maxTicks > 0 ? Math.min(100, Math.round((status.currentTick / status.maxTicks) * 100)) : 0),
    [status],
  )

  const effectivePhase = status.phase || (status.running ? 'RUNNING' : status.currentTick > 0 ? 'PAUSED' : 'READY')
  const systemStatusLabel = {
    RUNNING: 'Calisiyor',
    ERROR: 'Hata',
    COMPLETED: 'Tamamlandi',
    PAUSED: 'Beklemede',
  }[effectivePhase] || 'Hazir'

  const visibleError = error || (effectivePhase === 'ERROR'
    ? `${status.errorMessage || 'Simulasyon beklenmeyen bir hata nedeniyle durdu.'}${Number(status.failureTick) >= 0 ? ` (${status.failureTick}. dakika)` : ''}. Lutfen once Sifirla ile sistemi temizleyin.`
    : '')

  const liveTick = useMemo(() => {
    return Number(status.currentTick || 0)
  }, [status.currentTick])

  const simulationClock = useMemo(() => {
    const base = new Date('2026-03-29T08:00:00')
    base.setMinutes(base.getMinutes() + liveTick)
    return base
  }, [liveTick])

  const doAction = useCallback(async (label, fn) => {
    try {
      await fn()
      setMessage(label)
      setError('')
      await loadAll()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Islem basarisiz')
    }
  }, [loadAll])

  const loadRoute = useCallback(() =>
    doAction('Rota onizlemesi hazirlandi', async () => {
      const response = await api.get('/api/graph/route', {
        params: {
          sourceNodeId: resolvedRouteForm.sourceNodeId,
          targetNodeId: resolvedRouteForm.targetNodeId,
          tollPenalty: Number(resolvedRouteForm.tollPenalty),
        },
      })
      setRoutePreview(response.data)
    }), [doAction, resolvedRouteForm.sourceNodeId, resolvedRouteForm.targetNodeId, resolvedRouteForm.tollPenalty])

  const advanceSimulation = useCallback(() => {
    if (status.running) {
      setError('Simulasyon calisirken dakika ileri alinmaz. Once Durdur, sonra zamani ilerlet.')
      return
    }

    void doAction(`Simulasyon ${tickStep} dakika ileri alindi`, () =>
      api.post('/api/simulation/advance', null, { params: { ticks: tickStep } }),
    )
  }, [status.running, tickStep, doAction])

  const handleStart = useCallback(() => 
    doAction('Simulasyon baslatildi', () => api.post('/api/simulation/start')), [doAction])
  
  const handleStop = useCallback(() =>
    doAction('Simulasyon durduruldu', () => api.post('/api/simulation/stop')), [doAction])
  
  const handleReset = useCallback(() =>
    doAction('Simulasyon sifirlandi', () => api.post('/api/simulation/reset')), [doAction])

  const shared = {
    api,
    nodes,
    edges,
    products,
    inventory,
    orders,
    replenishments,
    vehicleReplenishments: simData.vehicleReplenishments,
    vehicles,
    vehicleAssignments,
    dispatchReadiness,
    reports,
    timeline,
    summary,
    reportPeriod,
    setReportPeriod,
    kpis,
    status,
    progress,
    routePreview,
    routeForm: resolvedRouteForm,
    setRouteForm,
    loadRoute,
    doAction,
    registerMicroAlert,
    simulationMsPerTick: SIMULATION_MS_PER_TICK,
    lastStatusSyncAt,
  }

  if (!isAuthenticated) {
    return <LoginScreen onLogin={() => setIsAuthenticated(true)} />;
  }

  return (
    <Shell>
      <PageHeader
        title="Sehir Lojistik Kontrol Merkezi"
        subtitle="Mikro depo karsilama, replenishment ve filo hareketleri tek panelde."
        status={systemStatusLabel}
      >
        <ActionButton onClick={handleStart}>
          ▶ Baslat
        </ActionButton>
        <ActionButton onClick={handleStop} variant="secondary">
          ⏸ Durdur
        </ActionButton>
        <ActionButton onClick={handleReset} variant="secondary">
          ↺ Sifirla
        </ActionButton>
        <div className="flex items-center gap-2 rounded-[10px] border border-white/[0.07] bg-[#111620] px-3 py-1.5">
          <span className="text-[11px] uppercase tracking-[0.16em] text-slate-500">Dakika</span>
          <input
            type="number"
            min="1"
            value={tickStep}
            onChange={(event) => setTickStep(Math.max(1, Number(event.target.value) || 1))}
            className="w-16 bg-transparent text-[13px] font-semibold text-white outline-none tabular-nums"
          />
        </div>
        <ActionButton onClick={advanceSimulation} variant="accent">
          +{tickStep} dk
        </ActionButton>
        <ClockPanel
          label="Operasyon Saati"
          value={simulationClock.toLocaleString('tr-TR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
        />
      </PageHeader>

      <div className="mt-4 flex gap-1.5">
        {tabs.map((item) => (
          <TabButton key={item.id} active={tab === item.id} onClick={() => setTab(item.id)}>
            <span className="mr-1.5 opacity-60">{item.icon}</span>
            {item.label}
          </TabButton>
        ))}
      </div>

      {message && <Notice tone="success">{message}</Notice>}
      {visibleError && <Notice tone="error">{visibleError}</Notice>}

      <AlertTicker
        title="Mikro Depo Siparis Akisi"
        items={microAlerts}
        emptyMessage="Yeni siparis dustugunde burada bildirim karti belirecek."
      />

      <Suspense fallback={<ScreenSkeleton />}>
        {tab === 'overview' && <OverviewScreen {...shared} />}
        {tab === 'map' && <MapScreen {...shared} />}
        {tab === 'operations' && <OperationsScreen {...shared} />}
        {tab === 'fleet' && <FleetScreen {...shared} />}
      </Suspense>
      
      <ToastContainer toasts={toasts} removeToast={removeToast} />
    </Shell>
  )
}
