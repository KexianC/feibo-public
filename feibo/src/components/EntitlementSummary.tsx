import { useEffect, useState } from 'react';
import { Crown, RefreshCw } from 'lucide-react';
import { EntitlementSnapshot, fetchEntitlements, formatQuotaSeconds } from '../services/entitlementApi';

export default function EntitlementSummary({ compact = false }: { compact?: boolean }) {
  const [data, setData] = useState<EntitlementSnapshot | null>(null);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const refresh = () => setRevision(value => value + 1);
    window.addEventListener('entitlements-updated', refresh);
    window.addEventListener('focus', refresh);
    return () => {
      window.removeEventListener('entitlements-updated', refresh);
      window.removeEventListener('focus', refresh);
    };
  }, []);
  useEffect(() => {
    let current = true;
    setError('');
    fetchEntitlements().then(value => { if (current) setData(value); })
      .catch(cause => { if (current) setError(cause instanceof Error ? cause.message : '权益加载失败'); });
    return () => { current = false; };
  }, [revision]);
  return <section aria-label="会员与可用额度" className={`rounded-lg border border-teal-100 bg-gradient-to-r from-teal-50 to-blue-50 ${compact ? 'p-3 text-xs' : 'p-4 text-sm'}`}>
    <div className="mb-2 flex items-center justify-between gap-2">
      <span className="flex items-center gap-1 font-medium text-teal-800"><Crown size={16} />{data ? (data.unlimited ? '管理员 · 无限权益' : data.planName) : '会员权益'}</span>
      <button type="button" title="刷新权益" aria-label="刷新权益" onClick={() => setRevision(value => value + 1)} className="rounded p-1 text-teal-700 hover:bg-teal-100"><RefreshCw size={14} /></button>
    </div>
    {error ? <p role="alert" className="text-red-600">{error}</p> : !data ? <p className="text-slate-500">正在加载权益…</p> : <>
      <dl className="space-y-1.5 text-slate-600">
        <div className="flex justify-between gap-2"><dt>普通模型对话</dt><dd>无限制</dd></div>
        <div className="flex justify-between gap-2"><dt>高级模型今日剩余</dt><dd>{data.unlimited || data.premiumChatDailyLimit < 0 ? '无限制' : `${data.premiumChatTodayRemaining} 次`}</dd></div>
        {!data.unlimited && data.premiumChatCreditsRemaining > 0 && <div className="flex justify-between gap-2"><dt>高级对话额外次数</dt><dd>{data.premiumChatCreditsRemaining} 次</dd></div>}
        <div className="flex justify-between gap-2"><dt>数字人合成剩余</dt><dd>{data.unlimited ? '无限制' : formatQuotaSeconds(data.digitalHumanSecondsRemaining)}</dd></div>
        <div className="flex justify-between gap-2"><dt>AI 视频剩余</dt><dd>{data.unlimited ? '无限制' : formatQuotaSeconds(data.aiVideoSecondsRemaining)}</dd></div>
      </dl>
      {!compact && !data.unlimited && <p className="mt-3 text-xs leading-5 text-slate-500">高级模型每日额度于北京时间零点重置；免费视频为累计赠送额度，生成中的任务会预占时长，失败后返还。</p>}
    </>}
  </section>;
}
