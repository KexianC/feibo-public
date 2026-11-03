import React, { useEffect, useRef, useState } from 'react';
import { Card, Form, Input, Button, Space, Table, Modal, Select, Popconfirm, message, Pagination, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import UserRoleMapService, { UserRoleMap, PageResponse } from '@/services/userRoleMapService';
import RoleService, { Role } from '@/services/roleService';

const { Option } = Select;

const AccountList: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [pageData, setPageData] = useState<PageResponse<UserRoleMap>>({ records: [], total: 0, pages: 0, size: 10, current: 1 });
  const [nickName, setNickName] = useState<string | undefined>();
  const [roleName, setRoleName] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const [editVisible, setEditVisible] = useState(false);
  const [editing, setEditing] = useState<UserRoleMap | null>(null);

  const didInitRef = useRef(false);

  const loadRoles = async () => {
    try {
      const list = await RoleService.getAllRoles();
      setRoles(list);
    } catch {}
  };

  /**
   * 加载账户列表分页数据（pageNum/pagesize/nickName/roleName）
   */
  const loadData = async (p = page, s = size) => {
    setLoading(true);
    try {
      const data = await UserRoleMapService.getPage({ pageNum: p, pagesize: s, nickName, roleName });
      setPageData(data);
      setPage(data.current || p);
      setSize(data.size || s);
    } catch (err) {
      message.error('加载账户列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadRoles();
    loadData(1, size);
  }, []);

  const handleSearch = () => {
    const v = searchForm.getFieldsValue();
    setNickName(v.nickName || undefined);
    setRoleName(v.roleName || undefined);
    setPage(1);
    loadData(1, size);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setNickName(undefined);
    setRoleName(undefined);
    setPage(1);
    loadData(1, size);
  };

  const handleAdd = () => {
    setEditing(null);
    editForm.resetFields();
    setEditVisible(true);
  };

  const handleEdit = async (record: UserRoleMap) => {
    try {
      const detail = await UserRoleMapService.getById(record.id);
      setEditing(detail);
      editForm.setFieldsValue({ userId: detail.userId, roleId: detail.roleId });
      setEditVisible(true);
    } catch {
      message.error('加载映射详情失败');
    }
  };

  const handleDelete = async (record: UserRoleMap) => {
    try {
      await UserRoleMapService.deleteById(record.id);
      message.success('删除成功');
      loadData(page, size);
    } catch {
      message.error('删除失败');
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.info('请先选择要删除的记录');
      return;
    }
    try {
      await UserRoleMapService.deleteBatch(selectedRowKeys as string[]);
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      loadData(page, size);
    } catch {
      message.error('批量删除失败');
    }
  };

  const handleSave = async () => {
    const v = await editForm.validateFields();
    try {
      if (editing) {
        await UserRoleMapService.update({ id: editing.id, userId: String(v.userId), roleId: String(v.roleId) });
        message.success('更新成功');
      } else {
        await UserRoleMapService.create({ userId: String(v.userId), roleId: String(v.roleId) });
        message.success('创建成功');
      }
      setEditVisible(false);
      setEditing(null);
      loadData(page, size);
    } catch {
      message.error('保存失败');
    }
  };

  const columns: ColumnsType<UserRoleMap> = [
    { title: '映射ID', dataIndex: 'id', key: 'id', width: 120 },
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 120 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 160 },
    { title: '角色ID', dataIndex: 'roleId', key: 'roleId', width: 120 },
    { title: '角色名称', dataIndex: 'roleName', key: 'roleName', width: 160, render: (t?: string) => t ? <Tag color="blue">{t}</Tag> : '-' },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
    {
      title: '操作', key: 'action', fixed: 'right', width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该映射？" onConfirm={() => handleDelete(record)}>
            <Button type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    },
  ];

  return (
    <div>
      <Card title="账户列表" extra={
        <Space>
          <Button type="primary" onClick={handleAdd}>新增映射</Button>
          <Popconfirm title="确认批量删除选中记录？" onConfirm={handleBatchDelete}>
            <Button danger>批量删除</Button>
          </Popconfirm>
        </Space>
      }>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="nickName" label="昵称">
            <Input placeholder="按用户昵称搜索" allowClear onChange={(e) => setNickName(e.target.value || undefined)} />
          </Form.Item>
          <Form.Item name="roleName" label="角色名">
            <Select allowClear style={{ width: 180 }} onChange={(v) => setRoleName(v || undefined)}>
              {roles.map(r => (
                <Option key={r.id} value={r.name}>{r.name}</Option>
              ))}
            </Select>
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
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        />
        <div style={{ padding: 12, display: 'flex', justifyContent: 'flex-end' }}>
          <Pagination
            total={pageData.total}
            current={pageData.current}
            pageSize={pageData.size}
            showSizeChanger
            showQuickJumper
            onChange={(p, s) => { setPage(p); setSize(s || size); loadData(p, s || size); }}
          />
        </div>
      </Card>

      <Modal
        title={editing ? '编辑映射' : '新增映射'}
        open={editVisible}
        onOk={handleSave}
        onCancel={() => { setEditVisible(false); setEditing(null); }}
        width={520}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="userId" label="用户ID" rules={[{ required: true, message: '请输入用户ID' }]}>
            <Input placeholder="请输入用户ID" />
          </Form.Item>
          <Form.Item name="roleId" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select placeholder="请选择角色">
              {roles.map(r => (
                <Option key={r.id} value={r.id}>{r.name}</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AccountList;
