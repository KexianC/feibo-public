import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Upload, Image, Tag, Pagination, message } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { PlusOutlined, UploadOutlined, SearchOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import IllustrationService, { IllustrationItem, PageResponse } from '@/services/illustrationService';

const IllustrationManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<IllustrationItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);


  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<IllustrationItem | null>(null);
  const [viewing, setViewing] = useState<IllustrationItem | null>(null);

  const [fileListIndex, setFileListIndex] = useState<UploadFile[]>([]);

  const didInitRef = useRef(false);

  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await IllustrationService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载插图列表失败');
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadData(1, size);
  }, []);

  const handleSearch = () => {
    const v = searchForm.getFieldsValue();
    setTitle(v.title || undefined);
    setPage(1);
    loadData(1, size, v.title || '');
  };

  const handleReset = () => {
    searchForm.resetFields();
    setTitle(undefined);
    setPage(1);
    loadData(1, size, '');
  };

  const uploadPropsIndex: UploadProps = {
    name: 'file',
    multiple: false,
    maxCount: 1,
    fileList: fileListIndex,
    beforeUpload: () => false,
    onChange: (info) => setFileListIndex(info.fileList),
    accept: 'image/*'
  };

  const handleAdd = () => {
    setFileListIndex([]);
    addForm.resetFields();
    setAddVisible(true);
  };

  const handleAddSubmit = async (values: any) => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const file = fileListIndex[0]?.originFileObj as File;
      await IllustrationService.create({ title: values.title }, file);
      message.success('新增成功');
      setAddVisible(false);
      setFileListIndex([]);
      addForm.resetFields();
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '新增失败');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const handleEdit = async (record: IllustrationItem) => {
    try {
      const detail = await IllustrationService.getById(record.id);
      setEditing(detail);
      editForm.setFieldsValue({ title: detail.title });
      setEditVisible(true);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    }
  };

  const handleEditSubmit = async (values: any) => {
    if (!editing) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const payload = { id: editing.id, title: values.title };
      await IllustrationService.update(payload);
      message.success('更新成功');
      setEditVisible(false);
      setEditing(null);
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '更新失败');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const handleDelete = async (record: IllustrationItem) => {
    try {
      await IllustrationService.delete(record.id);
      message.success('删除成功');
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  const handleView = async (record: IllustrationItem) => {
    try {
      const detail = await IllustrationService.getById(record.id);
      setViewing(detail);
      setViewVisible(true);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 120 },
    { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
    { title: '索引图', dataIndex: 'indexImage', key: 'indexImage', width: 160, render: (u: string) => (u ? <Image src={u} width={140} height={88} style={{ objectFit: 'cover' }} /> : '-') },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
    {
      title: '操作', key: 'action', fixed: 'right', width: 220,
      render: (_: any, record: IllustrationItem) => (
        <Space size="small">
          <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>详情</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该插图？" onConfirm={() => handleDelete(record)}>
            <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card title="插图管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增插图</Button>
          <Button icon={<ReloadOutlined />} onClick={() => loadData(page, size)}>刷新</Button>
        </Space>
      }>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="title" label="标题">
            <Input placeholder="按标题搜索" allowClear onChange={(e) => setTitle(e.target.value || undefined)} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<IllustrationItem>
          rowKey="id"
          columns={columns as any}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          scroll={{ x: 1000 }}
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

      <Modal
        title="编辑插图"
        open={editVisible}
        confirmLoading={saving}
        onOk={() => editForm.submit()}
        onCancel={() => { if (savingRef.current) return; setEditVisible(false); setEditing(null); }}
        okText="保存"
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditSubmit}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增插图"
        open={addVisible}
        confirmLoading={saving}
        onOk={() => addForm.submit()}
        onCancel={() => { if (savingRef.current) return; setAddVisible(false); setFileListIndex([]); addForm.resetFields(); }}
        okText="提交"
        width={600}
      >
        <Form form={addForm} layout="vertical" onFinish={handleAddSubmit}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item label="索引图">
            <Upload {...uploadPropsIndex} listType="picture">
              <Button icon={<UploadOutlined />}>选择索引图</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="插图详情"
        open={viewVisible}
        footer={null}
        onCancel={() => { setViewVisible(false); setViewing(null); }}
        width={700}
      >
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>索引图：</span>
                {viewing.indexImage ? <Image src={viewing.indexImage} width={200} /> : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>创建时间：</span>
                {viewing.createTime ? dayjs(viewing.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </div>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default IllustrationManage;
