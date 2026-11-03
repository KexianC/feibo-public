import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Drawer, Empty, List, Pagination, Space, Tag, Typography, message } from 'antd';
import { BellOutlined, ReloadOutlined } from '@ant-design/icons';
import { modelFailureNoticeService, type ModelFailureNotice } from '@/services/modelFailureNoticeService';

const OUTCOMES: Record<string, { label: string; color: string }> = {
  FALLBACK_SUCCEEDED: { label: '已由备用模型回答', color: 'green' },
  FALLBACK_FAILED: { label: '备用模型也失败', color: 'red' },
  FALLBACK_UNAVAILABLE: { label: '备用模型不可用', color: 'red' },
  PRIMARY_FAILED: { label: '模型回答失败', color: 'red' },
  CANCELLED: { label: '对话已取消', color: 'default' },
};
const PAGE_SIZE = 10;

const ModelFailureNotices: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [records, setRecords] = useState<ModelFailureNotice[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reading, setReading] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const refreshCountVersion = useRef(0);

  useEffect(() => {
    let active = true;
    const refreshCount = async () => {
      if (document.hidden) return;
      const version = ++refreshCountVersion.current;
      try {
        const count = await modelFailureNoticeService.unreadCount();
        if (active && version === refreshCountVersion.current) setUnreadCount(count);
      } catch {
        // A background poll must not interrupt the administrator's current work.
      }
    };
    void refreshCount();
    const interval = window.setInterval(() => { void refreshCount(); }, 60_000);
    document.addEventListener('visibilitychange', refreshCount);
    return () => {
      active = false;
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', refreshCount);
    };
  }, [refreshVersion]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setError('');
    modelFailureNoticeService.list(page, PAGE_SIZE).then(result => {
      if (!active) return;
      refreshCountVersion.current += 1;
      setRecords(result.records);
      setTotal(result.total);
      setUnreadCount(result.unreadCount);
    }).catch(() => {
      if (active) setError('通知加载失败，请重试');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [open, page, refreshVersion]);

  const markRead = useCallback(async (id?: string) => {
    if (reading) return;
    setReading(id ?? 'all');
    try {
      if (id) await modelFailureNoticeService.markRead(id);
      else await modelFailureNoticeService.markAllRead();
      setRefreshVersion(version => version + 1);
    } catch {
      message.error('标记已读失败，请重试');
    } finally {
      setReading(null);
    }
  }, [reading]);

  return <>
    <Badge count={unreadCount} overflowCount={99} size="small">
      <Button
        type="text"
        icon={<BellOutlined />}
        aria-label={`模型异常通知，${unreadCount} 条未读`}
        title="模型异常通知"
        onClick={() => setOpen(true)}
      />
    </Badge>
    <Drawer title="模型异常通知" open={open} onClose={() => setOpen(false)} width={560}>
      <Space style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Typography.Text type="secondary">未读 {unreadCount} 条</Typography.Text>
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshVersion(version => version + 1)}>刷新</Button>
          <Button disabled={unreadCount === 0 || reading !== null} loading={reading === 'all'} onClick={() => { void markRead(); }}>全部已读</Button>
        </Space>
      </Space>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      <List
        loading={loading}
        dataSource={records}
        locale={{ emptyText: <Empty description="暂无模型异常通知" /> }}
        renderItem={notice => {
          const outcome = OUTCOMES[notice.outcome] ?? { label: '模型异常', color: 'orange' };
          return <List.Item key={notice.id} style={{ alignItems: 'flex-start' }}>
            <div style={{ width: '100%', overflowWrap: 'anywhere' }}>
              <Space wrap style={{ marginBottom: 8 }}>
                {!notice.read && <Badge status="processing" />}
                <Typography.Text strong>{notice.originalModel || notice.originalProvider} 调用异常</Typography.Text>
                <Tag color={outcome.color}>{outcome.label}</Tag>
              </Space>
              <div><Typography.Text type="secondary">原模型：</Typography.Text>{notice.originalProvider} / {notice.originalModel}</div>
              {notice.fallbackModel && <div><Typography.Text type="secondary">备用模型：</Typography.Text>{notice.fallbackProvider} / {notice.fallbackModel}</div>}
              <div><Typography.Text type="secondary">错误类型：</Typography.Text>{notice.errorType}</div>
              <div><Typography.Text type="secondary">请求编号：</Typography.Text><Typography.Text copyable>{notice.requestId}</Typography.Text></div>
              {notice.conversationId && <div><Typography.Text type="secondary">对话编号：</Typography.Text>{notice.conversationId}</div>}
              <Space style={{ width: '100%', justifyContent: 'space-between', marginTop: 8 }}>
                <Typography.Text type="secondary">{new Date(notice.createdAt).toLocaleString('zh-CN', { hour12: false })}</Typography.Text>
                {notice.read
                  ? <Typography.Text type="secondary">已读</Typography.Text>
                  : <Button size="small" type="link" disabled={reading !== null} loading={reading === notice.id} onClick={() => { void markRead(notice.id); }}>标记已读</Button>}
              </Space>
            </div>
          </List.Item>;
        }}
      />
      {total > PAGE_SIZE && <Pagination current={page} pageSize={PAGE_SIZE} total={total} showSizeChanger={false} onChange={setPage} style={{ marginTop: 16 }} />}
    </Drawer>
  </>;
};

export default ModelFailureNotices;
