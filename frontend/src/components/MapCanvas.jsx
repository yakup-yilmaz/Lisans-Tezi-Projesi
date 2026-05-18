import { useEffect, useMemo, useRef, useState } from 'react'

const MOTION_INTERVAL_MS = 10
const BACKGROUND_HREF = '/map-background.svg'

function edgePairKey(fromNodeId, toNodeId) {
  return [fromNodeId, toNodeId].sort().join('::')
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

// Linear 2D Projection
function project(x, y, k = 1) {
  const offset = { x: 50, y: 50 }
  const scale = 0.95
  return {
    x: offset.x + Number(x || 0) * scale,
    y: offset.y + Number(y || 0) * scale
  }
}

// Organik Bézier eğrisi — her yol segmenti hafif kıvrımlı
function getEdgePath(from, to) {
  const dx = to.x - from.x
  const dy = to.y - from.y
  const dist = Math.sqrt(dx * dx + dy * dy)
  if (dist < 1) return `M ${from.x} ${from.y} L ${to.x} ${to.y}`

  // Deterministik tohum — aynı iki nokta her zaman aynı eğriyi üretir
  const seed = Math.abs(Math.sin(from.x * 12.9898 + from.y * 78.233 + to.x * 37.719 + to.y * 4.1414)) * 43758.5453
  const seedFrac = seed - Math.floor(seed)
  const direction = seedFrac > 0.5 ? 1 : -1
  const curvature = 0.08 + seedFrac * 0.14 // 0.08–0.22 oranında eğrilik

  // Orta nokta
  const mx = (from.x + to.x) / 2
  const my = (from.y + to.y) / 2

  // Dik doğrultudaki birim vektör
  const px = -dy / dist
  const py = dx / dist

  const offset = dist * curvature * direction
  const cx = mx + px * offset
  const cy = my + py * offset

  return `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}`
}

// Bézier eğrisinin orta noktası (etiket yerleştirme için)
function getEdgeMidpoint(from, to) {
  const dx = to.x - from.x
  const dy = to.y - from.y
  const dist = Math.sqrt(dx * dx + dy * dy)
  if (dist < 1) return { x: from.x, y: from.y }

  const seed = Math.abs(Math.sin(from.x * 12.9898 + from.y * 78.233 + to.x * 37.719 + to.y * 4.1414)) * 43758.5453
  const seedFrac = seed - Math.floor(seed)
  const direction = seedFrac > 0.5 ? 1 : -1
  const curvature = 0.08 + seedFrac * 0.14

  const mx = (from.x + to.x) / 2
  const my = (from.y + to.y) / 2
  const px = -dy / dist
  const py = dx / dist
  const offset = dist * curvature * direction

  // Quadratic Bézier t=0.5 → (from + 2*control + to) / 4
  const cx = mx + px * offset
  const cy = my + py * offset
  return { x: (from.x + 2 * cx + to.x) / 4, y: (from.y + 2 * cy + to.y) / 4 }
}

// Çoklu düğüm üzerinden kıvrımlı yol oluşturma
function getMultiNodePath(nodes) {
  if (nodes.length < 2) return ''
  const segments = []
  for (let i = 0; i < nodes.length - 1; i++) {
    const from = project(nodes[i].x, nodes[i].y)
    const to = project(nodes[i + 1].x, nodes[i + 1].y)
    if (i === 0) {
      segments.push(getEdgePath(from, to))
    } else {
      // Devam segmentleri — M kısmını atla
      const path = getEdgePath(from, to)
      segments.push(path.replace(/^M\s+[\d.]+\s+[\d.]+\s*/, ''))
    }
  }
  return segments.join(' ')
}

// Sabit yol genişliği — tüm yollar aynı standart kalınlıkta
function getRoadWidth(edge) {
  return 6
}

function getRoadSurface(edge) {
  if (edge.active) return '#0c1622'
  if (edge.trafficLocked) return '#1a0d10'
  if (edge.tollCost > 0) return '#1a180d'
  return '#080c14'
}

function getRoadLaneColor(edge) {
  if (edge.active) return '#38bdf8' // Blue/Turquoise
  if (edge.trafficLocked) return '#fb7185'
  if (edge.tollCost > 0) return '#facc15'
  return 'rgba(56,189,248,0.2)'
}

function getRoadGlowColor(edge) {
  if (edge.active) return 'rgba(14,165,233,0.45)'
  if (edge.trafficLocked) return 'rgba(244,63,94,0.2)'
  if (edge.tollCost > 0) return 'rgba(250,204,21,0.2)'
  return 'rgba(56,189,248,0.08)'
}

function shouldShowNodeLabel(node, highlighted) {
  // Only show labels for depots (MAIN_DEPOT / MICRO_DEPOT) and highlighted nodes
  return highlighted || node.type === 'MAIN_DEPOT' || node.type === 'MICRO_DEPOT'
}

function shouldShowEdgeLabel(edge, compact) {
  if (compact) return false
  return Number(edge.distanceKm || 0) > 0
}

function renderNodeGlyph(node, highlighted) {
  if (node.type === 'MAIN_DEPOT') {
    return (
      <g>
        {/* Foundation — Altın/Amber vurgu */}
        <path d="M -40 0 L 0 20 L 40 0 L 0 -20 Z" fill="rgba(255, 184, 0, 0.12)" stroke="rgba(255, 184, 0, 0.35)" />
        <rect x="-18" y="-14" width="36" height="28" rx="4" fill="#ffb800" stroke="#fff" strokeWidth="1" opacity="0.85" />
        <circle cx="0" cy="0" r="40" fill="none" stroke="rgba(255, 184, 0, 0.25)" strokeDasharray="5 5" className="orbital-rotate" />
        <circle cx="0" cy="0" r="52" fill="none" stroke="rgba(255, 184, 0, 0.1)" strokeWidth="0.5" />
        {highlighted && <circle cx="0" cy="0" r="56" fill="none" stroke="#fff" strokeWidth="1" strokeDasharray="2 2" />}
      </g>
    )
  }

  if (node.type === 'MICRO_DEPOT') {
    return (
      <g>
        {/* İzometrik küp — Zümrüt yeşili */}
        <path d="M -18 0 L 0 9 L 18 0 L 0 -9 Z" fill="#10b981" opacity="0.65" />
        <path d="M -18 0 L -18 -10 L 0 -1 L 0 9 Z" fill="#10b981" opacity="0.45" />
        <path d="M 18 0 L 18 -10 L 0 -1 L 0 9 Z" fill="#10b981" opacity="0.25" />
        <circle cx="0" cy="0" r="24" fill="none" stroke="rgba(16, 185, 129, 0.2)" strokeWidth="0.5" />
        {highlighted && <circle cx="0" cy="0" r="28" fill="none" stroke="#10b981" strokeWidth="1.5" strokeDasharray="4 2" />}
      </g>
    )
  }

  if (node.type === 'CUSTOMER') {
    return (
      <g>
        {/* Müşteri — Mor nokta */}
        <circle cx="0" cy="0" r={highlighted ? 5 : 3.5} fill={highlighted ? "#c4b5fd" : "rgba(167, 139, 250, 0.5)"} />
        {highlighted && <circle cx="0" cy="0" r="10" fill="none" stroke="rgba(167, 139, 250, 0.6)" strokeWidth="1" />}
      </g>
    )
  }

  return (
    <g>
      {/* Kavşak — İnce turkuaz */}
      <circle cx="0" cy="0" r={highlighted ? 4 : 2.5} fill={highlighted ? "#fff" : "rgba(0, 209, 255, 0.3)"} />
      {highlighted && <circle cx="0" cy="0" r="8" fill="none" stroke="rgba(0, 209, 255, 0.6)" strokeWidth="1" />}
    </g>
  )
}

export function MapCanvas({
  nodes,
  edges,
  vehicles,
  vehicleAssignments = [],
  routePreview,
  compact = false,
  simulationMsPerTick = 500,
  lastStatusSyncAt = 0,
  status,
  onSelectMicroDepotNode,
  onSelectVehicle,
  transform = { x: 0, y: 0, k: 1 },
  setTransform,
  selectedVehicleId,
}) {
  const [motionTick, setMotionTick] = useState(0)
  const isDragging = useRef(false)
  const dragStart = useRef({ x: 0, y: 0 })

  // Optimized City Lights Path
  const cityLightsPath = useMemo(() => {
    return Array.from({ length: 80 }).map(() => {
      const x = Math.random() * 1100
      const y = Math.random() * 1000
      return `M ${x} ${y} m -1, 0 a 1,1 0 1,0 2,0 a 1,1 0 1,0 -2,0`
    }).join(' ')
  }, [])

  const handleWheel = (e) => {
    const zoomSensitivity = 0.001
    const delta = -e.deltaY * zoomSensitivity
    setTransform((prev) => ({ ...prev, k: Math.min(Math.max(0.3, prev.k + delta), 4) }))
  }

  const handlePointerDown = (e) => {
    isDragging.current = true
    dragStart.current = { x: e.clientX - transform.x, y: e.clientY - transform.y }
    e.currentTarget.setPointerCapture(e.pointerId)
  }

  const handlePointerMove = (e) => {
    if (!isDragging.current) return
    setTransform((prev) => ({ ...prev, x: e.clientX - dragStart.current.x, y: e.clientY - dragStart.current.y }))
  }

  const handlePointerUp = (e) => {
    isDragging.current = false
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch {}
  }

  const safeNodes = Array.isArray(nodes) ? nodes : []
  const safeEdges = Array.isArray(edges) ? edges : []
  const safeVehicles = Array.isArray(vehicles) ? vehicles : []
  const safeAssignments = Array.isArray(vehicleAssignments) ? vehicleAssignments : []

  const BACKEND_INTERVAL_MS = Math.max(simulationMsPerTick, MOTION_INTERVAL_MS)
  const INTERPOLATION_STEPS = Math.max(1, Math.round(BACKEND_INTERVAL_MS / MOTION_INTERVAL_MS))

  const layoutedNodes = useMemo(() => {
    if (safeNodes.length === 0) {
      return safeNodes
    }

    const centerX = 550
    const centerY = 350
    const radius = Math.min(280, Math.max(120, safeNodes.length * 25))

    return safeNodes.map((node, index) => {
      if (node.x !== 0 || node.y !== 0) {
        return node
      }

      const angle = (2 * Math.PI * index) / safeNodes.length
      const x = centerX + radius * Math.cos(angle)
      const y = centerY + radius * Math.sin(angle)

      return { ...node, x, y }
    })
  }, [safeNodes])

  const nodeMap = useMemo(
    () => Object.fromEntries(layoutedNodes.map((node) => [node.id, node])),
    [layoutedNodes],
  )

  const assignmentByVehicleId = useMemo(
    () => new Map(safeAssignments.map((assignment) => [assignment.vehicleId, assignment])),
    [safeAssignments],
  )

  const highlightedNodes = routePreview?.nodeIds || []
  const routeEdges = useMemo(
    () => new Set((routePreview?.nodeIds || []).slice(0, -1).map((id, index, list) => `${id}->${list[index + 1]}`)),
    [routePreview],
  )

  // Find the MAIN_DEPOT node id
  const mainDepotNodeId = useMemo(
    () => layoutedNodes.find(n => n.type === 'MAIN_DEPOT')?.id || null,
    [layoutedNodes],
  )

  const stationaryVehicleGroups = useMemo(() => {
    const grouped = {}

    safeVehicles.forEach((vehicle) => {
      const assignment = assignmentByVehicleId.get(vehicle.id)
      const fromNodeId = assignment?.currentFromNodeId
      const toNodeId = assignment?.currentToNodeId
      const isTraveling = Boolean(
        fromNodeId &&
        toNodeId &&
        fromNodeId !== toNodeId &&
        vehicle.status === 'EN_ROUTE',
      )

      if (isTraveling) {
        return
      }

      // All idle/created vehicles anchor to MAIN_DEPOT as fleet
      const isIdle = vehicle.status === 'IDLE' || vehicle.status === 'CREATED'
      const anchorNodeId = isIdle && mainDepotNodeId
        ? mainDepotNodeId
        : (toNodeId || fromNodeId || vehicle.locationNodeId)
      if (!anchorNodeId) {
        return
      }

      grouped[anchorNodeId] ||= []
      grouped[anchorNodeId].push(vehicle.id)
    })

    return Object.fromEntries(
      Object.entries(grouped).map(([nodeId, vehicleIds]) => [nodeId, [...vehicleIds].sort()]),
    )
  }, [assignmentByVehicleId, safeVehicles, mainDepotNodeId])

  const vehicleOffsets = useMemo(
    () => Object.fromEntries(
      Object.entries(stationaryVehicleGroups).flatMap(([nodeId, vehicleIds]) => {
        const node = nodeMap[nodeId]
        const isMain = node?.type === 'MAIN_DEPOT'
        
        return vehicleIds.map((vehicleId, index) => {
          if (isMain) {
            // Fleet arc formation around Main Depot — evenly spaced semicircle below depot
            const totalCount = vehicleIds.length
            const arcSpan = Math.PI * 0.85 // ~150 degree arc 
            const arcStart = Math.PI * 0.075 // start slightly right of bottom
            const baseRadius = 70
            const rowCapacity = 6
            const row = Math.floor(index / rowCapacity)
            const indexInRow = index % rowCapacity
            const countInRow = Math.min(rowCapacity, totalCount - row * rowCapacity)
            const radius = baseRadius + row * 38
            const angle = arcStart + (arcSpan * (indexInRow + 0.5)) / countInRow
            const x = Math.cos(angle) * radius
            const y = Math.sin(angle) * radius
            return [vehicleId, { x, y }]
          } else {
            // Circular clustering for other nodes — tight ring
            const angle = (Math.PI * 2 * index) / Math.max(vehicleIds.length, 1)
            const radius = vehicleIds.length > 1 ? 22 : 0
            return [vehicleId, { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius }]
          }
        })
      }),
    ),
    [nodeMap, stationaryVehicleGroups],
  )

  const displayEdges = useMemo(() => {
    const byPair = new Map()

    safeEdges.forEach((edge) => {
      const key = edgePairKey(edge.fromNodeId, edge.toNodeId)
      const current = byPair.get(key)
      const active = routeEdges.has(`${edge.fromNodeId}->${edge.toNodeId}`) || routeEdges.has(`${edge.toNodeId}->${edge.fromNodeId}`)

      if (!current) {
        byPair.set(key, {
          fromNodeId: edge.fromNodeId,
          toNodeId: edge.toNodeId,
          distanceKm: Number(edge.distanceKm || 0),
          trafficFactor: Number(edge.trafficFactor || 1),
          tollCost: Number(edge.tollCost || 0),
          trafficLocked: Boolean(edge.trafficLocked),
          active,
        })
        return
      }

      current.distanceKm = Math.max(current.distanceKm, Number(edge.distanceKm || 0))
      current.trafficFactor = Math.max(current.trafficFactor, Number(edge.trafficFactor || 1))
      current.tollCost = Math.max(current.tollCost, Number(edge.tollCost || 0))
      current.trafficLocked = current.trafficLocked || Boolean(edge.trafficLocked)
      current.active = current.active || active
    })

    return [...byPair.values()].sort((left, right) => {
      const leftScore = Number(left.active) * 10 + Number(left.tollCost > 0) * 4 + Number(left.trafficLocked)
      const rightScore = Number(right.active) * 10 + Number(right.tollCost > 0) * 4 + Number(right.trafficLocked)
      return leftScore - rightScore
    })
  }, [routeEdges, safeEdges])

  useEffect(() => {
    const timer = window.setInterval(
      () => setMotionTick((value) => (value + 1) % INTERPOLATION_STEPS),
      MOTION_INTERVAL_MS,
    )

    return () => window.clearInterval(timer)
  }, [INTERPOLATION_STEPS])

  useEffect(() => {
    setMotionTick(0)
  }, [lastStatusSyncAt, status?.currentTick])

  const elapsedSinceLastSyncMs = status?.running
    ? Math.max(0, Date.now() - Number(lastStatusSyncAt || 0))
    : 0
  const elapsedBackendTicks = status?.running
    ? Math.floor(elapsedSinceLastSyncMs / BACKEND_INTERVAL_MS)
    : 0
  const smoothTickFraction = status?.running ? motionTick / INTERPOLATION_STEPS : 0

  // Grid Generation Labels (A1-Z9)
  const gridCells = useMemo(() => {
    const cells = []
    const cols = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')
    for (let i = 0; i < 10; i++) {
      for (let j = 0; j < 10; j++) {
        cells.push({ x: i * 110, y: j * 100, label: `${cols[i]}${j + 1}` })
      }
    }
    return cells
  }, [])

  return (
    <div className="relative overflow-hidden rounded-[28px] border border-sky-500/10 bg-[#05080c] p-2 shadow-[0_24px_80px_rgba(0,0,0,0.65)] scanline">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.14),_transparent_28%),radial-gradient(circle_at_78%_10%,_rgba(16,185,129,0.08),_transparent_32%)]" />
      

      {routePreview && (
        <div className="pointer-events-none absolute right-6 top-6 z-20 rounded-[14px] border border-sky-500/20 bg-[#0a121d]/90 px-4 py-3 text-right backdrop-blur-md">
          <p className="text-[9px] font-bold uppercase tracking-[0.2em] text-sky-400/80">Rota Analizi</p>
          <p className="mt-1 text-[20px] font-bold tabular-nums text-white">
            {Number(routePreview.totalDistanceKm || 0).toFixed(1)} <span className="text-[12px] font-normal text-sky-400/60">km</span>
          </p>
        </div>
      )}

      <svg
        viewBox="0 0 1100 1000"
        className={`relative z-10 w-full touch-none cursor-grab active:cursor-grabbing ${compact ? 'h-[600px]' : 'h-[1050px]'}`}
        onWheel={handleWheel}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerUp}
      >
        <defs>
          <clipPath id="map-clip">
            <rect width="1100" height="1000" rx="24" />
          </clipPath>
          <radialGradient id="map-vignette" cx="50%" cy="50%" r="74%">
            <stop offset="40%" stopColor="rgba(2,6,12,0)" />
            <stop offset="100%" stopColor="rgba(2,6,12,0.8)" />
          </radialGradient>
          <pattern id="modern-grid" width="100" height="100" patternUnits="userSpaceOnUse">
            <path d="M 100 0 L 0 0 0 100" fill="none" stroke="rgba(56,189,248,0.06)" strokeWidth="1" />
            <circle cx="0" cy="0" r="1.5" fill="rgba(56,189,248,0.2)" />
          </pattern>
          <filter id="road-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="6" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
          <filter id="bloom" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3" result="glow" />
            <feColorMatrix type="matrix" values="0 0 0 0 0  0 0 0 0 0.8  0 0 0 0 1  0 0 0 1 0" />
            <feMerge>
              <feMergeNode />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          {/* Toll yol gradyanı — altın/amber */}
          <linearGradient id="toll-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#d97706" />
            <stop offset="50%" stopColor="#fbbf24" />
            <stop offset="100%" stopColor="#d97706" />
          </linearGradient>
          <filter id="toll-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feColorMatrix in="blur" type="matrix" values="0 0 0 0 0.85  0 0 0 0 0.65  0 0 0 0 0.1  0 0 0 0.5 0" result="goldenGlow" />
            <feComposite in="SourceGraphic" in2="goldenGlow" operator="over" />
          </filter>
        </defs>

        <g clipPath="url(#map-clip)">
          <rect width="1100" height="1000" fill="#020408" />
          
          <rect width="1100" height="1000" fill="#05080c" opacity="0.4" />
          
          <g transform={`translate(${transform.x}, ${transform.y}) scale(${transform.k})`}>
            {/* The Isometric Ground Grid */}
            <g className="iso-grid">
               {gridCells.map(c => {
                 const p = project(c.x, c.y)
                 return (
                   <g key={c.label}>
                    <path d={`M ${p.x} ${p.y} L ${project(c.x + 110, c.y).x} ${project(c.x + 110, c.y).y} L ${project(c.x + 110, c.y + 100).x} ${project(c.x + 110, c.y + 100).y} L ${project(c.x, c.y + 100).x} ${project(c.x, c.y + 100).y} Z`} 
                      fill="none" stroke="rgba(0, 209, 255, 0.08)" strokeWidth="0.5" />
                    <text x={p.x + 55} y={p.y + 50} fill="rgba(0, 209, 255, 0.2)" fontSize="10" fontFamily="var(--text-tech)" textAnchor="middle" alignmentBaseline="middle">{c.label}</text>
                   </g>
                 )
               })}
            </g>

            {/* Atmosphere: City Lights */}
            <path 
              d={cityLightsPath}
              fill="rgba(255, 234, 0, 0.4)" 
              className="city-light-flicker"
              style={{ willChange: 'opacity' }}
            />
            {/* Cyber-Logistics Road System — Organik Bézier Yollar */}
            {displayEdges.map((edge) => {
              const node1 = nodeMap[edge.fromNodeId]
              const node2 = nodeMap[edge.toNodeId]
              if (!node1 || !node2) return null

              const from = project(node1.x, node1.y)
              const to = project(node2.x, node2.y)

              const baseWidth = 6
              const isActive = edge.active
              const isToll = edge.tollCost > 0
              const speed = isActive ? '2s' : '5s'
              const pathData = getEdgePath(from, to)

              return (
                <g key={`${edge.fromNodeId}-${edge.toNodeId}`}>
                  {/* Outer Glow — tollu yollar altın, aktif mavi, normal soluk */}
                  <path d={pathData} fill="none"
                    stroke={isToll ? "rgba(251, 191, 36, 0.25)" : isActive ? "rgba(0, 209, 255, 0.3)" : "rgba(56, 189, 248, 0.05)"}
                    strokeWidth={baseWidth + (isToll ? 12 : 8)} strokeLinecap="round"
                    filter={isToll ? "url(#toll-glow)" : "url(#road-glow)"} />

                  {/* Toll — dış çizgi (kenar şeritleri) */}
                  {isToll && (
                    <path d={pathData} fill="none"
                      stroke="#d97706" strokeWidth={baseWidth + 3}
                      strokeLinecap="round" opacity="0.7" />
                  )}

                  {/* Ana yol yüzeyi */}
                  <path d={pathData} fill="none"
                    stroke={isToll ? "#1a160a" : "#0a101a"}
                    strokeWidth={baseWidth} strokeLinecap="round" />

                  {/* Toll — iç orta çizgi (altın kesikli) */}
                  {isToll && (
                    <path d={pathData} fill="none"
                      stroke="#fbbf24" strokeWidth="2"
                      strokeDasharray="6 4" strokeLinecap="round"
                      className="route-animated" style={{ animationDuration: '3s' }} />
                  )}

                  {/* Energy Flow (Neon Dash) — normal yollar */}
                  {!isToll && (
                    <path d={pathData} fill="none"
                      stroke={edge.trafficLocked ? "#f43f5e" : isActive ? "#00d1ff" : "rgba(0, 209, 255, 0.2)"}
                      strokeWidth={isActive ? 2 : 1} strokeLinecap="round"
                      strokeDasharray={isActive ? "10, 20" : "5, 15"}
                      className="route-animated"
                      style={{ animationDuration: speed }} />
                  )}

                  {/* Etiket — toll yollarda ₺ göstergesi, normal yollarda mesafe */}
                  {shouldShowEdgeLabel(edge, compact) && !compact && (() => {
                    const mid = getEdgeMidpoint(from, to)
                    return (
                      <g transform={`translate(${mid.x}, ${mid.y - 10})`}>
                        {isToll ? (
                          <>
                            <rect x="-18" y="-9" width="36" height="14" rx="4"
                              fill="rgba(217, 119, 6, 0.2)" stroke="rgba(251, 191, 36, 0.4)" strokeWidth="0.5" />
                            <text x="0" y="2" fill="#fbbf24" fontSize="7" fontWeight="700"
                              fontFamily="var(--text-tech)" textAnchor="middle">
                              {edge.tollCost.toFixed(0)}₺
                            </text>
                          </>
                        ) : (
                          <text x="0" y="0" fill="rgba(0, 209, 255, 0.18)" fontSize="6"
                            fontWeight="500" fontFamily="var(--text-tech)" textAnchor="middle">
                            {edge.distanceKm.toFixed(1)}km
                          </text>
                        )}
                      </g>
                    )
                  })()}
                </g>
              )
            })}

            {/* Path Assignments (Vehicle Intent) — Kıvrımlı yol */}
            {safeAssignments.map((assignment) => {
              const vehicle = safeVehicles.find((item) => item.id === assignment.vehicleId)
              if (!vehicle || vehicle.status !== 'EN_ROUTE') return null
              const pathNodeIds = [assignment.currentFromNodeId || vehicle.locationNodeId, ...(assignment.remainingNodeIds || [])]
              const pathNodes = pathNodeIds.map(id => nodeMap[id]).filter(Boolean)
              if (pathNodes.length < 2) return null
              const curvedPath = getMultiNodePath(pathNodes)
              return (
                <path key={`path-${assignment.vehicleId}`} d={curvedPath} fill="none" 
                  stroke="rgba(56,189,248,0.3)" strokeWidth="2.5" strokeDasharray="8, 10" className="route-animated-soft" />
              )
            })}

            {/* Node Layer */}
            {layoutedNodes.map((node) => {
              const highlighted = highlightedNodes.includes(node.id)
              const showLabel = shouldShowNodeLabel(node, highlighted)
              const p = project(node.x, node.y)
              
              return (
                <g key={node.id}>
                  <g transform={`translate(${p.x}, ${p.y})`} 
                    onClick={(e) => {
                      if (node.type !== 'MICRO_DEPOT') return
                      e.stopPropagation()
                      onSelectMicroDepotNode?.(node)
                    }}
                    style={{ cursor: node.type === 'MICRO_DEPOT' ? 'pointer' : 'default' }}>
                    {renderNodeGlyph(node, highlighted)}
                  </g>
                  {showLabel && (
                    <g transform={`translate(${p.x}, ${p.y + (node.type === 'MAIN_DEPOT' ? 32 : 22)})`}>
                      {/* Dark backdrop for label readability */}
                      <rect 
                        x={-(node.id.length * 3.2 + 6)} 
                        y="-9" 
                        width={node.id.length * 6.4 + 12} 
                        height="14" 
                        rx="4" 
                        fill="rgba(5, 8, 12, 0.85)" 
                        stroke={node.type === 'MAIN_DEPOT' ? 'rgba(255,184,0,0.25)' : 'rgba(16,185,129,0.2)'}
                        strokeWidth="0.5"
                      />
                      <text x="0" y="2" fill={
                        highlighted ? '#fff' 
                        : node.type === 'MAIN_DEPOT' ? 'rgba(255, 184, 0, 0.85)' 
                        : 'rgba(16, 185, 129, 0.75)'
                      } fontSize={node.type === 'MAIN_DEPOT' ? '9' : '8'} fontWeight="700" fontFamily="var(--text-tech)" textAnchor="middle" className="uppercase">
                        {node.id}
                      </text>
                    </g>
                  )}
                </g>
              )
            })}

            {/* Vehicle Layer with HUD */}
            {safeVehicles.map((vehicle) => {
              const node = nodeMap[vehicle.locationNodeId]
              if (!node) return null

              const assignment = assignmentByVehicleId.get(vehicle.id)
              const isTraveling = assignment && assignment.currentToNodeId !== assignment.currentFromNodeId && vehicle.status === 'EN_ROUTE'
              const fromOrig = nodeMap[assignment?.currentFromNodeId] || nodeMap[vehicle.locationNodeId]
              const toOrig = nodeMap[assignment?.currentToNodeId] || node
              
              if (!fromOrig || !toOrig) return null

              const totalTicks = Number(assignment?.segmentTotalTicks || 1)
              const remainingTicks = Number(assignment?.segmentRemainingTicks || 0)
              const progress = isTraveling ? clamp((totalTicks - remainingTicks + smoothTickFraction) / totalTicks, 0, 1) : 0
              
              const rawPosX = fromOrig.x + (toOrig.x - fromOrig.x) * progress
              const rawPosY = fromOrig.y + (toOrig.y - fromOrig.y) * progress
              const offset = vehicleOffsets[vehicle.id] || { x: 0, y: 0 }
              
              const p = project(rawPosX + (isTraveling ? 0 : offset.x), rawPosY + (isTraveling ? 0 : offset.y))
              const isSelected = selectedVehicleId === vehicle.id
              
              // Status Colors
              const isProcessing = vehicle.status === 'LOADING' || vehicle.status === 'DELIVERING'
              const isMoving = vehicle.status === 'EN_ROUTE' || vehicle.status === 'RETURNING'
              const isAtBase = vehicle.status === 'IDLE'
              const statusColor = isProcessing ? "#ffea00" : isMoving ? "#39ff14" : isAtBase ? "#00d1ff" : "#00d1ff"

              const loadPct = Math.round((vehicle.loadFraction || 0) * 100)
              const cargoVal = assignment?.totalValue || 0

              const vehicleIndex = safeVehicles.findIndex(v => v.id === vehicle.id)

              return (
                <g 
                  key={vehicle.id} 
                  transform={`translate(${p.x}, ${p.y})`}
                  onClick={(e) => {
                    e.stopPropagation()
                    onSelectVehicle?.(vehicle.id)
                  }}
                  style={{ cursor: 'pointer' }}
                >
                   {/* Combined Marker and Number */}
                   {vehicle.dedicatedManual ? (
                     <rect x="-12" y="-12" width={isSelected ? "24" : "24"} height={isSelected ? "24" : "24"} rx="4" fill={statusColor} stroke="#05080c" strokeWidth="2" />
                   ) : (
                     <circle cx="0" cy="0" r={isSelected ? "14" : "12"} fill={statusColor} stroke="#05080c" strokeWidth="2" />
                   )}
                   <text y="4" fill="#05080c" fontSize="11" fontWeight="900" fontFamily="var(--text-tech)" textAnchor="middle">
                      {vehicleIndex + 1}
                   </text>

                   {isSelected && !vehicle.dedicatedManual && (
                     <circle cx="0" cy="0" r="18" fill="none" stroke={statusColor} strokeWidth="1.5" className="vehicle-pulse" />
                   )}
                   {isSelected && vehicle.dedicatedManual && (
                     <rect x="-16" y="-16" width="32" height="32" rx="6" fill="none" stroke={statusColor} strokeWidth="1.5" className="vehicle-pulse" />
                   )}
                </g>
              )
            })}

          </g>

          {/* ── Harita Lejantı ── */}
          <g transform="translate(20, 870)">
            <rect x="0" y="0" width="220" height="120" rx="12"
              fill="rgba(5, 8, 16, 0.88)" stroke="rgba(255,255,255,0.08)" strokeWidth="1" />
            <text x="14" y="20" fill="rgba(255,255,255,0.7)" fontSize="9" fontWeight="800"
              fontFamily="var(--text-tech)" letterSpacing="0.12em">HARİTA LEJANTI</text>

            {/* Aktif rota */}
            <line x1="14" y1="36" x2="38" y2="36" stroke="#00d1ff" strokeWidth="2.5"
              strokeDasharray="5 3" />
            <text x="44" y="39" fill="#94a3b8" fontSize="8" fontFamily="var(--text-tech)">Aktif Rota</text>

            {/* Toll yol */}
            <line x1="14" y1="52" x2="26" y2="52" stroke="#d97706" strokeWidth="4" />
            <line x1="26" y1="52" x2="38" y2="52" stroke="#fbbf24" strokeWidth="2" strokeDasharray="3 2" />
            <text x="44" y="55" fill="#94a3b8" fontSize="8" fontFamily="var(--text-tech)">Ücretli Yol (Toll)</text>

            {/* Kilitli trafik */}
            <line x1="14" y1="68" x2="38" y2="68" stroke="#f43f5e" strokeWidth="2"
              strokeDasharray="4 3" />
            <text x="44" y="71" fill="#94a3b8" fontSize="8" fontFamily="var(--text-tech)">Kilitli Trafik</text>

            {/* Normal yol */}
            <line x1="14" y1="84" x2="38" y2="84" stroke="rgba(0, 209, 255, 0.3)" strokeWidth="2" />
            <text x="44" y="87" fill="#94a3b8" fontSize="8" fontFamily="var(--text-tech)">Normal Yol</text>

            {/* Araç durumları */}
            <circle cx="210" cy="30" r="4" fill="#00d1ff" />
            <text x="220" y="33" fill="#94a3b8" fontSize="7" fontFamily="var(--text-tech)">Boşta (Idle)</text>
            <circle cx="210" cy="45" r="4" fill="#ffea00" />
            <text x="220" y="48" fill="#94a3b8" fontSize="7" fontFamily="var(--text-tech)">Yükleme/Boşaltma</text>
            <circle cx="210" cy="60" r="4" fill="#39ff14" />
            <text x="220" y="63" fill="#94a3b8" fontSize="7" fontFamily="var(--text-tech)">Seyir Halinde</text>

            {/* Düğüm açıklamaları */}
            <circle cx="21" cy="102" r="5" fill="#ffb800" />
            <text x="30" y="105" fill="#64748b" fontSize="7" fontFamily="var(--text-tech)">Ana Depo</text>
            <circle cx="76" cy="102" r="4" fill="#10b981" />
            <text x="84" y="105" fill="#64748b" fontSize="7" fontFamily="var(--text-tech)">Mikro Depo</text>
            <rect x="135" y="98" width="8" height="8" rx="2" fill="#00d1ff" />
            <text x="146" y="105" fill="#64748b" fontSize="7" fontFamily="var(--text-tech)">Manuel Araç</text>
          </g>
        </g>
      </svg>
    </div>
  )
}
