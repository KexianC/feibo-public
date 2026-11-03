import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Input, message, Modal, Pagination, Select, Space, Table, Tag } from 'antd';
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import OrderService, { Order } from '@/services/orderService';

const { Option } = Select;

const STATUS_LABELS: Record<number, { color: string; text: string }> = {
  0: { color: 'blue', text: '创建完成' },
  1: { color: 'orange', text: '等待支付' },
  2: { color: 'green', text: '支付成功' },
  3: { color: 'purple', text: '交易完成' },
  4: { color: 'gold', text: '等待退款' },
  5: { color: 'red', text: '已关单' },
};

const allowedManualTarget = (status: number): 3 | 5 | null => {
  if (status === 2) return 3;
  if (status === 0 || status === 1 || status === 4) return 5;
  return null;
};

const OrderManage: React.FC = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState<string>();
  const [status, setStatus] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<Order | null>(null);

  const loadOrders = useCallback(async (
    nextPage = page,
    nextSize = size,
    filters: { keyword?: string; status?: number } = { keyword, status },
  ) => {
    setLoading(true);
    try {
      const data = await OrderService.getOrdersPage({
        page: nextPage,
        size: nextSize,
        keyword: filters.keyword,
        status: filters.status,
      });
      setOrders(data.records ?? []);
      setTotal(data.total ?? 0);
      setPage(data.current ?? nextPage);
      setSize(data.size ?? nextSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '获取订单列表失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, page, size, status]);

  useEffect(() => {
    void loadOrders(1, size);
    // Initial load only; searches are explicitly submitted by the operator.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showDetail = async (order: Order) => {
    try {
      setDetail(await OrderService.getOrderDetail(order.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '获取订单详情失败');
    }
  };

  const confirmTransition = (order: Order) => {
    const target = allowedManualTarget(Number(order.status));
    if (target === null) {
      message.info('当前订单状态不允许人工变更');
      return;
    }
    const orderId = order.orderId || order.orderNo;
    Modal.confirm({
      title: target === 3 ? '确认交易完成' : '确认关单',
      content: `订单 ${orderId} 将变更为“${STATUS_LABELS[target].text}”，该操作会写入审计日志。`,
      okText: '确认',
      cancelText: '取消',
      okType: target === 5 ? 'danger' : 'primary',
      onOk: async () => {
        const changed = await OrderService.updateOrderStatus(orderId, target);
        if (!changed) throw new Error('后端未完成订单状态变更');
        message.success('订单状态已更新');
        await loadOrders(page, size);
      },
    });
  };

  const columns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 170 },
    { title: '订单类型', dataIndex: 'orderType', key: 'orderType', width: 140 },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: (value: number) => `¥${Number(value || 0).toFixed(2)}` },
    { title: '会员', dataIndex: 'member', key: 'member', width: 140 },
    { title: '套餐ID', dataIndex: 'packageId', key: 'packageId', width: 110 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 110,
      render: (value: number) => {
        const item = STATUS_LABELS[Number(value)] ?? { color: 'default', text: String(value) };
        return <Tag color={item.color}>{item.text}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    {
      title: '操作', key: 'action', width: 180,
      render: (_: unknown, order: Order) => {
        const target = allowedManualTarget(Number(order.status));
        return (
          <Space>
            <Button type="link" icon={<EyeOutlined />} onClick={() => void showDetail(order)}>查看</Button>
            <Button type="link" danger={target === 5} disabled={target === null} onClick={() => confirmTransition(order)}>
              {target === 3 ? '完成交易' : '关单'}
            </Button>
          </Space>
        );
      },
    },
  ];

  const resetFilters = () => {
    setKeyword(undefined);
    setStatus(undefined);
    setPage(1);
    void loadOrders(1, size, {});
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Card title="订单管理">
        <Space wrap style={{ marginBottom: 16 }}>
          <Input
            placeholder="订单号或会员名称"
            allowClear
            style={{ width: 300 }}
            value={keyword}
            maxLength={64}
            onChange={(event) => setKeyword(event.target.value || undefined)}
          />
          <Select placeholder="选择状态" allowClear style={{ width: 150 }} value={status} onChange={setStatus}>
            {Object.entries(STATUS_LABELS).map(([value, item]) => (
              <Option key={value} value={Number(value)}>{item.text}</Option>
            ))}
          </Select>
          <Button type="primary" icon={<SearchOutlined />} onClick={() => void loadOrders(1, size)}>搜索</Button>
          <Button icon={<ReloadOutlined />} onClick={resetFilters}>重置</Button>
        </Space>
        <Table
          columns={columns}
          dataSource={orders}
          loading={loading}
          rowKey={(order) => order.id}
          pagination={false}
          scroll={{ x: 1100 }}
        />
      </Card>

      <Pagination
        style={{ margin: 16, alignSelf: 'flex-end' }}
        total={total}
        current={page}
        pageSize={size}
        showSizeChanger
        showQuickJumper
        showTotal={(count, range) => `第 ${range[0]}-${range[1]} 条 / 共 ${count} 条`}
        onChange={(nextPage, nextSize) => void loadOrders(nextPage, nextSize)}
      />

      <Modal title="订单详情" open={detail !== null} footer={null} onCancel={() => setDetail(null)}>
        {detail && (
          <Space direction="vertical">
            <span>订单号：{detail.orderNo}</span>
            <span>会员：{detail.member || '-'}</span>
            <span>金额：¥{Number(detail.amount || 0).toFixed(2)}</span>
            <span>状态：{STATUS_LABELS[Number(detail.status)]?.text || detail.status}</span>
            <span>创建时间：{detail.createTime || '-'}</span>
          </Space>
        )}
      </Modal>
    </div>
  );
};

export default OrderManage;
