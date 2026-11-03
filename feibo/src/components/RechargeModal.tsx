import { useEffect, useRef, useState } from 'react';
import { X, Check, Smartphone } from 'lucide-react';
import { fetchOrderStatus, formatDuration, formatPrice, lockTradeOrder } from '../services/tradeApi';
import { EntitlementCatalogue, EntitlementProduct, EntitlementTerms, fetchEntitlementCatalogue, formatQuotaSeconds, refreshEntitlements } from '../services/entitlementApi';
import QRCodeModal from './QRCodeModal';
import EntitlementSummary from './EntitlementSummary';

interface RechargeModalProps { isOpen: boolean; onClose: () => void }

function Terms({ terms, recharge = false }: { terms: EntitlementTerms; recharge?: boolean }) {
  return <ul className="mt-3 space-y-1.5 text-xs leading-5 text-slate-600">
    {!recharge && <li>普通模型对话无限制</li>}
    {!recharge && <li>高级模型 {terms.premiumChatDailyLimit < 0 ? '无限制' : `${terms.premiumChatDailyLimit} 次 / 天`}</li>}
    <li>数字人合成 {formatQuotaSeconds(terms.digitalHumanSeconds)}</li>
    <li>AI 视频生成 {formatQuotaSeconds(terms.aiVideoSeconds)}</li>
    {Number(terms.premiumChatCredits) > 0 && <li>高级对话额外 {terms.premiumChatCredits} 次</li>}
  </ul>;
}

export default function RechargeModal({ isOpen, onClose }: RechargeModalProps) {
  const [tab, setTab] = useState<'membership' | 'recharge'>('membership');
  const [catalogue, setCatalogue] = useState<EntitlementCatalogue | null>(null);
  const [selected, setSelected] = useState<EntitlementProduct | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [order, setOrder] = useState<{ orderId: string; url: string } | null>(null);
  const [showQR, setShowQR] = useState(false);
  const [fulfilled, setFulfilled] = useState(false);
  const [revision, setRevision] = useState(0);
  const generation = useRef(0);

  useEffect(() => {
    const run = ++generation.current;
    if (!isOpen) return;
    setLoading(true); setError(''); setOrder(null); setShowQR(false); setFulfilled(false); setSelected(null); setProcessing(false);
    fetchEntitlementCatalogue().then(data => { if (run === generation.current) setCatalogue(data); })
      .catch(cause => { if (run === generation.current) setError(cause instanceof Error ? cause.message : '商品加载失败'); })
      .finally(() => { if (run === generation.current) setLoading(false); });
    return () => { generation.current++; };
  }, [isOpen, revision]);

  useEffect(() => {
    if (!isOpen || !order || fulfilled) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const check = async () => {
      try {
        const result = await fetchOrderStatus(order.orderId);
        if (!active) return;
        if (result.fulfilled) {
          setFulfilled(true); setShowQR(false); setError(''); refreshEntitlements(); return;
        }
        if (result.status === 4 || result.status === 5) {
          setShowQR(false); setOrder(null); setSelected(null);
          setError('订单已关闭或退款处理中，请重新选择商品或联系管理员。'); return;
        }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : '暂时无法查询支付状态');
      }
      if (active) timer = setTimeout(check, 4000);
    };
    void check();
    return () => { active = false; if (timer) clearTimeout(timer); };
  }, [isOpen, order, fulfilled]);

  const purchase = async () => {
    if (!selected || processing || (order && !fulfilled)) return;
    const run = generation.current;
    setProcessing(true); setError('');
    try {
      const result = await lockTradeOrder({ productId: String(selected.productId), productType: tab === 'recharge' });
      if (run !== generation.current) return;
      if (!result.url) throw new Error('支付链接暂不可用，请稍后重试');
      setOrder({ orderId: String(result.orderId), url: result.url }); setFulfilled(false); setShowQR(true);
    } catch (cause) {
      if (run === generation.current) setError(cause instanceof Error ? cause.message : '创建订单失败，请重试');
    } finally { if (run === generation.current) setProcessing(false); }
  };
  if (!isOpen) return null;
  const products = (tab === 'membership' ? catalogue?.plans : catalogue?.rechargePacks) ?? [];
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" role="dialog" aria-modal="true" aria-labelledby="recharge-title">
    <div className="mx-4 max-h-[85vh] w-[520px] max-w-full overflow-y-auto rounded-xl bg-white p-5 shadow-xl">
      <div className="mb-4 flex items-center justify-between"><h2 id="recharge-title" className="text-xl font-semibold text-slate-800">会员与充值</h2><button onClick={onClose} aria-label="关闭" className="rounded p-1 text-slate-500 hover:bg-slate-100"><X size={22} /></button></div>
      <EntitlementSummary />
      {fulfilled && <div role="status" className="mt-4 rounded-lg bg-green-50 p-3 text-sm text-green-800"><Check className="mr-1 inline" size={16} />支付成功，权益已到账。</div>}
      <div className="my-4 flex border-b border-slate-200">{(['membership', 'recharge'] as const).map(value => <button key={value} disabled={processing || Boolean(order && !fulfilled)} onClick={() => { setTab(value); setSelected(null); }} className={`flex-1 border-b-2 py-3 text-sm disabled:opacity-50 ${tab === value ? 'border-teal-600 font-medium text-teal-700' : 'border-transparent text-slate-500'}`}>{value === 'membership' ? '会员套餐' : '充值包'}</button>)}</div>
      {error && <div role="alert" className="mb-3 rounded bg-red-50 p-3 text-sm text-red-700">{error}{!order && <button className="ml-2 underline" onClick={() => setRevision(value => value + 1)}>重试</button>}</div>}
      {loading ? <p className="py-6 text-center text-sm text-slate-500">正在加载商品…</p> : <div className="grid gap-3 sm:grid-cols-2">
        {tab === 'membership' && catalogue?.freePlan && <div className="rounded-lg border border-teal-200 bg-teal-50/50 p-4"><div className="flex items-center justify-between"><h3 className="font-medium text-slate-800">免费版</h3><span className="text-xs text-teal-700">所有账号默认拥有</span></div><p className="mt-2 text-xl font-semibold text-teal-700">¥0</p><Terms terms={catalogue.freePlan} /><p className="mt-3 text-xs text-slate-500">永久有效 · 视频额度累计赠送</p></div>}
        {products.map(product => <button key={String(product.productId)} disabled={processing || Boolean(order && !fulfilled)} onClick={() => { setSelected(product); if (fulfilled) { setOrder(null); setFulfilled(false); } }} className={`rounded-lg border p-4 text-left disabled:opacity-60 ${selected?.productId === product.productId ? 'border-teal-500 bg-teal-50 ring-1 ring-teal-500' : 'border-slate-200 hover:border-teal-300'}`}><h3 className="font-medium text-slate-800">{product.name}</h3><p className="mt-2 text-xl font-semibold text-teal-700">{formatPrice(product.price)}</p><Terms terms={product} recharge={tab === 'recharge'} /><p className="mt-3 text-xs text-slate-500">{formatDuration(product.durationDays)} · 视频按累计时长扣减</p></button>)}
        {products.length === 0 && <p className="py-5 text-center text-sm text-slate-500 sm:col-span-2">暂无上架的{tab === 'membership' ? '付费套餐' : '充值包'}</p>}
      </div>}
      {order && !fulfilled ? <div className="mt-5 rounded-lg border border-teal-100 p-3 text-sm text-slate-600"><p>等待支付确认；到账后会自动刷新权益。</p><button className="mt-2 text-teal-700 underline" onClick={() => setShowQR(true)}>查看支付二维码</button></div> : <button onClick={purchase} disabled={!selected || processing || loading || fulfilled} className="mt-5 flex w-full items-center justify-center gap-2 rounded-lg bg-teal-600 py-3 text-sm font-medium text-white hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-50"><Smartphone size={17} />{processing ? '正在创建订单…' : fulfilled ? '权益已到账' : selected ? `微信支付 ${formatPrice(selected.price)}` : '请选择套餐或充值包'}</button>}
      <p className="mt-3 text-center text-xs text-slate-400">支付到账以服务器确认结果为准</p>
      <QRCodeModal isOpen={showQR && Boolean(order)} onClose={() => setShowQR(false)} url={order?.url ?? ''} />
    </div>
  </div>;
}
