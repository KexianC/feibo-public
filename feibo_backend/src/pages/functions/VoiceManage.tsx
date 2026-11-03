import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Popconfirm, Tag, Pagination, message, Select } from 'antd';
import { PlusOutlined, SearchOutlined, DeleteOutlined, EditOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import VoiceService, { VoiceItem, PageResponse } from '@/services/voiceService';

const VoiceManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [addForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [pageData, setPageData] = useState<PageResponse<VoiceItem>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [title, setTitle] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(8);

  const [addVisible, setAddVisible] = useState(false);
  const [viewVisible, setViewVisible] = useState(false);
  const [viewing, setViewing] = useState<VoiceItem | null>(null);
  const [editing, setEditing] = useState<VoiceItem | null>(null);


  const didInitRef = useRef(false);

  const requestGeneration = useRef(0);
  const loadData = async (p = page, s = size, searchTitle = title) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await VoiceService.getPage({ page: p, size: s, title: searchTitle });
      if (generation !== requestGeneration.current) return;
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err: any) {
      if (generation !== requestGeneration.current) return;
      message.error(err?.message || '加载语音列表失败');
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
      <Card title="配音管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); addForm.resetFields(); setAddVisible(true); }}>新增语音</Button>
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

        <Table<VoiceItem>
          rowKey="id"
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
            { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
            { title: '供应商语音 ID', dataIndex: 'digitalVoiceId', key: 'digitalVoiceId', width: 200 },
            { title: '是否显示', dataIndex: 'isDisplay', key: 'isDisplay', render: (v?: boolean) => v === true ? '显示' : v === false ? '隐藏' : '未设置' },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
            {
              title: '操作', key: 'action', fixed: 'right', width: 260,
              render: (_: any, record: VoiceItem) => (
                <Space size="small">
                  <Button type="link" icon={<EyeOutlined />} onClick={async () => { const d = await VoiceService.getById(record.id); setViewing(d); setViewVisible(true); }}>详情</Button>
                  <Button type="link" icon={<EditOutlined />} onClick={() => {
                    setEditing(record);
                    addForm.resetFields();
                    addForm.setFieldsValue({ title: record.title, digitalVoiceId: record.digitalVoiceId, styleName: record.styleName, gender: record.gender ?? 0, isDisplay: record.isDisplay ?? true });
                    setAddVisible(true);
                  }}>编辑</Button>
                  <Popconfirm title="确认删除该语音？" onConfirm={async () => { try { await VoiceService.delete(record.id); message.success('删除成功'); loadData(page, size); } catch (err: any) { message.error(err?.message || '删除失败'); } }}>
                    <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
                  </Popconfirm>
                </Space>
              )
            }
          ]}
          dataSource={pageData.records}
          loading={loading}
          pagination={false}
          scroll={{ x: 900 }}
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

      <Modal open={viewVisible} title="语音详情" footer={null} onCancel={() => { setViewVisible(false); setViewing(null); }} width={700}>
        {viewing && (
          <div>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <span style={{ marginRight: 8 }}>标题：</span>
                <Tag color="blue">{viewing.title}</Tag>
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

      <Modal confirmLoading={saving} open={addVisible} title={editing ? '编辑语音' : '新增语音'} onOk={() => addForm.submit()} onCancel={() => { if (savingRef.current) return; setAddVisible(false); setEditing(null); addForm.resetFields(); }} okText="提交" width={600}>
        <Form form={addForm} layout="vertical" onFinish={async (values) => {
          if (savingRef.current) return;
          savingRef.current = true;
          setSaving(true);
          try {
            const payload = { title: values.title, digitalVoiceId: values.digitalVoiceId, styleName: values.styleName, gender: values.gender, isDisplay: values.isDisplay ?? true };
            if (editing) await VoiceService.update({ id: editing.id, ...payload });
            else await VoiceService.create(payload);
            message.success(editing ? '保存成功' : '新增成功');
            setAddVisible(false);
            setEditing(null);
            addForm.resetFields();
            await loadData(page, size);
          } catch (err: any) {
            message.error(err?.message || (editing ? '保存失败' : '新增失败'));
          } finally {
            savingRef.current = false;
            setSaving(false);
          }
        }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="digitalVoiceId" label="供应商语音 ID" rules={[{ required: true, pattern: /^[1-9][0-9]{0,18}$/, message: '请输入供应商返回的数字语音 ID' }]} extra="填写已有供应商音色的业务 ID。">
            <Input placeholder="请输入供应商语音 ID" />
          </Form.Item>
          <Form.Item name="styleName" label="风格"><Input /></Form.Item>
          <Form.Item name="gender" label="性别" initialValue={0}><Select options={[{value:0,label:'未知'},{value:1,label:'男'},{value:2,label:'女'}]} /></Form.Item>
          <Form.Item name="isDisplay" label="是否显示" initialValue={true}><Select options={[{value:true,label:'显示'},{value:false,label:'隐藏'}]} /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default VoiceManage;
