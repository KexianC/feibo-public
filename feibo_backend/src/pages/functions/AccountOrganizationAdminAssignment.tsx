import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import CopywritingOrganizationAdminAssignmentService, {
  type CopywritingOrganizationAdminAssignment,
  type CopywritingOrganizationAdminOption,
  type CopywritingOrganizationReviewAccountOption,
  type CopywritingOrganizationReviewAccountPage,
} from '@/services/copywritingOrganizationAdminAssignmentService';

const emptyAccountPage = (
  current = 1,
  size = 10,
): CopywritingOrganizationReviewAccountPage => ({
  records: [],
  total: 0,
  pages: 0,
  size,
  current,
});

const getAccountUserId = (
  account: CopywritingOrganizationReviewAccountOption,
): string => account.accountUserId;

const AccountOrganizationAdminAssignment: React.FC = () => {
  const [assignmentForm] = Form.useForm();
  const didInitRef = useRef(false);
  const accountRequestRef = useRef(0);
  const adminRequestRef = useRef(0);
  const adminSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<CopywritingOrganizationReviewAccountPage>(
    emptyAccountPage(),
  );
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState<string | undefined>();
  const [assignmentMap, setAssignmentMap] = useState<
    Record<string, CopywritingOrganizationAdminAssignment[]>
  >({});
  const [assignmentVisible, setAssignmentVisible] = useState(false);
  const [editingAccount, setEditingAccount] = useState<
    CopywritingOrganizationReviewAccountOption | null
  >(null);
  const [adminLoading, setAdminLoading] = useState(false);
  const [adminOptions, setAdminOptions] = useState<CopywritingOrganizationAdminOption[]>([]);
  const [assignmentSaving, setAssignmentSaving] = useState(false);

  const loadAccounts = useCallback(async (
    currentPage: number,
    currentSize: number,
    currentKeyword?: string,
  ) => {
    const requestId = ++accountRequestRef.current;
    setLoading(true);
    try {
      const accounts = await CopywritingOrganizationAdminAssignmentService.getAccounts({
        page: currentPage,
        size: currentSize,
        keyword: currentKeyword,
      });
      const records = accounts.records || [];
      const accountUserIds = records.map(getAccountUserId).filter(Boolean);
      const assignments = await CopywritingOrganizationAdminAssignmentService.query(
        accountUserIds,
      );
      if (requestId !== accountRequestRef.current) return;

      const nextAssignmentMap = assignments.reduce<
        Record<string, CopywritingOrganizationAdminAssignment[]>
      >((result, assignment) => {
        if (!result[assignment.accountUserId]) result[assignment.accountUserId] = [];
        result[assignment.accountUserId].push(assignment);
        return result;
      }, {});

      setPageData({
        records,
        total: accounts.total || 0,
        pages: accounts.pages || 0,
        size: accounts.size || currentSize,
        current: accounts.current || currentPage,
      });
      setAssignmentMap(nextAssignmentMap);
      setPage(accounts.current || currentPage);
      setSize(accounts.size || currentSize);
    } catch (error) {
      if (requestId === accountRequestRef.current) {
        message.error((error as Error)?.message || '加载机构终审管理员分配失败');
      }
    } finally {
      if (requestId === accountRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    void loadAccounts(1, 10, undefined);
  }, [loadAccounts]);

  useEffect(() => () => {
    if (adminSearchTimerRef.current) clearTimeout(adminSearchTimerRef.current);
  }, []);

  const loadAdminOptions = async (
    searchKeyword?: string,
    currentAssignments: CopywritingOrganizationAdminAssignment[] = [],
  ) => {
    const requestId = ++adminRequestRef.current;
    setAdminLoading(true);
    try {
      const options = await CopywritingOrganizationAdminAssignmentService.getAdmins(
        searchKeyword,
      );
      if (requestId !== adminRequestRef.current) return;

      const selectedIds = new Set<string>(
        (assignmentForm.getFieldValue('adminUserIds') || []).map(String),
      );
      const selectedOptions = adminOptions.filter((option) => (
        selectedIds.has(option.adminUserId)
      ));
      const currentOptions = currentAssignments.map((assignment) => ({
        adminUserId: assignment.adminUserId,
        adminNickName: assignment.adminNickName,
      }));
      const unique = new Map<string, CopywritingOrganizationAdminOption>();
      [...selectedOptions, ...currentOptions, ...options].forEach((option) => {
        if (option.adminUserId) unique.set(option.adminUserId, option);
      });
      setAdminOptions(Array.from(unique.values()));
    } catch (error) {
      if (requestId === adminRequestRef.current) {
        message.error((error as Error)?.message || '加载机构终审管理员选项失败');
      }
    } finally {
      if (requestId === adminRequestRef.current) setAdminLoading(false);
    }
  };

  const openAssignmentModal = (account: CopywritingOrganizationReviewAccountOption) => {
    const accountUserId = getAccountUserId(account);
    const currentAssignments = assignmentMap[accountUserId] || [];
    setEditingAccount(account);
    setAdminOptions(currentAssignments.map((assignment) => ({
      adminUserId: assignment.adminUserId,
      adminNickName: assignment.adminNickName,
    })));
    assignmentForm.setFieldsValue({
      adminUserIds: currentAssignments.map((assignment) => assignment.adminUserId),
    });
    setAssignmentVisible(true);
    void loadAdminOptions(undefined, currentAssignments);
  };

  const closeAssignmentModal = () => {
    adminRequestRef.current += 1;
    setAssignmentVisible(false);
    setEditingAccount(null);
    setAdminOptions([]);
    setAdminLoading(false);
    assignmentForm.resetFields();
  };

  const handleAdminSearch = (value: string) => {
    if (adminSearchTimerRef.current) clearTimeout(adminSearchTimerRef.current);
    adminSearchTimerRef.current = setTimeout(() => {
      void loadAdminOptions(value);
    }, 300);
  };

  const saveAssignments = async (adminUserIds: string[]) => {
    if (!editingAccount) return;
    const accountUserId = getAccountUserId(editingAccount);
    setAssignmentSaving(true);
    try {
      const assignments = await CopywritingOrganizationAdminAssignmentService.update(
        accountUserId,
        adminUserIds,
      );
      setAssignmentMap((current) => ({
        ...current,
        [accountUserId]: assignments,
      }));
      message.success(
        adminUserIds.length > 0
          ? '机构终审管理员配置成功'
          : '已解除该账号全部机构终审管理员分配',
      );
      closeAssignmentModal();
    } catch (error) {
      message.error((error as Error)?.message || '保存机构终审管理员失败');
      throw error;
    } finally {
      setAssignmentSaving(false);
    }
  };

  const handleAssignmentSubmit = async () => {
    const values = await assignmentForm.validateFields();
    const adminUserIds = Array.from(new Set<string>(
      (values.adminUserIds || []).map((value: unknown) => String(value)),
    ));

    if (adminUserIds.length === 0) {
      Modal.confirm({
        title: '解除全部机构终审管理员分配',
        content: '保存空列表后，该账号将没有机构终审管理员，进入终审阶段的任务将无人处理。确认继续？',
        okText: '确认解除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: () => saveAssignments([]),
      });
      return;
    }

    await saveAssignments(adminUserIds);
  };

  const handleSearch = () => {
    const nextKeyword = searchInput.trim() || undefined;
    setKeyword(nextKeyword);
    setPage(1);
    void loadAccounts(1, size, nextKeyword);
  };

  const handleReset = () => {
    setSearchInput('');
    setKeyword(undefined);
    setPage(1);
    void loadAccounts(1, size, undefined);
  };

  const columns: ColumnsType<CopywritingOrganizationReviewAccountOption> = [
    {
      title: '账号',
      dataIndex: 'nickName',
      key: 'nickName',
      width: 180,
      render: (value?: string) => value || '未命名账号',
    },
    {
      title: '用户ID',
      key: 'userId',
      width: 190,
      render: (_, record) => getAccountUserId(record),
    },
    {
      title: '手机号',
      dataIndex: 'phoneNumber',
      key: 'phoneNumber',
      width: 150,
      render: (value?: string) => value || '-',
    },
    {
      title: '注册时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '账号状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (disabled?: boolean) => (
        <Tag color={disabled ? 'red' : 'green'}>{disabled ? '已禁用' : '正常'}</Tag>
      ),
    },
    {
      title: '已分配机构终审管理员',
      key: 'admins',
      render: (_, record) => {
        const assignments = assignmentMap[getAccountUserId(record)] || [];
        if (assignments.length === 0) return <span style={{ color: '#999' }}>未分配</span>;
        return (
          <Space size={[0, 4]} wrap>
            {assignments.map((assignment) => (
              <Tag key={assignment.id || assignment.adminUserId} color="purple">
                {assignment.adminNickName || assignment.adminUserId}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 140,
      render: (_, record) => (
        <Button type="link" onClick={() => openAssignmentModal(record)}>
          配置终审管理员
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="机构终审管理员分配"
      extra={<Button onClick={() => loadAccounts(page, size, keyword)}>刷新</Button>}
    >
      <Alert
        type="info"
        showIcon
        message="按提交账号分配所属医院/机构终审管理员"
        description="每个账号可绑定多位机构终审管理员。文案完成专家审核后，仅对应管理员可在智核医审中执行终审。"
        style={{ marginBottom: 16 }}
      />

      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          value={searchInput}
          placeholder="按账号昵称或手机号搜索"
          onChange={(event) => setSearchInput(event.target.value)}
          onSearch={handleSearch}
          style={{ width: 280 }}
        />
        <Button type="primary" onClick={handleSearch}>搜索</Button>
        <Button onClick={handleReset}>重置</Button>
      </Space>

      <Table<CopywritingOrganizationReviewAccountOption>
        rowKey={(record) => getAccountUserId(record)}
        columns={columns}
        dataSource={pageData.records}
        loading={loading}
        pagination={false}
        scroll={{ x: 1050 }}
      />
      <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
        <Pagination
          total={pageData.total}
          current={pageData.current}
          pageSize={pageData.size}
          showSizeChanger
          showQuickJumper
          showTotal={(total) => `共 ${total} 个账号`}
          onChange={(nextPage, nextSize) => {
            const resolvedSize = nextSize || size;
            setPage(nextPage);
            setSize(resolvedSize);
            void loadAccounts(nextPage, resolvedSize, keyword);
          }}
        />
      </div>

      <Modal
        open={assignmentVisible}
        title={`配置机构终审管理员 · ${editingAccount?.nickName || '未命名账号'}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={assignmentSaving}
        onOk={handleAssignmentSubmit}
        onCancel={closeAssignmentModal}
        destroyOnClose
      >
        <Form form={assignmentForm} layout="vertical">
          <Form.Item
            name="adminUserIds"
            label="机构终审管理员"
            extra="支持选择多位管理员；清空后保存需再次确认，并会解除该账号的全部终审管理员分配。"
          >
            <Select
              mode="multiple"
              showSearch
              allowClear
              filterOption={false}
              loading={adminLoading}
              placeholder="输入管理员昵称搜索"
              onSearch={handleAdminSearch}
              maxTagCount="responsive"
              options={adminOptions.map((admin) => ({
                value: admin.adminUserId,
                label: admin.phoneNumber
                  ? `${admin.adminNickName || admin.adminUserId} · ${admin.phoneNumber}`
                  : admin.adminNickName || admin.adminUserId,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AccountOrganizationAdminAssignment;
