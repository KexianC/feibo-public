import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  message,
  Modal,
  Pagination,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  CopyOutlined,
  DownloadOutlined,
  EyeOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  digitalWorksService,
  type DigitalWork,
  type PageQueryParams,
  type PendingDigitalQuota,
} from '../../services/digitalWorksService';
import { AuthService } from '@/services/auth';
import ResolveFailedVideoModal from '@/components/ResolveFailedVideoModal';

const { Search } = Input;
const { Text, Title } = Typography;

const formatComposeParams = (params: string | null | undefined): string => {
  if (!params) {
    return '暂无合成接口参数';
  }

  try {
    return JSON.stringify(JSON.parse(params), null, 2);
  } catch {
    return params;
  }
};

const captureSourceLabel: Record<DigitalWork['capture_source'], string> = {
  legacy_body: '历史作品参数',
  provider_request: '服务端供应商请求',
};

const statusTag = (status: string | null | undefined) => {
  const text = status || '未知';
  const normalized = text.toLowerCase();
  let color = 'default';

  if (text.includes('成功') || ['completed', 'suc', 'success'].includes(normalized)) {
    color = 'green';
  } else if (text.includes('失败') || ['failed', 'fail'].includes(normalized)) {
    color = 'red';
  } else if (text.includes('处理中') || ['processing', 'pending', 'ing'].includes(normalized)) {
    color = 'blue';
  }

  return <Tag color={color}>{text}</Tag>;
};

const SynthesisRecords: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<DigitalWork[]>([]);
  const [selectedRecord, setSelectedRecord] = useState<DigitalWork | null>(null);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [pendingQuota, setPendingQuota] = useState<PendingDigitalQuota[]>([]);
  const [pendingError, setPendingError] = useState('');
  const [resolvingWorkId, setResolvingWorkId] = useState<string>();
  const [refreshVersion, setRefreshVersion] = useState(0);
  const canResolve = AuthService.isSuperAdmin();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [searchParams, setSearchParams] = useState<PageQueryParams>({
    pageNum: 1,
    pageSize: 10,
  });
  const tableWrapperRef = useRef<HTMLDivElement>(null);
  const paginationRef = useRef<HTMLDivElement>(null);
  const [tableScrollY, setTableScrollY] = useState(400);

  const computeTableLayout = () => {
    const viewportHeight = window.innerHeight;
    const tableTop = tableWrapperRef.current?.getBoundingClientRect().top ?? 0;
    const paginationHeight = paginationRef.current?.offsetHeight ?? 0;
    const availableHeight = Math.max(
      240,
      Math.floor(viewportHeight - tableTop - paginationHeight - 32),
    );

    setTableScrollY(availableHeight);

    const pageSize = Math.max(5, Math.floor((availableHeight - 54) / 54));
    setSearchParams((previous) => {
      if (previous.pageSize === pageSize) {
        return previous;
      }
      return { ...previous, pageNum: 1, pageSize };
    });
  };

  useLayoutEffect(() => {
    const handleResize = () => computeTableLayout();
    window.addEventListener('resize', handleResize);
    computeTableLayout();
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    let current = true;
    const fetchDigitalWorks = async () => {
      try {
        setLoading(true);
        setPendingError('');
        const [pageResult, pendingResult] = await Promise.allSettled([
          digitalWorksService.getDigitalWorksPage(searchParams),
          canResolve ? digitalWorksService.getPendingQuota() : Promise.resolve([] as PendingDigitalQuota[]),
        ]);
        if (!current) return;
        if (pendingResult.status === 'fulfilled') setPendingQuota(pendingResult.value);
        else setPendingError(`获取待核销预占任务失败：${pendingResult.reason instanceof Error ? pendingResult.reason.message : '请求失败'}`);
        if (pageResult.status === 'rejected') throw pageResult.reason;
        const response = pageResult.value;
        setRecords(response.records || []);
        setPagination({
          current: response.current,
          pageSize: response.size,
          total: response.total,
        });
      } catch (error) {
        if (current) message.error(`获取合成审计记录失败：${(error as Error).message}`);
      } finally {
        if (current) setLoading(false);
      }
    };

    fetchDigitalWorks();
    return () => { current = false; };
  }, [searchParams, refreshVersion, canResolve]);

  const handleSearch = (value: string) => {
    const keyword = value.trim();
    setSearchParams((previous) => ({
      ...previous,
      pageNum: 1,
      keyword: keyword || undefined,
    }));
  };

  const handlePageChange = (pageNum: number, pageSize: number) => {
    setSearchParams((previous) => ({
      ...previous,
      pageNum,
      pageSize,
    }));
  };

  const handleView = (record: DigitalWork) => {
    setSelectedRecord(record);
    setIsModalVisible(true);
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    setSelectedRecord(null);
  };

  const handleDownload = (record: DigitalWork) => {
    if (!record.url) {
      message.error('文件链接不存在');
      return;
    }

    const link = document.createElement('a');
    link.href = record.url;
    link.download = record.title || `合成作品_${record.audit_id}`;
    link.target = '_blank';
    link.rel = 'noreferrer';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const copyComposeParams = async () => {
    if (!selectedRecord?.compose_params) {
      message.warning('暂无可复制的合成接口参数');
      return;
    }

    const text = formatComposeParams(selectedRecord.compose_params);
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      message.success('合成接口参数已复制');
    } catch {
      message.error('复制失败，请手动选择参数文本');
    }
  };

  const columns: ColumnsType<DigitalWork> = [
    {
      title: '审计ID',
      dataIndex: 'audit_id',
      key: 'audit_id',
      width: 100,
    },
    {
      title: '外部任务ID',
      dataIndex: 'external_id',
      key: 'external_id',
      width: 220,
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      width: 120,
      render: (value: string) => value || '未知用户',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 140,
      render: (value: string) => value || '-',
    },
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      width: 220,
      ellipsis: true,
      render: (value: string | null) => value || '未命名作品',
    },
    {
      title: '费用',
      dataIndex: 'bill',
      key: 'bill',
      width: 100,
      render: (bill: number | null) => (bill == null ? '-' : `¥${bill.toFixed(2)}`),
    },
    {
      title: '时长(秒)',
      dataIndex: 'duration',
      key: 'duration',
      width: 100,
      render: (duration: number | null) => duration ?? '-',
    },
    {
      title: '合成状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: statusTag,
    },
    {
      title: '失败原因',
      dataIndex: 'failure_reason',
      key: 'failure_reason',
      width: 240,
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: '审计状态',
      dataIndex: 'archived_only',
      key: 'archived_only',
      width: 110,
      render: (archivedOnly: boolean) =>
        archivedOnly ? <Tag color="gold">已归档</Tag> : <Tag color="green">作品可用</Tag>,
    },
    {
      title: '提交时间',
      dataIndex: 'submitted_at',
      key: 'submitted_at',
      width: 180,
      render: (value: string, record) => value || record.create_time || '-',
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: canResolve ? 280 : 150,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" icon={<EyeOutlined />} onClick={() => handleView(record)}>
            查看
          </Button>
          {record.url && (
            <Button type="link" icon={<DownloadOutlined />} onClick={() => handleDownload(record)}>
              下载
            </Button>
          )}
          {canResolve && !record.archived_only && record.status === '处理中' && !record.url && (
            <Button type="link" onClick={() => setResolvingWorkId(record.work_record_id)}>确认失败并退还额度</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      {canResolve && <Card title="待核销预占任务" style={{ marginBottom: 16 }}
        extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshVersion(value => value + 1)}>刷新任务</Button>}>
        <Alert type="info" showIcon style={{ marginBottom: 12 }} message="提交状态不明确的任务会继续预占额度。请核实供应商已经失败或未受理后，再核销并退还额度。" />
        {pendingError && <Alert type="error" showIcon message={pendingError} style={{ marginBottom: 12 }} />}
        <Table size="small" rowKey="work_record_id" dataSource={pendingQuota} loading={loading} pagination={{ pageSize: 5 }} scroll={{ x: 900 }} columns={[
          { title: '作品编号', dataIndex: 'work_record_id', width: 190 },
          { title: '账号 ID', dataIndex: 'user_id', width: 180 },
          { title: '作品标题', dataIndex: 'title', ellipsis: true },
          { title: '外部任务编号', dataIndex: 'external_id', ellipsis: true },
          { title: '创建时间', dataIndex: 'created_at', width: 180 },
          { title: '操作', key: 'resolve', width: 175, render: (_, record) => <Button type="link" onClick={() => setResolvingWorkId(record.work_record_id)}>确认失败并退还额度</Button> },
        ]} />
      </Card>}
      <Card>
        <div style={{ marginBottom: 16 }}>
          <Search
            placeholder="搜索任务ID、标题、用户或手机号"
            allowClear
            enterButton={<SearchOutlined />}
            style={{ width: 360 }}
            onSearch={handleSearch}
          />
        </div>

        <div ref={tableWrapperRef}>
          <Table
            columns={columns}
            dataSource={records}
            loading={loading}
            rowKey="audit_id"
            pagination={false}
            scroll={{ x: 1550, y: tableScrollY }}
          />
        </div>

        <div
          ref={paginationRef}
          style={{ padding: 8, display: 'flex', justifyContent: 'flex-end' }}
        >
          <Pagination
            total={pagination.total}
            current={pagination.current}
            pageSize={searchParams.pageSize}
            showSizeChanger
            showQuickJumper
            showTotal={(total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`}
            onChange={handlePageChange}
          />
        </div>
      </Card>

      <Modal
        title="合成记录详情"
        open={isModalVisible}
        onCancel={handleModalCancel}
        width={960}
        footer={[
          <Button key="close" onClick={handleModalCancel}>
            关闭
          </Button>,
          selectedRecord?.url ? (
            <Button
              key="download"
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleDownload(selectedRecord)}
            >
              下载文件
            </Button>
          ) : null,
        ]}
      >
        {selectedRecord && (
          <div>
            <Descriptions bordered size="small" column={2} labelStyle={{ width: 140 }}>
              <Descriptions.Item label="审计记录ID">
                {selectedRecord.audit_id}
              </Descriptions.Item>
              <Descriptions.Item label="归档状态">
                {selectedRecord.archived_only ? (
                  <Tag color="gold">已归档</Tag>
                ) : (
                  <Tag color="green">作品可用</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="外部任务ID" span={2}>
                <Text copyable={Boolean(selectedRecord.external_id)}>
                  {selectedRecord.external_id || '-'}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="标题" span={2}>
                {selectedRecord.title || '未命名作品'}
              </Descriptions.Item>
              <Descriptions.Item label="用户">
                {selectedRecord.username || '未知用户'}
              </Descriptions.Item>
              <Descriptions.Item label="手机号">
                {selectedRecord.phone || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="费用">
                {selectedRecord.bill == null ? '-' : `¥${selectedRecord.bill.toFixed(2)}`}
              </Descriptions.Item>
              <Descriptions.Item label="成片时长">
                {selectedRecord.duration == null ? '-' : `${selectedRecord.duration} 秒`}
              </Descriptions.Item>
              <Descriptions.Item label="合成状态">
                {statusTag(selectedRecord.status)}
              </Descriptions.Item>
              <Descriptions.Item label="失败原因">
                {selectedRecord.failure_reason || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="videoTime">
                {selectedRecord.video_time == null
                  ? '历史数据未记录'
                  : `${selectedRecord.video_time} 分钟`}
              </Descriptions.Item>
              <Descriptions.Item label="作品创建时间">
                {selectedRecord.create_time || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="审计提交时间">
                {selectedRecord.submitted_at || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="capture_source" span={2}>
                <Space>
                  <Tag color={selectedRecord.capture_source === 'provider_request' ? 'blue' : 'default'}>
                    {captureSourceLabel[selectedRecord.capture_source] || selectedRecord.capture_source}
                  </Tag>
                  <Text code>{selectedRecord.capture_source}</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="请求 SHA-256" span={2}>
                <Text code copyable={Boolean(selectedRecord.request_sha256)}>
                  {selectedRecord.request_sha256 || '-'}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="资源链接" span={2}>
                {selectedRecord.url ? (
                  <a href={selectedRecord.url} target="_blank" rel="noreferrer">
                    打开合成资源
                  </a>
                ) : (
                  '暂无资源链接'
                )}
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 24 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 12,
                }}
              >
                <Space>
                  <Title level={5} style={{ margin: 0 }}>
                    合成接口参数（永久审计）
                  </Title>
                  <Tag color="purple">不可随作品删除</Tag>
                </Space>
                <Button
                  icon={<CopyOutlined />}
                  onClick={copyComposeParams}
                  disabled={!selectedRecord.compose_params}
                >
                  复制参数
                </Button>
              </div>
              <pre
                style={{
                  maxHeight: 420,
                  minHeight: 120,
                  overflow: 'auto',
                  margin: 0,
                  padding: 16,
                  borderRadius: 6,
                  background: '#0f172a',
                  color: '#e2e8f0',
                  fontSize: 12,
                  lineHeight: 1.6,
                  whiteSpace: 'pre',
                  tabSize: 2,
                }}
              >
                {formatComposeParams(selectedRecord.compose_params)}
              </pre>
            </div>
          </div>
        )}
      </Modal>
      <ResolveFailedVideoModal open={Boolean(resolvingWorkId)} taskId={resolvingWorkId}
        onCancel={() => setResolvingWorkId(undefined)}
        onResolve={async input => {
          if (!resolvingWorkId) throw new Error('未选择任务');
          await digitalWorksService.resolveFailed(resolvingWorkId, input);
        }}
        onResolved={() => { setResolvingWorkId(undefined); setIsModalVisible(false); setSelectedRecord(null); setRefreshVersion(value => value + 1); }} />
    </div>
  );
};

export default SynthesisRecords;
