import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { SettingOutlined, ReloadOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { authenticatedRequest, AuthService } from '@/services/auth';
import ResolveFailedVideoModal from '@/components/ResolveFailedVideoModal';

interface Provider { provider: string; name: string; model: string; enabled: boolean; apiKeyConfigured: boolean; dailyLimit: number }
interface Settings { name: string; model: string; enabled: boolean; apiKey?: string; dailyLimit: number }
interface Task { id: string; provider: string; model: string; prompt: string; status: string; error?: string; createdAt: string }
const base = '/api/v1/admin/video-generation';
const labels: Record<string, string> = { submitting: '提交中', queued: '排队中', running: '生成中', succeeded: '已完成', failed: '失败', unknown: '待核查' };
export default function VideoGenerationManagement() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [editing, setEditing] = useState<Provider | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [resolving, setResolving] = useState<Task | null>(null);
  const [form] = Form.useForm<Settings>();
  const run = useRef(0);
  const load = async () => {
    const current = ++run.current; setLoading(true); setError('');
    try {
      const fresh = `?_t=${Date.now()}`;
      const [p, t] = await Promise.all([authenticatedRequest<Provider[]>(`${base}/providers${fresh}`), authenticatedRequest<Task[]>(`${base}/tasks${fresh}`)]);
      if (run.current === current) { setProviders(p.data); setTasks(t.data); }
    } catch (e) { if (run.current === current) setError(e instanceof Error ? e.message : '加载失败'); }
    finally { if (run.current === current) setLoading(false); }
  };
  useEffect(() => { void load(); return () => { run.current++; }; }, []);
  const save = async (values: Settings) => {
    if (!editing || saving) return;
    setSaving(true);
    try {
      await authenticatedRequest(`${base}/providers/${editing.provider}`, { method: 'PUT', body: JSON.stringify(values) });
      form.resetFields(); setEditing(null); message.success('视频模型配置已保存'); await load();
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败'); }
    finally { setSaving(false); }
  };
  return <div style={{ padding: 24 }}>
    <Space style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}><div><Typography.Title level={3} style={{ margin: 0 }}><VideoCameraOutlined /> AI 视频管理</Typography.Title><Typography.Text type="secondary">配置视频生成服务，查看平台调用记录与任务状态</Typography.Text></div><Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button></Space>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Alert type="info" showIcon message="启用后，模型将显示在用户端「AI 视频生成」。API Key 加密保存，留空保留原值。每日上限为该供应商全平台调用次数，提交失败及待核查任务也计入。" style={{ marginBottom: 20 }} />
    <Card title="供应商与模型" style={{ marginBottom: 24 }}>
      <Table rowKey="provider" loading={loading} dataSource={providers} pagination={false} scroll={{ x: 800 }} columns={[
        { title: '供应商', dataIndex: 'name', render: (value: string) => <strong>{value}</strong> },
        { title: '供应商模型 ID', dataIndex: 'model' },
        { title: '密钥', dataIndex: 'apiKeyConfigured', render: (value: boolean) => <Tag color={value ? 'green' : 'default'}>{value ? '已配置' : '未配置'}</Tag> },
        { title: '状态', dataIndex: 'enabled', render: (value: boolean) => <Tag color={value ? 'blue' : 'default'}>{value ? '已启用' : '已停用'}</Tag> },
        { title: '每日上限', dataIndex: 'dailyLimit', render: (value: number) => `${value} 次` },
        { title: '操作', render: (_: unknown, row: Provider) => <Button icon={<SettingOutlined />} onClick={() => { form.resetFields(); form.setFieldsValue({ name: row.name, model: row.model, enabled: row.enabled, dailyLimit: row.dailyLimit }); setEditing(row); }}>配置</Button> },
      ]} />
    </Card>
    <Card title="最近生成任务（最多 100 条）"><Table rowKey="id" dataSource={tasks} loading={loading} scroll={{ x: 950 }} pagination={{ pageSize: 10 }} columns={[
      { title: '任务编号', dataIndex: 'id', width: 170, ellipsis: true },
      { title: '供应商', dataIndex: 'provider', width: 110 },
      { title: '画面描述', dataIndex: 'prompt', ellipsis: true },
      { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => <Tag color={value === 'succeeded' ? 'green' : value === 'unknown' || value === 'failed' ? 'orange' : 'blue'}>{labels[value] || value}</Tag> },
      { title: '说明', dataIndex: 'error', ellipsis: true },
      { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string) => new Date(value).toLocaleString('zh-CN') },
      { title: '核销', key: 'resolve', width: 170, render: (_: unknown, task: Task) => AuthService.isSuperAdmin() && ['unknown', 'submitting'].includes(task.status)
        ? <Button type="link" onClick={() => setResolving(task)}>确认失败并退还额度</Button> : '—' },
    ]} /></Card>
    <Modal title={`配置 ${editing?.name || '视频模型'}`} open={!!editing} onCancel={() => { if (!saving) { setEditing(null); form.resetFields(); } }} footer={null} destroyOnClose maskClosable={!saving} closable={!saving}>
      <Form form={form} layout="vertical" onFinish={values => void save(values)} disabled={saving}>
        <Form.Item name="name" label="展示名称" rules={[{ required: true, whitespace: true }, { max: 80 }]}><Input maxLength={80} /></Form.Item>
        <Form.Item name="model" label="供应商模型 ID" extra="填写该账号已开通的文生视频模型 ID；请确认模型支持页面提供的时长与清晰度。" rules={[{ required: true }, { pattern: /^[A-Za-z0-9._-]{1,160}$/, message: '请输入有效的供应商模型 ID' }]}><Input maxLength={160} /></Form.Item>
        <Form.Item name="apiKey" label="API Key" extra={editing?.apiKeyConfigured ? '已配置，留空保留原密钥。' : '请从供应商官方控制台获取密钥。'} rules={[{ max: 4096 }]}><Input.Password autoComplete="new-password" placeholder="请输入新密钥" /></Form.Item>
        <Form.Item name="dailyLimit" label="全平台每日调用上限" rules={[{ required: true }]}><InputNumber min={1} max={1000} precision={0} addonAfter="次" /></Form.Item>
        <Form.Item name="enabled" label="用户端开放" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
        <Button type="primary" htmlType="submit" loading={saving} block>保存配置</Button>
      </Form>
    </Modal>
    <ResolveFailedVideoModal open={!!resolving} taskId={resolving?.id}
      onCancel={() => setResolving(null)}
      onResolve={async input => {
        if (!resolving) throw new Error('未选择任务');
        await authenticatedRequest(`${base}/tasks/${encodeURIComponent(resolving.id)}/resolve-failed`, { method: 'POST', body: JSON.stringify(input) });
      }}
      onResolved={() => { setResolving(null); void load(); }} />
  </div>;
}
