import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Upload, Tag, Pagination, Image, message } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { PlusOutlined, UploadOutlined, SearchOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import VideoService, { VideoItem, PageResponse } from '@/services/videoService';


const VideoManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [uploadForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<VideoItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);


  const [editVisible, setEditVisible] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<VideoItem | null>(null);
  const [viewing, setViewing] = useState<VideoItem | null>(null);

  const [fileListVideo, setFileListVideo] = useState<UploadFile[]>([]);
  const [fileListCover, setFileListCover] = useState<UploadFile[]>([]);

  const didInitRef = useRef(false);

  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await VideoService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载视频列表失败');
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

  const handleAdd = () => {
    setEditing(null);
    setFileListVideo([]);
    setFileListCover([]);
    editForm.resetFields();
    uploadForm.resetFields();
    setUploadVisible(true);
  };

  const uploadPropsVideo: UploadProps = {
    name: 'file',
    multiple: false,
    maxCount: 1,
    fileList: fileListVideo,
    beforeUpload: () => false,
    onChange: (info) => setFileListVideo(info.fileList),
  };

  const uploadPropsCover: UploadProps = {
    name: 'file',
    multiple: false,
    maxCount: 1,
    fileList: fileListCover,
    beforeUpload: () => false,
    onChange: (info) => setFileListCover(info.fileList),
  };

  const handleUploadSubmit = async (values: any) => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      if (fileListVideo.length === 0) {
        message.error('请选择视频文件');
        return;
      }
      const vfile = fileListVideo[0]?.originFileObj as File;
      const cfile = fileListCover[0]?.originFileObj as File;
      await VideoService.create({ title: values.title }, vfile, cfile);
      message.success('新增成功');
      setUploadVisible(false);
      setFileListVideo([]);
      setFileListCover([]);
      uploadForm.resetFields();
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '上传或新增失败');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const handleEdit = async (record: VideoItem) => {
    try {
      const detail = await VideoService.getById(record.id);
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
      await VideoService.update(payload);
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

  const handleDelete = async (record: VideoItem) => {
    try {
      await VideoService.delete(record.id);
      message.success('删除成功');
      loadData(page, size);
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  const handleView = async (record: VideoItem) => {
    try {
      const detail = await VideoService.getById(record.id);
      setViewing(detail);
      setViewVisible(true);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 120 },
    { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
    { title: '时长', dataIndex: 'duration', key: 'duration', width: 120, render: (v: string) => (v ? `${v}` : '-') },
    { title: '封面', dataIndex: 'coverUrl', key: 'coverUrl', width: 140, render: (u: string) => (u ? <Image src={u} width={120} height={68} style={{ objectFit: 'cover' }} /> : '-') },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
    {
      title: '操作', key: 'action', fixed: 'right', width: 220,
      render: (_: any, record: VideoItem) => (
        <Space size="small">
          <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>详情</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该视频？" onConfirm={() => handleDelete(record)}>
            <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card title="视频管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增视频</Button>
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

        <Table<VideoItem>
          rowKey="id"
          columns={columns as any}
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

      <Modal
        title="编辑视频"
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
        title="新增视频"
        open={uploadVisible}
        confirmLoading={saving}
        onOk={() => uploadForm.submit()}
        onCancel={() => { if (savingRef.current) return; setUploadVisible(false); setFileListVideo([]); setFileListCover([]); uploadForm.resetFields(); }}
        okText="提交"
        width={600}
      >
        <Form form={uploadForm} layout="vertical" onFinish={handleUploadSubmit}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item label="视频文件">
            <Upload {...uploadPropsVideo} accept="video/*" listType="text">
              <Button icon={<UploadOutlined />}>选择视频</Button>
            </Upload>
          </Form.Item>
          <Form.Item label="封面">
            <Upload {...uploadPropsCover} accept="image/*" listType="picture">
              <Button icon={<UploadOutlined />}>选择封面</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="视频详情"
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
                <span style={{ marginRight: 8 }}>时长：</span>
                {viewing.duration ? `${viewing.duration}` : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>封面：</span>
                {viewing.coverUrl ? <Image src={viewing.coverUrl} width={200} /> : '-'}
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

export default VideoManage;
