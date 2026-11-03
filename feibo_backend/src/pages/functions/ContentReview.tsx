import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Tag, Pagination, Popconfirm, message } from 'antd';
import dayjs from 'dayjs';
import ApprovalService, { CopywritingApprovalItem, PageResponse, CopywritingDetail } from '@/services/copywritingApprovalService';


const ContentReview: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<CopywritingApprovalItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(8);

  const [detailVisible, setDetailVisible] = useState(false);
  const [detail, setDetail] = useState<CopywritingDetail | null>(null);

  const didInitRef = useRef(false);

  /**
   * 加载文案审批分页数据
   */
  const loadData = async (p = page, s = size, filter = title) => {
    setLoading(true);
    try {
      const data = await ApprovalService.getPage({ page: p, size: s, title: filter });
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      message.error(err?.message || '加载文案列表失败');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 打开详情并加载文案内容
   */
  const openDetail = async (id: string) => {
    try {
      const d = await ApprovalService.getDetail(id);
      setDetail(d);
      setDetailVisible(true);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    }
  };

  /**
   * 删除文案（逻辑删除）
   * @param copywritingId 文案ID
   */
  const handleDelete = async (copywritingId: string) => {
    try {
      await ApprovalService.deleteById(copywritingId);
      message.success('删除成功');
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadData(1, size);
  }, []);

  const renderStatus = (s?: string) => {
    const val = String(s || '').trim();
    if (val === '平台审核通过' || val === '部门审核通过') return <Tag color="green">{val}</Tag>;
    if (val === '平台审核拒绝' || val === '部门审核拒绝') return <Tag color="red">{val}</Tag>;
    if (val === '提交文案') return <Tag color="gold">{val}</Tag>;
    return <Tag>{val || '-'}</Tag>;
  };

  return (
    <div>
      <Card title="文案查看" extra={
        <Space>
          <Button onClick={() => loadData(page, size)}>刷新</Button>
        </Space>
      }>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="title" label="标题">
            <Input placeholder="按标题搜索" allowClear onChange={(e) => setTitle(e.target.value || undefined)} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={() => loadData(1, size)}>搜索</Button>
              <Button onClick={() => { searchForm.resetFields(); setTitle(undefined); loadData(1, size, ''); }}>重置</Button>
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
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: CopywritingApprovalItem) => (
                <Space size="small">
                  <Button type="link" onClick={() => openDetail(record.copywritingId)}>详情</Button>
                  <Popconfirm title="确认删除该文案？" disabled={!!record.flowId} onConfirm={() => handleDelete(record.id)}>
                    <Button type="link" danger disabled={!!record.flowId} title={record.flowId ? '已有审批记录的文案须保留，可另建提交' : undefined}>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          scroll={{ x: 1200 }}
        />
        <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Pagination
            total={pageData.total}
            current={pageData.current}
            pageSize={8}
            showSizeChanger={false}
            onChange={(p) => { setPage(p); loadData(p, 8); }}
          />
        </div>
      </Card>

      <Modal open={detailVisible} title="文案详情" footer={null} onCancel={() => { setDetailVisible(false); setDetail(null); }} width={800}>
        {detail && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{detail.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>提交人身份：</span>
                {detail.submitterIdentity || '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>提交人名称：</span>
                {detail.submitterName || '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>审批状态：</span>
                {renderStatus(detail.status as any)}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>内容：</span>
                <div style={{ maxHeight: 360, overflow: 'auto', padding: 8, background: '#fafafa', border: '1px solid #eee' }}>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>{detail.content || '-'}</pre>
                </div>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>时间：</span>
                {detail.time ? dayjs(detail.time).format('YYYY-MM-DD HH:mm:ss') : (detail.createTime ? dayjs(detail.createTime).format('YYYY-MM-DD HH:mm:ss') : '-')}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>更新时间：</span>
                {detail.updateTime ? dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </div>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ContentReview;
