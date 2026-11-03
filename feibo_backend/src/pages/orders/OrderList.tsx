import React, { useState, useEffect, useRef } from 'react';
import { Table, Card, Button, Input, Select, Space, Tag, Modal, DatePicker, Statistic, Row, Col, message, Pagination } from 'antd';
import { SearchOutlined, EyeOutlined, DollarOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import OrderService, { Order } from '@/services/orderService';

const { Search } = Input;
const { Option } = Select;
const { RangePicker } = DatePicker;

const OrderList: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);

  // 查询条件与数据
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<number | undefined>();
  const [dateRange, setDateRange] = useState<any>(undefined);

  /**
   * 拉取订单分页数据
   * - 根据当前查询条件与分页设置，调用服务层接口
   */
  const fetchOrders = async (pageNum = page, pageSize = size) => {
    try {
      setLoading(true);
      const params: any = {
        page: pageNum,
        size: pageSize,
        keyword,
        status: statusFilter !== undefined ? String(statusFilter) : undefined,
      };
      if (dateRange && Array.isArray(dateRange) && dateRange.length === 2) {
        params.startDate = dayjs(dateRange[0]).format('YYYY-MM-DD HH:mm:ss');
        params.endDate = dayjs(dateRange[1]).format('YYYY-MM-DD HH:mm:ss');
      }
      const data = await OrderService.getOrdersPage(params);
      setOrders((data.records || []).map((o: any) => ({ ...o, id: String(o.id) })) as Order[]);
      setTotal(data.total || 0);
      setPage(data.current || pageNum);
      setSize(data.size || pageSize);
    } catch (error: any) {
      console.error('获取订单列表失败:', error);
      message.error(error?.message || '获取订单列表失败');
    } finally {
      setLoading(false);
    }
  };

  const didInitRef = useRef(false);
  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    fetchOrders(1, size);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 统计数据适配新字段
  const statistics = {
    totalOrders: orders.length,
    paidOrders: orders.filter(o => Number(o.status) === 2).length,
    pendingOrders: orders.filter(o => [0, 1].includes(Number(o.status))).length,
    totalRevenue: orders
      .filter(o => Number(o.status) === 2)
      .reduce((sum, o: any) => sum + (Number((o as any).amount) || 0), 0)
  };

  // 状态渲染映射（数值状态）
  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'blue', text: '创建完成' },
    1: { color: 'orange', text: '等待支付' },
    2: { color: 'green', text: '支付成功' },
    3: { color: 'purple', text: '交易完成' },
    4: { color: 'gold', text: '等待退款' },
    5: { color: 'red', text: '关单' },
  };

  // 详情弹窗使用的颜色/文本映射常量
  const statusColorMap: Record<number, string> = { 0: 'blue', 1: 'orange', 2: 'green', 3: 'purple', 4: 'gold', 5: 'red' };
  const statusTextMap: Record<number, string> = { 0: '创建完成', 1: '等待支付', 2: '支付成功', 3: '交易完成', 4: '等待退款', 5: '关单' };

  // 列定义适配新字段
  const columns: any = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 150 },
    { title: '订单类型', dataIndex: 'orderType', key: 'orderType', width: 120 },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: (v: number) => (<span style={{ fontWeight: 'bold', color: '#f5222d' }}>¥{(v || 0).toFixed(2)}</span>) },
    { title: '会员', dataIndex: 'member', key: 'member', width: 140 },
    { title: '套餐ID', dataIndex: 'packageId', key: 'packageId', width: 100 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (s: number) => { const cfg = statusMap[Number(s)] || { color: 'default', text: String(s) }; return <Tag color={cfg.color}>{cfg.text}</Tag>; } },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 160 },
    { title: '操作', key: 'action', width: 100, render: (_: any, record: Order) => (
      <Space size="middle">
        <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>查看</Button>
      </Space>
    ) },
  ];

  /**
   * 查看订单详情（使用当前行数据）
   */
  const handleView = (record: Order) => {
    setSelectedOrder(record);
    setIsModalVisible(true);
  };

  /**
   * 关闭详情弹窗
   */
  const handleModalCancel = () => {
    setIsModalVisible(false);
    setSelectedOrder(null);
  };

  return (
    <div>
      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总订单数"
              value={statistics.totalOrders}
              prefix={<ShoppingCartOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已支付订单"
              value={statistics.paidOrders}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="待支付订单"
              value={statistics.pendingOrders}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总收入"
              value={statistics.totalRevenue}
              prefix={<DollarOutlined />}
              precision={2}
              valueStyle={{ color: '#f5222d' }}
              suffix="元"
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            <Search
              placeholder="搜索订单号或会员"
              allowClear
              enterButton={<SearchOutlined />}
              size="middle"
              style={{ width: 300 }}
              onSearch={(val) => { setKeyword(val || undefined); fetchOrders(1, size); }}
              onChange={(e) => setKeyword(e.target.value || undefined)}
            />
            <Select
              placeholder="选择状态"
              allowClear
              style={{ width: 140 }}
              value={statusFilter as any}
              onChange={(val) => setStatusFilter(val as any)}
            >
              <Option value={0}>创建完成</Option>
              <Option value={1}>等待支付</Option>
              <Option value={2}>支付成功</Option>
              <Option value={3}>交易完成</Option>
              <Option value={4}>等待退款</Option>
              <Option value={5}>关单</Option>
            </Select>
            <RangePicker
              placeholder={["开始日期", "结束日期"]}
              style={{ width: 240 }}
              value={dateRange}
              onChange={(val) => setDateRange(val || undefined)}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={() => fetchOrders(1, size)}>搜索</Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={orders}
          loading={loading}
          rowKey={(row: Order) => row.id}
          pagination={false}
          scroll={{ x: 1400, y:  Math.max(240, window.innerHeight - 420) }}
        />
        <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Pagination
            total={total}
            current={page}
            pageSize={size}
            showSizeChanger
            showQuickJumper
            showTotal={(t, range) => `第 ${range[0]}-${range[1]} 条/总共 ${t} 条`}
            onChange={(p, s) => { setPage(p); setSize(s || size); fetchOrders(p, s || size); }}
          />
        </div>
      </Card>

      {/* 详情弹窗保留 */}
      <Modal
        title="订单详情"
        open={isModalVisible}
        onCancel={handleModalCancel}
        footer={[
          <Button key="close" onClick={handleModalCancel}>
            关闭
          </Button>
        ]}
        width={800}
      >
        {/* 详情弹窗内容适配新字段 */}
        {selectedOrder && (
          <div>
            <Row gutter={16}>
              <Col span={12}>
                <h4>订单信息</h4>
                <p><strong>订单号：</strong>{selectedOrder.orderNo}</p>
                <p><strong>订单ID：</strong>{selectedOrder.id}</p>
                <p><strong>订单类型：</strong>{(selectedOrder as any).orderType}</p>
                <p><strong>会员：</strong>{(selectedOrder as any).member}</p>
                <p><strong>套餐ID：</strong>{(selectedOrder as any).packageId}</p>
                <p><strong>创建时间：</strong>{selectedOrder.createTime}</p>
                <p><strong>更新时间：</strong>{(selectedOrder as any).updateTime}</p>
              </Col>
              <Col span={12}>
                <h4>支付信息</h4>
                <p><strong>金额：</strong><span style={{ color: '#f5222d', fontWeight: 'bold' }}>¥{Number((selectedOrder as any).amount || 0).toFixed(2)}</span></p>
                <p><strong>状态：</strong>
                  <Tag color={statusColorMap[Number(selectedOrder.status)] || 'default'}>
                    {statusTextMap[Number(selectedOrder.status)] || String(selectedOrder.status)}
                  </Tag>
                </p>
              </Col>
            </Row>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default OrderList;
