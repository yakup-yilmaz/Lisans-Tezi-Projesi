// ─── Screen Skeleton Loader ───────────────────────────────────────────────────
export function ScreenSkeleton() {
  return (
    <div className="mt-4 space-y-4 animate-pulse">
      <div className="h-48 bg-[#1c2230] rounded-[16px]" />
      <div className="grid gap-4 xl:grid-cols-2">
        <div className="h-64 bg-[#1c2230] rounded-[16px]" />
        <div className="h-64 bg-[#1c2230] rounded-[16px]" />
      </div>
      <div className="grid gap-4 xl:grid-cols-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-32 bg-[#1c2230] rounded-[16px]" />
        ))}
      </div>
    </div>
  )
}

// ─── Animated Skeleton ────────────────────────────────────────────────────────
export function SkeletonCard() {
  return (
    <div className="rounded-[16px] border border-white/[0.07] bg-[#0e1118] p-4 animate-pulse">
      <div className="h-4 bg-[#1c2230] rounded w-1/4 mb-3" />
      <div className="space-y-2">
        <div className="h-3 bg-[#1c2230] rounded w-full" />
        <div className="h-3 bg-[#1c2230] rounded w-5/6" />
      </div>
    </div>
  )
}

// ─── Tab Panel with Lazy Loading ──────────────────────────────────────────────
export function TabPanel({ children }) {
  return (
    <Suspense fallback={<ScreenSkeleton />}>
      {children}
    </Suspense>
  )
}

import { useState, useEffect, Suspense } from 'react'
import { ResponsiveContainer } from 'recharts'

// ─── Design Tokens ────────────────────────────────────────────────────────────
// Koyu, yoğun, kontrast yüksek. Referanstaki Transcope estetiği:
// Almost-black surfaces, warm orange accent, crisp white type, subtle borders.
// Enhanced: Glassmorphism, smooth animations, better hierarchy

export function Shell({ children }) {
  return (
    <div className="mx-auto min-h-screen max-w-[1920px] w-full px-3 py-4 sm:px-6 sm:py-5 lg:px-8 font-['Inter',system-ui,sans-serif] bg-gradient-to-br from-[#04060a] via-[#0a0e18] to-[#0d1222]">
      <div className="fixed inset-0 pointer-events-none overflow-hidden">
        <div className="absolute -top-24 right-1/4 w-[500px] h-[500px] bg-orange-500/[0.04] rounded-full blur-[120px]" />
        <div className="absolute bottom-1/4 -left-12 w-[400px] h-[400px] bg-sky-500/[0.03] rounded-full blur-[100px]" />
        <div className="absolute top-1/2 right-0 w-[300px] h-[300px] bg-emerald-500/[0.02] rounded-full blur-[80px]" />
      </div>
      <div className="relative">
        {children}
      </div>
    </div>
  )
}

// ─── Page Header ─────────────────────────────────────────────────────────────
export function PageHeader({ title, subtitle, status, children }) {
  const isRunning = status === 'Calisiyor'
  const isError = status === 'Hata'

  return (
    <header className="relative overflow-hidden rounded-[20px] border border-white/[0.06] bg-[#0a0e18]/80 px-6 py-5 shadow-[0_8px_48px_rgba(0,0,0,0.6)] backdrop-blur-xl">
      {/* animated gradient background */}
      <div className="pointer-events-none absolute inset-0 opacity-30">
        <div className={`absolute inset-0 bg-gradient-to-r ${isRunning ? 'from-emerald-500/15 via-transparent to-transparent' : isError ? 'from-rose-500/15 via-transparent to-transparent' : 'from-orange-500/8 via-transparent to-transparent'}`} />
      </div>

      {/* subtle top highlight */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />

      <div className="relative flex flex-wrap items-center justify-between gap-4">
        {/* Left: branding + title */}
        <div className="flex items-center gap-4">
          <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] shadow-[0_0_24px_rgba(249,115,22,0.5)] transition-all duration-300 ${isRunning ? 'bg-emerald-500' : isError ? 'bg-rose-500' : 'bg-gradient-to-br from-orange-500 to-amber-600'}`}>
            <svg width="20" height="20" viewBox="0 0 18 18" fill="none">
              <path d="M3 9h12M9 3l6 6-6 6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-500">City Logistics</p>
            <h1 className="text-lg font-bold text-white leading-tight">{title}</h1>
          </div>
        </div>

        {/* Center: controls */}
        <div className="flex flex-wrap items-center gap-2">{children}</div>

        {/* Right: status pill */}
        <div className={`flex items-center gap-2 rounded-full border backdrop-blur-md px-4 py-2.5 text-sm font-medium transition-all duration-300 ${
          isRunning ? 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300 shadow-[0_0_20px_rgba(16,185,129,0.2)]' :
          isError   ? 'border-rose-500/40 bg-rose-500/15 text-rose-300 shadow-[0_0_20px_rgba(244,63,94,0.2)]' :
                      'border-white/[0.12] bg-white/[0.06] text-slate-400 shadow-[0_0_20px_rgba(255,255,255,0.05)]'
        }`}>
          <span className={`h-2 w-2 rounded-full ${
            isRunning ? 'bg-emerald-400 animate-pulse shadow-[0_0_8px_rgba(16,185,129,0.6)]' :
            isError   ? 'bg-rose-400 shadow-[0_0_8px_rgba(244,63,94,0.6)]' :
                        'bg-slate-500'
          }`} />
          <span className="font-semibold">{status}</span>
        </div>
      </div>

      {subtitle && (
        <p className="mt-3 text-[14px] text-slate-500 max-w-xl leading-relaxed font-medium">{subtitle}</p>
      )}
    </header>
  )
}

// ─── Clock Panel ─────────────────────────────────────────────────────────────
export function ClockPanel({ label, value, accent = 'dark' }) {
  return (
    <div className="flex items-center gap-3 rounded-[12px] border border-white/[0.07] bg-[#161b24] px-4 py-2.5">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" className="shrink-0 text-orange-400">
        <circle cx="7" cy="7" r="6" stroke="currentColor" strokeWidth="1.5" />
        <path d="M7 4v3l2 1.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
      <div>
        <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{label}</p>
        <p className="text-sm font-semibold text-white tabular-nums">{value}</p>
      </div>
    </div>
  )
}

// ─── Alert Ticker ─────────────────────────────────────────────────────────────
export function AlertTicker({ title, items, emptyMessage }) {
  return (
    <section className="mt-4 rounded-[14px] border border-orange-500/20 bg-orange-950/20 backdrop-blur-xl p-4 shadow-[0_4px_20px_rgba(249,115,22,0.08)]">
      <div className="flex items-center justify-between gap-4 mb-3">
        <div className="flex items-center gap-2.5">
          <span className="h-1.5 w-1.5 rounded-full bg-orange-400 animate-pulse shadow-[0_0_8px_rgba(251,146,60,0.5)]" />
          <p className="text-[11px] font-bold uppercase tracking-[0.2em] text-orange-300/90">{title}</p>
        </div>
        <span className="rounded-full border border-orange-500/25 bg-orange-500/10 px-2.5 py-0.5 text-[10px] font-semibold text-orange-300">
          {items.length > 0 ? `${items.length} CANLI` : 'TEMİZ'}
        </span>
      </div>
      {!items.length ? (
        <p className="text-[12px] text-slate-600 font-medium">{emptyMessage}</p>
      ) : (
        <div className="grid gap-2.5 lg:grid-cols-3">
          {items.map((item, idx) => (
            <div key={item.id} className="rounded-[10px] border border-orange-500/15 bg-[#0a0e18]/60 p-3 backdrop-blur-sm transition-all duration-300 hover:border-orange-500/30 hover:bg-orange-950/20">
              <p className="text-[12px] font-bold text-white/90">{item.title}</p>
              <p className="mt-0.5 text-[11px] text-slate-400 leading-relaxed">{item.subtitle}</p>
              <p className="mt-2 text-[10px] uppercase tracking-[0.14em] font-semibold text-orange-400/80">{item.meta}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

// ─── Card ─────────────────────────────────────────────────────────────────────
export function Card({ title, subtitle, children, className = '', action }) {
  return (
    <section className={`group rounded-[16px] border border-white/[0.06] bg-[#0a0e18]/70 backdrop-blur-xl shadow-[0_4px_24px_rgba(0,0,0,0.4)] transition-all duration-300 hover:border-white/[0.10] hover:shadow-[0_8px_40px_rgba(0,0,0,0.6)] ${className}`}>
      {(title || subtitle || action) && (
        <div className="flex items-start justify-between gap-3 border-b border-white/[0.04] px-5 py-4 group-hover:border-white/[0.07] transition-colors">
          <div>
            {title && <h2 className="text-[15px] font-bold text-white/95 leading-tight tracking-tight">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-[12px] text-slate-500 font-medium">{subtitle}</p>}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </div>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

// ─── Panel (inner surface) ────────────────────────────────────────────────────
export function Panel({ children, className = '' }) {
  return (
    <div className={`rounded-[14px] border border-white/[0.08] bg-gradient-to-br from-[#111620] to-[#0e1118] p-4 backdrop-blur-sm transition-all duration-300 hover:border-white/[0.12] ${className}`}>
      {children}
    </div>
  )
}

// ─── Metric Card ──────────────────────────────────────────────────────────────
export function MetricCard({ label, value, sub, tone = 'sky', icon, delta }) {
  const tones = {
    sky:     { bg: 'bg-sky-500/12', border: 'border-sky-500/30', text: 'text-sky-400', icon: 'text-sky-400', shadow: 'shadow-[0_0_20px_rgba(14,165,233,0.15)]' },
    emerald: { bg: 'bg-emerald-500/12', border: 'border-emerald-500/30', text: 'text-emerald-400', icon: 'text-emerald-400', shadow: 'shadow-[0_0_20px_rgba(16,185,129,0.15)]' },
    amber:   { bg: 'bg-orange-500/12', border: 'border-orange-500/30', text: 'text-orange-400', icon: 'text-orange-400', shadow: 'shadow-[0_0_20px_rgba(249,115,22,0.15)]' },
    rose:    { bg: 'bg-rose-500/12', border: 'border-rose-500/30', text: 'text-rose-400', icon: 'text-rose-400', shadow: 'shadow-[0_0_20px_rgba(244,63,94,0.15)]' },
  }
  const t = tones[tone] || tones.sky

  return (
    <div className={`group relative overflow-hidden rounded-[14px] border ${t.border} ${t.bg} p-4 backdrop-blur-xl transition-all duration-300 hover:scale-[1.02] ${t.shadow}`}>
      <div className="pointer-events-none absolute -top-6 -right-6 w-20 h-20 rounded-full opacity-20 blur-2xl" style={{background: t.text.includes('sky') ? '#0ea5e9' : t.text.includes('emerald') ? '#10b981' : t.text.includes('orange') ? '#f97316' : '#f43f5e'}} />
      <div className="relative flex items-start justify-between gap-2">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">{label}</p>
          <p className="mt-3 text-[28px] font-extrabold text-white leading-none tabular-nums">{value}</p>
          {sub && <p className="mt-1.5 text-[11px] text-slate-500 font-medium">{sub}</p>}
          {delta !== undefined && (
            <p className={`mt-2 text-[11px] font-semibold ${delta >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
              {delta >= 0 ? '↑' : '↓'} {Math.abs(delta)}%
            </p>
          )}
        </div>
        {icon && (
          <span className={`text-2xl ${t.icon} opacity-60 group-hover:opacity-90 transition-opacity`}>{icon}</span>
        )}
      </div>
    </div>
  )
}

export function StatGrid({ items }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <MetricCard key={item.label} {...item} />
      ))}
    </div>
  )
}

// ─── Notice ───────────────────────────────────────────────────────────────────
export function Notice({ tone, children }) {
  const tones = {
    success: 'border-emerald-500/25 bg-emerald-500/8 text-emerald-200',
    error:   'border-rose-500/25 bg-rose-500/8 text-rose-200',
  }
  return (
    <div className={`mt-4 flex items-start gap-3 rounded-[12px] border px-4 py-3 text-[13px] leading-relaxed ${tones[tone]}`}>
      <span>{tone === 'error' ? '⚠' : '✓'}</span>
      <span>{children}</span>
    </div>
  )
}

// ─── Action Button ────────────────────────────────────────────────────────────
export function ActionButton({ children, onClick, variant = 'primary', disabled = false, title, ariaLabel }) {
  const variants = {
    primary:   'bg-gradient-to-r from-orange-500 to-amber-600 text-white hover:from-orange-400 hover:to-amber-500 shadow-[0_2px_16px_rgba(249,115,22,0.35)] hover:shadow-[0_4px_24px_rgba(249,115,22,0.5)] active:scale-[0.97]',
    secondary: 'bg-white/[0.04] text-slate-300 hover:bg-white/[0.08] border border-white/[0.08] hover:border-white/[0.15] active:scale-[0.97]',
    accent:    'bg-gradient-to-r from-amber-400 to-amber-500 text-[#0a0c10] font-bold hover:from-amber-300 hover:to-amber-400 shadow-[0_2px_16px_rgba(251,191,36,0.35)] active:scale-[0.97]',
    ghost:     'text-slate-400 hover:text-white hover:bg-white/[0.06]',
  }
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-label={ariaLabel}
      className={`inline-flex items-center gap-2 rounded-[10px] px-4 py-2 text-[12px] font-semibold transition-all duration-200 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer ${variants[variant]}`}
    >
      {children}
    </button>
  )
}

// ─── Tab Button ───────────────────────────────────────────────────────────────
export function TabButton({ active, children, onClick, ariaLabel }) {
  return (
    <button
      onClick={onClick}
      aria-label={ariaLabel}
      aria-current={active ? 'page' : undefined}
      className={`rounded-[10px] px-4 py-2 text-[12px] font-semibold transition-all duration-200 cursor-pointer ${
        active
          ? 'bg-gradient-to-r from-orange-500 to-amber-600 text-white shadow-[0_2px_16px_rgba(249,115,22,0.3)]'
          : 'bg-white/[0.03] text-slate-400 border border-white/[0.06] hover:text-white hover:bg-white/[0.06] hover:border-white/[0.10]'
      }`}
    >
      {children}
    </button>
  )
}

// ─── Label ────────────────────────────────────────────────────────────────────
export function Label({ children }) {
  return (
    <span className="mb-1.5 block text-[12px] font-medium text-slate-400">{children}</span>
  )
}

// ─── Input ────────────────────────────────────────────────────────────────────
export function Input({ label, type = 'text', value, onChange, placeholder, required }) {
  return (
    <label className="block">
      {label && <Label>{label}{required && <span className="text-rose-500"> *</span>}</Label>}
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        required={required}
        onChange={(e) => onChange(e.target.value)}
        style={{ colorScheme: 'dark' }}
        className="w-full rounded-[12px] border border-white/[0.14] bg-[#111620] px-4 py-3 text-[13px] text-white outline-none transition duration-200 placeholder:text-slate-600 focus:border-orange-500/60 focus:ring-2 focus:ring-orange-500/20 focus:shadow-[0_0_16px_rgba(249,115,22,0.1)]"
      />
    </label>
  )
}

// ─── Select ───────────────────────────────────────────────────────────────────
export function Select({ label, value, onChange, options }) {
  return (
    <label className="block">
      {label && <Label>{label}</Label>}
      <div className="relative">
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          style={{ colorScheme: 'dark' }}
          className="w-full appearance-none rounded-[10px] border border-white/[0.12] bg-[#111620] pl-3 pr-9 py-2.5 text-[13px] text-white outline-none transition focus:border-orange-500/50 focus:ring-1 focus:ring-orange-500/20 cursor-pointer"
        >
          {options.map((option) => (
            <option
              key={option}
              value={option}
              style={{ backgroundColor: '#111620', color: '#e2e8f0', padding: '8px' }}
            >
              {option}
            </option>
          ))}
        </select>
        {/* Custom dropdown chevron */}
        <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" className="text-slate-400">
            <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
      </div>
      {/* Seçili değer göstergesi */}
      {value && (
        <div className="mt-1 flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-orange-400" />
          <span className="text-[10px] font-medium text-orange-400/80 tracking-wide">{value}</span>
        </div>
      )}
    </label>
  )
}

// ─── Info List ────────────────────────────────────────────────────────────────
export function InfoList({ items }) {
  return (
    <div className="space-y-2">
      {items.map((item) => (
        <div key={item.label} className="flex items-center justify-between gap-4 py-2 border-b border-white/[0.04] last:border-0">
          <span className="text-[13px] text-slate-500">{item.label}</span>
          <span className="text-[13px] font-semibold text-white tabular-nums">{item.value}</span>
        </div>
      ))}
    </div>
  )
}

// ─── Route Steps ─────────────────────────────────────────────────────────────
export function RouteSteps({ steps }) {
  if (!steps.length) {
    return (
      <div className="rounded-[12px] border border-dashed border-white/10 p-4 text-center text-[13px] text-slate-600">
        Rota seçildiğinde duraklar burada görünür
      </div>
    )
  }
  return (
    <div className="space-y-1.5">
      {steps.map((step, index) => (
        <div key={`${step}-${index}`} className="flex items-center gap-3">
          <div className="flex flex-col items-center">
            <div className={`flex h-6 w-6 items-center justify-center rounded-full text-[10px] font-bold ${
              index === 0 ? 'bg-orange-500 text-white' :
              index === steps.length - 1 ? 'bg-emerald-500 text-white' :
              'bg-[#1c2230] text-slate-400'
            }`}>
              {index + 1}
            </div>
            {index < steps.length - 1 && (
              <div className="w-px h-4 bg-white/[0.06]" />
            )}
          </div>
          <div className="flex-1 rounded-[8px] border border-white/[0.06] bg-[#111620] px-3 py-2">
            <p className="text-[13px] font-medium text-white">{step}</p>
            <p className="text-[10px] uppercase tracking-[0.14em] text-slate-600">
              {index === 0 ? 'Başlangıç' : index === steps.length - 1 ? 'Hedef' : 'Ara nokta'}
            </p>
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── List Panel ───────────────────────────────────────────────────────────────
export function ListPanel({ items, empty }) {
  if (!items.length) {
    return (
      <div className="rounded-[12px] border border-dashed border-white/10 p-4 text-center text-[13px] text-slate-600">{empty}</div>
    )
  }
  return (
    <div className="space-y-2">
      {items.map((item) => (
        <div key={`${item.title}-${item.subtitle}`} className="flex items-start justify-between gap-3 rounded-[12px] border border-white/[0.06] bg-[#111620] p-4">
          <div className="min-w-0">
            <p className="text-[13px] font-semibold text-white truncate">{item.title}</p>
            <p className="mt-0.5 text-[12px] text-slate-500 leading-relaxed">{item.subtitle}</p>
          </div>
          <div className="shrink-0 text-right">
            <span className="rounded-full bg-[#1c2230] px-2.5 py-1 text-[11px] text-slate-300">{item.badge}</span>
            <p className="mt-1.5 text-[10px] uppercase tracking-[0.14em] text-slate-600">{item.meta}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── Chart Card ───────────────────────────────────────────────────────────────
export function ChartCard({ title, children, height = 'h-52' }) {
  return (
    <div className={`${height} w-full`}>
      {title && (
        <p className="mb-3 text-[12px] font-medium uppercase tracking-[0.16em] text-slate-500">{title}</p>
      )}
      <div className="h-full w-full">
        <ResponsiveContainer width="100%" height="100%">
          {children}
        </ResponsiveContainer>
      </div>
    </div>
  )
}

// ─── Data Table ───────────────────────────────────────────────────────────────
export function DataTable({ columns, rows, emptyMessage }) {
  if (!rows.length) {
    return (
      <div className="rounded-[12px] border border-dashed border-white/10 p-6 text-center text-[13px] text-slate-600">
        {emptyMessage || 'Veri yok'}
      </div>
    )
  }
  return (
    <div className="overflow-auto rounded-[12px] border border-white/[0.07]">
      <table className="min-w-full text-left text-[13px]">
        <thead>
          <tr className="border-b border-white/[0.07] bg-[#111620]">
            {columns.map((col) => (
              <th key={col} className="px-4 py-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500 whitespace-nowrap">
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr key={ri} className="border-b border-white/[0.04] bg-[#0e1118] transition-colors hover:bg-[#131820] last:border-0">
              {row.map((cell, ci) => (
                <td key={`${ri}-${ci}`} className="px-4 py-3 text-slate-300 align-middle">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ─── Status Badge ─────────────────────────────────────────────────────────────
export function StatusBadge({ status }) {
  const map = {
    COMPLETED:              'bg-emerald-500/15 text-emerald-300 border-emerald-500/25',
    WAITING_REPLENISHMENT:  'bg-orange-500/15 text-orange-300 border-orange-500/25',
    IN_TRANSIT:             'bg-sky-500/15 text-sky-300 border-sky-500/25',
    LOADING:                'bg-amber-500/15 text-amber-300 border-amber-500/25',
    DELIVERING:             'bg-amber-500/15 text-amber-300 border-amber-500/25',
    EN_ROUTE:               'bg-emerald-500/15 text-emerald-300 border-emerald-500/25',
    RETURNING:              'bg-emerald-500/15 text-emerald-300 border-emerald-500/25',
    IDLE:                   'bg-sky-500/15 text-sky-300 border-sky-500/25',
    CREATED:                'bg-slate-700/50 text-slate-300 border-slate-600/25',
    ASSIGNED:               'bg-violet-500/15 text-violet-300 border-violet-500/25',
    FULFILLED_FROM_MICRO:   'bg-teal-500/15 text-teal-300 border-teal-500/25',
    FAILED:                 'bg-rose-500/15 text-rose-300 border-rose-500/25',
  }
  const cls = map[status] || 'bg-slate-700/50 text-slate-400 border-slate-600/25'
  const label = status === 'DELIVERING' ? 'UNLOADING' : status === 'FULFILLED_FROM_MICRO' ? 'MICROFULFILLED' : status
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-[0.1em] ${cls}`}>
      {label}
    </span>
  )
}

// ─── Section Header (alt başlık) ──────────────────────────────────────────────
export function SectionLabel({ children, className = '' }) {
  return (
    <p className={`text-[11px] font-semibold uppercase tracking-[0.20em] text-slate-600 ${className}`}>{children}</p>
  )
}

// ─── Divider ─────────────────────────────────────────────────────────────────
export function Divider() {
  return <div className="my-4 border-b border-white/[0.05]" />
}

// ─── Empty State ─────────────────────────────────────────────────────────────
export function EmptyState({ message }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[14px] border border-dashed border-white/10 py-10 text-center">
      <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-[#161b24] text-slate-600">
        <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
          <path d="M9 3v6m0 3v.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="9" cy="9" r="7.5" stroke="currentColor" strokeWidth="1.5" />
        </svg>
      </div>
      <p className="text-[13px] text-slate-600">{message}</p>
    </div>
  )
}

// ─── Progress Bar ─────────────────────────────────────────────────────────────
export function ProgressBar({ value, max, tone = 'orange', showLabel = true }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  const fill = tone === 'emerald' ? 'bg-gradient-to-r from-emerald-500 to-emerald-400' : tone === 'sky' ? 'bg-gradient-to-r from-sky-500 to-sky-400' : 'bg-gradient-to-r from-orange-500 to-amber-400'
  return (
    <div className="space-y-2">
      {showLabel && (
        <div className="flex justify-between text-[12px]">
          <span className="text-slate-500 font-medium">{value} / {max}</span>
          <span className="font-semibold text-white">{pct}%</span>
        </div>
      )}
      <div className="h-2 overflow-hidden rounded-full bg-[#1c2230] shadow-inset">
        <div
          className={`h-full rounded-full transition-all duration-500 shadow-[0_0_12px_rgba(249,115,22,0.3)] ${fill}`}
          style={{ width: `${Math.max(pct, 2)}%` }}
        />
      </div>
    </div>
  )
}

// ─── Sidebar Layout ───────────────────────────────────────────────────────────
// Referanstaki gibi sol dar sidebar + sağ geniş içerik
export function SidebarLayout({ sidebar, children }) {
  return (
    <div className="grid gap-4 xl:grid-cols-[300px,1fr]">
      <div className="flex flex-col gap-4">{sidebar}</div>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

// ─── Mini Stat (sidebar için kompakt metric) ──────────────────────────────────
export function MiniStat({ label, value, tone = 'default' }) {
  const tones = {
    default:  'text-white',
    emerald:  'text-emerald-400',
    orange:   'text-orange-400',
    sky:      'text-sky-400',
    rose:     'text-rose-400',
  }
  return (
    <div className="flex items-center justify-between py-2 border-b border-white/[0.04] last:border-0">
      <span className="text-[12px] text-slate-500">{label}</span>
      <span className={`text-[13px] font-bold tabular-nums ${tones[tone]}`}>{value}</span>
    </div>
  )
}

// ─── Fleet Distribution Bar (referanstaki gibi) ───────────────────────────────
export function FleetDistributionBar({ label, count, total, icon }) {
  const pct = total > 0 ? Math.round((count / total) * 100) : 0
  return (
    <div className="flex items-center gap-3 py-1.5">
      <span className="w-16 shrink-0 text-[12px] text-slate-400">{icon} {label}</span>
      <div className="flex-1 h-1.5 overflow-hidden rounded-full bg-[#1c2230]">
        <div
          className="h-full rounded-full bg-orange-500/70 transition-all duration-500"
          style={{ width: `${Math.max(pct, 2)}%` }}
        />
      </div>
      <span className="w-8 text-right text-[12px] font-bold text-slate-300 tabular-nums">{pct}%</span>
    </div>
  )
}

// ─── Vehicle Status Pill ──────────────────────────────────────────────────────
export function VehicleStatusPill({ status }) {
  return <StatusBadge status={status} />
}

// ─── Toast Notification (Pops up when a vehicle gets assigned) ───────────────

export function Toast({ id, title, subtitle, meta, onClose }) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    // animate in
    requestAnimationFrame(() => setVisible(true))
    // auto remove
    const timer = setTimeout(() => {
      setVisible(false)
      setTimeout(() => onClose(id), 300) // wait for fade out
    }, 5000)
    return () => clearTimeout(timer)
  }, [id, onClose])

  return (
    <div
      className={`pointer-events-auto flex w-80 max-w-full items-start gap-4 rounded-[16px] border border-orange-500/30 bg-[#161b24] p-4 shadow-[0_8px_32px_rgba(249,115,22,0.15)] transition-all duration-300 transform ${
        visible ? 'translate-x-0 opacity-100 scale-100' : 'translate-x-8 opacity-0 scale-95'
      }`}
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-orange-500 shadow-[0_0_15px_rgba(249,115,22,0.4)]">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M5 12l5 5L20 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-bold text-white truncate">{title}</p>
        <p className="mt-0.5 text-[12px] text-slate-400 break-words leading-relaxed">{subtitle}</p>
        {meta && <p className="mt-2 text-[10px] uppercase font-bold tracking-[0.16em] text-orange-400">{meta}</p>}
      </div>
      <button onClick={() => setVisible(false)} className="shrink-0 text-slate-500 hover:text-white transition">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M18 6L6 18M6 6l12 12"/>
        </svg>
      </button>
    </div>
  )
}

export function ToastContainer({ toasts, removeToast }) {
  if (!toasts || toasts.length === 0) return null

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-3 pointer-events-none">
      {toasts.map(t => (
        <Toast key={t.id} {...t} onClose={removeToast} />
      ))}
    </div>
  )
}
