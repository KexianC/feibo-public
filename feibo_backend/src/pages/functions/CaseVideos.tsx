import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Upload, Image, Tag, Pagination, message, Select } from 'antd';
import type { UploadFile } from 'antd';
import { PlusOutlined, UploadOutlined, SearchOutlined, EditOutlined, DeleteOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import CaseVideoService, { CaseVideoItem, PageResponse } from '@/services/caseVideoService';

const { Option } = Select;

const CaseVideos: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<CaseVideoItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const [editVisible, setEditVisible] = useState(false);
  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [editing, setEditing] = useState<CaseVideoItem | null>(null);
  const [viewing, setViewing] = useState<CaseVideoItem | null>(null);

  const [videoList, setVideoList] = useState<UploadFile[]>([]);
  const [coverList, setCoverList] = useState<UploadFile[]>([]);

  const didInitRef = useRef(false);

  /**
   * 加载案例视频分页数据
   */
  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await CaseVideoService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载案例视频列表失败');
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
      <Card title="案例视频" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setVideoList([]); setCoverList([]); addForm.resetFields(); setAddVisible(true); }}>新增案例视频</Button>
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

        <Table<CaseVideoItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
            { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
            { title: '场景', dataIndex: 'scene', key: 'scene', width: 160 },
            { title: '行业', dataIndex: 'industry', key: 'industry', width: 160 },
            { title: '封面', dataIndex: 'coverUrl', key: 'coverUrl', width: 140, render: (u: string) => (u ? <Image src={u} width={100} height={60} style={{ height: 60, objectFit: 'cover', borderRadius: 6 }} /> : '-') },
            { title: '视频', dataIndex: 'videoUrl', key: 'videoUrl', width: 240, render: (u: string) => (u ? <a href={u} target="_blank" rel="noreferrer">查看视频</a> : '-') },
            { title: '排序', dataIndex: 'sort', key: 'sort', width: 100 },
            { title: '是否显示', dataIndex: 'isShow', key: 'isShow', width: 120, render: (v: number) => (v === 1 ? <Tag color="green">显示</Tag> : <Tag color="red">隐藏</Tag>) },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 220,
              render: (_: any, record: CaseVideoItem) => (
                <Space size="small">
                  <Button type="link" icon={<EyeOutlined />} onClick={async () => { const d = await CaseVideoService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" icon={<EditOutlined />} onClick={async () => { const d = await CaseVideoService.getById(record.id); setEditing(d); setVideoList([]); setCoverList([]); editForm.setFieldsValue({ title: d.title, scene: d.scene, industry: d.industry, sort: d.sort, isShow: d.isShow }); setEditVisible(true); }}>编辑</Button>
                  <Popconfirm title="确认删除该案例视频？" onConfirm={async () => { try { await CaseVideoService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
                    <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          scroll={{ x: 1300 }}
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

      <Modal open={viewVisible} title="案例视频详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={720}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
              </div>
              <div>
                <span style={{ marginRight: 8 }}>封面：</span>
                {viewing.coverUrl ? <Image src={viewing.coverUrl} width={160} height={96} style={{ height: 96, objectFit: 'cover', borderRadius: 6 }} /> : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>视频：</span>
                {viewing.videoUrl ? <a href={viewing.videoUrl} target="_blank" rel="noreferrer">查看视频</a> : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>排序：</span>
                {typeof viewing.sort !== 'undefined' ? viewing.sort : '-'}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>是否显示：</span>
                {viewing.isShow === 1 ? <Tag color="green">显示</Tag> : <Tag color="red">隐藏</Tag>}
              </div>
              <div>
                <span style={{ marginRight: 8 }}>创建时间：</span>
                {viewing.createTime ? dayjs(viewing.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </div>
            </Space>
          </div>
        )}
      </Modal>

      <Modal confirmLoading={saving} open={editVisible} title="编辑案例视频" onOk={() => editForm.submit()} onCancel={() => { if (savingRef.current) return; setEditVisible(false); setEditing(null); }} okText="保存" width={640}>
        <Form form={editForm} layout="vertical" onFinish={async (values) => {
          /** 提交编辑案例视频 */
          if (!editing) return;
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            await CaseVideoService.update({ id: editing.id, title: values.title, scene: values.scene, industry: values.industry, sort: Number(values.sort ?? 0), isShow: values.isShow }, videoList[0]?.originFileObj as File | undefined, coverList[0]?.originFileObj as File | undefined);
            message.success('更新成功');
            setEditVisible(false);
            setEditing(null);
            setCoverList([]);
            setVideoList([]);
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
          <Form.Item name="scene" label="场景"> 
            <Input placeholder="请输入场景" />
          </Form.Item>
          <Form.Item name="industry" label="行业"> 
            <Input placeholder="请输入行业" />
          </Form.Item>
          <Form.Item name="sort" label="排序"> 
            <Input type="number" placeholder="请输入排序" />
          </Form.Item>
          <Form.Item name="isShow" label="是否显示"> 
            <Select>
              <Option value={1}>显示</Option>
              <Option value={0}>隐藏</Option>
            </Select>
          </Form.Item>
          <Form.Item label="封面图">
            <Upload name="cover" multiple={false} maxCount={1} fileList={coverList} beforeUpload={() => false} onChange={(info) => setCoverList(info.fileList)} accept="image/*" listType="picture">
              <Button icon={<UploadOutlined />}>选择封面图</Button>
            </Upload>
          </Form.Item>
          <Form.Item label="视频文件">
            <Upload name="video" multiple={false} maxCount={1} fileList={videoList} beforeUpload={() => false} onChange={(info) => setVideoList(info.fileList)} accept="video/*" listType="text">
              <Button icon={<UploadOutlined />}>选择视频文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      <Modal confirmLoading={saving} open={addVisible} title="新增案例视频" onOk={() => addForm.submit()} onCancel={() => { if (savingRef.current) return; setAddVisible(false); setVideoList([]); setCoverList([]); addForm.resetFields(); }} okText="提交" width={640}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          /** 提交新增案例视频 */
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            await CaseVideoService.create({ title: values.title, scene: values.scene, industry: values.industry, sort: Number(values.sort ?? 0), isShow: values.isShow ?? 1 }, videoList[0]?.originFileObj as File, coverList[0]?.originFileObj as File);
            message.success('新增成功');
            setAddVisible(false);
            setVideoList([]);
            setCoverList([]);
            addForm.resetFields();
            loadData(page, size);
          } catch (err: any) {
            message.error(err?.message || '新增失败');
          } finally {
            savingRef.current = false;
            setSaving(false);
          }
        }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="scene" label="场景"> 
            <Input placeholder="请输入场景" />
          </Form.Item>
          <Form.Item name="industry" label="行业"> 
            <Input placeholder="请输入行业" />
          </Form.Item>
          <Form.Item name="isShow" label="是否显示" initialValue={1}> 
            <Select>
              <Option value={1}>显示</Option>
              <Option value={0}>隐藏</Option>
            </Select>
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={0}> 
            <Input type="number" />
          </Form.Item>
          <Form.Item label="封面图">
            <Upload name="cover" multiple={false} maxCount={1} fileList={coverList} beforeUpload={() => false} onChange={(info) => setCoverList(info.fileList)} accept="image/*" listType="picture">
              <Button icon={<UploadOutlined />}>选择封面图</Button>
            </Upload>
          </Form.Item>
          <Form.Item label="视频文件">
            <Upload name="video" multiple={false} maxCount={1} fileList={videoList} beforeUpload={() => false} onChange={(info) => setVideoList(info.fileList)} accept="video/*" listType="text">
              <Button icon={<UploadOutlined />}>选择视频文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CaseVideos;
