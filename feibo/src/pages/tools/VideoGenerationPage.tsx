import { useEffect, useRef, useState } from 'react';
import { Clapperboard, Sparkles, RefreshCw, Play, ArrowUpRight, Clock, Film } from 'lucide-react';
import { videoGenerationApi, type VideoCreate, type VideoProvider, type VideoTask } from '@/services/videoGenerationApi';
import './videoGeneration.css';
import { AppRequestError } from '@/services/http/errors';
import EntitlementSummary from '@/components/EntitlementSummary';
import RechargeModal from '@/components/RechargeModal';
import { refreshEntitlements } from '@/services/entitlementApi';

const statusNames: Record<string, string> = { submitting: '正在提交', queued: '排队中', running: '生成中', succeeded: '已完成', failed: '生成失败', unknown: '待核查' };
const examples = [
  { title: '自然风光', text: '清晨的山间森林，阳光穿过薄雾，镜头缓慢向前推进，树叶随微风轻轻摇动，写实电影质感，柔和自然光，无文字。' },
  { title: '健康生活', text: '明亮温馨的厨房，一双手将新鲜蔬菜摆入白色餐盘，镜头从俯拍缓慢切换至近景，自然光，生活纪录片风格，无文字。' },
  { title: '科普片头', text: '清澈的蓝色水滴在纯白背景中缓慢漂浮，柔和的光线折射出细腻纹理，镜头平稳环绕，简洁的三维动画风格，无文字。' },
];
const message = (error: unknown) => error instanceof Error ? error.message : '请求失败，请稍后重试';

export default function VideoGenerationPage() {
  const [providers, setProviders] = useState<VideoProvider[]>([]);
  const [provider, setProvider] = useState('');
  const [prompt, setPrompt] = useState('');
  const [duration, setDuration] = useState(5);
  const [resolution, setResolution] = useState('720P');
  const [ratio, setRatio] = useState('16:9');
  const [tasks, setTasks] = useState<VideoTask[]>([]);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [showRecharge, setShowRecharge] = useState(false);
  const generation = useRef(0);
  const submitting = useRef(false);
  const pending = useRef<VideoCreate | null>(null);
  const taskRef = useRef(tasks);
  taskRef.current = tasks;
  const load = async () => {
    const run = ++generation.current;
    setLoading(true); setError('');
    try {
      const [available, history] = await Promise.all([videoGenerationApi.providers(), videoGenerationApi.tasks()]);
      if (generation.current !== run) return;
      setProviders(available); setTasks(history);
      setProvider(current => available.some(p => p.provider === current) ? current : available[0]?.provider || '');
      setSelected(current => history.some(t => t.id === current) ? current : history[0]?.id || '');
    } catch (e) { if (generation.current === run) setError(message(e)); }
    finally { if (generation.current === run) setLoading(false); }
  };
  useEffect(() => { void load(); return () => { generation.current++; }; }, []);
  useEffect(() => { setDuration(provider === 'minimax' ? 6 : 5); setRatio('16:9'); setResolution('720P'); }, [provider]);
  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      const run = generation.current;
      const active = taskRef.current.filter(t => ['queued', 'running'].includes(t.status));
      const results = await Promise.allSettled(active.map(t => videoGenerationApi.refresh(t.id)));
      if (cancelled) return;
      if (run === generation.current) {
        const updates = results.flatMap(r => r.status === 'fulfilled' ? [r.value] : []);
        if (updates.length) {
          setTasks(previous => previous.map(t => updates.find(u => u.id === t.id) || t));
          if (updates.some(t => ['failed', 'succeeded'].includes(t.status))) refreshEntitlements();
        }
        if (results.some(r => r.status === 'rejected')) setError('任务状态暂时无法更新，系统会继续查询，也可手动刷新。');
      }
      timer = setTimeout(poll, 10000);
    };
    timer = setTimeout(poll, 10000);
    return () => { cancelled = true; clearTimeout(timer); };
  }, []);
  const submit = async () => {
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setError('');
    const run = generation.current;
    const request = pending.current || { requestId: crypto.randomUUID(), provider, prompt: prompt.trim(), duration, resolution, ratio };
    pending.current = request;
    try {
      const task = await videoGenerationApi.create(request);
      if (run !== generation.current) return;
      pending.current = null;
      setTasks(previous => [task, ...previous.filter(t => t.id !== task.id)]); setSelected(task.id);
      refreshEntitlements();
    } catch (e) {
      if (run === generation.current) {
        if (e instanceof AppRequestError && (String(e.code) === '400' || [400, 403, 422].includes(e.status || 0))) pending.current = null;
        setError(`${message(e)}${pending.current ? '。再次点击将核查同一请求，不会新建重复任务。' : ''}`);
      }
    }
    finally { submitting.current = false; if (run === generation.current) setBusy(false); }
  };
  const refreshSelected = async () => {
    if (!selected || busy) return;
    const run = generation.current; setBusy(true); setError('');
    try {
      const task = await videoGenerationApi.refresh(selected);
      if (run === generation.current) setTasks(previous => previous.map(t => t.id === task.id ? task : t));
    } catch (e) { if (run === generation.current) setError(message(e)); }
    finally { if (run === generation.current) setBusy(false); }
  };
  const current = tasks.find(t => t.id === selected);
  const locked = busy || pending.current !== null;
  return <div className="video-studio">
    <div className="video-studio-heading"><div><span className="video-eyebrow">AI VIDEO STUDIO</span><h1>让灵感，成为画面 <Sparkles size={23} /></h1><p>描述你的创意，使用 AI 生成一段全新的视频。</p></div><span className="video-mode"><Clapperboard size={17} /> 文字生成视频</span></div>
    {error && <div role="alert" className="video-alert">{error} <button disabled={busy} onClick={() => void load()}>重新加载</button></div>}
    <div className="video-studio-grid">
      <section className="video-create-card" aria-label="视频生成设置">
        <h2><Sparkles size={18} /> 创作设置</h2>
        <EntitlementSummary compact />
        <button type="button" className="mt-2 text-sm text-teal-700 underline" onClick={() => setShowRecharge(true)}>会员与充值</button>
        <label htmlFor="video-provider">生成模型</label>
        <select id="video-provider" value={provider} disabled={locked || loading} onChange={e => setProvider(e.target.value)}>{!providers.length && <option value="">{loading ? '正在加载模型…' : '暂无可用模型'}</option>}{providers.map(p => <option key={p.provider} value={p.provider}>{p.name}</option>)}</select>
        {!loading && !providers.length && <p className="video-hint">请管理员在后台「AI 视频管理」配置并启用模型后开始创作。</p>}
        <div className="video-label-row"><label htmlFor="video-prompt">画面描述</label><span>{prompt.length} / 2000</span></div>
        <textarea id="video-prompt" value={prompt} disabled={locked} maxLength={2000} onChange={e => setPrompt(e.target.value)} placeholder="描述主体、场景、动作与镜头，例如：清晨的森林，阳光穿过薄雾，镜头缓慢向前推进…" />
        <div className="video-examples">{examples.map(example => <button key={example.title} disabled={locked} onClick={() => setPrompt(example.text)}>{example.title} <ArrowUpRight size={12} /></button>)}</div>
        <div className="video-options"><div><label htmlFor="video-duration">视频时长</label><select id="video-duration" value={duration} disabled={locked} onChange={e => { setDuration(+e.target.value); if (+e.target.value === 10 && provider === 'minimax') setResolution('720P'); }}><option value={provider === 'minimax' ? 6 : 5}>{provider === 'minimax' ? 6 : 5} 秒</option><option value={10}>10 秒</option></select></div><div><label htmlFor="video-resolution">清晰度</label><select id="video-resolution" disabled={locked} value={resolution} onChange={e => setResolution(e.target.value)}><option>720P</option><option disabled={provider === 'minimax' && duration === 10}>1080P</option></select></div></div>
        <label htmlFor="video-ratio">画面比例</label><select id="video-ratio" value={ratio} disabled={locked || provider === 'minimax'} onChange={e => setRatio(e.target.value)}><option value="16:9">16:9 横屏</option><option value="9:16">9:16 竖屏</option><option value="1:1">1:1 方形</option></select>
        {provider === 'minimax' && <p className="video-hint">MiniMax 文生视频使用模型默认横屏；10 秒视频支持 720P。</p>}
        <button className="video-generate" disabled={busy || loading || (!pending.current && (!provider || !prompt.trim()))} onClick={() => void submit()}><Sparkles size={18} />{busy ? '正在处理…' : pending.current ? '核查上次提交' : '开始生成视频'}</button>
        <p className="video-footnote">生成需要一些时间，可离开页面后返回查看。请勿在描述中填写患者隐私信息。</p>
      </section>
      <section className="video-result-card" aria-label="视频预览">
        <div className="video-result-title"><h2><Film size={18} /> 生成结果</h2>{current && <button disabled={busy} onClick={() => void refreshSelected()}><RefreshCw size={15} /> 刷新状态</button>}</div>
        <div className="video-preview">{current?.status === 'succeeded' && current.videoUrl ? <video key={current.videoUrl} src={current.videoUrl} controls preload="metadata" /> : <div className="video-empty"><div className="video-empty-icon"><Play size={30} /></div><h3>{current ? statusNames[current.status] || current.status : '你的下一段精彩，从这里开始'}</h3><p>{current?.error || (current ? '任务已保存，生成完成后将在这里显示视频。' : '选择模型并描述画面，让 AI 帮你把想象变成视频。')}</p><span>Seedance · MiniMax</span></div>}</div>
        {current && <div className="video-result-info"><p>{current.prompt}</p><div><span>{current.provider === 'minimax' ? 'MiniMax' : 'Seedance'} · {current.duration} 秒 · {current.resolution}</span>{current.videoUrl && <a href={current.videoUrl} target="_blank" rel="noreferrer">打开 / 保存视频 <ArrowUpRight size={14} /></a>}</div>{current.videoUrl && <small>视频链接可能过期，请及时保存；无法播放时可刷新状态更新链接。</small>}</div>}
        <div className="video-history-title"><h2><Clock size={17} /> 最近生成</h2><span>{tasks.length} 个任务</span></div>
        <div className="video-history">{!tasks.length ? <p className="video-hint">{loading ? '正在加载历史记录…' : '还没有生成记录，开始你的第一次创作吧。'}</p> : tasks.map(task => <button key={task.id} className={task.id === selected ? 'selected' : ''} onClick={() => setSelected(task.id)}><div className="video-history-icon"><Film size={20} /></div><div><strong>{task.prompt}</strong><small>{task.duration} 秒 · {task.resolution} · {new Date(task.createdAt).toLocaleString('zh-CN')}</small></div><span className={`video-status ${task.status}`}>{statusNames[task.status] || task.status}</span></button>)}</div>
      </section>
    </div>
    <RechargeModal isOpen={showRecharge} onClose={() => setShowRecharge(false)} />
  </div>;
}
