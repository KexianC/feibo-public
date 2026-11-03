import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Progress,
  Segmented,
  Skeleton,
  Space,
  Statistic,
  Tag,
} from 'antd';
import {
  AuditOutlined,
  LoginOutlined,
  MessageOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  TrophyOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import dayjs, { Dayjs } from 'dayjs';
import DashboardService, { VideoRankItem } from '@/services/dashboardService';
import { digitalWorksService } from '@/services/digitalWorksService';

type RangeType = 'today' | 'week' | 'month' | 'custom';

interface DashboardMetrics {
  loginTotal: number;
  loginFailed: number;
  videoTotal: number;
  videoSuccess: number;
  videoFailed: number;
  videoProcessing: number;
  videoDuration: number;
  conversationTotal: number;
  auditTotal: number;
  auditPending: number;
  auditPassed: number;
  auditRejected: number;
}

interface LoginRankItem {
  name: string;
  total: number;
  failed: number;
  success: number;
}

const emptyMetrics: DashboardMetrics = {
  loginTotal: 0,
  loginFailed: 0,
  videoTotal: 0,
  videoSuccess: 0,
  videoFailed: 0,
  videoProcessing: 0,
  videoDuration: 0,
  conversationTotal: 0,
  auditTotal: 0,
  auditPending: 0,
  auditPassed: 0,
  auditRejected: 0,
};

const dashboardStyles = `
  .dashboard-page {
    height: calc(100vh - 120px);
    overflow: hidden;
    padding: 12px;
    background:
      linear-gradient(90deg, rgba(58, 160, 255, 0.08) 1px, transparent 1px),
      linear-gradient(0deg, rgba(58, 160, 255, 0.08) 1px, transparent 1px),
      linear-gradient(135deg, #07111f 0%, #0b1b32 46%, #071827 100%);
    background-size: 28px 28px, 28px 28px, 100% 100%;
    color: #d9f6ff;
    display: grid;
    grid-template-rows: auto 108px minmax(0, 1fr);
    gap: 10px;
    position: relative;
  }

  .dashboard-page::before {
    content: "";
    position: absolute;
    inset: 0;
    pointer-events: none;
    background:
      linear-gradient(90deg, rgba(34, 211, 238, 0.16), transparent 18%, transparent 82%, rgba(124, 58, 237, 0.14)),
      linear-gradient(180deg, rgba(6, 182, 212, 0.12), transparent 22%, transparent 78%, rgba(16, 185, 129, 0.10));
  }

  .dashboard-page > * {
    position: relative;
    z-index: 1;
  }

  .dashboard-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    min-height: 36px;
  }

  .dashboard-title {
    margin: 0;
    font-size: 20px;
    line-height: 26px;
    font-weight: 700;
    color: #e6fbff;
    text-shadow: 0 0 18px rgba(34, 211, 238, 0.45);
  }

  .dashboard-subtitle {
    color: #8ac7da;
    font-size: 12px;
  }

  .metric-grid {
    min-height: 0;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
  }

  .content-grid {
    min-height: 0;
    display: grid;
    grid-template-columns: 1.05fr 1fr 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
    gap: 10px;
  }

  .dashboard-card {
    height: 100%;
    overflow: hidden;
    color: #d9f6ff;
    background:
      linear-gradient(180deg, rgba(14, 35, 64, 0.90), rgba(8, 20, 38, 0.86)),
      linear-gradient(135deg, rgba(34, 211, 238, 0.18), rgba(124, 58, 237, 0.08));
    border: 1px solid rgba(78, 208, 255, 0.28);
    border-radius: 8px;
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.05),
      0 0 0 1px rgba(6, 182, 212, 0.08),
      0 12px 32px rgba(0, 0, 0, 0.24),
      0 0 28px rgba(34, 211, 238, 0.08);
    backdrop-filter: blur(10px);
  }

  .dashboard-card .ant-card-head {
    min-height: 40px;
    padding: 0 12px;
    color: #e6fbff;
    background: linear-gradient(90deg, rgba(34, 211, 238, 0.12), rgba(124, 58, 237, 0.07));
    border-bottom: 1px solid rgba(78, 208, 255, 0.20);
  }

  .dashboard-card .ant-card-head-title {
    padding: 9px 0;
    font-size: 14px;
  }

  .dashboard-card .ant-card-body {
    height: calc(100% - 41px);
    padding: 12px;
    overflow: hidden;
  }

  .metric-card {
    height: 108px;
  }

  .metric-card .ant-card-body {
    height: 100%;
    padding: 12px;
    display: grid;
    grid-template-rows: auto 1fr auto;
    gap: 4px;
  }

  .metric-head,
  .section-title,
  .metric-foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  .metric-head {
    color: #8ac7da;
    font-size: 12px;
  }

  .metric-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    box-shadow: inset 0 0 18px rgba(255, 255, 255, 0.06), 0 0 18px rgba(34, 211, 238, 0.14);
  }

  .metric-foot {
    color: #76a9bd;
    font-size: 12px;
  }

  .rank-list {
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
    overflow: hidden;
  }

  .rank-row {
    display: grid;
    grid-template-columns: 30px minmax(0, 1fr) auto;
    align-items: center;
    gap: 8px;
    min-height: 30px;
  }

  .rank-badge {
    width: 26px;
    height: 24px;
    border-radius: 7px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(34, 211, 238, 0.12);
    border: 1px solid rgba(34, 211, 238, 0.28);
    color: #67e8f9;
    font-weight: 700;
    font-size: 12px;
  }

  .rank-badge.top {
    background: rgba(250, 204, 21, 0.14);
    border-color: rgba(250, 204, 21, 0.38);
    color: #fde68a;
  }

  .rank-title {
    color: #dffbff;
    font-size: 13px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rank-meta {
    color: #76a9bd;
    font-size: 11px;
    line-height: 14px;
  }

  .rank-value {
    min-width: 72px;
    text-align: right;
    font-weight: 700;
  }

  .module-card {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    height: 100%;
  }

  .module-stat {
    border: 1px solid rgba(78, 208, 255, 0.18);
    border-radius: 8px;
    padding: 10px;
    min-height: 0;
    background: linear-gradient(180deg, rgba(9, 29, 54, 0.80), rgba(5, 18, 35, 0.72));
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  .module-label {
    color: #8ac7da;
    font-size: 12px;
  }

  .module-value {
    margin-top: 4px;
    font-size: 22px;
    line-height: 28px;
    font-weight: 700;
  }

  .audit-grid {
    height: 100%;
    display: grid;
    grid-template-rows: 1fr;
  }

  .audit-bars {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
    min-height: 0;
    align-items: end;
  }

  .audit-bar-wrap {
    min-height: 0;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    gap: 8px;
  }

  .audit-bar {
    border-radius: 6px 6px 0 0;
    min-height: 8px;
    box-shadow: 0 0 18px currentColor;
  }

  .dashboard-page .ant-statistic-content {
    color: #e6fbff;
    text-shadow: 0 0 18px rgba(34, 211, 238, 0.20);
  }

  .dashboard-page .ant-progress-bg {
    box-shadow: 0 0 14px currentColor;
  }

  .dashboard-page .ant-empty-description,
  .dashboard-page .ant-picker-input > input,
  .dashboard-page .ant-segmented,
  .dashboard-page .ant-btn {
    color: #b8eaff;
  }

  .dashboard-page .ant-picker,
  .dashboard-page .ant-segmented {
    background: rgba(8, 28, 52, 0.78);
    border-color: rgba(78, 208, 255, 0.22);
  }

  .dashboard-page .ant-segmented-item-selected {
    color: #062033;
    background: #67e8f9;
  }

  .dashboard-page .ant-btn {
    background: rgba(8, 28, 52, 0.78);
    border-color: rgba(78, 208, 255, 0.28);
  }

  @media (max-width: 1100px) {
    .dashboard-page {
      height: auto;
      min-height: calc(100vh - 120px);
      overflow: auto;
      grid-template-rows: auto auto auto;
    }

    .metric-grid,
    .content-grid {
      grid-template-columns: 1fr;
    }

    .dashboard-card {
      min-height: 260px;
    }
  }
`;

const dateFormat = 'YYYY-MM-DD';

const formatNumber = (value?: number) => Number(value || 0).toLocaleString();

const formatDuration = (seconds?: number) => {
  const value = Number(seconds || 0);
  const hours = Math.floor(value / 3600);
  const minutes = Math.floor((value % 3600) / 60);
  if (hours <= 0) return `${minutes} 分钟`;
  return `${hours} 小时 ${minutes} 分钟`;
};

const getRangeLabel = (range: [Dayjs, Dayjs] | null) => {
  if (!range) return '未选择日期范围';
  return `${range[0].format(dateFormat)} 至 ${range[1].format(dateFormat)}`;
};

const maskName = (value?: string) => {
  const text = String(value || '未知账号');
  if (text.length <= 1) return text;
  if (text.includes('@')) {
    const [name, domain] = text.split('@');
    return `${name.slice(0, 2)}***@${domain || ''}`;
  }
  if (/^1\d{10}$/.test(text)) return `${text.slice(0, 3)}****${text.slice(-4)}`;
  return `${text.slice(0, 1)}***${text.length > 2 ? text.slice(-1) : ''}`;
};

const MetricCard: React.FC<{
  title: string;
  value: number;
  icon: React.ReactNode;
  color: string;
  note: string;
}> = ({ title, value, icon, color, note }) => (
  <Card className="dashboard-card metric-card">
    <div className="metric-head">
      <span>{title}</span>
      <span className="metric-icon" style={{ color, background: `${color}18` }}>{icon}</span>
    </div>
    <Statistic
      value={value}
      valueStyle={{ color: '#e6fbff', fontSize: 26, lineHeight: '32px', fontWeight: 700 }}
      formatter={(v) => formatNumber(Number(v))}
    />
    <div className="metric-foot">
      <span>{note}</span>
    </div>
  </Card>
);

const LoginRanking: React.FC<{ data: LoginRankItem[] }> = ({ data }) => {
  if (data.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无登录排行" />;

  return (
    <div className="rank-list">
      {data.map((item, index) => (
        <div className="rank-row" key={item.name}>
          <span className={`rank-badge ${index < 3 ? 'top' : ''}`}>{index + 1}</span>
          <div style={{ minWidth: 0 }}>
            <div className="rank-title">{maskName(item.name)}</div>
            <div className="rank-meta">成功 {item.success} · 失败 {item.failed}</div>
          </div>
          <div className="rank-value" style={{ color: '#67e8f9' }}>{item.total} 次</div>
        </div>
      ))}
    </div>
  );
};

const VideoDurationRanking: React.FC<{ data: VideoRankItem[] }> = ({ data }) => {
  if (data.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无合成时长排行" />;

  return (
    <div className="rank-list">
      {data.slice(0, 6).map((item, index) => (
        <div className="rank-row" key={`${item.nick_name}-${index}`}>
          <span className={`rank-badge ${index < 3 ? 'top' : ''}`}>{index + 1}</span>
          <div style={{ minWidth: 0 }}>
            <div className="rank-title">{maskName(item.nick_name)}</div>
            <div className="rank-meta">累计视频合成时长</div>
          </div>
          <div className="rank-value" style={{ color: '#34d399' }}>{formatDuration(item.total_duration)}</div>
        </div>
      ))}
    </div>
  );
};

const CoreModuleStats: React.FC<{
  metrics: DashboardMetrics;
  loginRiskRate: number;
  videoSuccessRate: number;
}> = ({ metrics, loginRiskRate, videoSuccessRate }) => (
  <div className="module-card">
    <div className="module-stat">
      <div className="module-label">登录安全</div>
      <div className="module-value" style={{ color: loginRiskRate > 20 ? '#fb7185' : '#67e8f9' }}>{100 - loginRiskRate}%</div>
      <Progress percent={Math.max(0, 100 - loginRiskRate)} size="small" strokeColor={loginRiskRate > 20 ? '#fb7185' : '#67e8f9'} showInfo={false} />
      <div className="rank-meta">失败率 {loginRiskRate}%</div>
    </div>
    <div className="module-stat">
      <div className="module-label">视频合成成功率</div>
      <div className="module-value" style={{ color: '#34d399' }}>{videoSuccessRate}%</div>
      <Progress percent={videoSuccessRate} size="small" strokeColor="#34d399" showInfo={false} />
      <div className="rank-meta">失败记录 {metrics.videoFailed}</div>
    </div>
    <div className="module-stat">
      <div className="module-label">AI 对话总量</div>
      <div className="module-value" style={{ color: '#a78bfa' }}>{formatNumber(metrics.conversationTotal)}</div>
      <div className="rank-meta">按会话创建时间统计</div>
    </div>
    <div className="module-stat">
      <div className="module-label">审核待处理</div>
      <div className="module-value" style={{ color: '#facc15' }}>{formatNumber(metrics.auditPending)}</div>
      <div className="rank-meta">总审核 {formatNumber(metrics.auditTotal)}</div>
    </div>
  </div>
);

const AuditSummary: React.FC<{ metrics: DashboardMetrics }> = ({ metrics }) => {
  const maxValue = Math.max(1, metrics.auditPending, metrics.auditPassed, metrics.auditRejected);
  const bars = [
    { label: '待审', value: metrics.auditPending, color: '#facc15' },
    { label: '通过', value: metrics.auditPassed, color: '#34d399' },
    { label: '拒绝', value: metrics.auditRejected, color: '#fb7185' },
  ];

  return (
    <div className="audit-grid">
      <div className="audit-bars">
        {bars.map(item => (
          <div className="audit-bar-wrap" key={item.label}>
            <div className="audit-bar" style={{ height: `${Math.max(8, (item.value / maxValue) * 120)}px`, background: item.color }} />
            <div>
              <div className="rank-title">{formatNumber(item.value)}</div>
              <div className="rank-meta">{item.label}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const VideoQualitySummary: React.FC<{
  metrics: DashboardMetrics;
  videoSuccessRate: number;
}> = ({ metrics, videoSuccessRate }) => (
  <div style={{ height: '100%', display: 'grid', gridTemplateRows: 'auto 1fr', gap: 10 }}>
    <div>
      <div className="module-label">合成成功率</div>
      <div className="module-value" style={{ color: '#34d399' }}>{videoSuccessRate}%</div>
      <Progress percent={videoSuccessRate} size="small" strokeColor="#34d399" showInfo={false} />
    </div>
    <div className="module-card">
      <div className="module-stat">
        <div className="module-label">成功记录</div>
        <div className="module-value" style={{ color: '#34d399' }}>{formatNumber(metrics.videoSuccess)}</div>
      </div>
      <div className="module-stat">
        <div className="module-label">失败记录</div>
        <div className="module-value" style={{ color: '#fb7185' }}>{formatNumber(metrics.videoFailed)}</div>
      </div>
    </div>
  </div>
);

const Dashboard: React.FC = () => {
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>([
    dayjs().startOf('day'),
    dayjs().endOf('day'),
  ]);
  const [dateRangeType, setDateRangeType] = useState<RangeType>('today');
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState('');
  const [metrics, setMetrics] = useState<DashboardMetrics>(emptyMetrics);
  const [loginRanking, setLoginRanking] = useState<LoginRankItem[]>([]);
  const [videoRanking, setVideoRanking] = useState<VideoRankItem[]>([]);
  const loadGeneration = useRef(0);

  const dateParams = useMemo(() => ({
    startDate: dateRange?.[0]?.format(dateFormat),
    endDate: dateRange?.[1]?.format(dateFormat),
  }), [dateRange]);

  const loginRiskRate = metrics.loginTotal > 0 ? Math.round((metrics.loginFailed / metrics.loginTotal) * 100) : 0;
  const videoCompleted = metrics.videoSuccess + metrics.videoFailed;
  const videoSuccessRate = videoCompleted > 0 ? Math.round((metrics.videoSuccess / videoCompleted) * 100) : 0;

  const handleDateRangePreset = (type: RangeType) => {
    setDateRangeType(type);
    if (type === 'custom') return;

    const nextRange: [Dayjs, Dayjs] = type === 'today'
      ? [dayjs().startOf('day'), dayjs().endOf('day')]
      : type === 'week'
        ? [dayjs().startOf('week'), dayjs().endOf('week')]
        : [dayjs().startOf('month'), dayjs().endOf('month')];

    setDateRange(nextRange);
  };

  const handleDateRangeChange = (dates: [Dayjs, Dayjs] | null) => {
    setDateRange(dates);
    setDateRangeType('custom');
  };

  const loadDashboard = async () => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    setErrorText('');
    try {
      const [loginSummary, videoStatistics, conversationSummary, auditSummary, durationRank] = await Promise.all([
        DashboardService.getLoginSummary(dateParams),
        digitalWorksService.getVideoStatistics(dateParams),
        DashboardService.getConversationSummary(dateParams),
        DashboardService.getAuditSummary(dateParams),
        DashboardService.getVideoRanking(dateParams),
      ]);
      if (generation !== loadGeneration.current) return;
      setLoginRanking(loginSummary.ranking || []);
      setVideoRanking(durationRank || []);
      setMetrics({
        loginTotal: Number(loginSummary.total) || 0,
        loginFailed: Number(loginSummary.failed) || 0,
        videoTotal: videoStatistics.total,
        videoSuccess: videoStatistics.success,
        videoFailed: videoStatistics.failed,
        videoProcessing: videoStatistics.processing,
        videoDuration: videoStatistics.totalDuration,
        conversationTotal: Number(conversationSummary.total) || 0,
        auditTotal: Number(auditSummary.total) || 0,
        auditPending: Number(auditSummary.pending) || 0,
        auditPassed: Number(auditSummary.passed) || 0,
        auditRejected: Number(auditSummary.rejected) || 0,
      });
    } catch (err: any) {
      if (generation !== loadGeneration.current) return;
      setErrorText(err?.message || '驾驶舱数据加载失败');
    } finally {
      if (generation === loadGeneration.current) setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
    return () => { loadGeneration.current += 1; };
  }, [dateParams.startDate, dateParams.endDate]);

  return (
    <div className="dashboard-page">
      <style>{dashboardStyles}</style>

      <div className="dashboard-header">
        <div>
          <h1 className="dashboard-title">首页驾驶舱</h1>
          <div className="dashboard-subtitle">
            一屏展示核心数据、登录排行榜、合成时长排行榜。当前周期：{getRangeLabel(dateRange)}
          </div>
        </div>
        <Space wrap>
          <Segmented
            value={dateRangeType}
            onChange={(value) => handleDateRangePreset(value as RangeType)}
            options={[
              { label: '今日', value: 'today' },
              { label: '本周', value: 'week' },
              { label: '本月', value: 'month' },
              { label: '自定义', value: 'custom' },
            ]}
          />
          <DatePicker.RangePicker
            value={dateRange}
            format={dateFormat}
            onChange={(value) => handleDateRangeChange(value as [Dayjs, Dayjs] | null)}
          />
          <Button icon={<ReloadOutlined />} onClick={loadDashboard} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      {errorText && (
        <Alert
          type="warning"
          showIcon
          message="驾驶舱数据加载异常"
          description={errorText}
          style={{ position: 'absolute', right: 16, top: 56, zIndex: 2, maxWidth: 520 }}
        />
      )}

      <Skeleton loading={loading && metrics.loginTotal === 0 && metrics.videoTotal === 0 && metrics.conversationTotal === 0} active paragraph={{ rows: 10 }}>
        <div className="metric-grid">
          <MetricCard
            title="登录访问"
            value={metrics.loginTotal}
            icon={<LoginOutlined />}
            color="#67e8f9"
            note={`失败登录 ${formatNumber(metrics.loginFailed)} 次`}
          />
          <MetricCard
            title="视频合成"
            value={metrics.videoTotal}
            icon={<VideoCameraOutlined />}
            color="#34d399"
            note={`合成时长 ${formatDuration(metrics.videoDuration)}`}
          />
          <MetricCard
            title="AI 对话"
            value={metrics.conversationTotal}
            icon={<MessageOutlined />}
            color="#a78bfa"
            note="仅展示汇总，不展示用户问题"
          />
          <MetricCard
            title="审核数据"
            value={metrics.auditTotal}
            icon={<AuditOutlined />}
            color="#facc15"
            note={`待处理 ${formatNumber(metrics.auditPending)} 条`}
          />
        </div>

        <div className="content-grid">
          <Card
            className="dashboard-card"
            title={
              <div className="section-title">
                <Space>
                  <SafetyCertificateOutlined style={{ color: '#67e8f9' }} />
                  <span>核心数据健康度</span>
                </Space>
                <Tag color={loginRiskRate > 20 ? 'red' : 'green'}>登录失败率 {loginRiskRate}%</Tag>
              </div>
            }
          >
            <VideoQualitySummary metrics={metrics} videoSuccessRate={videoSuccessRate} />
          </Card>

          <Card
            className="dashboard-card"
            title={
              <div className="section-title">
                <Space>
                  <LoginOutlined style={{ color: '#67e8f9' }} />
                  <span>登录排行榜</span>
                </Space>
                <Tag color="blue">TOP 6</Tag>
              </div>
            }
          >
            <LoginRanking data={loginRanking} />
          </Card>

          <Card
            className="dashboard-card"
            title={
              <div className="section-title">
                <Space>
                  <TrophyOutlined style={{ color: '#34d399' }} />
                  <span>合成时长排行榜</span>
                </Space>
                <Tag color="green">TOP 6</Tag>
              </div>
            }
          >
            <VideoDurationRanking data={videoRanking} />
          </Card>

          <Card
            className="dashboard-card"
            title={
              <div className="section-title">
                <Space>
                  <AuditOutlined style={{ color: '#facc15' }} />
                  <span>审核状态分布</span>
                </Space>
                <Tag color="gold">不展示明细</Tag>
              </div>
            }
          >
            <AuditSummary metrics={metrics} />
          </Card>

          <Card
            className="dashboard-card"
            style={{ gridColumn: 'span 2' }}
            title={
              <div className="section-title">
                <Space>
                  <VideoCameraOutlined style={{ color: '#34d399' }} />
                  <span>视频合成质量</span>
                </Space>
                <Tag color="green">成功率 {videoSuccessRate}%</Tag>
              </div>
            }
          >
            <CoreModuleStats metrics={metrics} loginRiskRate={loginRiskRate} videoSuccessRate={videoSuccessRate} />
          </Card>
        </div>
      </Skeleton>
    </div>
  );
};

export default Dashboard;
