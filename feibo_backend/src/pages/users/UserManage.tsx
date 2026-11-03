import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Select, Button, Space, Table, Tag, Avatar, Popconfirm, message, Modal, Pagination, Image, Checkbox, Upload } from 'antd';
import dayjs from 'dayjs';
import { SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SettingOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import UserService, { User, UserPageParams, PageResponse, UpdateUserParams, AIModel } from '../../services/userService';
import AdminDigitalModelService, { AdminDigitalModelItem } from '@/services/adminDigitalModelService';

const { Option } = Select;

const UserManage: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [userData, setUserData] = useState<User[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 6,
    total: 0,
  });
  const [searchParams, setSearchParams] = useState<UserPageParams>({
    page: 1,
    size: 6,
  });
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [availableModels, setAvailableModels] = useState<AIModel[]>([]);
  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [bindLoading, setBindLoading] = useState(false);
  const [bindingUser, setBindingUser] = useState<User | null>(null);
  const [allModels, setAllModels] = useState<AdminDigitalModelItem[]>([]);
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([]);
  const [modelKeyword, setModelKeyword] = useState<string>('');
  const [hospitalFilter, setHospitalFilter] = useState<string | undefined>(undefined);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailUser, setDetailUser] = useState<User | null>(null);
  const [bulkVisible, setBulkVisible] = useState(false);
  const [bulkFile, setBulkFile] = useState<File | null>(null);
  const [bulkLoading, setBulkLoading] = useState(false);

  // 窗口高度监听：用于设定表格纵向滚动高度，保证底部分页始终可见
  const [windowHeight, setWindowHeight] = useState<number>(window.innerHeight);
  useEffect(() => {
    /**
     * 监听窗口尺寸变化，动态更新滚动高度
     */
    const onResize = () => setWindowHeight(window.innerHeight);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  const tableScrollY = Math.max(240, windowHeight - 420);

  /**
   * 清洗头像URL，移除反引号和多余引号/空格
   */
  const sanitizeAvatar = (url?: string): string | undefined => {
    if (!url) return undefined;
    return url.replace(/[`'\"]+/g, '').trim();
  };

  /**
   * 懒加载缩略图：仅使用原生 lazy，加载完成后保持显示，通过不透明度避免闪烁
   */
  const LazyThumb: React.FC<{ src?: string; size?: number }> = React.memo(({ src, size = 100 }) => {
    const [loaded, setLoaded] = useState(false);
    const cleaned = sanitizeAvatar(src);
    return (
      <div style={{ width: size, height: size, borderRadius: 8, background: '#f5f5f5', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center', willChange: 'opacity', contain: 'content' }}>
        {cleaned ? (
          <img
            src={cleaned}
            width={size}
            height={size}
            style={{ objectFit: 'cover', borderRadius: 8, display: 'block', opacity: loaded ? 1 : 0, transition: 'opacity 120ms ease-out' }}
            loading="lazy"
            decoding="async"
            alt="thumb"
            onLoad={() => setLoaded(true)}
          />
        ) : (
          <Avatar icon={<UserOutlined />} shape="square" size={size} />
        )}
      </div>
    );
  });

  /**
   * 性别显示映射（0/1/2 -> 未知/男/女）
   */
  const renderGender = (g?: number): string => {
    if (g === 1) return '男';
    if (g === 2) return '女';
    return '未知';
  };
  useEffect(() => {
    loadUserData();
    loadAvailableModels();
  }, [searchParams]);

  /**
   * 加载用户分页数据（映射后端字段）
   */
  const loadUserData = async () => {
    try {
      setLoading(true);
      const response: PageResponse<User> = await UserService.getUsersPage(searchParams);
      setUserData(response.records);
      /**
       * 更新分页状态（以查询参数为准，前后端统一为1基页码）
       * - 保证 Table 的 pagination 与 searchParams 一致，避免页码错位显示相同数据
       */
      setPagination({
        current: searchParams.page,
        pageSize: searchParams.size,
        total: response.total,
      });
    } catch (error) {
      message.error('获取用户数据失败: ' + (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  /**
   * 加载可用AI模型
   */
  const loadAvailableModels = async () => {
    try {
      const models = await UserService.getAvailableModels();
      setAvailableModels(models);
    } catch (error) {
      console.error('获取AI模型列表失败:', error);
    }
  };

  /**
   * 加载用户绑定所需的模型数据
   */
  const loadUserBindingData = async (user: User) => {
    try {
      setBindLoading(true);
      const [models, bound] = await Promise.all([
        AdminDigitalModelService.getAllModels(),
        AdminDigitalModelService.getUserBoundModels(String(user.userId ?? user.id)),
      ]);
      setAllModels(models);
      const boundIds = bound.map(m => String(m.id));
      setSelectedModelIds(Array.from(new Set(boundIds)));
    } catch (error) {
      message.error('加载模型数据失败: ' + (error as Error).message);
    } finally {
      setBindLoading(false);
    }
  };

  /**
   * 处理搜索条件（使用 keyword 与状态）
   */
  const handleSearch = (values: any) => {
    setSearchParams(prev => ({
      ...prev,
      // 后端与前端统一为1基页码，重置为第一页
      page: 1,
      keyword: values.username || values.id || values.mobile || undefined,
      status: values.status || undefined,
      oderByUpdate: typeof values.oderByUpdate !== 'undefined' ? Number(values.oderByUpdate) : undefined,
      oderByCreate: typeof values.oderByCreate !== 'undefined' ? Number(values.oderByCreate) : undefined,
    }));
  };

  /**
   * 进入编辑模式，填充编辑表单（字段映射）
   * 状态布尔值：false=启用(ACTIVE)，true=禁用(DISABLED)
   */
  const handleEdit = (record: User) => {
    setEditingUser(record);
    editForm.setFieldsValue({
      nickname: record.nickName,
      phone: record.phoneNumber,
    });
    setEditModalVisible(true);
  };

  /**
   * 切换用户状态（禁用/启用）
   */
  const handleToggleStatus = async (record: User) => {
    try {
      const nextStatus = record.status ? 'ACTIVE' : 'DISABLED';
      await UserService.updateStatus(String(record.id), nextStatus);
      message.success(nextStatus === 'ACTIVE' ? '启用成功' : '禁用成功');
      loadUserData();
    } catch (error) {
      message.error('状态更新失败: ' + (error as Error).message);
    }
  };

  /**
   * 追踪用户（提示昵称/用户ID）
   */
  const handleViewDetail = async (record: User) => {
    try {
      const d = await UserService.getUserById(String(record.id));
      const clean = (u?: string) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : u);
      const mapped: User = {
        id: Number(d.id ?? record.id),
        userId: Number(d.userId ?? record.userId ?? 0),
        nickName: d.nickName ?? record.nickName,
        avatar: clean(d.avatar ?? record.avatar),
        phoneNumber: d.phoneNumber ?? record.phoneNumber,
        gender: d.gender ?? record.gender,
        status: typeof d.status === 'boolean' ? d.status : (record.status ?? false),
        openId: d.openId ?? record.openId,
        createTime: d.createTime ?? record.createTime,
        updateTime: d.updateTime ?? record.updateTime,
      } as User;
      setDetailUser(mapped);
      setDetailVisible(true);
    } catch (error) {
      message.error('获取用户详情失败');
    }
  };

  /**
   * 绑定模型（展示模型列表）
   */
  const handleBindModel = (record: User) => {
    setBindingUser(record);
    setSelectedModelIds([]);
    setBindModalVisible(true);
    loadUserBindingData(record);
  };


  /**
   * 绑定套餐（提示昵称/用户ID）
   */
  const handleBindPackage = (record: User) => {
    message.info(`绑定套餐: ${record.nickName || record.userId || record.id}`);
  };

  /**
   * 添加用户：打开新增表单（默认状态为ACTIVE）
   * - 将编辑对象置为空，表示新增模式
   * - 重置并设置默认表单值
   * - 打开模态框
   */
  const handleAdd = () => {
    setEditingUser(null);
    editForm.resetFields();
    setEditModalVisible(true);
    message.info('新增模式：请输入昵称和手机号创建用户');
  };

  /**
   * 提交编辑（映射表单到更新参数）
   */
  const handleEditModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      // 新增模式：仅使用 nickName 与 phoneNumber 创建
      if (!editingUser) {
        const nickName = values.nickname;
        const phoneNumber = values.phone;
        if (!nickName || !phoneNumber) {
          message.error('请填写昵称和手机号');
          return;
        }
        await UserService.createUser({ nickName, phoneNumber });
        message.success('创建用户成功');
        setEditModalVisible(false);
        loadUserData();
        return;
      }
      if (editingUser) {
        const updateParams: UpdateUserParams = {
          nickName: values.nickname,
          phoneNumber: values.phone,
        };
        await UserService.updateUser(String(editingUser.id), updateParams);
        message.success('更新用户信息成功');
        setEditModalVisible(false);
        editForm.resetFields();
        setEditingUser(null);
        loadUserData();
      }
    } catch (error) {
      message.error('更新用户信息失败: ' + (error as Error).message);
    }
  };

  /**
   * 取消编辑
   */
  const handleEditModalCancel = () => {
    setEditModalVisible(false);
    editForm.resetFields();
    setEditingUser(null);
  };

  /**
   * 渲染编辑模态框的表单项
   * - 新增模式：仅显示“昵称、手机号”两个必填项
   * - 编辑模式：保留完整字段（用户名、昵称、邮箱、手机号、状态）
   * @returns 不同模式下的表单项 JSX
   */
  const renderEditModalFields = (): React.ReactNode => {
    if (!editingUser) {
      return (
        <>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item
            name="phone"
            label="手机号"
            rules={[{ required: true, message: '请输入手机号' }]}
          >
            <Input placeholder="请输入手机号" />
          </Form.Item>
        </>
      );
    }
    return (
      <>
        <Form.Item
          name="nickname"
          label="昵称"
          rules={[{ required: true, message: '请输入昵称' }]}
        >
          <Input placeholder="请输入昵称" />
        </Form.Item>
        <Form.Item
          name="phone"
          label="手机号"
          rules={[{ required: true, message: '请输入手机号' }]}
        >
          <Input placeholder="请输入手机号" />
        </Form.Item>
      </>
    );
  };

  /**
   * 分页变化
   */
  const handleTableChange = (page: number, pageSize: number) => {
    setSearchParams(prev => ({
      ...prev,
      // 直接使用1基页码与后端保持一致
      page: page,
      size: pageSize,
    }));
  };

  const columns: ColumnsType<User> = [
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 160,
      render: (v: number) => v ?? '-',
    },
    {
      title: '昵称',
      dataIndex: 'nickName',
      key: 'nickName',
      width: 160,
      render: (text: string) => text || '-',
    },
    {
      title: '手机号码',
      dataIndex: 'phoneNumber',
      key: 'phoneNumber',
      width: 150,
      render: (text: string) => text || '-',
    },
    {
      title: '头像',
      dataIndex: 'avatar',
      key: 'avatar',
      width: 90,
      render: (avatar?: string) => {
        const clean = sanitizeAvatar(avatar);
        return clean ? <Avatar src={clean} /> : <Avatar icon={<UserOutlined />} />;
      },
    },
    {
      title: '性别',
      dataIndex: 'gender',
      key: 'gender',
      width: 90,
      render: (g: number) => renderGender(g),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      /**
       * 将布尔状态映射到字符串状态并渲染标签
       * 约定：false=ACTIVE(启用)，true=DISABLED(禁用)
       */
      render: (status: boolean) => {
        const str = status ? 'DISABLED' : 'ACTIVE';
        const colorClass = UserService.getStatusColor(str);
        const text = UserService.formatUserStatus(str);
        return <Tag className={colorClass}>{text}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (text?: string) => UserService.formatDateTime(text),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (text?: string) => UserService.formatDateTime(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      fixed: 'right',
      /**
       * 操作列：横向排列并允许换行，避免按钮拥挤导致断行
       * 将每个按钮设置为不折行以提升可读性
       */
      render: (_, record: User) => (
        <Space size="small" wrap>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
            style={{ whiteSpace: 'nowrap' }}
          >
            查看详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<SettingOutlined />}
            onClick={() => handleBindModel(record)}
            style={{ whiteSpace: 'nowrap' }}
          >模型绑定</Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            style={{ whiteSpace: 'nowrap' }}
          >
            编辑
          </Button>
          <Popconfirm
            title={record.status ? '确定要启用这个用户吗？' : '确定要禁用这个用户吗？'}
            onConfirm={() => handleToggleStatus(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger={record.status ? false : true}
              icon={<DeleteOutlined />}
              style={{ whiteSpace: 'nowrap' }}
            >{record.status ? '启用' : '禁用'}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 搜索表单 */}
      <Card style={{ marginBottom: 16 }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
        >
          <Form.Item name="username" label="用户名">
            <Input placeholder="请输入用户名" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="id" label="用户ID">
            <Input placeholder="请输入用户ID" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="mobile" label="手机号">
            <Input placeholder="请输入手机号" style={{ width: 120 }} />
          </Form.Item>
          <Form.Item label="排序">
            <Select
              placeholder="选择排序方式"
              style={{ width: 180 }}
              allowClear
              onChange={(val) => {
                setSearchParams(prev => {
                  const base = { ...prev, page: 1, oderByUpdate: undefined, oderByCreate: undefined } as any;
                  if (!val) return base;
                  if (val === 'update_asc') return { ...base, oderByUpdate: 0 };
                  if (val === 'update_desc') return { ...base, oderByUpdate: 1 };
                  if (val === 'create_asc') return { ...base, oderByCreate: 0 };
                  if (val === 'create_desc') return { ...base, oderByCreate: 1 };
                  return base;
                });
              }}
            >
              <Option value="update_asc">按更新时间升序</Option>
              <Option value="update_desc">按更新时间降序</Option>
              <Option value="create_asc">按创建时间升序</Option>
              <Option value="create_desc">按创建时间降序</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button 
              type="primary" 
              htmlType="submit" 
              icon={<SearchOutlined />}
              loading={loading}
            />
          </Form.Item>
          {/* 将“添加”按钮移至搜索按钮右侧，保持同一行 */}
          <Form.Item>
            <Button 
              type="primary" 
              icon={<PlusOutlined />}
              onClick={handleAdd}
            >添加</Button>
          </Form.Item>
          <Form.Item>
            <Button icon={<UploadOutlined />} onClick={() => setBulkVisible(true)}>批量新增</Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 数据表格 + 底部分页：分页固定在底部，表格主体可滚动 */}
      <Card style={{ display: 'flex', flexDirection: 'column' }}>
        <Table
          columns={columns}
          dataSource={userData}
          rowKey="id"
          loading={loading}
          pagination={false}
          /**
           * 横向滚动 + 纵向滚动高度受窗口高度控制
           * 确保分页栏在小屏也能显示在底部
           */
          scroll={{ x: 'max-content', y: tableScrollY }}
        />
        <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Pagination
            total={pagination.total}
            current={searchParams.page}
            pageSize={searchParams.size}
            showSizeChanger
            showQuickJumper
            showTotal={(t, range) => `第 ${range[0]}-${range[1]} 条/共 ${t} 条`}
            onChange={(p, s) => handleTableChange(p, s || searchParams.size)}
          />
        </div>
      </Card>

      {/* 编辑用户模态框 */}
        <Modal
          title={editingUser ? '编辑用户' : '添加用户'}
          open={editModalVisible}
          onOk={handleEditModalOk}
          onCancel={handleEditModalCancel}
          width={600}
        >
          <Form
            form={editForm}
            layout="vertical"
            initialValues={editingUser || {}}
          >
            {renderEditModalFields()}
          </Form>
        </Modal>

        <Modal
          title="批量新增用户"
          open={bulkVisible}
          onCancel={() => { setBulkVisible(false); setBulkFile(null); }}
          onOk={async () => {
            if (!bulkFile) { message.warning('请先选择Excel文件'); return; }
            try {
              setBulkLoading(true);
              const importErrors = await UserService.createUsersByExcel(bulkFile);
              if (importErrors.length > 0) {
                Modal.warning({ title: '导入结果需核对', width: 640,
                  content: <div><p>以下记录已跳过，请修正后重新导入。其他记录请在用户列表核对。</p><pre style={{ whiteSpace: 'pre-wrap', maxHeight: 360, overflow: 'auto' }}>{importErrors.join('\n')}</pre></div> });
              } else message.success('导入完成，未返回失败记录');
              setBulkVisible(false);
              setBulkFile(null);
              loadUserData();
            } catch (e: any) {
              message.error(e?.message || '批量新增失败');
            } finally {
              setBulkLoading(false);
            }
          }}
          confirmLoading={bulkLoading}
          width={520}
        >
          <Upload.Dragger
            multiple={false}
            accept=".xlsx,.xls"
            beforeUpload={(file) => { setBulkFile(file); return false; }}
            fileList={bulkFile ? [({ ...bulkFile, uid: '1' } as any)] : []}
            onRemove={() => { setBulkFile(null); return true; }}
          >
            <p>Excel表头须为：姓名、手机号、绑定模型（模型名称）</p>
            <p>模型可留空，表示不绑定；多个模型名用中文或英文逗号分隔。未知或重名模型所在行将跳过并显示错误。</p>
            <p>仅支持 .xlsx/.xls</p>
          </Upload.Dragger>
        </Modal>

        <Modal
          title="用户详情"
          open={detailVisible}
          onCancel={() => { setDetailVisible(false); setDetailUser(null); }}
          footer={null}
          width={560}
        >
          {detailUser && (
            <div>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Avatar src={detailUser.avatar} size={48} icon={<UserOutlined />} />
                  <div>
                    <div>昵称：{detailUser.nickName || '-'}</div>
                    <div>手机号：{detailUser.phoneNumber || '-'}</div>
                  </div>
                </div>
                <div>用户ID：{String(detailUser.userId || '')}</div>
                <div>状态：{detailUser.status ? '已禁用' : '正常'}</div>
                <div>性别：{detailUser.gender === 1 ? '男' : detailUser.gender === 2 ? '女' : '未设置'}</div>
                <div>创建时间：{detailUser.createTime ? dayjs(detailUser.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</div>
                <div>更新时间：{detailUser.updateTime ? dayjs(detailUser.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</div>
              </Space>
            </div>
          )}
        </Modal>

        {/* 模型绑定模态框：头像勾选 */}
          <Modal
            title="绑定的数字人模型"
            open={bindModalVisible}
            onOk={async () => {
              if (!bindingUser) { setBindModalVisible(false); return; }
              try {
                setBindLoading(true);
                {
                  const idSet = new Set<string>();
                  const lookup = new Map<string, AdminDigitalModelItem>();
                  allModels.forEach(m => lookup.set(String(m.id), m));
                  selectedModelIds.forEach(k => {
                    const m = lookup.get(k);
                    if (m && /^\d+$/.test(String(m.id))) idSet.add(String(m.id));
                  });
                  await AdminDigitalModelService.updateUserBindings(String(bindingUser.userId ?? bindingUser.id), Array.from(idSet));
                }
                message.success('绑定关系已更新');
                setBindModalVisible(false);
              } catch (error) {
                message.error('更新绑定失败: ' + (error as Error).message);
              } finally {
                setBindLoading(false);
              }
            }}
          onCancel={() => { setBindModalVisible(false); setSelectedModelIds([]); setBindingUser(null); }}
          width={760}
          confirmLoading={bindLoading}
          >
          {/* 筛选区域：医院与关键词 */}
          <div style={{ marginBottom: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
            <Select
              allowClear
              placeholder="按医院筛选"
              value={hospitalFilter}
              onChange={(v) => setHospitalFilter(v || undefined)}
              style={{ width: 240 }}
            >
              {Array.from(new Set(allModels.map(m => m.hospital).filter(Boolean))).map(h => (
                <Option key={String(h)} value={String(h)}>{String(h)}</Option>
              ))}
            </Select>
            <Input
              allowClear
              placeholder="关键词（名称/编号）"
              value={modelKeyword}
              onChange={(e) => setModelKeyword(e.target.value)}
              style={{ width: 260 }}
              prefix={<SearchOutlined />}
            />
          </div>
          <div style={{ maxHeight: 480, overflowY: 'auto', paddingRight: 8 }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 12 }}>
              {allModels.length === 0 && !bindLoading && (
                <div style={{ gridColumn: '1 / -1', textAlign: 'center', color: '#999' }}>暂无模型数据</div>
              )}
              {(allModels.filter(m => {
                const kw = modelKeyword.trim().toLowerCase();
                const nameOk = kw ? (String(m.title || '').toLowerCase().includes(kw) || String(m.modelCode || '').toLowerCase().includes(kw)) : true;
                const hospOk = hospitalFilter ? String(m.hospital || '') === hospitalFilter : true;
                return nameOk && hospOk;
              })).map((m, index) => {
                const key = String(m.id ?? m.modelCode ?? index);
                const checked = selectedModelIds.includes(key);
                return (
                  <div key={key} style={{ position: 'relative' }}>
                    <LazyThumb src={m.avatarUrl} size={100} />
                    <div style={{ marginTop: 6, fontSize: 12, textAlign: 'center', color: '#555', maxWidth: 100, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {m.title || '-'}
                    </div>
                    {m.hospital && (
                      <div style={{ fontSize: 11, textAlign: 'center', color: '#888', maxWidth: 100, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {m.hospital}
                      </div>
                    )}
                    <Checkbox
                      checked={checked}
                      onChange={(e) => {
                        setSelectedModelIds(prev => {
                          if (e.target.checked) {
                            if (prev.includes(key)) return prev;
                            return [...prev, key];
                          }
                          return prev.filter(k => k !== key);
                        });
                      }}
                      style={{ position: 'absolute', top: 6, right: 6, background: 'transparent', padding: 4, borderRadius: 6, zIndex: 3 }}
                    />
                  </div>
                );
              })}
            </div>
          </div>
          <div style={{ marginTop: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>已选 {selectedModelIds.length} 个</div>
            <Space>
              <Button onClick={() => setSelectedModelIds((allModels.filter(m => {
                const kw = modelKeyword.trim().toLowerCase();
                const nameOk = kw ? (String(m.title || '').toLowerCase().includes(kw) || String(m.modelCode || '').toLowerCase().includes(kw)) : true;
                const hospOk = hospitalFilter ? String(m.hospital || '') === hospitalFilter : true;
                return nameOk && hospOk;
              })).map(m => String(m.id)))}>全选</Button>
              <Button onClick={() => setSelectedModelIds([])}>清空</Button>
            </Space>
          </div>
        </Modal>

      </div>
    );
  };
  
  export default UserManage;
