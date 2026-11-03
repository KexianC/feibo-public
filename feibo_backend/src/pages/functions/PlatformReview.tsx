import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Tag, Pagination, message } from 'antd';
import dayjs from 'dayjs';
import ApprovalService, { CopywritingApprovalItem, PageResponse, CopywritingDetail } from '@/services/copywritingApprovalService';

const PlatformReview: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<CopywritingApprovalItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [submitterName, setSubmitterName] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const [detailVisible, setDetailVisible] = useState(false);
  const [detail, setDetail] = useState<CopywritingDetail | null>(null);
  const [actionVisible, setActionVisible] = useState(false);
  const [actionComment, setActionComment] = useState<string>('');
  const [actionType, setActionType] = useState<'claim' | 'approve' | null>(null);
  const [actionRecord, setActionRecord] = useState<CopywritingApprovalItem | null>(null);

  const didInitRef = useRef(false);

  /** 加载平台审核分页数据（固定审核状态=3） */
  const loadData = async (p = page, s = size) => {
    setLoading(true);
    try {
      const data = await ApprovalService.getPage({ page: p, size: s, submitterName, status: 3 });
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      message.error(err?.message || '加载列表失败');
    } finally {
      setLoading(false);
    }
  };

  /** 打开详情并加载文案内容 */
  const openDetail = async (copywritingId: string) => {
    try {
      const d = await ApprovalService.getDetail(copywritingId);
      setDetail(d);
      setDetailVisible(true);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadData(1, size);
  }, []);

  const renderStatus = (s?: string) => {
    const val = String(s || '').trim();
    if (val === '平台审核通过') return <Tag color="green">{val}</Tag>;
    if (val === '部门审核通过') return <Tag color="green">{val}</Tag>;
    if (val === '提交文案') return <Tag color="gold">{val}</Tag>;
    return <Tag>{val || '-'}</Tag>;
  };

  /** 提交平台审核操作 */
  const submitExpertAction = async () => {
    if (!actionRecord || !actionType) return;
    try {
      const flowId = String(actionRecord.flowId ?? actionRecord.id ?? actionRecord.copywritingId);
      const copyId = String(actionRecord.copywritingId);
      const res = await ApprovalService.approveByPlatform({ comment: actionComment, copywritingApprovalFlowId: flowId, copywritingId: copyId });
      const ok = res && (res.code === '0000' || res.code === 200 || res.code === '200');
      if (!ok) {
        throw new Error((res as any)?.msg || (res as any)?.info || '操作失败');
      }
      message.success('审核成功');
      setActionVisible(false);
      setActionType(null);
      setActionRecord(null);
      setActionComment('');
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '操作失败');
    }
  };

  return (
    <div>
      <Card title="平台审核" extra={<Space><Button onClick={() => loadData(page, size)}>刷新</Button></Space>}>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="submitterName" label="提交人姓名">
            <Input placeholder="按提交人姓名搜索" allowClear onChange={(e) => setSubmitterName(e.target.value || undefined)} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={() => loadData(1, size)}>搜索</Button>
              <Button onClick={() => { searchForm.resetFields(); setSubmitterName(undefined); loadData(1, size); }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<CopywritingApprovalItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 140 },
            { title: '标题', dataIndex: 'title', key: 'title', width: 280 },
            { title: '提交人身份', dataIndex: 'submitterIdentity', key: 'submitterIdentity', width: 140 },
            { title: '提交人名称', dataIndex: 'submitterName', key: 'submitterName', width: 180 },
            { title: '审批状态', dataIndex: 'status', key: 'status', width: 160, render: (s: string) => renderStatus(s) },
            { title: '评价', dataIndex: 'evaluation', key: 'evaluation', width: 200 },
            { title: '时间', dataIndex: 'time', key: 'time', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '操作', key: 'action', fixed: 'right', width: 200, render: (_: any, record: CopywritingApprovalItem) => (
              <Space size="small">
                <Button type="link" onClick={() => openDetail(record.copywritingId)}>详情</Button>
                <Button type="primary" onClick={() => { setActionType('approve'); setActionRecord(record); setActionVisible(true); }}>审核</Button>
              </Space>
            ) }
          ]}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
          scroll={{ x: 1200 }}
        />
        <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Pagination
            total={pageData.total}
            current={pageData.current}
            pageSize={pageData.size}
            showSizeChanger={false}
            onChange={(p) => { setPage(p); loadData(p, size); }}
          />
        </div>
      </Card>

      <Modal open={detailVisible} title="文案详情" footer={null} onCancel={() => { setDetailVisible(false); setDetail(null); }} width={800}>
        {detail && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div><span style={{ marginRight: 8 }}>标题：</span><Tag color="blue">{detail.title}</Tag></div>
              <div><span style={{ marginRight: 8 }}>提交人身份：</span>{detail.submitterIdentity || '-'}</div>
              <div><span style={{ marginRight: 8 }}>提交人名称：</span>{detail.submitterName || '-'}</div>
              <div><span style={{ marginRight: 8 }}>审批状态：</span>{renderStatus(detail.status as any)}</div>
              <div><span style={{ marginRight: 8 }}>内容：</span><div style={{ maxHeight: 360, overflow: 'auto', padding: 8, background: '#fafafa', border: '1px solid #eee' }}><pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>{detail.content || '-'}</pre></div></div>
              <div><span style={{ marginRight: 8 }}>时间：</span>{detail.time ? dayjs(detail.time).format('YYYY-MM-DD HH:mm:ss') : (detail.createTime ? dayjs(detail.createTime).format('YYYY-MM-DD HH:mm:ss') : '-')}</div>
              <div><span style={{ marginRight: 8 }}>更新时间：</span>{detail.updateTime ? dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</div>
            </Space>
          </div>
        )}
      </Modal>

      <Modal
        open={actionVisible}
        title={actionType === 'claim' ? '认领审核任务' : '审核'}
        onOk={submitExpertAction}
        onCancel={() => { setActionVisible(false); setActionType(null); setActionRecord(null); setActionComment(''); }}
        okText="提交"
      >
        <Form layout="vertical">
          <Form.Item label="备注">
            <Input.TextArea rows={4} placeholder="请输入备注" value={actionComment} onChange={(e) => setActionComment(e.target.value)} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PlatformReview;
