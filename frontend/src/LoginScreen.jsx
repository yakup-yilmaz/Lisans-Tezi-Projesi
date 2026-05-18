import React, { useState, useEffect } from 'react';
import { Card, Input, Notice } from './components/ui';

export default function LoginScreen({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMousePos({ x: e.clientX, y: e.clientY });
    };
    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  const handleLogin = (e) => {
    e.preventDefault();
    if (username === 'admin' && password === '1234') {
      onLogin();
    } else {
      setError('Erişim reddedildi. Geçersiz kimlik bilgileri.');
    }
  };

  return (
    <div className="min-h-screen bg-[#04060a] text-white selection:bg-sky-500/30 overflow-x-hidden font-sans">
      
      {/* Interactive Background Glow */}
      <div 
        className="fixed inset-0 pointer-events-none transition-transform duration-300 ease-out z-0"
        style={{
          background: `radial-gradient(circle at ${mousePos.x}px ${mousePos.y}px, rgba(14, 165, 233, 0.08) 0%, transparent 40%)`
        }}
      />

      {/* Main Login Section */}
      <div className="relative min-h-screen flex flex-col items-center justify-center p-6">
        
        {/* Tech Grid Pattern */}
        <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/carbon-fibre.png')] opacity-[0.03] pointer-events-none" />
        <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-sky-500/50 to-transparent opacity-30" />
        
        <div className="w-full max-w-md z-10 space-y-8">
          
          {/* Header */}
          <div className="text-center space-y-4 animate-in fade-in slide-in-from-top-10 duration-1000">
             <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-sky-500/10 border border-sky-500/20 text-[10px] font-bold text-sky-400 uppercase tracking-[0.2em] mb-4">
                <span className="w-2 h-2 rounded-full bg-sky-500 animate-pulse" />
                Simulation Systems Online
             </div>
             <h1 className="text-7xl font-black tracking-tighter italic">
                <span className="bg-clip-text text-transparent bg-gradient-to-b from-white to-slate-500">Y</span>
                <span className="bg-clip-text text-transparent bg-gradient-to-r from-sky-400 to-indigo-500">Logistics</span>
             </h1>
             <p className="text-slate-500 text-xs font-medium tracking-[0.4em] uppercase">
                Advanced Logistics Hub
             </p>
          </div>

          {/* Login Card */}
          <Card className="relative p-10 backdrop-blur-3xl bg-black/40 border-white/10 shadow-[0_0_50px_rgba(0,0,0,0.5)] overflow-hidden group">
            <div className="absolute top-0 left-0 w-1 h-full bg-gradient-to-b from-sky-500 to-transparent opacity-50" />
            
            <form onSubmit={handleLogin} className="space-y-8 relative">
              <div className="space-y-3">
                <div className="flex justify-between items-center">
                   <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Operator Identity</label>
                   <span className="text-[9px] text-sky-500/50 font-mono">SYS_AUTH_01</span>
                </div>
                <Input 
                  placeholder="admin" 
                  value={username} 
                  onChange={setUsername}
                  className="bg-white/[0.03] border-white/5 focus:border-sky-500/50 h-14 text-base tracking-wide"
                />
              </div>

              <div className="space-y-3">
                <div className="flex justify-between items-center">
                   <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Access Key</label>
                   <span className="text-[9px] text-sky-500/50 font-mono">ENCRYPTED_AES</span>
                </div>
                <Input 
                  type="password" 
                  placeholder="••••" 
                  value={password} 
                  onChange={setPassword}
                  className="bg-white/[0.03] border-white/5 focus:border-sky-500/50 h-14 text-base tracking-wide"
                />
              </div>

              {error && <Notice tone="error">{error}</Notice>}

              <button 
                type="submit"
                className="w-full h-14 bg-white text-black font-black text-sm rounded-xl tracking-[0.2em] hover:bg-sky-400 transition-all duration-300 transform active:scale-95 shadow-[0_0_30px_rgba(255,255,255,0.1)] uppercase"
              >
                Login
              </button>
            </form>
          </Card>

          <div className="text-center animate-in fade-in duration-1000 delay-700">
             <div className="h-10 w-px bg-gradient-to-b from-sky-500/50 to-transparent mx-auto mb-4" />
             <p className="text-[10px] text-slate-600 font-bold uppercase tracking-[0.3em]">
                Scroll to analyze core algorithms
             </p>
          </div>
        </div>
      </div>

      {/* Technical Deep Dive Sections */}
      <div className="max-w-6xl mx-auto px-8 pb-32 space-y-40">
        
        {/* Section 1: Greedy Dispatch */}
        <section className="grid lg:grid-cols-2 gap-20 items-center">
          <div className="space-y-8">
            <h3 className="text-5xl font-black tracking-tighter">Greedy Dispatch &<br/>Resource Allocation</h3>
            <p className="text-slate-400 leading-relaxed text-lg font-light">
              Sistem, binlerce olasılığı milisaniyeler içinde eleyen <span className="text-sky-400 font-bold">"Greedy Dispatch"</span> motoru üzerine kuruludur. 
              Talepler, aciliyet ve stok seviyelerine göre önceliklendirilir; en uygun araç, en verimli rota ile eşleştirilerek operasyonel hız maksimize edilir.
            </p>
            <div className="grid grid-cols-2 gap-4">
               <div className="p-4 bg-white/5 border border-white/5 rounded-xl">
                  <p className="text-sky-400 font-bold text-xl">0.4ms</p>
                  <p className="text-[10px] text-slate-500 uppercase">Decision Time</p>
               </div>
               <div className="p-4 bg-white/5 border border-white/5 rounded-xl">
                  <p className="text-sky-400 font-bold text-xl">99.8%</p>
                  <p className="text-[10px] text-slate-500 uppercase">Allocation Rate</p>
               </div>
            </div>
          </div>
          <div className="relative group">
             <div className="absolute inset-0 bg-sky-500/20 blur-[100px] opacity-0 group-hover:opacity-100 transition-opacity" />
             <div className="relative aspect-video bg-black/40 border border-white/10 rounded-2xl p-6 overflow-hidden">
                <div className="flex gap-2 mb-4">
                   <div className="w-2 h-2 rounded-full bg-rose-500" />
                   <div className="w-2 h-2 rounded-full bg-amber-500" />
                   <div className="w-2 h-2 rounded-full bg-emerald-500" />
                </div>
                <div className="space-y-3 font-mono text-[11px] text-sky-300/70">
                   <p>&gt; Analyzing replenishment pool...</p>
                   <p>&gt; Found 12 candidate routes for ECO-TRANSIT</p>
                   <p className="text-emerald-400">&gt; Optimal match found: Benefit 1240.5</p>
                   <p>&gt; Committing dispatch sequence...</p>
                </div>
             </div>
          </div>
        </section>

        {/* Section 2: LES Score Matrix */}
        <section className="grid lg:grid-cols-2 gap-20 items-center">
          <div className="lg:order-2 space-y-8 text-right">
            <h3 className="text-5xl font-black tracking-tighter">Logistics Efficiency<br/>Score (LES) Matrix</h3>
            <p className="text-slate-400 leading-relaxed text-lg font-light">
              Başarı tesadüf değildir. Her görev, <span className="text-indigo-400 font-bold">Benefit/Cost</span> oranını temel alan LES puanlama sisteminden geçer. 
              Kapasite doluluğu, aciliyet ve kargo finansal değeri birleştirilerek her rotaya 0-2000 arası bir verimlilik skoru atanır.
            </p>
            <div className="inline-block p-6 bg-indigo-500/10 border border-indigo-500/20 rounded-2xl">
               <code className="text-indigo-300 text-sm font-bold leading-relaxed">
                  LES = (Capacity * W1) + (Urgency² * W2)<br/>
                  Target: Maximize Efficiency / Distance
               </code>
            </div>
          </div>
          <div className="lg:order-1 bg-gradient-to-br from-indigo-500/10 via-transparent to-transparent border border-white/5 rounded-[40px] p-12 relative overflow-hidden">
             <div className="absolute inset-0 opacity-20 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-indigo-500 via-transparent to-transparent" />
             <div className="relative space-y-6">
                <div className="flex justify-between items-end h-32 gap-2">
                   <div className="w-full bg-indigo-500/20 h-1/4 rounded-t-lg" />
                   <div className="w-full bg-indigo-500/40 h-2/4 rounded-t-lg" />
                   <div className="w-full bg-indigo-500/60 h-3/4 rounded-t-lg" />
                   <div className="w-full bg-indigo-500 h-full rounded-t-lg" />
                </div>
                <p className="text-center text-[10px] text-slate-500 uppercase tracking-widest font-bold">Efficiency Benchmarking</p>
             </div>
          </div>
        </section>

        {/* Section 3: Human-in-the-Loop */}
        <section className="grid lg:grid-cols-2 gap-20 items-center">
          <div className="space-y-8">
            <h3 className="text-5xl font-black tracking-tighter">Human-in-the-Loop<br/>Intervention Layer</h3>
            <p className="text-slate-400 leading-relaxed text-lg font-light">
              Yüksek otonomiye rağmen, sistem operasyonel esneklik için insan kararını merkeze alır. 
              <span className="text-emerald-400 font-bold">%50-74 doluluk bandındaki</span> kritik sevkiyatlar, yöneticinin onayına sunularak dinamik bir karar süreci yönetilir.
            </p>
            <div className="flex items-center gap-4">
               <div className="flex -space-x-3">
                  {[1,2,3].map(i => <div key={i} className="w-10 h-10 rounded-full border-2 border-[#04060a] bg-slate-800" />)}
               </div>
               <p className="text-xs text-slate-500 font-medium">Collaborative Decision System</p>
            </div>
          </div>
          <div className="bg-emerald-500/5 border border-emerald-500/10 rounded-[40px] p-12 flex items-center justify-center">
             <div className="relative">
                <div className="w-24 h-24 rounded-full border-4 border-emerald-500/30 animate-ping absolute inset-0" />
                <div className="w-24 h-24 rounded-full bg-emerald-500/20 border-2 border-emerald-500 flex items-center justify-center relative">
                   <span className="text-emerald-400 text-xs font-black uppercase tracking-tighter">Approve</span>
                </div>
             </div>
          </div>
        </section>

        {/* Footer */}
        <footer className="pt-32 border-t border-white/5 flex flex-col md:flex-row justify-between items-center gap-8">
          <div className="space-y-2">
            <h4 className="text-2xl font-black italic">YLogistics</h4>
            <p className="text-[10px] text-slate-600 uppercase tracking-[0.3em]">Advanced Decision Support System</p>
          </div>
          <div className="flex gap-12 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
             <span className="hover:text-sky-400 cursor-pointer transition-colors">Documentation</span>
             <span className="hover:text-sky-400 cursor-pointer transition-colors">Architecture</span>
             <span className="hover:text-sky-400 cursor-pointer transition-colors">Metrics</span>
          </div>
        </footer>
      </div>
    </div>
  );
}
