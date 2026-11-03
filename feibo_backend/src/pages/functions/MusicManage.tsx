import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Upload, Tag, Pagination, message } from 'antd';
import type { UploadFile } from 'antd';
import { PlusOutlined, UploadOutlined, SearchOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import MusicService, { MusicItem, PageResponse } from '@/services/musicService';

const MusicManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<MusicItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<MusicItem | null>(null);
  const [viewing, setViewing] = useState<MusicItem | null>(null);

  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const didInitRef = useRef(false);

  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await MusicService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载音乐列表失败');
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
      <Card title="音乐管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddVisible(true)}>新增音乐</Button>
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

        <Table<MusicItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 120 },
            { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
            { title: '时长', dataIndex: 'duration', key: 'duration', width: 120, render: (v: number) => (v ? `${v}s` : '-') },
            { title: '试听', dataIndex: 'musicUrl', key: 'musicUrl', width: 240, render: (u: string) => (u ? <audio controls src={u} style={{ width: 220 }} /> : '-') },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: MusicItem) => (
                <Space size="small">
                  <Button type="link" icon={<EyeOutlined />} onClick={async () => { const d = await MusicService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" icon={<EditOutlined />} onClick={async () => { const d = await MusicService.getById(record.id); setEditing(d); editForm.setFieldsValue({ title: d.title }); setEditVisible(true); }}>编辑</Button>
                  <Popconfirm title="确认删除该音乐？" onConfirm={async () => { try { await MusicService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
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

      <Modal open={viewVisible} title="音乐详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={700}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>时长：</span>
                {viewing.duration ? `${viewing.duration}s` : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>试听：</span>
                {viewing.musicUrl ? <audio controls src={viewing.musicUrl} style={{ width: 500 }} /> : '-'}
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

      <Modal confirmLoading={saving} open={editVisible} title="编辑音乐" onOk={() => editForm.submit()} onCancel={() => { if (savingRef.current) return; setEditVisible(false); setEditing(null); }} okText="保存">
        <Form form={editForm} layout="vertical" onFinish={async (values) => {
          if (!editing) return;
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            await MusicService.update({ id: editing.id, title: values.title });
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
        </Form>
      </Modal>

      <Modal confirmLoading={saving} open={addVisible} title="新增音乐" onOk={() => addForm.submit()} onCancel={() => { if (savingRef.current) return; setAddVisible(false); setFileList([]); addForm.resetFields(); }} okText="提交" width={600}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            if (fileList.length === 0) {
              message.error('请选择音乐文件');
              return;
            }
            const f = fileList[0].originFileObj as File;
            await MusicService.create({ title: values.title }, f);
            message.success('新增成功');
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
          <Form.Item label="音乐文件">
            <Upload name="file" multiple={false} maxCount={1} fileList={fileList} beforeUpload={() => false} onChange={(info) => setFileList(info.fileList)} accept="audio/*" listType="text">
              <Button icon={<UploadOutlined />}>选择音乐</Button>
            </Upload>
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MusicManage;
