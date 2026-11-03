import { authenticatedRequest } from './auth';

export type MonitorStatus = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';
export type MonitorComponentId = 'server' | 'jvm' | 'database' | 'connectionPool' | 'redis' | 'storage';
export interface MonitorMetric { key: string; label: string; value: number | string | null; unit?: string; note?: string }
export interface MonitorComponent { id: MonitorComponentId; name: string; status: MonitorStatus; message?: string; metrics: MonitorMetric[] }
export interface SystemMonitorSnapshot { sampledAt: string; refreshIntervalSeconds: number; components: MonitorComponent[] }
const componentIds: MonitorComponentId[] = ['server', 'jvm', 'database', 'connectionPool', 'redis', 'storage'];
const statuses: MonitorStatus[] = ['UP', 'DEGRADED', 'DOWN', 'UNKNOWN'];
const requiredMetrics: Record<MonitorComponentId, string[]> = {
  server: ['cpuPercent', 'logicalProcessors', 'systemLoad1m', 'physicalTotal', 'physicalUsed', 'physicalUsedPercent'],
  jvm: ['heapUsed', 'heapMax', 'heapUsedPercent', 'nonHeapUsed', 'threadCount', 'gcCount', 'gcTime', 'uptime'],
  database: ['latency', 'threadsRunning', 'threadsConnected', 'maxConnections', 'connectionUsagePercent', 'queriesPerSecond', 'schemaBytes'],
  connectionPool: ['active', 'idle', 'total', 'maximum', 'pending'],
  redis: ['latency', 'usedMemory', 'maxMemory', 'connectedClients', 'blockedClients', 'operationsPerSecond'],
  storage: ['diskTotal', 'diskUsable', 'diskUsedPercent'],
};

// Missing values must never become healthy zeroes in a monitoring view.
export const validateMonitorSnapshot = (input: unknown): SystemMonitorSnapshot => {
  const snapshot = input as SystemMonitorSnapshot;
  if (!snapshot || typeof snapshot.sampledAt !== 'string' || !Number.isFinite(Date.parse(snapshot.sampledAt))
    || !Number.isFinite(snapshot.refreshIntervalSeconds) || snapshot.refreshIntervalSeconds <= 0
    || !Array.isArray(snapshot.components) || snapshot.components.length !== componentIds.length) {
    throw new Error('监控数据不完整，请稍后重试');
  }
  const seen = new Set<string>();
  for (const component of snapshot.components) {
    if (!component || !componentIds.includes(component.id) || seen.has(component.id)
      || typeof component.name !== 'string' || !statuses.includes(component.status)
      || (component.message != null && typeof component.message !== 'string')
      || !Array.isArray(component.metrics)) throw new Error('监控数据不完整，请稍后重试');
    seen.add(component.id);
    const metricKeys = new Set<string>();
    for (const metric of component.metrics) {
      if (!metric || typeof metric.key !== 'string' || metricKeys.has(metric.key) || typeof metric.label !== 'string'
        || (metric.value !== null && typeof metric.value !== 'string' && typeof metric.value !== 'number')
        || (typeof metric.value === 'number' && (!Number.isFinite(metric.value) || metric.value < 0))
        || (metric.unit != null && typeof metric.unit !== 'string') || (metric.note != null && typeof metric.note !== 'string')) {
        throw new Error('监控指标格式异常，请稍后重试');
      }
      metricKeys.add(metric.key);
    }
    if (!requiredMetrics[component.id].every(key => metricKeys.has(key))
      || (component.status === 'UP' && component.metrics.some(metric => metric.value === null))) {
      throw new Error('监控指标不完整，无法确认运行状态');
    }
  }
  return snapshot;
};

export const getSystemMonitorSnapshot = async (): Promise<SystemMonitorSnapshot> => {
  const response = await authenticatedRequest<unknown>('/api/admin/system/monitor', { method: 'GET', cache: 'no-store', timeoutMs: 12000 });
  return validateMonitorSnapshot(response.data);
};

export const formatMonitorMetric = (metric?: MonitorMetric): string => {
  if (!metric || metric.value === null) return '未采集';
  if (typeof metric.value === 'string') return metric.value || '未采集';
  const value = metric.value;
  if (metric.unit === 'bytes') {
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
    const power = Math.min(Math.max(value > 0 ? Math.floor(Math.log(value) / Math.log(1024)) : 0, 0), units.length - 1);
    return `${(value / 1024 ** power).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} ${units[power]}`;
  }
  if (metric.unit === 'seconds') {
    if (value < 60) return `${Math.floor(value)} 秒`;
    const days = Math.floor(value / 86400), hours = Math.floor(value % 86400 / 3600), minutes = Math.floor(value % 3600 / 60);
    return `${days ? `${days} 天 ` : ''}${hours ? `${hours} 小时 ` : ''}${minutes} 分钟`;
  }
  const suffix = ({ percent: '%', ms: ' ms', count: '', perSecond: ' /秒' } as Record<string, string>)[metric.unit || ''];
  return `${value.toLocaleString('zh-CN', { maximumFractionDigits: metric.unit === 'count' ? 0 : 2 })}${suffix ?? (metric.unit ? ` ${metric.unit}` : '')}`;
};
