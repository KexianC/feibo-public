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
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import CopywritingExpertAssignmentService, {
  type CopywritingExpertAssignment,
  type CopywritingExpertOption,
  type CopywritingReviewAccountOption,
  type CopywritingReviewAccountPage,
} from '@/services/copywritingExpertAssignmentService';
import RoleService, { type Role } from '@/services/roleService';
import UserRoleMapService, {
  type PageResponse,
  type UserRoleMap,
} from '@/services/userRoleMapService';
import UserService, { type User } from '@/services/userService';
import AccountOrganizationAdminAssignment from './AccountOrganizationAdminAssignment';

const EXPERT_ROLE_NAME = 'EXPERT';
const emptyPage = (current = 1, size = 10): PageResponse<UserRoleMap> => ({
  records: [],
  total: 0,
  pages: 0,
  size,
  current,
});

const emptyAccountPage = (current = 1, size = 10): CopywritingReviewAccountPage => ({
  records: [],
  total: 0,
  pages: 0,
  size,
  current,
});

const getAccountUserId = (account: CopywritingReviewAccountOption): string => account.accountUserId;

const AccountExpertAssignment: React.FC = () => {
  const [assignmentForm] = Form.useForm();
  const didInitRef = useRef(false);
  const expertSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const expertRequestRef = useRef(0);
  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<CopywritingReviewAccountPage>(emptyAccountPage());
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState<string | undefined>();
  const [assignmentMap, setAssignmentMap] = useState<Record<string, CopywritingExpertAssignment[]>>({});
  const [assignmentVisible, setAssignmentVisible] = useState(false);
  const [editingAccount, setEditingAccount] = useState<CopywritingReviewAccountOption | null>(null);
  const [expertLoading, setExpertLoading] = useState(false);
  const [expertOptions, setExpertOptions] = useState<CopywritingExpertOption[]>([]);
  const [assignmentSaving, setAssignmentSaving] = useState(false);

  const loadAccounts = useCallback(async (
    currentPage: number,
    currentSize: number,
    currentKeyword?: string,
  ) => {
    setLoading(true);
    try {
      const accounts = await CopywritingExpertAssignmentService.getAccounts({
        page: currentPage,
        size: currentSize,
        keyword: currentKeyword,
      });
      const records = accounts.records || [];
      const accountUserIds = records.map(getAccountUserId).filter(Boolean);
      const assignments = await CopywritingExpertAssignmentService.query(accountUserIds);
      const nextAssignmentMap = assignments.reduce<Record<string, CopywritingExpertAssignment[]>>(
        (result, assignment) => {
          if (!result[assignment.accountUserId]) result[assignment.accountUserId] = [];
          result[assignment.accountUserId].push(assignment);
          return result;
        },
        {},
      );

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
      message.error((error as Error)?.message || '加载账号专家分配失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    void loadAccounts(1, 10, undefined);
  }, [loadAccounts]);

  useEffect(() => () => {
    if (expertSearchTimerRef.current) clearTimeout(expertSearchTimerRef.current);
  }, []);

  const loadExpertOptions = async (
    searchKeyword?: string,
    currentAssignments: CopywritingExpertAssignment[] = [],
  ) => {
    const requestId = ++expertRequestRef.current;
    setExpertLoading(true);
    try {
      const options = await CopywritingExpertAssignmentService.getExperts(searchKeyword);
      if (requestId !== expertRequestRef.current) return;

      const selectedIds = new Set<string>(
        (assignmentForm.getFieldValue('expertUserIds') || []).map(String),
      );
      const selectedOptions = expertOptions.filter((option) => selectedIds.has(option.expertUserId));
      const currentOptions = currentAssignments.map((assignment) => ({
        expertUserId: assignment.expertUserId,
        expertNickName: assignment.expertNickName,
      }));
      const unique = new Map<string, CopywritingExpertOption>();
      [...selectedOptions, ...currentOptions, ...options].forEach((option) => {
        if (option.expertUserId) unique.set(option.expertUserId, option);
      });
      setExpertOptions(Array.from(unique.values()));
    } catch (error) {
      if (requestId === expertRequestRef.current) {
        message.error((error as Error)?.message || '加载专家选项失败');
      }
    } finally {
      if (requestId === expertRequestRef.current) setExpertLoading(false);
    }
  };

  const openAssignmentModal = (account: CopywritingReviewAccountOption) => {
    const accountUserId = getAccountUserId(account);
    const currentAssignments = assignmentMap[accountUserId] || [];
    setEditingAccount(account);
    setExpertOptions(currentAssignments.map((assignment) => ({
      expertUserId: assignment.expertUserId,
      expertNickName: assignment.expertNickName,
    })));
    assignmentForm.setFieldsValue({
      expertUserIds: currentAssignments.map((assignment) => assignment.expertUserId),
    });
    setAssignmentVisible(true);
    void loadExpertOptions(undefined, currentAssignments);
  };

  const handleExpertSearch = (value: string) => {
    if (expertSearchTimerRef.current) clearTimeout(expertSearchTimerRef.current);
    expertSearchTimerRef.current = setTimeout(() => {
      void loadExpertOptions(value);
    }, 300);
  };

  const saveAssignments = async (expertUserIds: string[]) => {
    if (!editingAccount) return;
    const accountUserId = getAccountUserId(editingAccount);
    setAssignmentSaving(true);
    try {
      const assignments = await CopywritingExpertAssignmentService.update(
        accountUserId,
        expertUserIds,
      );
      setAssignmentMap((current) => ({
        ...current,
        [accountUserId]: assignments,
      }));
      message.success(expertUserIds.length > 0 ? '账号审核专家配置成功' : '已解除该账号全部专家分配');
      setAssignmentVisible(false);
      setEditingAccount(null);
      setExpertOptions([]);
      assignmentForm.resetFields();
    } catch (error) {
      message.error((error as Error)?.message || '保存账号审核专家失败');
      throw error;
    } finally {
      setAssignmentSaving(false);
    }
  };

  const handleAssignmentSubmit = async () => {
    const values = await assignmentForm.validateFields();
    const expertUserIds = Array.from(new Set<string>(
      (values.expertUserIds || []).map((value: unknown) => String(value)),
    ));

    if (expertUserIds.length === 0) {
      Modal.confirm({
        title: '解除全部专家分配',
        content: '保存空列表后，该账号将不再绑定任何审核专家。确认继续？',
        okText: '确认解除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: () => saveAssignments([]),
      });
      return;
    }

    await saveAssignments(expertUserIds);
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

  const columns: ColumnsType<CopywritingReviewAccountOption> = [
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
      title: '已分配审核专家',
      key: 'experts',
      render: (_, record) => {
        const assignments = assignmentMap[getAccountUserId(record)] || [];
        if (assignments.length === 0) return <span style={{ color: '#999' }}>未分配</span>;
        return (
          <Space size={[0, 4]} wrap>
            {assignments.map((assignment) => (
              <Tag key={assignment.id || assignment.expertUserId} color="blue">
                {assignment.expertNickName || assignment.expertUserId}
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
      width: 120,
      render: (_, record) => (
        <Button type="link" onClick={() => openAssignmentModal(record)}>
          配置专家
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="账号专家分配"
      extra={<Button onClick={() => loadAccounts(page, size, keyword)}>刷新</Button>}
    >
      <Alert
        type="info"
        showIcon
        message="按提交账号分配审核专家"
        description="每个提交账号可绑定多位审核专家，同一位专家也可服务多个账号。"
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

      <Table<CopywritingReviewAccountOption>
        rowKey={(record) => getAccountUserId(record)}
        columns={columns}
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
        title={`配置审核专家 · ${editingAccount?.nickName || '未命名账号'}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={assignmentSaving}
        onOk={handleAssignmentSubmit}
        onCancel={() => {
          setAssignmentVisible(false);
          setEditingAccount(null);
          setExpertOptions([]);
          assignmentForm.resetFields();
        }}
        destroyOnClose
      >
        <Form form={assignmentForm} layout="vertical">
          <Form.Item
            name="expertUserIds"
            label="审核专家"
            extra="支持选择多位专家；清空后保存可解除该账号的全部专家分配。"
          >
            <Select
              mode="multiple"
              showSearch
              allowClear
              filterOption={false}
              loading={expertLoading}
              placeholder="输入专家昵称搜索"
              onSearch={handleExpertSearch}
              maxTagCount="responsive"
              options={expertOptions.map((expert) => ({
                value: expert.expertUserId,
                label: expert.phoneNumber
                  ? `${expert.expertNickName || expert.expertUserId} · ${expert.phoneNumber}`
                  : expert.expertNickName || expert.expertUserId,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

const ExpertAccountRoleManagement: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [addForm] = Form.useForm();
  const didInitRef = useRef(false);
  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<UserRoleMap>>(emptyPage());
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [nickName, setNickName] = useState<string | undefined>();
  const [expertRole, setExpertRole] = useState<Role | null>(null);
  const [addVisible, setAddVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [candidateUsers, setCandidateUsers] = useState<User[]>([]);

  const loadData = async (
    currentPage = page,
    currentSize = size,
    currentNickName = nickName,
    role = expertRole,
  ) => {
    if (!role) {
      setPageData(emptyPage(currentPage, currentSize));
      return;
    }

    setLoading(true);
    try {
      const data = await UserRoleMapService.getPage({
        pageNum: currentPage,
        pagesize: currentSize,
        nickName: currentNickName,
        roleName: role.name,
      });
      setPageData(data);
      setPage(data.current || currentPage);
      setSize(data.size || currentSize);
    } catch (error) {
      message.error((error as Error)?.message || '加载审核专家失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;

    void (async () => {
      try {
        const roles = await RoleService.getAllRoles();
        const role = roles.find(
          (item) => item.name?.trim().toUpperCase() === EXPERT_ROLE_NAME,
        ) ?? null;
        setExpertRole(role);
        if (!role) {
          message.error('系统中未找到 EXPERT 角色，请先在账户角色中创建');
          return;
        }
        await loadData(1, size, undefined, role);
      } catch (error) {
        message.error((error as Error)?.message || '加载专家角色失败');
      }
    })();
  }, []);

  const handleSearch = () => {
    const nextNickName = searchForm.getFieldValue('nickName')?.trim() || undefined;
    setNickName(nextNickName);
    setPage(1);
    void loadData(1, size, nextNickName);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setNickName(undefined);
    setPage(1);
    void loadData(1, size, undefined);
  };

  const loadCandidateUsers = async (keyword?: string) => {
    setCandidateLoading(true);
    try {
      const data = await UserService.getUsersPage({
        page: 1,
        size: 20,
        keyword: keyword?.trim() || undefined,
      });
      setCandidateUsers(data.records || []);
    } catch (error) {
      setCandidateUsers([]);
      message.error((error as Error)?.message || '加载用户列表失败');
    } finally {
      setCandidateLoading(false);
    }
  };

  const openAddModal = () => {
    if (!expertRole) return;
    addForm.resetFields();
    setAddVisible(true);
    void loadCandidateUsers();
  };

  const handleAdd = async () => {
    if (!expertRole) return;
    const values = await addForm.validateFields();
    setSaving(true);
    try {
      await UserRoleMapService.create({
        userId: String(values.userId),
        roleId: String(expertRole.roleId || expertRole.id),
      });
      message.success('审核专家添加成功');
      setAddVisible(false);
      addForm.resetFields();
      await loadData(1, size);
    } catch (error) {
      message.error((error as Error)?.message || '添加失败，该用户可能已是审核专家');
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (record: UserRoleMap) => {
    try {
      await UserRoleMapService.deleteById(record.id);
      message.success('审核专家已移除');
      const nextPage = pageData.records.length === 1 && page > 1 ? page - 1 : page;
      setPage(nextPage);
      await loadData(nextPage, size);
    } catch (error) {
      message.error((error as Error)?.message || '移除审核专家失败');
    }
  };

  const columns: ColumnsType<UserRoleMap> = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 190 },
    {
      title: '专家姓名',
      dataIndex: 'username',
      key: 'username',
      width: 180,
      render: (value?: string) => value || '-',
    },
    {
      title: '审核角色',
      dataIndex: 'roleName',
      key: 'roleName',
      width: 140,
      render: (value?: string) => <Tag color="blue">{value || EXPERT_ROLE_NAME}</Tag>,
    },
    {
      title: '配置时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 120,
      render: (_, record) => (
        <Popconfirm
          title="移除审核专家"
          description="移除后，该账号将不再拥有 EXPERT 角色。确认继续？"
          okText="确认移除"
          cancelText="取消"
          onConfirm={() => handleRemove(record)}
        >
          <Button type="link" danger>移除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title="审核专家配置"
      extra={(
        <Space>
          <Button onClick={() => loadData(page, size)}>刷新</Button>
          <Button type="primary" disabled={!expertRole} onClick={openAddModal}>
            添加审核专家
          </Button>
        </Space>
      )}
    >
      <Alert
        type="info"
        showIcon
        message="配置说明"
        description="此处管理拥有 EXPERT 角色的审核专家。添加后，专家可使用自己的账号进入智核医审领取并审核待审任务。"
        style={{ marginBottom: 16 }}
      />
      {!expertRole && (
        <Alert
          type="warning"
          showIcon
          message="未找到 EXPERT 角色"
          description="请先到账户管理的“账户角色”页面创建 EXPERT 角色。"
          style={{ marginBottom: 16 }}
        />
      )}

      <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
        <Form.Item name="nickName" label="专家姓名">
          <Input
            allowClear
            placeholder="按用户昵称搜索"
            onPressEnter={handleSearch}
            style={{ width: 240 }}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" onClick={handleSearch}>搜索</Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<UserRoleMap>
        rowKey="id"
        columns={columns}
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
          showTotal={(total) => `共 ${total} 位审核专家`}
          onChange={(nextPage, nextSize) => {
            const resolvedSize = nextSize || size;
            setPage(nextPage);
            setSize(resolvedSize);
            void loadData(nextPage, resolvedSize);
          }}
        />
      </div>

      <Modal
        open={addVisible}
        title="添加审核专家"
        okText="添加"
        cancelText="取消"
        confirmLoading={saving}
        onOk={handleAdd}
        onCancel={() => {
          setAddVisible(false);
          addForm.resetFields();
          setCandidateUsers([]);
        }}
        destroyOnClose
      >
        <Form form={addForm} layout="vertical">
          <Form.Item
            name="userId"
            label="选择用户"
            rules={[{ required: true, message: '请选择要添加的用户' }]}
          >
            <Select
              showSearch
              allowClear
              filterOption={false}
              loading={candidateLoading}
              placeholder="输入昵称、手机号或用户ID搜索"
              onSearch={(value) => loadCandidateUsers(value)}
              notFoundContent={candidateLoading ? '加载中...' : '未找到用户'}
              options={candidateUsers.map((user) => ({
                value: String(user.userId),
                label: `${user.nickName || '未命名用户'} · ${user.phoneNumber || user.userId}`,
                disabled: Boolean(user.status),
              }))}
            />
          </Form.Item>
          <Form.Item label="授予角色">
            <Input value={expertRole?.name || EXPERT_ROLE_NAME} disabled />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

const ExpertReview: React.FC = () => (
  <Tabs
    defaultActiveKey="assignment"
    items={[
      {
        key: 'assignment',
        label: '账号专家分配',
        children: <AccountExpertAssignment />,
      },
      {
        key: 'expert-accounts',
        label: '专家账号',
        children: <ExpertAccountRoleManagement />,
      },
      {
        key: 'organization-admin-assignment',
        label: '机构终审分配',
        children: <AccountOrganizationAdminAssignment />,
      },
    ]}
  />
);

export default ExpertReview;
