import React, { useState, useMemo } from 'react';
import { ActionButton, Card, Input, Select, Notice } from './ui';

export function ManuelControlPanel({ nodes, products, inventory, replenishments = [], api, doAction }) {
  const [activeTab, setActiveTab] = useState('consume'); // 'consume' or 'replenish'
  const [replenishList, setReplenishList] = useState([]);

  const [selectedDepot, setSelectedDepot] = useState('M1');
  const [selectedProduct, setSelectedProduct] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [localFeedback, setLocalFeedback] = useState({ message: '', tone: 'info' });

  const microDepots = useMemo(() => 
    nodes.filter(n => n.type === 'MICRO_DEPOT').map(n => n.id), 
    [nodes]
  );

  const currentStock = useMemo(() => {
    const item = inventory.find(i => i.depotId === selectedDepot && i.productId === selectedProduct);
    return item ? item.quantity : 0;
  }, [inventory, selectedDepot, selectedProduct]);

  // Yeni: Yoldaki (bekleyen veya yolda olan) miktar
  const incomingStock = useMemo(() => {
    return replenishments
      .filter(r => r.status !== 'COMPLETED' && r.targetDepotId === selectedDepot)
      .flatMap(r => r.items || [])
      .filter(item => item.productId === selectedProduct)
      .reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  }, [replenishments, selectedDepot, selectedProduct]);

  const maxCapacity = 100;
  const totalStockProjection = currentStock + incomingStock;
  const isCapacityCritical = totalStockProjection >= maxCapacity;

  const addToReplenishList = () => {
    if (quantity <= 0) return;
    
    // Ön Kontrol: Eklenirse 100'ü geçecek mi?
    if (totalStockProjection + Number(quantity) > maxCapacity) {
      setLocalFeedback({ 
        message: `Kapasite aşımı! Toplam (${totalStockProjection} + ${quantity}) 100'ü geçemez.`, 
        tone: 'error' 
      });
      return;
    }

    const exists = replenishList.some(item => item.depotId === selectedDepot && item.productId === selectedProduct);
    if (exists) {
      setLocalFeedback({ message: 'Bu depo ve ürün zaten listede var.', tone: 'error' });
      return;
    }
    setReplenishList([...replenishList, { depotId: selectedDepot, productId: selectedProduct, quantity: Number(quantity) }]);
    setLocalFeedback({ message: 'Listeye eklendi.', tone: 'success' });
    setTimeout(() => setLocalFeedback({ message: '', tone: 'info' }), 2000);
  };

  const removeFromReplenishList = (index) => {
    setReplenishList(replenishList.filter((_, i) => i !== index));
  };

  const handleAction = async () => {
    const isConsume = activeTab === 'consume';
    setLocalFeedback({ message: '', tone: 'info' });

    try {
      if (isConsume) {
        if (quantity > currentStock) {
          setLocalFeedback({ message: 'Yetersiz stok!', tone: 'error' });
          return;
        }
        await doAction('Manuel Tüketim', () => 
          api.post('/api/inventory/consume', {
            depotId: selectedDepot,
            productId: selectedProduct,
            quantity: Number(quantity)
          })
        );
      } else {
        if (replenishList.length === 0) {
          setLocalFeedback({ message: 'Lütfen en az bir ikmal talebi ekleyin.', tone: 'error' });
          return;
        }
        
        // doAction içindeki hata yakalamayı bypass edip burada yakalamak için doğrudan çağrı yapıyoruz
        // veya doAction'ın hata fırlatmasını bekliyoruz.
        // Backend'deki BadRequestException'ı yakalayıp ekranda göstermek için:
        for (const item of replenishList) {
          try {
            await api.post('/api/inventory/replenish', item);
          } catch (err) {
             const msg = err.response?.data?.message || err.message;
             setLocalFeedback({ message: `HATA: ${msg}`, tone: 'error' });
             return; // İlk hatada dur
          }
        }
        setReplenishList([]);
      }

      setLocalFeedback({ message: 'İşlem başarıyla gerçekleşti.', tone: 'success' });
      setTimeout(() => setLocalFeedback({ message: '', tone: 'info' }), 4000);
    } catch (err) {
      setLocalFeedback({ message: err.message, tone: 'error' });
    }
  };

  return (
    <Card 
      title="Akıllı Envanter Yönetimi" 
      subtitle="Mikro depolarda manuel tüketim veya araçlı ikmal görevleri planlayın."
      className="overflow-hidden border border-white/[0.08] bg-[#0d1117] shadow-2xl"
    >
      {/* Tab Switcher */}
      <div className="mb-6 flex overflow-hidden rounded-xl border border-white/[0.05] bg-[#161b22] p-1">
        <button
          onClick={() => setActiveTab('consume')}
          className={`flex-1 rounded-lg py-2 text-[12px] font-bold transition-all ${
            activeTab === 'consume' 
            ? 'bg-rose-500/10 text-rose-400 shadow-[inset_0_0_12px_rgba(244,63,94,0.1)]' 
            : 'text-slate-500 hover:text-slate-300'
          }`}
        >
          🔻 TÜKETİM SİMÜLASYONU
        </button>
        <button
          onClick={() => setActiveTab('replenish')}
          className={`flex-1 rounded-lg py-2 text-[12px] font-bold transition-all ${
            activeTab === 'replenish' 
            ? 'bg-emerald-500/10 text-emerald-400 shadow-[inset_0_0_12px_rgba(16,185,129,0.1)]' 
            : 'text-slate-500 hover:text-slate-300'
          }`}
        >
          🔺 MANUEL İKMAL GÖREVİ
        </button>
      </div>

      <div className="space-y-5">
        <div className="grid grid-cols-2 gap-4">
          <Select 
            label="Mikro Depo Seçimi" 
            value={selectedDepot} 
            onChange={setSelectedDepot} 
            options={microDepots} 
          />
          <Select 
            label="Ürün Kategorisi" 
            value={selectedProduct} 
            onChange={setSelectedProduct} 
            options={products.map(p => p.id)} 
          />
        </div>

        {activeTab === 'consume' ? (
          <div className="rounded-2xl border border-white/[0.05] bg-[#111620] p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-[11px] font-medium uppercase tracking-wider text-slate-500">Mevcut Stok Durumu (Max: 100)</span>
              <span className={`text-[13px] font-bold ${currentStock < 20 ? 'text-rose-400' : 'text-emerald-400'}`}>
                {currentStock} / 100 Birim
              </span>
            </div>
            
            <div className="relative h-3 w-full overflow-hidden rounded-full bg-black/40">
              <div 
                className="absolute left-0 top-0 h-full transition-all duration-500 bg-rose-500"
                style={{ width: `${currentStock}%` }}
              />
               <div 
                className="absolute h-full bg-white/20 transition-all duration-300"
                style={{ 
                  left: `${Math.max(0, currentStock - quantity)}%`, 
                  width: `${Math.min(quantity, currentStock)}%` 
                }}
              />
            </div>
          </div>
        ) : (
          <div className="rounded-2xl border border-white/[0.05] bg-[#111620] p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-[11px] font-medium uppercase tracking-wider text-slate-500">Kapasite Durumu (Mevcut + Yolda)</span>
              <span className={`text-[13px] font-bold ${totalStockProjection >= 90 ? 'text-amber-400' : 'text-emerald-400'}`}>
                {totalStockProjection} / 100 Birim
              </span>
            </div>
            <div className="flex gap-1.5 text-[10px] mb-2">
              <span className="text-slate-500">Stok: <span className="text-white">{currentStock}</span></span>
              <span className="text-slate-500">|</span>
              <span className="text-slate-500">Yolda: <span className="text-sky-400">{incomingStock}</span></span>
            </div>
            <div className="relative h-3 w-full overflow-hidden rounded-full bg-black/40">
              <div 
                className="absolute left-0 top-0 h-full bg-emerald-600 transition-all duration-500"
                style={{ width: `${currentStock}%` }}
              />
              <div 
                className="absolute top-0 h-full bg-sky-500/60 transition-all duration-500"
                style={{ left: `${currentStock}%`, width: `${incomingStock}%` }}
              />
            </div>
            {isCapacityCritical && (
              <p className="mt-2 text-[10px] font-bold text-amber-400 uppercase tracking-tight">
                ⚠️ Kapasite Sınırına Yakın / Dolu
              </p>
            )}
          </div>
        )}

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-[12px] font-semibold text-slate-400">İşlem Miktarı</label>
            <span className="tabular-nums text-[14px] font-bold text-white">{quantity} Adet</span>
          </div>
          <input 
            type="range" 
            min="1" 
            max={activeTab === 'consume' ? Math.max(1, currentStock) : 50} 
            value={quantity} 
            onChange={(e) => setQuantity(Number(e.target.value))}
            className="h-1.5 w-full cursor-pointer appearance-none rounded-full bg-white/10 accent-indigo-500"
          />
        </div>

        {activeTab === 'replenish' && (
          <div className="space-y-3">
             <button
              onClick={addToReplenishList}
              className="w-full rounded-xl border border-emerald-500/30 bg-emerald-500/5 py-2 text-[11px] font-bold text-emerald-400 hover:bg-emerald-500/10"
            >
              + GÖREV LİSTESİNE EKLE
            </button>

            {replenishList.length > 0 && (
              <div className="max-h-[120px] space-y-2 overflow-y-auto rounded-xl border border-white/[0.05] bg-black/20 p-2">
                {replenishList.map((item, idx) => (
                  <div key={idx} className="flex items-center justify-between rounded-lg bg-white/5 px-3 py-2">
                    <div className="flex flex-col">
                      <span className="text-[10px] font-bold text-emerald-400">{item.depotId}</span>
                      <span className="text-[9px] text-slate-400">{item.productId} x{item.quantity}</span>
                    </div>
                    <button 
                      onClick={() => removeFromReplenishList(idx)}
                      className="text-slate-500 hover:text-rose-400"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {localFeedback.message && (
          <Notice tone={localFeedback.tone}>{localFeedback.message}</Notice>
        )}

        <div className="pt-2">
          <button
            onClick={handleAction}
            className={`group relative flex w-full items-center justify-center gap-2 overflow-hidden rounded-xl py-3.5 transition-all active:scale-[0.98] ${
              activeTab === 'consume'
              ? 'bg-gradient-to-r from-rose-600 to-orange-600 text-white shadow-[0_4px_20px_rgba(225,29,72,0.3)]'
              : 'bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-[0_4px_20px_rgba(5,150,105,0.3)]'
            }`}
          >
            <span className="font-bold tracking-wide">
              {activeTab === 'consume' ? 'MÜŞTERİ TALEBİ SİMÜLE ET' : 'KARE ARACI SEVK ET'}
            </span>
            <span className="text-lg transition-transform group-hover:translate-x-1">
              {activeTab === 'consume' ? '→' : '🚚'}
            </span>
          </button>
        </div>
      </div>

      <div className="mt-6 border-t border-white/[0.05] pt-4">
        <p className="text-center text-[11px] leading-relaxed text-slate-500">
          {activeTab === 'consume' 
            ? "Müşteri siparişi simülasyonu, seçilen depoda stok azalması yaratarak 'Otomatik İkmal' algoritmasını test etmenizi sağlar."
            : "Manuel görevler sadece 'Kare Araç' (7 No) tarafından yerine getirilir. Bekleme süresi ve doluluk kısıtlarına takılmadan anında göreve çıkar."
          }
        </p>
      </div>
    </Card>
  );
}
