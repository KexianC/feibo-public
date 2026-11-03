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
import LogService, { OperationLog, LogPageParams } from '../../services/logService';

const { RangePicker } = DatePicker;
const { Option } = Select;
const { TextArea } = Input;

/**
 * 操作日志页面
 * 提供系统操作记录的查看、筛选、搜索功能
 */
const OperationLogComponent: React.FC = () => {
  // 表格数据状态
  const [logs, setLogs] = useState<OperationLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const loadGeneration = useRef(0);

  // 搜索条件状态
  // 搜索条件：操作者、操作方法、时间范围（接口文档要求）
  const [searchOperator, setSearchOperator] = useState<string>('');
  const [operationMethodFilter, setOperationMethodFilter] = useState<string>('');
  const [dateRange, setDateRange] = useState<[string, string] | undefined>(undefined);

  // 详情模态框状态
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<OperationLog | null>(null);

  /**
   * 加载操作日志数据
   */
  const loadOperationLogs = async (currentPage: number = page, currentSize: number = size, filters: Partial<LogPageParams> = {}) => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    try {
      // 按接口文档传参：page/size/operator/operationMethod/startTime/endTime
      const params: LogPageParams = {
        page: currentPage,
        size: currentSize,
        operator: searchOperator || undefined,
        operationMethod: operationMethodFilter || undefined,
        startTime: dateRange?.[0],
        endTime: dateRange?.[1],
        ...filters,
      };

      const response = await LogService.getOperationLogsPage(params);
      if (generation !== loadGeneration.current) return;
      setLogs(response.records);
      setTotal(response.total);
      setPage(currentPage);
      setSize(currentSize);
    } catch (error) {
      if (generation !== loadGeneration.current) return;
      message.error('加载操作日志失败: ' + (error as Error).message);
    } finally {
      if (generation === loadGeneration.current) setLoading(false);
    }
  };

  /**
   * 页面初始化时加载数据
   */
  useEffect(() => {
    loadOperationLogs();
    return () => { loadGeneration.current += 1; };
  }, []);

  /**
   * 处理搜索
   */
  const handleSearch = () => {
    loadOperationLogs(1, size);
  };

  /**
   * 重置搜索条件
   */
  const handleReset = () => {
    setSearchOperator('');
    setOperationMethodFilter('');
    setDateRange(undefined);
    loadOperationLogs(1, size, { operator: undefined, operationMethod: undefined, startTime: undefined, endTime: undefined });
  };

  /**
   * 查看日志详情
   */
  const handleViewDetail = (record: OperationLog) => {
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
    loadOperationLogs(pagination.current, pagination.pageSize);
  };

  /**
   * 处理日期范围变化
   */
  const handleDateRangeChange = (dates: any) => {
    if (dates && dates.length === 2) {
      setDateRange([
        dates[0].format('YYYY-MM-DD HH:mm:ss'),
        dates[1].format('YYYY-MM-DD HH:mm:ss')
      ]);
    } else {
      setDateRange(undefined);
    }
  };

  /**
   * 格式化参数字符串
   */
  const formatParams = (params: string) => {
    if (!params) return '-';
    try {
      // 尝试解析JSON格式的参数
      const parsed = JSON.parse(params);
      return JSON.stringify(parsed, null, 2);
    } catch (error) {
      // 如果不是JSON格式，直接返回原字符串
      return params;
    }
  };

  /**
   * 获取状态配置
   */
  const getStatusConfig = (status: number) => {
    return LogService.getStatusConfig(status, 'operation');
  };

  /**
   * 表格列定义
   */
  const columns: ColumnsType<OperationLog> = [
    {
      title: '日志ID',
      dataIndex: 'logId',
      key: 'logId',
      width: 120,
      render: (text: string) => <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>{text}</span>
    },
    {
      title: '用户',
      dataIndex: 'operator',
      key: 'operator',
      width: 120,
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
      title: '请求方法',
      dataIndex: 'operationMethod',
      key: 'operationMethod',
      width: 100,
      render: (text: string) => {
        const methodColors = {
          'GET': 'green',
          'POST': 'blue',
          'PUT': 'orange',
          'DELETE': 'red',
          'PATCH': 'purple'
        };
        const color = methodColors[text as keyof typeof methodColors] || 'default';
        return <Tag color={color}>{text}</Tag>;
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => {
        const config = getStatusConfig(status);
        return <Tag color={config.color}>{config.text}</Tag>;
      }
    },
    {
      title: 'IP地址',
      dataIndex: 'operatorIp',
      key: 'operatorIp',
      width: 140,
      render: (text: string) => <code>{text}</code>
    },
    {
      title: '登录地区',
      dataIndex: 'loginRegion',
      key: 'loginRegion',
      width: 150,
      render: (text: string) => (
        <Tooltip placement="topLeft" title={text}>
          <span>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '登录设备',
      dataIndex: 'loginDevice',
      key: 'loginDevice',
      width: 220,
      ellipsis: { showTitle: false },
      render: (text: string) => (
        <Tooltip placement="topLeft" title={text}>
          <span>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '操作描述',
      dataIndex: 'operationDesc',
      key: 'operationDesc',
      width: 300,
      ellipsis: { showTitle: false },
      render: (text: string) => (
        <Tooltip placement="topLeft" title={text}>
          <span>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '操作时间',
      dataIndex: 'operationTime',
      key: 'operationTime',
      width: 180,
      render: (text: string) => LogService.formatDateTime(text)
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record: OperationLog) => (
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
      <Card title="操作日志" style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', width: '100%' }}>
          <Space wrap>
            <Input
              placeholder="搜索操作者"
              allowClear
              style={{ width: 200 }}
              value={searchOperator}
              onChange={(e) => setSearchOperator(e.target.value)}
              prefix={<SearchOutlined />}
            />
            <Input
              placeholder="搜索操作方法"
              allowClear
              style={{ width: 200 }}
              value={operationMethodFilter}
              onChange={(e) => setOperationMethodFilter(e.target.value)}
              prefix={<SearchOutlined />}
            />
            <RangePicker
              value={dateRange ? [dayjs(dateRange[0]), dayjs(dateRange[1])] : null}
              style={{ width: 240 }}
              placeholder={["开始时间", "结束时间"]}
              onChange={handleDateRangeChange}
              format="YYYY-MM-DD HH:mm:ss"
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
          scroll={{ x: 1650 }}
        />
      </Card>

      {/* 日志详情模态框 */}
      <Modal
        title="操作日志详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={800}
      >
        {selectedLog && (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="日志ID">
              <code>{selectedLog.logId}</code>
              <Button
                type="link"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopy(selectedLog.logId)}
                style={{ marginLeft: 8 }}
              >
                复制
              </Button>
            </Descriptions.Item>
            <Descriptions.Item label="操作用户">
              <strong>{selectedLog.operator}</strong>
            </Descriptions.Item>
            <Descriptions.Item label="用户昵称">
              {selectedLog.nickName?.trim() || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="IP地址">
              <code>{selectedLog.operatorIp}</code>
              <Button
                type="link"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopy(selectedLog.operatorIp || '')}
                style={{ marginLeft: 8 }}
              >
                复制
              </Button>
            </Descriptions.Item>
            <Descriptions.Item label="登录地区">
              {selectedLog.loginRegion}
            </Descriptions.Item>
            <Descriptions.Item label="登录设备">
              <TextArea
                value={selectedLog.loginDevice}
                readOnly
                rows={3}
                style={{ width: '100%', fontSize: '12px' }}
              />
            </Descriptions.Item>
            <Descriptions.Item label="请求方法">
              <Tag color={{
                'GET': 'green',
                'POST': 'blue',
                'PUT': 'orange',
                'DELETE': 'red',
                'PATCH': 'purple'
              }[selectedLog.operationMethod || ''] || 'default'}>
                {selectedLog.operationMethod}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={getStatusConfig(selectedLog.status || 0).color}>
                {getStatusConfig(selectedLog.status || 0).text}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="操作描述">
              <TextArea
                value={selectedLog.operationDesc}
                readOnly
                rows={4}
                style={{ width: '100%', fontSize: '12px', fontFamily: 'monospace' }}
              />
            </Descriptions.Item>
            <Descriptions.Item label="操作时间">
              {LogService.formatDateTime(selectedLog.operationTime)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default OperationLogComponent;
