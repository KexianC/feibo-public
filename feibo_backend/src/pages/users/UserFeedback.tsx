import React, { useEffect, useRef, useState } from 'react';
import { Button, Card, DatePicker, Descriptions, Form, Input, message, Modal, Select, Space, Table, Tag } from 'antd';
import type { Dayjs } from 'dayjs';
import FeedbackService, { FeedbackPriority, FeedbackStatus, type FeedbackPageParams, type UserFeedback } from '@/services/feedbackService';
import { AuthService } from '@/services/auth';

type Filters = Omit<FeedbackPageParams, 'pageNum' | 'pageSize'>;
const statusOptions = [0, 1, 2, 3].map(value => ({ value, label: FeedbackService.getStatusConfig(value).text }));
const priorityOptions = [0, 1, 2, 3].map(value => ({ value, label: FeedbackService.getPriorityConfig(value).text }));
const typeOptions = [1, 2, 3, 4].map(value => ({ value, label: FeedbackService.getTypeConfig(value).text }));
export default function UserFeedbackPage() {
  const [records, setRecords] = useState<UserFeedback[]>([]);
  const [filters, setFilters] = useState<Filters>({});
  const [dates, setDates] = useState<[Dayjs, Dayjs] | null>(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<UserFeedback>();
  const [replying, setReplying] = useState<UserFeedback>();
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const request = useRef(0);
  const canManage = AuthService.isSuperAdmin();
  const load = async (pageNum = pagination.current, pageSize = pagination.pageSize, values = filters) => {
    if (values.userId && !/^[1-9][0-9]{0,18}$/.test(values.userId.trim())) { message.error('请输入完整数字用户ID'); return; }
    const generation = ++request.current;
    setLoading(true);
    try {
      const data = await FeedbackService.getFeedbackPage({ ...values, userId: values.userId?.trim() || undefined, pageNum, pageSize });
      if (generation !== request.current) return;
      setRecords(data.records); setPagination({ current: data.current, pageSize: data.size, total: data.total });
    } catch (error) { if (generation === request.current) message.error(error instanceof Error ? error.message : '加载反馈失败'); }
    finally { if (generation === request.current) setLoading(false); }
  };
  useEffect(() => { void load(1, 10, {}); return () => { request.current++; }; }, []);
  const change = (key: keyof Filters, value: unknown) => setFilters(previous => ({ ...previous, [key]: value }));
  const transition = (record: UserFeedback, close: boolean) => Modal.confirm({
    title: close ? '确认关闭反馈' : '确认标记为已解决',
    content: record.title || record.content,
    onOk: async () => {
      try {
        if (close) await FeedbackService.closeFeedback(record.id, record.version);
        else await FeedbackService.resolveFeedback(record.id, record.version);
        message.success(close ? '反馈已关闭' : '反馈已解决');
      } catch (error) { message.error(error instanceof Error ? error.message : '处理失败'); throw error; } finally { await load(); }
    },
  });
  const saveReply = async () => {
    if (!replying) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      await FeedbackService.replyFeedback(replying.id, values.reply.trim(), replying.version, values.priority);
      message.success('处理回复已保存'); setReplying(undefined); form.resetFields();
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
    finally { setSaving(false); await load(); }
  };
  return <Card title="用户反馈">
    <Space wrap style={{ marginBottom: 16 }}>
      <Input placeholder="反馈内容关键词" value={filters.keyword || ''} allowClear onChange={e => change('keyword', e.target.value)} />
      <Input placeholder="用户ID" value={filters.userId || ''} allowClear onChange={e => change('userId', e.target.value)} />
      <Select placeholder="类型" style={{ width: 130 }} allowClear value={filters.type} options={typeOptions} onChange={value => change('type', value)} />
      <Select placeholder="状态" style={{ width: 130 }} allowClear value={filters.status} options={statusOptions} onChange={(value: FeedbackStatus) => change('status', value)} />
      <Select placeholder="优先级" style={{ width: 130 }} allowClear value={filters.priority} options={priorityOptions} onChange={(value: FeedbackPriority) => change('priority', value)} />
      <DatePicker.RangePicker value={dates} onChange={value => {
        const range = value?.[0] && value?.[1] ? [value[0], value[1]] as [Dayjs, Dayjs] : null;
        setDates(range); setFilters(previous => ({ ...previous, startDate: range?.[0].format('YYYY-MM-DD'), endDate: range?.[1].format('YYYY-MM-DD') }));
      }} />
      <Button type="primary" onClick={() => void load(1)}>搜索</Button>
      <Button onClick={() => { setFilters({}); setDates(null); void load(1, pagination.pageSize, {}); }}>重置</Button>
    </Space>
    <Table<UserFeedback> rowKey="id" dataSource={records} loading={loading} scroll={{ x: 1050 }} pagination={{ ...pagination, showSizeChanger: true, onChange: (p, s) => void load(p, s) }} columns={[
      { title: '反馈ID', dataIndex: 'id', width: 100 },
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '反馈内容', dataIndex: 'content', ellipsis: true },
      { title: '类型', dataIndex: 'type', width: 100, render: value => FeedbackService.getTypeConfig(value).text },
      { title: '状态', dataIndex: 'status', width: 90, render: value => { const item = FeedbackService.getStatusConfig(value); return <Tag color={item.color}>{item.text}</Tag>; } },
      { title: '优先级', dataIndex: 'priority', width: 90, render: value => FeedbackService.getPriorityConfig(value).text },
      { title: '提交时间', dataIndex: 'createTime', width: 170 },
      { title: '操作', width: canManage ? 240 : 70, render: (_, record) => <Space>
        <Button type="link" onClick={() => setDetail(record)}>查看</Button>
        {canManage && record.status !== FeedbackStatus.CLOSED && <>
          <Button type="link" onClick={() => { setReplying(record); form.setFieldsValue({ reply: record.reply || '', priority: record.priority ?? FeedbackPriority.MEDIUM }); }}>处理回复</Button>
          {record.status !== FeedbackStatus.RESOLVED && <Button type="link" onClick={() => transition(record, false)}>解决</Button>}
          <Button type="link" onClick={() => transition(record, true)}>关闭</Button>
        </>}
      </Space> },
    ]} />
    <Modal title="反馈详情" open={!!detail} footer={null} onCancel={() => setDetail(undefined)}>
      {detail && <Descriptions column={1}>
        <Descriptions.Item label="反馈ID">{detail.id}</Descriptions.Item>
        <Descriptions.Item label="用户ID">{detail.userId}</Descriptions.Item>
        <Descriptions.Item label="类型">{FeedbackService.getTypeConfig(detail.type).text}</Descriptions.Item>
        <Descriptions.Item label="提交时间">{detail.createTime}</Descriptions.Item>
        <Descriptions.Item label="内容">{detail.content}</Descriptions.Item>
        <Descriptions.Item label="状态">{FeedbackService.getStatusConfig(detail.status).text}</Descriptions.Item>
        <Descriptions.Item label="优先级">{FeedbackService.getPriorityConfig(detail.priority).text}</Descriptions.Item>
        <Descriptions.Item label="处理回复">{detail.reply || '尚未回复'}</Descriptions.Item>
        <Descriptions.Item label="处理时间">{detail.updateTime || '-'}</Descriptions.Item>
      </Descriptions>}
    </Modal>
    <Modal title="处理反馈" open={!!replying} confirmLoading={saving} okText="保存回复" onOk={() => void saveReply()} onCancel={() => { if (!saving) setReplying(undefined); }}>
      <Form form={form} layout="vertical">
        <Form.Item name="reply" label="处理回复" rules={[{ required: true, whitespace: true, message: '请输入处理回复' }, { max: 5000 }]}><Input.TextArea rows={5} maxLength={5000} showCount /></Form.Item>
        <Form.Item name="priority" label="优先级" rules={[{ required: true }]}><Select options={priorityOptions} /></Form.Item>
      </Form>
    </Modal>
  </Card>;
}
