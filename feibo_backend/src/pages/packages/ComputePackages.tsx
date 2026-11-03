import React, { useEffect, useRef, useState } from 'react';
import { Card, Table, Space, Input, Button, Modal, Form, InputNumber, Select, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/lib/table';
import PackageService, { Package, PackagePageParams, PackageParams } from '../../services/packageService';
import { SearchOutlined, ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';

const { Option } = Select;

/**
 * 算力包管理页面
 * 提供列表展示、搜索筛选、分页、详情、新增、编辑、删除等功能
 */
const ComputePackages: React.FC = () => {
  // 表格数据与分页状态
  const [packages, setPackages] = useState<Package[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  // 搜索筛选状态
  const [keyword, setKeyword] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [minPrice, setMinPrice] = useState<number | undefined>(undefined);
  const [maxPrice, setMaxPrice] = useState<number | undefined>(undefined);

  // 详情/新增/编辑模态框状态
  const [detailVisible, setDetailVisible] = useState(false);
  const [editVisible, setEditVisible] = useState(false);
  const [current, setCurrent] = useState<Package | null>(null);
  const [form] = Form.useForm<PackageParams & { id?: string }>();
  const [saving, setSaving] = useState(false);
  const requestGeneration = useRef(0);

  /**
   * 加载算力包分页数据
   * 根据搜索与分页参数调用服务端接口并设置表格数据
   */
  const loadPackages = async (currentPage: number = page, currentSize: number = size, reset = false) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const params: PackagePageParams = {
        page: currentPage,
        size: currentSize,
        name: reset ? undefined : keyword.trim() || undefined,
        status: reset ? undefined : statusFilter,
        minPrice: reset ? undefined : minPrice,
        maxPrice: reset ? undefined : maxPrice,
      };
      if (params.minPrice !== undefined && params.maxPrice !== undefined && params.minPrice > params.maxPrice) {
        throw new Error('最低价不能大于最高价');
      }
      const res = await PackageService.getPackagesPage(params);
      if (generation !== requestGeneration.current) return;
      setPackages(res.records);
      setTotal(res.total);
      setPage(currentPage);
      setSize(currentSize);
    } catch (e) {
      if (generation === requestGeneration.current) message.error('加载算力包失败：' + (e as Error).message);
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  };

  /**
   * 初始化加载
   * 组件挂载后拉取首屏数据
   */
  useEffect(() => {
    loadPackages();
    return () => { requestGeneration.current++; };
  }, []);

  /**
   * 执行搜索
   * 将页码重置为 1 后发起请求
   */
  const handleSearch = () => {
    loadPackages(1, size);
  };

  /**
   * 重置筛选条件
   * 清空搜索框与筛选项后重新加载
   */
  const handleReset = () => {
    setKeyword('');
    setStatusFilter(undefined);
    setMinPrice(undefined);
    setMaxPrice(undefined);
    loadPackages(1, size, true);
  };

  /**
   * 分页变化
   * 响应表格分页器变更
   */
  const handleTableChange = (pagination: any) => {
    loadPackages(pagination.current, pagination.pageSize);
  };

  /**
   * 打开详情
   * 设置当前选中项并显示详情模态框
   */
  const handleView = (record: Package) => {
    setCurrent(record);
    setDetailVisible(true);
  };

  /**
   * 打开新增
   * 重置表单并显示编辑模态框
   */
  const handleAdd = () => {
    setCurrent(null);
    form.resetFields();
    form.setFieldsValue({ status: 0, value: 0, price: 0 });
    setEditVisible(true);
  };

  /**
   * 打开编辑
   * 回填当前记录到表单并显示编辑模态框
   */
  const handleEdit = (record: Package) => {
    setCurrent(record);
    form.resetFields();
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      price: record.price,
      value: record.value,
      aiFeatureTemplateId: record.aiFeatureTemplateId,
      status: record.status,
      id: record.id,
    } as any);
    setEditVisible(true);
  };

  /**
   * 提交新增/编辑
   * 表单校验通过后调用服务层进行保存
   */
  const submitEdit = async () => {
    if (saving) return;
    try {
      const values = await form.validateFields();
      setSaving(true);
      if (values.id) {
        await PackageService.updatePackage(values as PackageParams & { id: string });
        message.success('更新成功');
      } else {
        await PackageService.createPackage(values as PackageParams);
        message.success('新增成功');
      }
      setEditVisible(false);
      loadPackages(page, size);
    } catch (e) {
      if (!(e && typeof e === 'object' && 'errorFields' in e)) message.error('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  /**
   * 删除套餐
   * 二次确认后调用服务层删除所选记录
   */
  const handleDelete = (record: Package) => {
    Modal.confirm({
      title: '删除确认',
      content: `确定删除套餐【${record.name}】吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await PackageService.deletePackage(record.id);
          message.success('删除成功');
          loadPackages(page, size);
        } catch (e) {
          message.error('删除失败：' + (e as Error).message);
        }
      },
    });
  };

  /**
   * 状态标签
   * 将派生状态（由 isDeleted 派生）渲染为颜色标签
   */
  const renderStatus = (status: number) => {
    const map = {
      0: { text: '启用', color: 'green' },
      1: { text: '禁用', color: 'red' },
    } as const;
    const conf = (map as any)[status] || { text: '未知', color: 'default' };
    return <Tag color={conf.color}>{conf.text}</Tag>;
  };

  /**
   * 是否显示标签
   * 0 不显示，1 显示
   */
  const renderDisplay = (isDisplay?: number) => {
    if (isDisplay === 1) return <Tag color="green">显示</Tag>;
    if (isDisplay === 0) return <Tag color="default">不显示</Tag>;
    return <Tag>未知</Tag>;
  };

  /**
   * 表格列定义
   * 适配最新接口返回字段进行展示
   */
  const columns: ColumnsType<Package> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 100 },
    { title: '包ID', dataIndex: 'aiRechargePackId', key: 'aiRechargePackId', width: 180 },
    { title: '包代码', dataIndex: 'packCode', key: 'packCode', width: 140 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 220 },
    { title: '价格', dataIndex: 'price', key: 'price', width: 120, render: (v) => `¥${Number(v).toFixed(2)}` },
    { title: '算力额度', dataIndex: 'value', key: 'value', width: 140 },
    { title: '模板ID', dataIndex: 'aiFeatureTemplateId', key: 'aiFeatureTemplateId', width: 220 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: renderStatus },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
    {
      title: '操作', key: 'action', fixed: 'right', width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>详情</Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      )
    },
  ];

  return (
    <Card title="算力包管理">
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="按名称搜索"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 200 }}
          prefix={<SearchOutlined />}
          allowClear
        />
        <Select
          placeholder="选择状态"
          allowClear
          style={{ width: 160 }}
          value={statusFilter}
          onChange={setStatusFilter}
        >
          <Option value={0}>启用</Option>
          <Option value={1}>禁用</Option>
        </Select>
        <InputNumber
          placeholder="最低价"
          value={minPrice}
          onChange={(v) => setMinPrice(v ?? undefined)}
          style={{ width: 120 }}
          min={0}
        />
        <InputNumber
          placeholder="最高价"
          value={maxPrice}
          onChange={(v) => setMaxPrice(v ?? undefined)}
          style={{ width: 120 }}
          min={0}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
        <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
        <Button type="dashed" icon={<PlusOutlined />} onClick={handleAdd}>新增算力包</Button>
      </Space>

      <Table
        columns={columns}
        dataSource={packages}
        rowKey="id"
        loading={loading}
        pagination={{
          total,
          current: page,
          pageSize: size,
          showSizeChanger: true,
          showQuickJumper: true,
        }}
        onChange={handleTableChange}
        scroll={{ x: 1200 }}
      />

      {/* 详情模态框 */}
      <Modal
        title="算力包详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={700}
      >
        {current && (
          <div style={{ lineHeight: 2 }}>
            <div><strong>ID：</strong>{current.id}</div>
            <div><strong>包ID：</strong>{current.aiRechargePackId}</div>
            <div><strong>包代码：</strong>{current.packCode}</div>
            <div><strong>名称：</strong>{current.name}</div>
            <div><strong>价格：</strong>¥{Number(current.price).toFixed(2)}</div>
            <div><strong>算力额度：</strong>{current.value}</div>
            <div><strong>模板ID：</strong>{current.aiFeatureTemplateId}</div>
            <div><strong>状态：</strong>{renderStatus(current.status)}</div>
            <div><strong>创建时间：</strong>{current.createTime}</div>
            <div><strong>更新时间：</strong>{current.updateTime}</div>
          </div>
        )}
      </Modal>

      {/* 新增/编辑模态框 */}
      <Modal
        title={current ? '编辑算力包' : '新增算力包'}
        open={editVisible}
        onCancel={() => { if (!saving) setEditVisible(false); }}
        confirmLoading={saving}
        closable={!saving}
        maskClosable={!saving}
        cancelButtonProps={{ disabled: saving }}
        onOk={submitEdit}
        okText="提交"
        cancelText="取消"
        width={700}
      >
        <Form form={form} layout="vertical" disabled={saving}>
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="算力包名称" name="name" rules={[{ required: true, message: '请输入算力包名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="价格" name="price" rules={[{ required: true, message: '请输入价格' }]}> 
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="算力额度" name="value" extra="按绑定功能模板的计量单位发放；此项不设置有效期。" rules={[{ required: true, message: '请输入算力额度' }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="功能模板 ID" name="aiFeatureTemplateId" rules={[{ pattern: /^[1-9]\d{0,18}$/, message: '请输入有效的功能模板 ID' }]}>
            <Input placeholder="绑定实际功能模板" />
          </Form.Item>
          <Form.Item label="状态" name="status" rules={[{ required: true, message: '请选择状态' }]}> 
            <Select placeholder="选择状态">
              <Option value={0}>启用</Option>
              <Option value={1}>禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default ComputePackages;
