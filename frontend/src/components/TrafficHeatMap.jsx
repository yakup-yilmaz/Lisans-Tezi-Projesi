import { useMemo } from 'react'

/**
 * TrafficHeatMap — Trafik yoğunluk haritası
 * 
 * Ana haritayla aynı güzergahları gösterir ancak yolları trafik katsayısına göre
 * renklendirir: yeşil (boş) → sarı (orta) → kırmızı (yoğun).
 * 
 * trafficFactor aralıkları:
 *   < 0.8  → Koyu yeşil (çok boş)
 *   0.8–1.0 → Yeşil (normal altı)
 *   1.0–1.3 → Lime/Sarımsı-yeşil (normal)
 *   1.3–1.8 → Turuncu (yoğunlaşıyor)
 *   1.8–2.2 → Kırmızı-turuncu (yoğun)
 *   > 2.2  → Koyu kırmızı (çok yoğun)
 */

function edgePairKey(fromNodeId, toNodeId) {
  return [fromNodeId, toNodeId].sort().join('::')
}

function project(x, y) {
  const offset = { x: 50, y: 50 }
  const scale = 0.95
  return {
    x: offset.x + Number(x || 0) * scale,
    y: offset.y + Number(y || 0) * scale,
  }
}

function getEdgePath(from, to) {
  const dx = to.x - from.x
  const dy = to.y - from.y
  const dist = Math.sqrt(dx * dx + dy * dy)
  if (dist < 1) return `M ${from.x} ${from.y} L ${to.x} ${to.y}`

  const seed = Math.abs(Math.sin(from.x * 12.9898 + from.y * 78.233 + to.x * 37.719 + to.y * 4.1414)) * 43758.5453
  const seedFrac = seed - Math.floor(seed)
  const direction = seedFrac > 0.5 ? 1 : -1
  const curvature = 0.08 + seedFrac * 0.14

  const mx = (from.x + to.x) / 2
  const my = (from.y + to.y) / 2
  const px = -dy / dist
  const py = dx / dist
  const ofs = dist * curvature * direction
  const cx = mx + px * ofs
  const cy = my + py * ofs

  return `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}`
}

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
  const ofs = dist * curvature * direction
  const cx = mx + px * ofs
  const cy = my + py * ofs
  return { x: (from.x + 2 * cx + to.x) / 4, y: (from.y + 2 * cy + to.y) / 4 }
}

/**
 * Trafik katsayısını HSL renk aralığına dönüştürür.
 * Yeşil (120°) → Sarı (60°) → Kırmızı (0°)
 */
function trafficToColor(factor) {
  const f = Math.max(0.5, Math.min(3.0, Number(factor) || 1))
  // 0.5 → hue=130 (yeşil), 1.0 → hue=90 (lime), 2.0 → hue=30 (turuncu), 3.0 → hue=0 (kırmızı)
  const t = (f - 0.5) / 2.5 // 0..1 arası normalize
  const hue = Math.round(130 - t * 130) // 130° → 0°
  const sat = 85 + t * 15 // %85 → %100 doygunluk
  const light = 52 - t * 14 // %52 → %38 parlaklık (koyu kırmızı vurgusu)
  return `hsl(${hue}, ${sat}%, ${light}%)`
}

function trafficToGlow(factor) {
  const f = Math.max(0.5, Math.min(3.0, Number(factor) || 1))
  const t = (f - 0.5) / 2.5
  const hue = Math.round(130 - t * 130)
  return `hsla(${hue}, 90%, 50%, 0.35)`
}

/** Trafik katsayısından okunabilir etiket */
function trafficLabel(factor) {
  const f = Number(factor) || 1
  if (f < 0.8) return 'Boş'
  if (f < 1.1) return 'Normal'
  if (f < 1.5) return 'Orta'
  if (f < 2.0) return 'Yoğun'
  if (f < 2.5) return 'Çok Yoğun'
  return 'Tıkanık'
}

/** Yol genişliği — yoğun yollar biraz daha kalın */
function trafficWidth(factor) {
  const f = Math.max(0.5, Math.min(3.0, Number(factor) || 1))
  return 4 + (f - 0.5) * 1.6
}

export function TrafficHeatMap({ nodes, edges }) {
  const safeNodes = Array.isArray(nodes) ? nodes : []
  const safeEdges = Array.isArray(edges) ? edges : []

  const layoutedNodes = useMemo(() => {
    if (safeNodes.length === 0) return safeNodes

    const centerX = 550
    const centerY = 350
    const radius = Math.min(280, Math.max(120, safeNodes.length * 25))

    return safeNodes.map((node, index) => {
      if (node.x !== 0 || node.y !== 0) return node
      const angle = (2 * Math.PI * index) / safeNodes.length
      return { ...node, x: centerX + radius * Math.cos(angle), y: centerY + radius * Math.sin(angle) }
    })
  }, [safeNodes])

  const nodeMap = useMemo(
    () => Object.fromEntries(layoutedNodes.map((n) => [n.id, n])),
    [layoutedNodes],
  )

  const displayEdges = useMemo(() => {
    const byPair = new Map()
    safeEdges.forEach((edge) => {
      const key = edgePairKey(edge.fromNodeId, edge.toNodeId)
      const prev = byPair.get(key)
      if (!prev) {
        byPair.set(key, {
          fromNodeId: edge.fromNodeId,
          toNodeId: edge.toNodeId,
          distanceKm: Number(edge.distanceKm || 0),
          trafficFactor: Number(edge.trafficFactor || 1),
          tollCost: Number(edge.tollCost || 0),
          trafficLocked: Boolean(edge.trafficLocked),
        })
      } else {
        prev.trafficFactor = Math.max(prev.trafficFactor, Number(edge.trafficFactor || 1))
        prev.tollCost = Math.max(prev.tollCost, Number(edge.tollCost || 0))
        prev.trafficLocked = prev.trafficLocked || Boolean(edge.trafficLocked)
      }
    })
    return [...byPair.values()].sort((a, b) => a.trafficFactor - b.trafficFactor)
  }, [safeEdges])

  // İstatistikler
  const stats = useMemo(() => {
    if (displayEdges.length === 0) return { avg: 1, max: 1, min: 1, congested: 0, free: 0 }
    const factors = displayEdges.map(e => e.trafficFactor)
    const avg = factors.reduce((s, f) => s + f, 0) / factors.length
    return {
      avg: avg.toFixed(2),
      max: Math.max(...factors).toFixed(2),
      min: Math.min(...factors).toFixed(2),
      congested: factors.filter(f => f >= 1.8).length,
      free: factors.filter(f => f < 1.0).length,
    }
  }, [displayEdges])

  return (
    <div className="relative overflow-hidden rounded-[20px] border border-white/[0.08] bg-[#070a10]">
      {/* Başlık ve istatistikler */}
      <div className="flex items-center justify-between border-b border-white/[0.06] px-4 py-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-rose-500/15 border border-rose-500/20">
            <span className="text-[14px]">🌡️</span>
          </div>
          <div>
            <p className="text-[13px] font-bold text-white">Trafik Yoğunluk Haritası</p>
            <p className="text-[10px] text-slate-500">Anlık trafik katsayısına göre yol renklendirmesi</p>
          </div>
        </div>
        <div className="flex items-center gap-3 text-[10px]">
          <div className="text-center">
            <p className="text-slate-500">Ortalama</p>
            <p className="font-bold text-white tabular-nums">×{stats.avg}</p>
          </div>
          <div className="text-center">
            <p className="text-slate-500">Yoğun</p>
            <p className="font-bold text-rose-400 tabular-nums">{stats.congested}</p>
          </div>
          <div className="text-center">
            <p className="text-slate-500">Boş</p>
            <p className="font-bold text-emerald-400 tabular-nums">{stats.free}</p>
          </div>
        </div>
      </div>

      {/* Renk skalası (lejant) */}
      <div className="flex items-center gap-3 border-b border-white/[0.04] px-4 py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-slate-600 shrink-0">Skala</p>
        <div className="flex flex-1 h-2.5 rounded-full overflow-hidden">
          <div className="flex-1" style={{ background: 'hsl(130, 85%, 52%)' }} title="Boş (×0.5)" />
          <div className="flex-1" style={{ background: 'hsl(100, 87%, 48%)' }} title="Normal altı (×0.8)" />
          <div className="flex-1" style={{ background: 'hsl(70, 90%, 46%)' }} title="Normal (×1.0)" />
          <div className="flex-1" style={{ background: 'hsl(45, 92%, 44%)' }} title="Orta (×1.3)" />
          <div className="flex-1" style={{ background: 'hsl(20, 95%, 42%)' }} title="Yoğun (×1.8)" />
          <div className="flex-1" style={{ background: 'hsl(0, 100%, 38%)' }} title="Çok Yoğun (×2.5+)" />
        </div>
        <div className="flex gap-3 text-[9px] text-slate-500 shrink-0">
          <span className="text-emerald-400">Boş</span>
          <span className="text-yellow-400">Orta</span>
          <span className="text-rose-400">Yoğun</span>
        </div>
      </div>

      {/* SVG Harita */}
      <svg viewBox="0 0 1100 1000" className="w-full h-[520px]">
        <defs>
          <filter id="traffic-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="5" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        <rect width="1100" height="1000" fill="#050810" />

        {/* Grid çizgileri — hafif */}
        <g opacity="0.3">
          {Array.from({ length: 11 }).map((_, i) => (
            <line key={`v-${i}`} x1={i * 100 + 50} y1="0" x2={i * 100 + 50} y2="1000"
              stroke="rgba(255,255,255,0.03)" strokeWidth="0.5" />
          ))}
          {Array.from({ length: 10 }).map((_, i) => (
            <line key={`h-${i}`} x1="0" y1={i * 100 + 50} x2="1100" y2={i * 100 + 50}
              stroke="rgba(255,255,255,0.03)" strokeWidth="0.5" />
          ))}
        </g>

        {/* Yollar — trafik sırasıyla (en yoğun üstte) */}
        {displayEdges.map((edge) => {
          const n1 = nodeMap[edge.fromNodeId]
          const n2 = nodeMap[edge.toNodeId]
          if (!n1 || !n2) return null

          const from = project(n1.x, n1.y)
          const to = project(n2.x, n2.y)
          const pathData = getEdgePath(from, to)
          const color = trafficToColor(edge.trafficFactor)
          const glow = trafficToGlow(edge.trafficFactor)
          const width = trafficWidth(edge.trafficFactor)
          const mid = getEdgeMidpoint(from, to)

          return (
            <g key={`${edge.fromNodeId}-${edge.toNodeId}`}>
              {/* Glow katmanı */}
              <path d={pathData} fill="none"
                stroke={glow} strokeWidth={width + 6} strokeLinecap="round"
                filter="url(#traffic-glow)" />
              {/* Ana yol */}
              <path d={pathData} fill="none"
                stroke={color} strokeWidth={width} strokeLinecap="round"
                opacity="0.9" />
              {/* Kilitli trafik göstergesi */}
              {edge.trafficLocked && (
                <path d={pathData} fill="none"
                  stroke="rgba(255,255,255,0.4)" strokeWidth="1"
                  strokeDasharray="3 3" strokeLinecap="round" />
              )}
              {/* Katsayı etiketi */}
              <g transform={`translate(${mid.x}, ${mid.y})`}>
                <rect x="-16" y="-8" width="32" height="14" rx="4"
                  fill="rgba(5,8,16,0.85)" stroke={color} strokeWidth="0.5" />
                <text x="0" y="3" fill={color} fontSize="7" fontWeight="700"
                  fontFamily="var(--text-tech)" textAnchor="middle">
                  ×{edge.trafficFactor.toFixed(1)}
                </text>
              </g>
            </g>
          )
        })}

        {/* Düğümler */}
        {layoutedNodes.map((node) => {
          const p = project(node.x, node.y)
          const isDepot = node.type === 'MAIN_DEPOT' || node.type === 'MICRO_DEPOT'
          const isMain = node.type === 'MAIN_DEPOT'

          return (
            <g key={node.id} transform={`translate(${p.x}, ${p.y})`}>
              <circle cx="0" cy="0" r={isMain ? 8 : isDepot ? 5 : 2.5}
                fill={isMain ? '#ffb800' : isDepot ? '#10b981' : 'rgba(148,163,184,0.4)'}
                stroke={isMain ? 'rgba(255,184,0,0.4)' : isDepot ? 'rgba(16,185,129,0.3)' : 'none'}
                strokeWidth="1" />
              {isDepot && (
                <text x="0" y={isMain ? 16 : 14} fill={isMain ? '#ffb800' : '#10b981'}
                  fontSize="7" fontWeight="700" fontFamily="var(--text-tech)"
                  textAnchor="middle" opacity="0.8">
                  {node.id}
                </text>
              )}
            </g>
          )
        })}
      </svg>

      {/* Alt istatistik çubuğu */}
      <div className="grid grid-cols-5 gap-px border-t border-white/[0.06] bg-white/[0.02]">
        <div className="p-2.5 text-center">
          <p className="text-[16px] font-bold text-white tabular-nums">{displayEdges.length}</p>
          <p className="text-[9px] uppercase tracking-widest text-slate-600">Toplam Yol</p>
        </div>
        <div className="p-2.5 text-center">
          <p className="text-[16px] font-bold text-emerald-400 tabular-nums">{stats.free}</p>
          <p className="text-[9px] uppercase tracking-widest text-slate-600">Boş Yol</p>
        </div>
        <div className="p-2.5 text-center">
          <p className="text-[16px] font-bold text-rose-400 tabular-nums">{stats.congested}</p>
          <p className="text-[9px] uppercase tracking-widest text-slate-600">Yoğun Yol</p>
        </div>
        <div className="p-2.5 text-center">
          <p className="text-[16px] font-bold text-amber-400 tabular-nums">×{stats.max}</p>
          <p className="text-[9px] uppercase tracking-widest text-slate-600">Maks. Katsayı</p>
        </div>
        <div className="p-2.5 text-center">
          <p className="text-[16px] font-bold text-sky-400 tabular-nums">×{stats.min}</p>
          <p className="text-[9px] uppercase tracking-widest text-slate-600">Min. Katsayı</p>
        </div>
      </div>
    </div>
  )
}
