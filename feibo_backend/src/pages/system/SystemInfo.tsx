import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Progress, Select, Skeleton, Space, Tag, Tooltip } from 'antd';
import { CloudServerOutlined, DatabaseOutlined, HddOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { formatMonitorMetric, getSystemMonitorSnapshot, type MonitorComponent, type MonitorComponentId, type MonitorMetric, type SystemMonitorSnapshot } from '@/services/systemMonitorService';
import './SystemInfo.css';

const statusLabels = {
  UP: { text: '正常', color: 'success' }, DEGRADED: { text: '需要关注', color: 'warning' },
  DOWN: { text: '连接异常', color: 'error' }, UNKNOWN: { text: '待采集', color: 'default' },
};
const componentDescriptions: Record<MonitorComponentId, string> = {
  server: '当前应用所在服务器的资源使用情况', jvm: '应用进程的内存、线程和运行时间',
  database: '应用数据库的连接、查询负载和空间', connectionPool: '应用连接池的占用与等待情况',
  redis: '缓存服务的连接、内存和处理情况', storage: '应用所在磁盘卷的可用空间',
};
const metricOf = (snapshot: SystemMonitorSnapshot | null, componentId: MonitorComponentId, key: string) =>
  snapshot?.components.find(component => component.id === componentId)?.metrics.find(metric => metric.key === key);
const numberOf = (snapshot: SystemMonitorSnapshot, key: string) => {
  const value = metricOf(snapshot, 'server', key)?.value;
  return typeof value === 'number' ? value : null;
};
const timeText = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
interface HistoryPoint { time: string; cpu: number | null; memory: number | null }

function Trend({ points }: { points: HistoryPoint[] }) {
  const buildPath = (key: 'cpu' | 'memory') => {
    let connected = false;
    return points.map((point, index) => {
      if (point[key] === null) { connected = false; return ''; }
      const start = Date.parse(points[0].time), end = Date.parse(points[points.length - 1].time);
      const x = 28 + (Date.parse(point.time) - start) / Math.max(end - start, 1) * 804;
      const y = 110 - Math.min(100, Math.max(0, point[key]!));
      const segment = `${connected ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`;
      connected = true;
      return segment;
    }).join(' ');
  };
  if (points.length < 2) return <div className="system-monitor-trend-empty">等待下一次采样后显示趋势，仅记录本次打开页面期间的数据。</div>;
  return <div className="system-monitor-trend">
    <svg viewBox="0 0 860 130" role="img" aria-label="本次会话服务器 CPU 和内存使用率趋势，纵轴为百分比">
      {[0, 50, 100].map(percent => <g key={percent}>
        <line x1="28" x2="832" y1={110 - percent} y2={110 - percent} stroke="#e8edf4" strokeDasharray="4 4" />
        <text x="21" y={114 - percent} textAnchor="end" fontSize="10" fill="#8693a6">{percent}</text>
      </g>)}
      <path d={buildPath('cpu')} fill="none" stroke="#1677ff" strokeWidth="2.5" />
      <path d={buildPath('memory')} fill="none" stroke="#18a889" strokeWidth="2.5" />
    </svg>
    <div className="system-monitor-trend-times"><span>{timeText(points[0].time)}</span><span>{timeText(points[points.length - 1].time)}</span></div>
  </div>;
}

function MetricRow({ metric }: { metric: MonitorMetric }) {
  const percent = metric.unit === 'percent' && typeof metric.value === 'number' ? metric.value : null;
  return <div className="system-monitor-metric" data-metric={metric.key}>
    <div className="system-monitor-metric-line"><span className="system-monitor-metric-label">{metric.label}</span>
      <span className={metric.value === null ? 'system-monitor-metric-missing' : 'system-monitor-metric-value'}>{formatMonitorMetric(metric)}</span></div>
    {percent !== null && <Progress percent={Math.min(100, Math.max(0, percent))} showInfo={false} size="small" strokeColor={percent >= 90 ? '#ff4d4f' : percent >= 80 ? '#faad14' : '#1677ff'} />}
    {metric.note && <div className="system-monitor-metric-note">{metric.note}</div>}
  </div>;
}

function ComponentCard({ component, historical }: { component: MonitorComponent; historical: boolean }) {
  const status = statusLabels[component.status];
  return <Card className="system-monitor-component" data-component={component.id} title={component.name}
    extra={<Tag color={historical ? 'default' : status.color}>{historical ? '上次采样' : status.text}</Tag>}>
    <div className="system-monitor-component-description">{componentDescriptions[component.id]}</div>
    {component.message && <Alert className="system-monitor-component-message" type={component.status === 'DOWN' ? 'error' : component.status === 'UP' ? 'info' : 'warning'} message={component.message} showIcon />}
    {component.metrics.length ? component.metrics.map(metric => <MetricRow key={metric.key} metric={metric} />) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用指标" />}
  </Card>;
}

export default function SystemInfo() {
  const container = useRef<HTMLDivElement>(null);
  const mounted = useRef(false), active = useRef(false), generation = useRef(0), resumeWhenIdle = useRef(false);
  const pending = useRef<Promise<void> | null>(null);
  const latestSample = useRef(0);
  const [snapshot, setSnapshot] = useState<SystemMonitorSnapshot | null>(null);
  const [history, setHistory] = useState<HistoryPoint[]>([]);
  const [loading, setLoading] = useState(false), [error, setError] = useState('');
  const [refreshInterval, setRefreshInterval] = useState(15), [pageActive, setPageActive] = useState(false), [now, setNow] = useState(Date.now());

  const refresh = useCallback(() => {
    if (!mounted.current || !active.current) return;
    if (pending.current) { resumeWhenIdle.current = true; return; }
    const run = ++generation.current;
    setLoading(true);
    const request = (async () => {
      try {
        const next = await getSystemMonitorSnapshot();
        if (!mounted.current || !active.current || run !== generation.current) return;
        if (Date.parse(next.sampledAt) < latestSample.current) throw new Error('监控采样时间早于当前结果');
        latestSample.current = Date.parse(next.sampledAt);
        setSnapshot(current => !current || Date.parse(next.sampledAt) >= Date.parse(current.sampledAt) ? next : current);
        setError(''); setNow(Date.now());
        setHistory(current => {
          if (current.length && Date.parse(next.sampledAt) <= Date.parse(current[current.length - 1].time)) return current;
          return [...current, { time: next.sampledAt, cpu: numberOf(next, 'cpuPercent'), memory: numberOf(next, 'physicalUsedPercent') }].slice(-40);
        });
      } catch (cause) {
        if (!mounted.current || !active.current || run !== generation.current) return;
        setError(cause instanceof Error && cause.message === '权限不足' ? '当前账号没有查看系统监控的权限。' : '暂时无法获取监控数据，请稍后重试。');
      } finally {
        pending.current = null;
        if (mounted.current && run === generation.current) setLoading(false);
        if (mounted.current && active.current && resumeWhenIdle.current) { resumeWhenIdle.current = false; refresh(); }
      }
    })();
    pending.current = request;
  }, []);

  useEffect(() => {
    mounted.current = true;
    const pane = container.current?.closest('[role="tabpanel"]');
    const updateVisibility = () => {
      const visible = !document.hidden && pane?.getAttribute('aria-hidden') !== 'true' && !pane?.classList.contains('ant-tabs-tabpane-hidden');
      const wasActive = active.current; active.current = visible; setPageActive(visible);
      if (!visible) { generation.current++; resumeWhenIdle.current = false; setLoading(false); }
      else if (!wasActive) refresh();
    };
    const observer = new MutationObserver(updateVisibility);
    if (pane) observer.observe(pane, { attributes: true, attributeFilter: ['class', 'aria-hidden', 'style'] });
    document.addEventListener('visibilitychange', updateVisibility); updateVisibility();
    return () => {
      mounted.current = false; active.current = false; generation.current++; resumeWhenIdle.current = false;
      observer.disconnect(); document.removeEventListener('visibilitychange', updateVisibility);
    };
  }, [refresh]);

  useEffect(() => {
    if (!pageActive || loading || refreshInterval === 0) return;
    const timer = window.setTimeout(refresh, refreshInterval * 1000);
    return () => window.clearTimeout(timer);
  }, [refresh, refreshInterval, pageActive, loading]);
  useEffect(() => {
    if (!pageActive) return;
    const timer = window.setInterval(() => setNow(Date.now()), 5000);
    return () => window.clearInterval(timer);
  }, [pageActive]);

  const ageSeconds = snapshot ? Math.max(0, (now - Date.parse(snapshot.sampledAt)) / 1000) : 0;
  const stale = !!snapshot && ageSeconds > Math.max(60, (refreshInterval || snapshot.refreshIntervalSeconds) * 3);
  const historical = !!error || stale;
  const attention = snapshot?.components.filter(component => component.status !== 'UP').length || 0;
  const overview = [
    { label: '服务器 CPU', component: 'server' as const, key: 'cpuPercent', icon: <CloudServerOutlined /> },
    { label: '服务器内存', component: 'server' as const, key: 'physicalUsedPercent', icon: <ThunderboltOutlined /> },
    { label: '数据库活动线程', component: 'database' as const, key: 'threadsRunning', icon: <DatabaseOutlined /> },
    { label: '磁盘使用率', component: 'storage' as const, key: 'diskUsedPercent', icon: <HddOutlined /> },
  ];
  return <div ref={container} className="system-monitor-page">
    <div className="system-monitor-header"><div><h1>系统监控</h1><p>查看服务状态与资源负载，数据来自实际运行环境。</p></div>
      <Space wrap><label htmlFor="system-monitor-refresh">自动刷新</label>
        <Select id="system-monitor-refresh" aria-label="自动刷新间隔" value={refreshInterval} onChange={setRefreshInterval}
          options={[{ value: 15, label: '每 15 秒' }, { value: 30, label: '每 30 秒' }, { value: 60, label: '每 60 秒' }, { value: 0, label: '关闭' }]} style={{ width: 118 }} />
        <Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>立即刷新</Button></Space></div>
    <div className="system-monitor-status" role="status" aria-live="polite">
      <Tag color={!snapshot ? 'default' : historical ? 'warning' : attention ? 'warning' : 'success'}>{!snapshot ? '等待采集' : historical ? '数据待更新' : attention ? `${attention} 项需要关注` : '各项运行正常'}</Tag>
      <span>{snapshot ? `最近采样：${timeText(snapshot.sampledAt)}` : error ? '未能获取采样' : '正在连接监控服务'}</span>
      <span className="system-monitor-refresh-note">{!pageActive ? '页面未显示，自动刷新已暂停' : refreshInterval === 0 ? '自动刷新已关闭' : '页面隐藏时暂停刷新'}</span></div>
    {error && <Alert type="error" showIcon message={error} description={snapshot ? '当前展示的是上次成功采样的数据，不能据此判断服务当前正常。' : '未获取到有效指标，恢复连接后可重试。'} />}
    {!error && stale && <Alert type="warning" showIcon message="采样数据已过期" description="下方保留最近一次结果，请刷新后查看当前状态。" />}
    {!snapshot && loading ? <Card><Skeleton active paragraph={{ rows: 8 }} /></Card> : !snapshot ? <Card><Empty description="暂无监控数据"><Button type="primary" onClick={refresh} loading={loading}>重新获取</Button></Empty></Card> : <>
      <div className="system-monitor-overview">{overview.map(item => {
        const metric = metricOf(snapshot, item.component, item.key);
        return <Card key={item.key} className="system-monitor-overview-card"><div className="system-monitor-overview-label">{item.icon}<span>{item.label}</span></div>
          <div className="system-monitor-overview-value">{formatMonitorMetric(metric)}</div>
          <Tooltip title={metric?.note}><div className="system-monitor-overview-note">{historical ? '上次采样数据' : metric?.note || '最近采样值'}</div></Tooltip></Card>;
      })}</div>
      <Card className="system-monitor-trend-card" title="服务器资源趋势" extra={<Space><span className="system-monitor-legend cpu">CPU</span><span className="system-monitor-legend memory">内存</span></Space>}>
        <Trend points={history} /><div className="system-monitor-trend-caption">仅展示本次会话的实际采样，最多保留 40 个点；断开或未采集的指标不会补成 0。</div></Card>
      <div className="system-monitor-component-grid">{snapshot.components.map(component => <ComponentCard key={component.id} component={component} historical={historical} />)}</div>
    </>}
  </div>;
}
