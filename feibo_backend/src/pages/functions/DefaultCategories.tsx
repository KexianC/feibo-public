import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Tag, Pagination, message, Select } from 'antd';
import dayjs from 'dayjs';
import CopywritingCategoryService, { CopywritingCategoryItem, PageResponse } from '@/services/copywritingCategoryService';

const { Option } = Select;

const DefaultCategories: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<CopywritingCategoryItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<CopywritingCategoryItem | null>(null);
  const [viewing, setViewing] = useState<CopywritingCategoryItem | null>(null);

  const didInitRef = useRef(false);

  /**
   * 加载默认分类分页数据
   */
  const loadData = async (p = page, s = size, filter = title) => {
    setLoading(true);
    try {
      const data = await CopywritingCategoryService.getPage({ page: p, size: s, title: filter });
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      message.error(err?.message || '加载分类列表失败');
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
      <Card title="默认分类管理" extra={
        <Space>
          <Button type="primary" onClick={() => setAddVisible(true)}>新增分类</Button>
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

        <Table<CopywritingCategoryItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
            { title: '名称', dataIndex: 'title', key: 'title', width: 240 },
            { title: '是否启用', dataIndex: 'isShow', key: 'isShow', width: 120, render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>) },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: CopywritingCategoryItem) => (
                <Space size="small">
                  <Button type="link" onClick={async () => { const d = await CopywritingCategoryService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" onClick={async () => { const d = await CopywritingCategoryService.getById(record.id); setEditing(d); editForm.setFieldsValue({ title: d.title, isShow: d.isShow }); setEditVisible(true); }}>编辑</Button>
                  <Popconfirm title="确认删除该分类？" onConfirm={async () => { try { await CopywritingCategoryService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          scroll={{ x: 1100 }}
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

      <Modal open={viewVisible} title="分类详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={600}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>是否显示：</span>
                {viewing.isShow === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>}
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

      <Modal open={editVisible} title="编辑分类" onOk={() => editForm.submit()} onCancel={() => { setEditVisible(false); setEditing(null); }} okText="保存">
        <Form form={editForm} layout="vertical" onFinish={async (values) => {
          /** 提交编辑分类 */
          if (!editing) return;
          try {
            await CopywritingCategoryService.update({ id: editing.id, title: values.title, isShow: values.isShow });
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
          <Form.Item name="isShow" label="是否启用">
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>停用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal open={addVisible} title="新增分类" onOk={() => addForm.submit()} onCancel={() => { setAddVisible(false); addForm.resetFields(); }} okText="提交" width={600}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          /** 提交新增分类 */
          try {
            const created = await CopywritingCategoryService.create({ title: values.title, isShow: values.isShow ?? 1 });
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
          <Form.Item name="isShow" label="是否启用" initialValue={1}>
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>停用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DefaultCategories;
