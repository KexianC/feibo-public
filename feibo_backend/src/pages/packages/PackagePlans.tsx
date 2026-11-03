import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Select,
  Modal,
  Form,
  InputNumber,
  Tag,
  message,
  Popconfirm,
  Row,
  Col,
  Descriptions
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  EyeOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { Package } from '../../services/packageService';
import PlanService, { PlanParams } from '../../services/planService';

const { Option } = Select;

/**
 * 套餐设置页面
 * 提供套餐的增删改查功能，包括分页查询、条件筛选、状态管理等
 */
const PackagePlans: React.FC = () => {
  // 表格数据状态
  const [packages, setPackages] = useState<Package[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  // 搜索条件状态
  const [searchPackageName, setSearchPackageName] = useState<string>('');
  // 已移除不再适用的筛选项：category/version/chargeType/amountRange
  // 如需按 isActive/planCode 筛选，可后续新增下拉框
  const requestGeneration = useRef(0);
  const [saving, setSaving] = useState(false);

  // 模态框状态
  const [modalVisible, setModalVisible] = useState(false);
  const [modalTitle, setModalTitle] = useState('');
  const [editingPackage, setEditingPackage] = useState<Package | null>(null);
  const [form] = Form.useForm();

  // 详情模态框状态
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedPackage, setSelectedPackage] = useState<Package | null>(null);

  /**
   * 加载套餐列表数据
   * 使用后台文档参数：pageNum/pageSize/packageName/category/version
   * 完全依赖后端筛选，移除本地过滤逻辑
   */
  const loadPackages = async (currentPage: number = page, currentSize: number = size, keyword = searchPackageName) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    try {
      const data = await PlanService.getPlansPage({
        pageNum: currentPage,
        pageSize: currentSize,
        packageName: keyword.trim() || undefined,
      });
      if (generation !== requestGeneration.current) return;
      const records: Package[] = (data.records || []).map((item: any) => {
        // 将后端返回映射为页面使用的 Package 结构
        const durationDays = typeof item.duration === 'number' ? item.duration : 0;
        const price = item.amount ?? item.price ?? 0;
        return {
          id: String(item.id),
          name: item.packageName ?? item.name ?? '',
          description: '',
          price: Number(price),
          duration: Number(durationDays),
          features: [],
          status: item.status ?? (Number(item.isDeleted) === 1 ? 1 : 0),
          createTime: item.createTime,
          updateTime: item.updateTime,
          // 保留原始字段便于展示
          packageId: item.id,
          packageName: item.packageName ?? item.name,
          durationText: item.durationText ?? (durationDays === -1 ? '永久' : `${durationDays}天`),
          amount: item.amount ?? item.price,
          // 新增字段适配
          aiMembershipPlanId: item.aiMembershipPlanId !== undefined ? String(item.aiMembershipPlanId) : undefined,
          planCode: item.planCode,
          isActive: item.isActive,
          isDeleted: item.isDeleted,
        } as Package;
      });

      setPackages(records);
      setTotal(data.total || 0);
      setPage(data.current || currentPage);
      setSize(data.size || currentSize);
    } catch (error) {
      if (generation === requestGeneration.current) message.error('加载套餐列表失败: ' + (error as Error).message);
    } finally {
      if (generation === requestGeneration.current) setLoading(false);
    }
  };

  /**
   * 页面初始化时加载数据
   */
  useEffect(() => {
    loadPackages();
    return () => { requestGeneration.current++; };
  }, []);

  /**
   * 处理搜索
   */
  const handleSearch = () => {
    loadPackages(1, size);
  };

  /**
   * 重置搜索条件
   */
  const handleReset = () => {
    setSearchPackageName('');
    // 已移除无用筛选项的重置
    loadPackages(1, size, '');
  };

  /**
   * 打开新增套餐模态框
   */
  const handleAdd = () => {
    setEditingPackage(null);
    setModalTitle('新增套餐');
    form.resetFields();
    form.setFieldsValue({
      status: 0,
    });
    setModalVisible(true);
  };

  /**
   * 打开编辑套餐模态框
   */
  const handleEdit = (record: Package) => {
    setEditingPackage(record);
    setModalTitle('编辑套餐');
    form.setFieldsValue({
      name: record.name,
      price: record.price,
      duration: record.duration,
      status: record.status
    });
    setModalVisible(true);
  };

  /**
   * 查看套餐详情
   */
  const handleViewDetail = (record: Package) => {
    // 调用后台详情接口，获取最新数据：GET /api/admin/function/package/{id}
    PlanService.getPlanById(record.id)
      .then((item) => {
        const durationDays = typeof item.duration === 'number' ? item.duration : (record.duration ?? 0);
        const price = item.amount ?? item.price ?? record.price;
        const mapped: Package = {
          id: String(item.id ?? record.id),
          name: item.packageName ?? record.name,
          description: record.description,
          price: Number(price),
          duration: Number(durationDays),
          features: record.features,
          status: item.status,
          createTime: item.createTime ?? record.createTime,
          updateTime: item.updateTime ?? record.updateTime,
          packageId: record.packageId,
          packageName: item.packageName ?? record.packageName,
          category: item.category ?? record.category,
          version: item.version ?? record.version,
          sort: item.sort ?? record.sort,
          chargeType: item.chargeType ?? record.chargeType,
          durationText: item.durationText ?? (item.duration === -1 ? '永久' : `${item.duration}天`),
          amount: item.amount ?? record.amount,
          isDeleted: item.isDeleted ?? record.isDeleted,
        };
        setSelectedPackage(mapped);
        setDetailModalVisible(true);
      })
      .catch(() => {
        // 请求失败时回退到当前记录
        setSelectedPackage(record);
        setDetailModalVisible(true);
      });
  };

  const handleToggleStatus = async (record: Package) => {
    try {
      const nextStatus = record.status === 0 ? 1 : 0;
      await PlanService.updateStatus(record.id, nextStatus);
      message.success(nextStatus === 0 ? '启用成功' : '禁用成功');
      loadPackages();
    } catch (error) {
      message.error('状态更新失败: ' + (error as Error).message);
    }
  };

  /**
   * 提交表单（新增或更新）
   */
  const handleSubmit = async () => {
    if (saving) return;
    try {
      const values = await form.validateFields();
      const params: PlanParams = {
        name: values.name,
        price: values.price,
        duration: values.duration,
        status: values.status
      };
      setSaving(true);

      if (editingPackage) {
        // 更新套餐（调用 PlanService -> PUT /api/admin/function/package）
        await PlanService.updatePlan(editingPackage.id, params);
        message.success('更新套餐成功');
      } else {
        // 新增套餐（调用 PlanService -> POST /api/admin/function/package）
        await PlanService.createPlan(params);
        message.success('新增套餐成功');
      }

      setModalVisible(false);
      form.resetFields();
      loadPackages();
    } catch (error) {
      if (!(error && typeof error === 'object' && 'errorFields' in error)) message.error('操作失败: ' + (error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  /**
   * 处理表格分页变化
   */
  const handleTableChange = (pagination: any) => {
    loadPackages(pagination.current, pagination.pageSize);
  };

  /**
   * 状态映射
   */
  const getStatusConfig = (status: number) => {
    const configs = {
      0: { color: 'green', text: '启用' },
      1: { color: 'red', text: '禁用' }
    };
    return configs[status as keyof typeof configs] || { color: 'default', text: '未知' };
  };

  /**
   * 表格列定义（仅展示后端实际字段）
   */
  const columns: ColumnsType<Package> = [
    {
      title: '套餐ID',
      dataIndex: 'packageId',
      key: 'packageId',
      width: 120,
      render: (text: number) => <span style={{ fontFamily: 'monospace' }}>{text}</span>
    },
    {
      title: '套餐名称',
      dataIndex: 'packageName',
      key: 'packageName',
      width: 180,
      render: (text: string) => <strong>{text}</strong>
    },
    // 新增：计划代码
    {
      title: '计划代码',
      dataIndex: 'planCode',
      key: 'planCode',
      width: 160,
    },
    // 新增：激活状态
    {
      title: '激活状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: number) => (
        <Tag color={status === 0 ? 'green' : 'volcano'}>{status === 0 ? '已激活' : '未激活'}</Tag>
      )
    },
    {
      title: '时长',
      dataIndex: 'durationText',
      key: 'durationText',
      width: 120
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (amount: number) => (
        <span style={{ color: '#f5222d', fontWeight: 'bold' }}>¥{Number(amount).toFixed(2)}</span>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record: Package) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            danger={record.status === 0}
            onClick={() => handleToggleStatus(record)}
          >
            {record.status === 0 ? '禁用' : '启用'}
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card title="套餐设置" style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input
              placeholder="套餐名称"
              allowClear
              style={{ width: 200 }}
              value={searchPackageName}
              onChange={(e) => setSearchPackageName(e.target.value)}
              prefix={<SearchOutlined />}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增套餐
          </Button>
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
            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
          }}
          onChange={handleTableChange}
          scroll={{ x: 1000 }}
        />
      </Card>

      {/* 新增/编辑套餐模态框 */}
      <Modal
        title={modalTitle}
        open={modalVisible}
        onOk={handleSubmit}
        confirmLoading={saving}
        closable={!saving}
        maskClosable={!saving}
        cancelButtonProps={{ disabled: saving }}
        onCancel={() => {
          if (saving) return;
          setModalVisible(false);
          form.resetFields();
        }}
        width={600}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          disabled={saving}
          initialValues={{ status: 0 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="套餐名称"
                rules={[{ required: true, message: '请输入套餐名称' }]}
              >
                <Input placeholder="请输入套餐名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="price"
                label="价格"
                rules={[{ required: true, message: '请输入价格' }]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  precision={2}
                  placeholder="请输入价格"
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="duration"
                label="有效期（天）"
                extra="-1 表示永久"
                rules={[{ required: true, message: '请输入有效期' }, { validator: (_, value) => Number.isInteger(value) && (value === -1 || value > 0) ? Promise.resolve() : Promise.reject(new Error('请输入正整数，或 -1 表示永久')) }]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  min={-1}
                  precision={0}
                  placeholder="请输入时长（天）"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="status"
                label="状态"
                rules={[{ required: true, message: '请选择状态' }]}
              >
                <Select placeholder="请选择状态">
                  <Option value={0}>启用</Option>
                  <Option value={1}>禁用</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

        </Form>
      </Modal>

      {/* 套餐详情模态框 */}
      <Modal
        title="套餐详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={600}
      >
        {selectedPackage && (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="套餐ID">
              <code>{selectedPackage.packageId}</code>
            </Descriptions.Item>
            <Descriptions.Item label="套餐名称">
              <strong>{selectedPackage.packageName}</strong>
            </Descriptions.Item>
            <Descriptions.Item label="计划代码">
              {selectedPackage.planCode}
            </Descriptions.Item>
            <Descriptions.Item label="激活状态">
              {selectedPackage.status === 0 ? <Tag color="green">已激活</Tag> : <Tag color="volcano">未激活</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="时长">
              {selectedPackage.durationText}
            </Descriptions.Item>
            <Descriptions.Item label="金额">
              <span style={{ color: '#f5222d', fontSize: '18px', fontWeight: 'bold' }}>
                ¥{Number(selectedPackage.amount).toFixed(2)}
              </span>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default PackagePlans;
