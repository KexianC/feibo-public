import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Popconfirm, Space, Spin, Table, Tag, Tooltip, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import RoleService, {
  Permission,
  Role,
  RolePageParams,
  PageResponse,
  RoleParams,
  RolePermissionSnapshot,
} from '@/services/roleService';
import {
  PROTECTED_ROLE_NOTICE,
  isProtectedRoleCode,
  isValidRoleCode,
  normalizeRoleCode,
} from './accountRolePolicy';

const SUPER_ADMIN_ONLY_CAPABILITIES = new Set([
  'user.manage',
  'identity.manage',
  'audit.read',
  'system.manage',
  'finance.manage',
]);

/**
 * 账户角色管理页面
 * - 列表、搜索、分页
 * - 创建/编辑/删除
 * - 权限目录与角色权限采用版本化全量更新，避免并发覆盖
 */
const AccountRoles: React.FC = () => {
  // 搜索与分页表单
  const [searchForm] = Form.useForm();
  // 编辑表单
  const [editForm] = Form.useForm();

  // 页面状态
  const [loading, setLoading] = useState<boolean>(false);
  const [pageData, setPageData] = useState<PageResponse<Role>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [searchParams, setSearchParams] = useState<RolePageParams>({ pageNum: 1, pageSize: 10 });
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  // 编辑模态框
  const [editVisible, setEditVisible] = useState<boolean>(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [permissionVisible, setPermissionVisible] = useState<boolean>(false);
  const [permissionLoading, setPermissionLoading] = useState<boolean>(false);
  const [permissionSaving, setPermissionSaving] = useState<boolean>(false);
  const [permissionRole, setPermissionRole] = useState<Role | null>(null);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<string[]>([]);
  const [permissionSnapshot, setPermissionSnapshot] = useState<RolePermissionSnapshot | null>(null);

  /**
   * 加载角色列表数据（分页）
   */
  const loadRoles = useCallback(async (params: RolePageParams) => {
    setLoading(true);
    try {
      const data = await RoleService.getRolesPage(params);
      setPageData(data);
      setSelectedRowKeys([]);
    } catch (err) {
      console.error('加载角色列表失败');
      message.error('加载角色列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * 初始化加载
   */
  useEffect(() => {
    loadRoles({ pageNum: 1, pageSize: 10 });
  }, [loadRoles]);

  /**
   * 友好时间格式化显示
   * @param t ISO 时间字符串
   */
  const formatTime = (t?: string) => {
    if (!t) return '-';
    const d = new Date(t);
    if (isNaN(d.getTime())) return t;
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  };

  /**
   * 执行搜索
   */
  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    const params: RolePageParams = {
      pageNum: 1,
      pageSize: searchParams.pageSize,
      name: values.name || undefined,
    };
    setSearchParams(params);
    loadRoles(params);
  };

  /**
   * 重置搜索条件
   */
  const handleReset = () => {
    searchForm.resetFields();
    const params: RolePageParams = { pageNum: 1, pageSize: 10 };
    setSearchParams(params);
    loadRoles(params);
  };

  /**
   * 新增角色（打开编辑模态框）
   */
  const handleAdd = () => {
    setEditingRole(null);
    editForm.resetFields();
    setEditVisible(true);
  };

  /**
   * 编辑角色（加载详情并打开编辑模态框）
   */
  const handleEdit = async (record: Role) => {
    if (isProtectedRoleCode(record.name)) {
      message.warning(PROTECTED_ROLE_NOTICE);
      return;
    }
    if (!record.databaseId) {
      message.error('角色数据缺少数据库主键，已停止编辑');
      return;
    }
    try {
      const detail = await RoleService.getRoleById(record.databaseId);
      if (isProtectedRoleCode(detail.name)) {
        message.warning(PROTECTED_ROLE_NOTICE);
        return;
      }
      if (!detail.databaseId) {
        message.error('角色详情缺少数据库主键，已停止编辑');
        return;
      }
      setEditingRole(detail);
      editForm.setFieldsValue({
        name: normalizeRoleCode(detail.name),
        description: detail.description,
      });
      setEditVisible(true);
    } catch (err) {
      console.error('获取角色详情失败');
      message.error('获取角色详情失败');
    }
  };

  /**
   * 删除角色
   */
  const handleDelete = async (record: Role) => {
    if (isProtectedRoleCode(record.name)) {
      message.warning(PROTECTED_ROLE_NOTICE);
      return;
    }
    if (!record.databaseId) {
      message.error('角色数据缺少数据库主键，已停止删除');
      return;
    }
    try {
      const deleted = await RoleService.deleteRole(record.databaseId);
      if (!deleted) throw new Error('后端未删除该角色');
      message.success('删除成功');
      loadRoles(searchParams);
    } catch (err) {
      console.error('删除角色失败');
      message.error('删除失败');
    }
  };

  /**
   * 批量删除角色
   */
  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.info('请先选择需要删除的角色');
      return;
    }
    const selectedKeys = new Set(selectedRowKeys.map(String));
    const includesProtectedRole = pageData.records.some(
      (role) => selectedKeys.has(String(role.databaseId)) && isProtectedRoleCode(role.name),
    );
    if (includesProtectedRole) {
      message.warning(PROTECTED_ROLE_NOTICE);
      return;
    }
    try {
      const deleted = await RoleService.deleteRolesBatch(selectedRowKeys as string[]);
      if (!deleted) throw new Error('后端未完成批量删除');
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      loadRoles(searchParams);
    } catch (err) {
      console.error('批量删除失败');
      message.error('批量删除失败');
    }
  };

  /**
   * 保存角色（新增或更新）
   */
  const handleSaveRole = async () => {
    let values: { name: string; description?: string };
    try {
      values = await editForm.validateFields();
    } catch {
      return;
    }

    if (editingRole && isProtectedRoleCode(editingRole.name)) {
      message.warning(PROTECTED_ROLE_NOTICE);
      return;
    }
    if (editingRole && !editingRole.databaseId) {
      message.error('角色数据缺少数据库主键，已停止更新');
      return;
    }

    const normalizedName = normalizeRoleCode(values.name);
    if (isProtectedRoleCode(normalizedName)) {
      editForm.setFields([{ name: 'name', errors: ['系统保留角色码不可用于自定义角色'] }]);
      return;
    }
    const payload: RoleParams = {
      name: normalizedName,
      description: values.description,
    };
    try {
      if (editingRole) {
        const updated = await RoleService.updateRole({
          databaseId: editingRole.databaseId!,
          ...payload,
        });
        if (!updated) throw new Error('后端未更新该角色');
        message.success('更新成功');
      } else {
        const created = await RoleService.createRole(payload);
        if (!created) throw new Error('后端未创建该角色');
        message.success('创建成功');
      }
      setEditVisible(false);
      setEditingRole(null);
      loadRoles(searchParams);
    } catch (err) {
      console.error('保存角色失败');
      message.error('保存失败');
    }
  };

  const loadRolePermissions = async (role: Role) => {
    if (!role.databaseId) {
      message.error('角色数据缺少数据库主键，已停止权限编辑');
      return false;
    }
    setPermissionLoading(true);
    setPermissionSnapshot(null);
    try {
      // Both reads must succeed before the save control is enabled. In
      // particular, a failed catalog read must never be interpreted as an
      // intentionally empty assignment set.
      const [catalog, snapshot] = await Promise.all([
        RoleService.getPermissions(),
        RoleService.getRolePermissions(role.databaseId),
      ]);
      const catalogIds = new Set(catalog.map(item => String(item.id)));
      const unknownAssignments = snapshot.permissionIds.filter(id => !catalogIds.has(String(id)));
      if (unknownAssignments.length > 0) {
        throw new Error('角色包含当前目录无法识别的权限，已停止编辑');
      }
      setPermissions(catalog);
      setSelectedPermissionIds(snapshot.permissionIds.map(String));
      setPermissionSnapshot(snapshot);
      return true;
    } catch (err) {
      console.error('加载角色权限失败');
      message.error(err instanceof Error ? err.message : '加载角色权限失败');
      setPermissions([]);
      setSelectedPermissionIds([]);
      setPermissionSnapshot(null);
      return false;
    } finally {
      setPermissionLoading(false);
    }
  };

  const handleEditPermissions = async (role: Role) => {
    setPermissionRole(role);
    setPermissionVisible(true);
    await loadRolePermissions(role);
  };

  const confirmPermissionUpdate = (empty: boolean): Promise<boolean> => new Promise(resolve => {
    Modal.confirm({
      title: empty ? '确认清空该角色的全部权限？' : '确认更新该角色权限？',
      content: empty
        ? '清空后该角色将不再获得任何能力权限。此操作会写入安全审计日志。'
        : '将以当前版本全量替换该角色的权限集合，并写入安全审计日志。',
      okText: empty ? '确认清空' : '确认保存',
      okButtonProps: empty ? { danger: true } : undefined,
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });

  const handleSavePermissions = async () => {
    if (!permissionRole?.databaseId || !permissionSnapshot || permissionLoading) {
      message.error('角色权限尚未完整加载，已停止保存');
      return;
    }
    const empty = selectedPermissionIds.length === 0;
    if (!(await confirmPermissionUpdate(empty))) return;

    setPermissionSaving(true);
    try {
      const updated = await RoleService.updateRolePermissions(permissionRole.databaseId, {
        version: permissionSnapshot.version,
        permissionIds: selectedPermissionIds,
        confirmEmpty: empty,
      });
      setPermissionSnapshot(updated);
      setSelectedPermissionIds(updated.permissionIds.map(String));
      message.success('角色权限已更新');
      setPermissionVisible(false);
      setPermissionRole(null);
    } catch (err) {
      console.error('更新角色权限失败');
      if (RoleService.isConflictError(err)) {
        message.warning('角色权限已被其他管理员修改，正在重新加载最新值');
        await loadRolePermissions(permissionRole);
      } else {
        message.error(err instanceof Error ? err.message : '更新角色权限失败');
      }
    } finally {
      setPermissionSaving(false);
    }
  };

  /**
   * 表格分页与大小变化
   */
  const handleTableChange = (page: number, pageSize: number) => {
    const params: RolePageParams = {
      ...searchParams,
      pageNum: page,
      pageSize: pageSize,
    };
    setSearchParams(params);
    loadRoles(params);
  };

  // 列定义
  const columns: ColumnsType<Role> = [
    { title: '数据库ID', dataIndex: 'databaseId', key: 'databaseId', width: 180, render: (t?: string) => t || '-' },
    { title: '角色ID', dataIndex: 'roleId', key: 'roleId', width: 180, render: (t?: string) => t || '-' },
    {
      title: '角色名称',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (name: string) => (
        <Space size="small">
          <span>{name}</span>
          {isProtectedRoleCode(name) && <Tag color="gold">系统保留</Tag>}
        </Space>
      ),
    },
    { title: '描述', dataIndex: 'description', key: 'description', width: 240, render: (t?: string) => t || '-' },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t?: string) => formatTime(t) },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180, render: (t?: string) => formatTime(t) },
    {
      title: '操作', key: 'action', fixed: 'right', width: 300,
      render: (_, record) => {
        const protectedRole = isProtectedRoleCode(record.name);
        return (
          <Space size="small" wrap>
            {protectedRole ? (
              <Tooltip title={PROTECTED_ROLE_NOTICE}>
                <span><Button type="link" disabled>编辑</Button></span>
              </Tooltip>
            ) : (
              <Button type="link" onClick={() => handleEdit(record)}>编辑</Button>
            )}
            {protectedRole ? (
              <Tooltip title={PROTECTED_ROLE_NOTICE}>
                <span><Button type="link" danger disabled>删除</Button></span>
              </Tooltip>
            ) : (
              <Popconfirm title="确认删除该角色？" onConfirm={() => handleDelete(record)}>
                <Button type="link" danger>删除</Button>
              </Popconfirm>
            )}
            <Button
              type="link"
              disabled={!record.databaseId}
              title={!record.databaseId ? '角色数据缺少数据库主键' : '编辑角色能力权限'}
              onClick={() => handleEditPermissions(record)}
            >权限</Button>
          </Space>
        );
      }
    },
  ];

  return (
    <div>
      <Card title="账户角色" extra={
        <Space>
          <Button type="primary" onClick={handleAdd}>新增角色</Button>
          <Popconfirm title="确认批量删除选中角色？" onConfirm={handleBatchDelete}>
            <Button danger>批量删除</Button>
          </Popconfirm>
        </Space>
      }>
        <Alert
          type="info"
          showIcon
          message="系统保留角色防误操作"
          description={PROTECTED_ROLE_NOTICE}
          style={{ marginBottom: 16 }}
        />
        {/* 搜索区域 */}
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="name" label="角色名称">
            <Input placeholder="请输入角色名称" allowClear />
          </Form.Item>
          {/* 后端返回未包含状态字段，移除状态筛选 */}
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>搜索</Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        {/* 表格 */}
        <Table<Role>
          rowKey={(record) => record.databaseId ?? `missing:${record.roleId ?? record.name}`}
          columns={columns}
          dataSource={pageData.records}
          loading={loading}
          pagination={{
            current: pageData.current,
            pageSize: pageData.size,
            total: pageData.total,
            onChange: (p, s) => handleTableChange(p, s || pageData.size)
          }}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys, rows) => {
              setSelectedRowKeys(
                keys.filter((key) => {
                  const role = rows.find((row) => String(row.databaseId) === String(key));
                  return !role || !isProtectedRoleCode(role.name);
                }),
              );
            },
            getCheckboxProps: (record) => ({
              disabled: isProtectedRoleCode(record.name) || !record.databaseId,
              title: isProtectedRoleCode(record.name)
                ? PROTECTED_ROLE_NOTICE
                : (!record.databaseId ? '角色数据缺少数据库主键，不能批量删除' : undefined),
            }),
          }}
        />
      </Card>

      {/* 新增/编辑角色模态框 */}
      <Modal
        title={editingRole ? '编辑角色' : '新增角色'}
        open={editVisible}
        onOk={handleSaveRole}
        onCancel={() => { setEditVisible(false); setEditingRole(null); }}
        width={520}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="name"
            label="角色码"
            extra="保存时会自动去除首尾空格并转为大写；SUPERADMIN 按 SUPER_ADMIN 识别。"
            rules={[
              { required: true, message: '请输入角色码' },
              {
                validator: async (_, value) => {
                  if (!value) return;
                  if (!isValidRoleCode(value)) {
                    throw new Error('角色码需为 2-64 位字母、数字或下划线，且必须以字母开头');
                  }
                  if (isProtectedRoleCode(value)) {
                    throw new Error('系统保留角色码不可用于自定义角色');
                  }
                },
              },
            ]}
          >
            <Input
              maxLength={64}
              placeholder="例如：CONTENT_AUDITOR"
              onBlur={(event) => editForm.setFieldValue('name', normalizeRoleCode(event.target.value))}
            />
          </Form.Item>
          <Form.Item name="description" label="角色描述">
            <Input.TextArea rows={3} placeholder="请输入角色描述" />
          </Form.Item>
          {/* 后端返回未包含状态字段，编辑不提供状态选择 */}
        </Form>
      </Modal>

      <Modal
        title={`角色权限${permissionRole ? `：${permissionRole.name}` : ''}`}
        open={permissionVisible}
        onOk={handleSavePermissions}
        okText="保存权限"
        confirmLoading={permissionSaving}
        okButtonProps={{ disabled: permissionLoading || !permissionSnapshot }}
        onCancel={() => {
          if (permissionSaving) return;
          setPermissionVisible(false);
          setPermissionRole(null);
          setPermissionSnapshot(null);
          setPermissions([]);
          setSelectedPermissionIds([]);
        }}
        width={640}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message="权限保存采用版本校验"
          description="若其他管理员已更新该角色，当前保存会被拒绝并重新加载，避免覆盖他人的修改。"
          style={{ marginBottom: 16 }}
        />
        <Spin spinning={permissionLoading}>
          <Checkbox.Group
            style={{ display: 'grid', gap: 12 }}
            value={selectedPermissionIds}
            disabled={!permissionSnapshot}
            onChange={values => setSelectedPermissionIds(values.map(String))}
          >
            {permissions.map(permission => (
              <Checkbox
                key={permission.id}
                value={String(permission.id)}
                disabled={Boolean(
                  permissionRole
                  && normalizeRoleCode(permissionRole.name) !== 'SUPER_ADMIN'
                  && SUPER_ADMIN_ONLY_CAPABILITIES.has(permission.code)
                  && !selectedPermissionIds.includes(String(permission.id)),
                )}
              >
                <Space size="small" wrap>
                  <span>{permission.name}</span>
                  <Tag>{permission.code}</Tag>
                  {permission.reserved && <Tag color="gold">系统保留</Tag>}
                  {SUPER_ADMIN_ONLY_CAPABILITIES.has(permission.code) && (
                    <Tag color="red">仅超级管理员角色</Tag>
                  )}
                  {permission.description && (
                    <span style={{ color: '#888' }}>{permission.description}</span>
                  )}
                </Space>
              </Checkbox>
            ))}
          </Checkbox.Group>
          {!permissionLoading && permissionSnapshot && permissions.length === 0 && (
            <Alert
              type="error"
              showIcon
              message="权限目录为空"
              description="为避免误清空，保存前仍需二次确认；请优先检查权限种子是否已执行。"
              style={{ marginTop: 12 }}
            />
          )}
        </Spin>
      </Modal>

    </div>
  );
};

export default AccountRoles;
