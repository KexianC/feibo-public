import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Select,
  DatePicker,
  Tag,
  message,
  Tooltip,
  Descriptions,
  Modal
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  CopyOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import dayjs from 'dayjs';
import LogService, { LoginLog, LogPageParams } from '../../services/logService';

const { RangePicker } = DatePicker;
const { Option } = Select;

/**
 * 登录日志页面
 * 提供用户登录记录的查看、筛选、搜索功能
 */
const LoginLogComponent: React.FC = () => {
  // 表格数据状态
  const [logs, setLogs] = useState<LoginLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const loadGeneration = useRef(0);

  // 搜索条件状态
  const [searchUsername, setSearchUsername] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [dateRange, setDateRange] = useState<[string, string] | undefined>(undefined);

  // 详情模态框状态
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<LoginLog | null>(null);

  /**
   * 加载登录日志数据
   */
  const loadLoginLogs = async (currentPage: number = page, currentSize: number = size, filters: Partial<LogPageParams> = {}) => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    try {
      const params: LogPageParams = {
        page: currentPage,
        size: currentSize,
        username: searchUsername || undefined,
        status: statusFilter,
        startDate: dateRange?.[0],
        endDate: dateRange?.[1],
        ...filters,
      };

      const response = await LogService.getLoginLogsPage(params);
      if (generation !== loadGeneration.current) return;
      setLogs(response.records);
      setTotal(response.total);
      setPage(currentPage);
      setSize(currentSize);
    } catch (error) {
      if (generation !== loadGeneration.current) return;
      message.error('加载登录日志失败: ' + (error as Error).message);
    } finally {
      if (generation === loadGeneration.current) setLoading(false);
    }
  };

  /**
   * 页面初始化时加载数据
   */
  useEffect(() => {
    loadLoginLogs();
    return () => { loadGeneration.current += 1; };
  }, []);

  /**
   * 处理搜索
   */
  const handleSearch = () => {
    loadLoginLogs(1, size);
  };

  /**
   * 重置搜索条件
   */
  const handleReset = () => {
    setSearchUsername('');
    setStatusFilter(undefined);
    setDateRange(undefined);
    loadLoginLogs(1, size, { username: undefined, status: undefined, startDate: undefined, endDate: undefined });
  };

  /**
   * 查看日志详情
   */
  const handleViewDetail = (record: LoginLog) => {
    setSelectedLog(record);
    setDetailModalVisible(true);
  };

  /**
   * 复制到剪贴板
   */
  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success('复制成功');
    } catch (error) {
      message.error('复制失败');
    }
  };

  /**
   * 处理表格分页变化
   */
  const handleTableChange = (pagination: any) => {
    loadLoginLogs(pagination.current, pagination.pageSize);
  };

  /**
   * 处理日期范围变化
   */
  const handleDateRangeChange = (dates: any) => {
    if (dates && dates.length === 2) {
      setDateRange([
        dates[0].format('YYYY-MM-DD'),
        dates[1].format('YYYY-MM-DD')
      ]);
    } else {
      setDateRange(undefined);
    }
  };

  /**
   * 获取状态配置
   */
  const getStatusConfig = (status: number) => {
    return LogService.getStatusConfig(status, 'login');
  };

  /**
   * 获取登录类型标签
   */
  const getLoginTypeTag = (loginType: number) => {
    const typeMap = {
      1: { text: '密码登录', color: 'blue' },
      2: { text: '验证码登录', color: 'green' },
      3: { text: '第三方登录', color: 'orange' },
      4: { text: '扫码登录', color: 'purple' }
    };
    const config = typeMap[loginType as keyof typeof typeMap] || { text: '未知', color: 'default' };
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  /**
   * 表格列定义（对齐后端登录日志返回字段）
   */
  const columns: ColumnsType<LoginLog> = [
    {
      title: '日志ID',
      dataIndex: 'logId',
      key: 'logId',
      width: 120,
      render: (text: string) => <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>{text}</span>
    },
    {
      title: '登录用户',
      dataIndex: 'loginUser',
      key: 'loginUser',
      width: 140,
      render: (text: string) => <strong>{text}</strong>
    },
    {
      title: '用户昵称',
      dataIndex: 'nickName',
      key: 'nickName',
      width: 140,
      render: (text?: string) => <span>{text?.trim() || '-'}</span>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: number) => {
        const config = getStatusConfig(status);
        return <Tag color={config.color}>{config.text}</Tag>;
      }
    },
    {
      title: 'IP地址',
      dataIndex: 'loginIp',
      key: 'loginIp',
      width: 140,
      render: (text: string) => <code>{text}</code>
    },
    {
      title: '登录地区',
      dataIndex: 'loginRegion',
      key: 'loginRegion',
      width: 160,
      ellipsis: { showTitle: true },
      render: (text: string) => <span>{text || '-'}</span>
    },
    {
      title: '登录设备',
      dataIndex: 'loginUserAgent',
      key: 'loginUserAgent',
      width: 240,
      ellipsis: {
        showTitle: false,
      },
      render: (text: string) => (
        <Tooltip placement="topLeft" title={text}>
          <span>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      key: 'loginTime',
      width: 180,
      render: (text: string) => LogService.formatDateTime(text)
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record: LoginLog) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card title="登录日志" style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input
              placeholder="搜索登录用户"
              allowClear
              style={{ width: 200 }}
              value={searchUsername}
              onChange={(e) => setSearchUsername(e.target.value)}
              prefix={<SearchOutlined />}
            />
            <Select
              placeholder="选择状态"
              allowClear
              style={{ width: 120 }}
              value={statusFilter}
              onChange={setStatusFilter}
            >
              <Option value={1}>成功</Option>
              <Option value={2}>失败</Option>
            </Select>
            <RangePicker
              value={dateRange ? [dayjs(dateRange[0]), dayjs(dateRange[1])] : null}
              style={{ width: 240 }}
              placeholder={['开始日期', '结束日期']}
              onChange={handleDateRangeChange}
              format="YYYY-MM-DD"
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Space>

        <Table
          columns={columns}
          dataSource={logs}
          rowKey="logId"
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
          scroll={{ x: 1300 }}
        />
      </Card>

      {/* 日志详情模态框 */}
      <Modal
        title="登录日志详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={700}
      >
        {selectedLog && (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="日志ID">
              <code>{selectedLog.logId}</code>
              <Button
                type="link"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopy(selectedLog.logId!)}
                style={{ marginLeft: 8 }}
              >
                复制
              </Button>
            </Descriptions.Item>
            <Descriptions.Item label="登录用户">
              <strong>{selectedLog.loginUser}</strong>
            </Descriptions.Item>
            <Descriptions.Item label="用户昵称">
              {selectedLog.nickName?.trim() || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={getStatusConfig(selectedLog.status).color}>
                {getStatusConfig(selectedLog.status).text}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="IP地址">
              <code>{selectedLog.loginIp}</code>
              <Button
                type="link"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopy(selectedLog.loginIp!)}
                style={{ marginLeft: 8 }}
              >
                复制
              </Button>
            </Descriptions.Item>
            <Descriptions.Item label="登录地区">
              <span style={{ fontSize: '12px' }}>{selectedLog.loginRegion || '-'}</span>
            </Descriptions.Item>
            <Descriptions.Item label="登录设备">
              <span style={{ fontSize: '12px', wordBreak: 'break-all' }}>
                {selectedLog.loginUserAgent}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="登录时间">
              {LogService.formatDateTime(selectedLog.loginTime)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default LoginLogComponent;
