import React, { useState, useEffect } from 'react';
import { Table, Card, Button, Input, Select, Space, Tag, Modal, message, Descriptions } from 'antd';
import { SearchOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import UserService, { User, UserPageParams, PageResponse } from '../../services/userService';

const { Search } = Input;
const { Option } = Select;

const UserList: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 8,
    total: 0,
  });
  const [searchParams, setSearchParams] = useState<UserPageParams>({
    page: 1,
    size: 8,
  });
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [userDetailLoading, setUserDetailLoading] = useState(false);

  // 获取用户列表
  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response: PageResponse<User> = await UserService.getUsersPage(searchParams);
      setUsers(response.records || []);
      setPagination({
        current: response.current || 1,
        pageSize: response.size || 8,
        total: response.total || 0,
      });
    } catch (error) {
      message.error('获取用户列表失败: ' + (error as Error).message);
      setUsers([]); // 确保在错误时设置为空数组
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [searchParams]);

  const columns: ColumnsType<User> = [
    {
      title: '用户ID',
      dataIndex: 'id',
      key: 'id',
      width: 100,
    },
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 120,
    },
    {
      title: '昵称',
      dataIndex: 'nickName',
      key: 'nickName',
      width: 120,
      render: (text) => text || '-',
    },
    {
      title: '头像',
      dataIndex: 'avatar',
      key: 'avatar',
      width: 80,
      render: (avatar) => (
        <img 
          src={avatar || 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/avatar/default.jpg'} 
          alt="头像" 
          style={{ width: 40, height: 40, borderRadius: '50%' }}
        />
      ),
    },
    {
      title: '手机号',
      dataIndex: 'phoneNumber',
      key: 'phoneNumber',
      width: 130,
      render: (text) => text || '-',
    },
    {
      title: '性别',
      dataIndex: 'gender',
      key: 'gender',
      width: 80,
      render: (gender: number) => {
        const genderMap = { 0: '未知', 1: '男', 2: '女' };
        return genderMap[gender as keyof typeof genderMap] || '未知';
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: boolean) => (
        <Tag color={status ? 'red' : 'green'}>
          {status ? '禁用' : '启用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (text) => UserService.formatDateTime(text),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 160,
      render: (text) => UserService.formatDateTime(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button 
          type="link" 
          icon={<EyeOutlined />} 
          onClick={() => handleViewDetail(record)}
        >详情</Button>
      ),
    },
  ];

  const handleViewDetail = async (record: User) => {
    try {
      setUserDetailLoading(true);
      setIsModalVisible(true);
      const userDetail = await UserService.getUserById(record.id.toString());
      setSelectedUser(userDetail);
    } catch (error) {
      message.error('获取用户详情失败: ' + (error as Error).message);
      setIsModalVisible(false);
    } finally {
      setUserDetailLoading(false);
    }
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    setSelectedUser(null);
  };

  const onSelectChange = (newSelectedRowKeys: React.Key[]) => {
    setSelectedRowKeys(newSelectedRowKeys);
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: onSelectChange,
  };

  // 处理搜索
  const handleSearch = (value: string) => {
    setSearchParams(prev => ({
      ...prev,
      page: 1,
      keyword: value || undefined,
    }));
  };

  // 处理状态筛选
  const handleStatusChange = (status: string) => {
    setSearchParams(prev => ({
      ...prev,
      page: 1,
      status: status || undefined,
    }));
  };

  // 处理分页变化
  const handleTableChange = (page: number, pageSize: number) => {
    setSearchParams(prev => ({
      ...prev,
      page: page,
      size: pageSize,
    }));
  };

  return (
    <div>
      <Card>
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            <Search
              placeholder="搜索用户名、邮箱或手机号"
              allowClear
              enterButton={<SearchOutlined />}
              size="middle"
              style={{ width: 300 }}
              onSearch={handleSearch}
            />
          </Space>
        </div>

        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={(users || []).map(user => ({ ...user, key: user.id }))}
          loading={loading}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/总共 ${total} 条`,
            onChange: handleTableChange,
            onShowSizeChange: handleTableChange,
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      <Modal
        title="用户详情"
        open={isModalVisible}
        onCancel={handleModalCancel}
        footer={[
          <Button key="close" onClick={handleModalCancel}>
            关闭
          </Button>
        ]}
        width={600}
      >
        {userDetailLoading ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            加载中...
          </div>
        ) : selectedUser ? (
          <Descriptions column={2} bordered>
            <Descriptions.Item label="用户ID">
              {selectedUser.userId}
            </Descriptions.Item>
            <Descriptions.Item label="昵称">
              {selectedUser.nickName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="手机号">
              {selectedUser.phoneNumber || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="性别">
              {selectedUser.gender === 1 ? '男' : selectedUser.gender === 2 ? '女' : '未知'}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={selectedUser.status ? 'red' : 'green'}>
                {selectedUser.status ? '禁用' : '启用'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="头像">
              {selectedUser.avatar ? (
                <img 
                  src={selectedUser.avatar} 
                  alt="头像" 
                  style={{ width: 50, height: 50, borderRadius: '50%' }}
                />
              ) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间" span={2}>
              {UserService.formatDateTime(selectedUser.createTime)}
            </Descriptions.Item>
            <Descriptions.Item label="更新时间" span={2}>
              {UserService.formatDateTime(selectedUser.updateTime)}
            </Descriptions.Item>
            {selectedUser.openId && (
              <Descriptions.Item label="OpenID" span={2}>
                {selectedUser.openId}
              </Descriptions.Item>
            )}
          </Descriptions>
        ) : null}
      </Modal>
    </div>
  );
};

export default UserList;