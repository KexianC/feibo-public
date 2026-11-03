import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Tag, Pagination, message } from 'antd';
import dayjs from 'dayjs';
import TemplateService, { TemplateItem, PageResponse } from '@/services/templateService';

const { TextArea } = Input;


const TemplateManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<TemplateItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<TemplateItem | null>(null);
  const [viewing, setViewing] = useState<TemplateItem | null>(null);

  const didInitRef = useRef(false);

  /**
   * 加载数字模板分页数据
   */
  const loadData = async (p = page, s = size, filter = title) => {
    setLoading(true);
    try {
      const data = await TemplateService.getPage({ page: p, size: s, title: filter });
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      message.error(err?.message || '加载模板列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadData(1, size);
  }, []);

  return (
    <div>
      <Card title="模板管理" extra={
        <Space>
          <Button type="primary" onClick={() => setAddVisible(true)}>新增模板</Button>
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

        <Table<TemplateItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
            { title: '标题', dataIndex: 'title', key: 'title', width: 240 },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: TemplateItem) => (
                <Space size="small">
                  <Button type="link" onClick={async () => { const d = await TemplateService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" onClick={async () => { const d = await TemplateService.getById(record.id); setEditing(d); editForm.setFieldsValue({ title: d.title, content: d.content }); setEditVisible(true); }}>编辑</Button>
                  <Popconfirm title="确认删除该模板？" onConfirm={async () => { try { await TemplateService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
                    <Button type="link" danger>删除</Button>
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
            pageSize={pageData.size}
            showSizeChanger
            showQuickJumper
            onChange={(p, s) => { setPage(p); setSize(s || size); loadData(p, s || size); }}
          />
        </div>
      </Card>

      <Modal open={viewVisible} title="模板详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={700}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>内容：</span>
                <div style={{ maxHeight: 360, overflow: 'auto', padding: 8, background: '#fafafa', border: '1px solid #eee' }}>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>{viewing.content || '-'}</pre>
                </div>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>创建时间：</span>
                {viewing.createTime ? dayjs(viewing.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>更新时间：</span>
                {viewing.updateTime ? dayjs(viewing.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </div>
            </Space>
          </div>
        )}
      </Modal>

      <Modal open={editVisible} title="编辑模板" onOk={() => editForm.submit()} onCancel={() => { setEditVisible(false); setEditing(null); }} okText="保存">
        <Form form={editForm} layout="vertical" onFinish={async (values) => {
          /** 提交编辑模板 */
          if (!editing) return;
          try {
            await TemplateService.update({ id: editing.id, title: values.title, content: values.content });
            message.success('更新成功');
            setEditVisible(false);
            setEditing(null);
            loadData(page, size);
          } catch (err: any) {
            message.error(err?.message || '更新失败');
          }
        }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="content" label="模板内容（JSON对象）" rules={[{ required: true, message: '请输入模板JSON' }, { validator: async (_, value) => { try { const parsed = JSON.parse(value); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(); } catch { throw new Error('请输入有效的JSON对象'); } } }]}>
            <TextArea rows={6} placeholder="请输入模板内容" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal open={addVisible} title="新增模板" onOk={() => addForm.submit()} onCancel={() => { setAddVisible(false); addForm.resetFields(); }} okText="提交" width={600}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          /** 提交新增模板 */
          try {
            const created = await TemplateService.create({ title: values.title, content: values.content });
            if (!created || typeof (created as any).id === 'undefined') throw new Error('创建失败');
            message.success('新增成功');
            setAddVisible(false);
            addForm.resetFields();
            loadData(page, size);
          } catch (err: any) {
            message.error(err?.message || '新增失败');
          }
        }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="content" label="模板内容（JSON对象）" rules={[{ required: true, message: '请输入模板JSON' }, { validator: async (_, value) => { try { const parsed = JSON.parse(value); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(); } catch { throw new Error('请输入有效的JSON对象'); } } }]}>
            <TextArea rows={6} placeholder="请输入模板内容" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TemplateManage;
