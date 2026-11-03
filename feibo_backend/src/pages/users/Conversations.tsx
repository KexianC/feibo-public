import React, { useState, useEffect, useRef } from 'react';
import { Table, Card, Button, Input, Select, Space, Tag, Modal, Descriptions, message, InputNumber } from 'antd';
import { SearchOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import ConversationUsage, { tokenText, costText } from './ConversationUsage';
import ConversationService, { 
  Conversation, 
  ConversationDetail, 
  ConversationPageParams, 
  PageResponse 
} from '../../services/conversationService';

const { Search } = Input;
const { Option } = Select;
const { TextArea } = Input;

const Conversations: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [selectedConversation, setSelectedConversation] = useState<ConversationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const detailRequest = useRef(0);
  const listRequest = useRef(0);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [searchParams, setSearchParams] = useState<ConversationPageParams>({
    pageNum: 1,
    pageSize: 10,
  });

  /**
   * 获取对话列表
   * 根据当前搜索与分页参数调用服务端接口，更新列表与分页信息。
   */
  const fetchConversations = async () => {
    const request = ++listRequest.current;
    try {
      setLoading(true);
      const response: PageResponse<Conversation> = await ConversationService.getConversationsPage(searchParams);
      if (request !== listRequest.current) return;
      setConversations(response.records || []);
      setPagination({
        current: response.current || 1,
        pageSize: response.size || 10,
        total: response.total || 0,
      });
    } catch (error) {
      if (request !== listRequest.current) return;
      message.error('获取对话列表失败: ' + (error as Error).message);
      setConversations([]);
    } finally {
      if (request === listRequest.current) setLoading(false);
    }
  };

  useEffect(() => {
    fetchConversations();
  }, [searchParams]);

  const columns: ColumnsType<Conversation> = [
    {
      title: '对话ID',
      dataIndex: 'id',
      key: 'id',
      width: 120,
      ellipsis: true,
    },
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      width: 100,
      render: (text) => text || '-',
    },
    {
      title: '问题',
      dataIndex: 'question',
      key: 'question',
      width: 300,
      ellipsis: true,
      render: (text) => text || '-',
    },
    { title: '使用模型', dataIndex: 'model', width: 220, render: value => value || '未记录' },
    { title: '平台 Token', width: 160, render: (_, row) => <div>
      {tokenText(row.measuredTokens)}
      <div style={{ color: '#8c8c8c', fontSize: 12 }}>已记录 {row.meteredReplies ?? 0}/{row.totalReplies ?? 0} 次回复</div>
    </div> },
    { title: '预估费用', width: 170, render: (_, row) => <div>
      {costText(row.estimatedCost)}
      <div style={{ color: '#8c8c8c', fontSize: 12 }}>已计价 {row.pricedReplies ?? 0}/{row.totalReplies ?? 0} 次回复</div>
    </div> },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (text) => ConversationService.formatDateTime(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Button 
          type="link" 
          icon={<EyeOutlined />} 
          onClick={() => handleView(record)}
        >
          查看
        </Button>
      ),
    },
  ];

  /**
   * 查看对话详情
   * 打开详情弹窗并加载指定对话的详细信息。
   */
  const handleView = async (record: Conversation) => {
    const request = ++detailRequest.current;
    setSelectedConversation(null);
    try {
      setDetailLoading(true);
      setIsModalVisible(true);
      // 使用record.id而不是conversationId，因为API响应中只有id字段
        const detail = await ConversationService.getConversationDetail(record.id);
        if (request !== detailRequest.current) return;
      setSelectedConversation(detail);
      } catch (error) {
        if (request !== detailRequest.current) return;
      message.error('获取对话详情失败: ' + (error as Error).message);
      setIsModalVisible(false);
    } finally {
        if (request === detailRequest.current) setDetailLoading(false);
    }
  };



  /**
   * 关闭详情弹窗
   */
  const handleModalCancel = () => {
    ++detailRequest.current;
    setIsModalVisible(false);
    setSelectedConversation(null);
  };

  /**
   * 用户名搜索
   */
  const handleUsernameSearch = (value: string) => {
    setSearchParams(prev => ({
      ...prev,
      pageNum: 1,
      username: value || undefined,
    }));
  };

  /**
   * 问题内容搜索
   */
  const handleContentSearch = (value: string) => {
    setSearchParams(prev => ({
      ...prev,
      pageNum: 1,
      content: value || undefined,
    }));
  };

  /**
   * 模型筛选（数值类型）
   */
  const handleModelFilter = (modelId: string | number | null) => {
    setSearchParams(prev => ({
      ...prev,
      pageNum: 1,
      model: modelId == null ? undefined : String(modelId),
    }));
  };

  /**
   * 分页变化处理
   * 同步更新 pageNum 与 pageSize。
   */
  const handleTableChange = (page: number, pageSize: number) => {
    setSearchParams(prev => ({
      ...prev,
      pageNum: page,
      pageSize: pageSize,
    }));
  };

  return (
    <div>
      <Card>
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            <Search
              placeholder="搜索用户名"
              allowClear
              enterButton={<SearchOutlined />}
              size="middle"
              style={{ width: 220 }}
              onSearch={handleUsernameSearch}
            />
            <Search
              placeholder="搜索问题内容"
              allowClear
              enterButton={<SearchOutlined />}
              size="middle"
              style={{ width: 260 }}
              onSearch={handleContentSearch}
            />
            <InputNumber
              stringMode
              precision={0}
              min="1"
              placeholder="模型ID"
              style={{ width: 160 }}
              controls={false}
              onChange={handleModelFilter}
            />
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={conversations}
          loading={loading}
          rowKey="id"
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
        title="对话详情"
        open={isModalVisible}
        onCancel={handleModalCancel}
        footer={[
          <Button key="close" onClick={handleModalCancel}>
            关闭
          </Button>
        ]}
        width={800}
        loading={detailLoading}
      >
        {selectedConversation && (
          <div>
            <Descriptions column={2} bordered>
              <Descriptions.Item label="对话ID">{selectedConversation.id}</Descriptions.Item>
              <Descriptions.Item label="用户">{selectedConversation.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="模型">{selectedConversation.model || '-'}</Descriptions.Item>
              <Descriptions.Item label="平台 Token">{tokenText(selectedConversation.tokens)}（已记录 {selectedConversation.meteredReplies ?? 0}/{selectedConversation.totalReplies ?? 0} 次回复）</Descriptions.Item>
              <Descriptions.Item label="预估费用" span={2}>{costText(selectedConversation.estimatedCost)}（已计价 {selectedConversation.pricedReplies ?? 0}/{selectedConversation.totalReplies ?? 0} 次回复；未计价部分不含在内）</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {ConversationService.formatDateTime(selectedConversation.createTime)}
              </Descriptions.Item>
            </Descriptions>
            
            <div style={{ marginTop: 16 }}>
              <h4>对话消息：</h4>
              {selectedConversation.messages && selectedConversation.messages.length > 0 ? (
                selectedConversation.messages.map((message, index) => (
                  <div key={message.id || index} style={{ marginBottom: 16 }}>
                    <div style={{ fontWeight: 'bold', marginBottom: 4 }}>
                      {message.role === 'user' ? '用户问题：' : 'AI回答：'}
                    </div>
                    <ConversationUsage message={message} />
                    <TextArea 
                      value={message.content} 
                      readOnly 
                      rows={message.role === 'user' ? 2 : 4}
                      style={{ marginBottom: 8 }}
                    />
                    <div style={{ fontSize: '12px', color: '#999' }}>
                      {ConversationService.formatDateTime(message.createTime)}
                    </div>
                  </div>
                ))
              ) : (
                <div>
                  <h4>用户问题：</h4>
                  <TextArea 
                    value={selectedConversation.title || '暂无问题'} 
                    readOnly 
                    rows={3}
                    style={{ marginBottom: 16 }}
                  />
                  
                  <h4>AI回答：</h4>
                  <TextArea 
                    value={'暂无回答'} 
                    readOnly 
                    rows={6}
                  />
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Conversations;
