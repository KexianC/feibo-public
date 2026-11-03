import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Upload, Image, Tag, Pagination, message } from 'antd';
import type { UploadFile } from 'antd';
import { PlusOutlined, UploadOutlined, SearchOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import ModelService, { ModelItem, PageResponse } from '@/services/modelService';


const ModelManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<ModelItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(5);

  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<ModelItem | null>(null);
  const [viewing, setViewing] = useState<ModelItem | null>(null);

  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const didInitRef = useRef(false);

  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await ModelService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载模型列表失败');
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadData(1, size);
  }, []);

  return (
    <div>
      <Card title="模型管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddVisible(true)}>导入模型 JSON</Button>
          <Button icon={<ReloadOutlined />} onClick={() => loadData(page, size)}>刷新</Button>
        </Space>
      }>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="title" label="标题">
            <Input placeholder="按标题搜索" allowClear onChange={(e) => setTitle(e.target.value || undefined)} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={() => loadData(1, size)}>搜索</Button>
              <Button onClick={() => { searchForm.resetFields(); setTitle(undefined); loadData(1, size, ''); }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<ModelItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
            { title: '名称', dataIndex: 'title', key: 'title', width: 200 },
            { title: '头像', dataIndex: 'avatarUrl', key: 'avatarUrl', width: 90, render: (u: string) => (u ? <Image src={u} width={60} height={60} style={{ height: 60, objectFit: 'cover', borderRadius: 6 }} /> : '-') },
            { title: '模型图片', dataIndex: 'modelUrl', key: 'modelUrl', width: 240, render: (u: string) => (u ? <a href={u} target="_blank" rel="noreferrer">查看</a> : '-') },
            { title: '排序', dataIndex: 'sort', key: 'sort', width: 120 },
            { title: '发布状态', dataIndex: 'status', key: 'status', width: 120, render: (v?: string) => v || '未设置' },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: ModelItem) => (
                <Space size="small">
                  <Button type="link" icon={<EyeOutlined />} onClick={async () => { const d = await ModelService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" icon={<EditOutlined />} onClick={async () => { const d = await ModelService.getById(record.id); setEditing(d); editForm.setFieldsValue({ title: d.title, sort: d.sort, status: d.status }); setEditVisible(true); }}>编辑</Button>
                  <Popconfirm title="确认删除该模型？" onConfirm={async () => { try { await ModelService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
                    <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
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

      <Modal open={viewVisible} title="模型详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={700}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>模型图片：</span>
                {viewing.modelUrl ? <a href={viewing.modelUrl} target="_blank" rel="noreferrer">查看</a> : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>排序：</span>
                {viewing.sort}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>发布状态：</span>
                {viewing.status || '未设置'}
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

      <Modal confirmLoading={saving} open={editVisible} title="编辑模型" onOk={() => editForm.submit()} onCancel={() => { if (savingRef.current) return; setEditVisible(false); setEditing(null); }} okText="保存">
        <Form form={editForm} layout="vertical" onFinish={async (values) => {
          if (!editing) return;
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            await ModelService.update({ id: editing.id, title: values.title, sort: values.sort, status: values.status });
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
        }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="sort" label="排序"> 
            <Input type="number" placeholder="请输入排序" />
          </Form.Item>
          <Form.Item name="status" label="发布状态" extra="填写供应商模型目录中的真实状态，例如 published。">
            <Input placeholder="保留或填写模型发布状态" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal confirmLoading={saving} open={addVisible} title="导入模型 JSON" onOk={() => addForm.submit()} onCancel={() => { if (savingRef.current) return; setAddVisible(false); setFileList([]); addForm.resetFields(); }} okText="提交" width={600}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            if (fileList.length === 0) {
              message.error('请选择模型JSON文件');
              return;
            }
            const f = fileList[0].originFileObj as File;
            await ModelService.uploadJson(f);
            message.success('模型导入成功');
            setAddVisible(false);
            setFileList([]);
            addForm.resetFields();
            loadData(page, size);
          } catch (err: any) {
            message.error(err?.message || '新增失败');
          } finally {
            savingRef.current = false;
            setSaving(false);
          }
        }}>
          <p>选择供应商导出的模型目录 JSON（data.records，最多 500 条）；导入会直接保存模型，不需要另外创建。</p>
          <Form.Item label="模型JSON文件">
            <Upload name="file" multiple={false} maxCount={1} fileList={fileList} beforeUpload={() => false} onChange={(info) => setFileList(info.fileList)} accept="application/json" listType="text">
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ModelManage;
