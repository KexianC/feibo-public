import React, { useMemo, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { createLocalRecord, loadLocalRecords, LocalRecord, saveLocalRecords, touchLocalRecord } from '@/services/localAdminStore';

export interface MarketRecord extends LocalRecord {
  [key: string]: any;
}

export interface MarketField {
  name: string;
  label: string;
  type?: 'text' | 'number' | 'select' | 'textarea';
  required?: boolean;
  options?: { value: string | number; label: string }[];
}

interface MarketAdminPageProps {
  title: string;
  storageKey: string;
  seed: MarketRecord[];
  fields: MarketField[];
  columns: ColumnsType<MarketRecord>;
  keywordFields: string[];
  statusField?: string;
}

const renderField = (field: MarketField) => {
  if (field.type === 'number') {
    return <InputNumber min={0} style={{ width: '100%' }} />;
  }
  if (field.type === 'select') {
    return <Select options={field.options || []} />;
  }
  if (field.type === 'textarea') {
    return <Input.TextArea rows={4} />;
  }
  return <Input />;
};

const MarketAdminPage: React.FC<MarketAdminPageProps> = ({
  title,
  storageKey,
  seed,
  fields,
  columns,
  keywordFields,
  statusField = 'status',
}) => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<MarketRecord[]>(() => loadLocalRecords(storageKey, seed));
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>();
  const [editing, setEditing] = useState<MarketRecord | null>(null);
  const [visible, setVisible] = useState(false);

  const statusOptions = useMemo(() => {
    const values = Array.from(new Set(records.map(item => item[statusField]).filter(Boolean)));
    return values.map(value => ({ value, label: String(value) }));
  }, [records, statusField]);

  const filteredRecords = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    return records
      .filter(item => !status || item[statusField] === status)
      .filter(item => !text || keywordFields.some(field => String(item[field] ?? '').toLowerCase().includes(text)));
  }, [records, keyword, keywordFields, status, statusField]);

  const persist = (next: MarketRecord[]) => {
    setRecords(next);
    saveLocalRecords(storageKey, next);
  };

  const openEditor = (record?: MarketRecord) => {
    setEditing(record || null);
    form.setFieldsValue(record || {});
    setVisible(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      persist(records.map(item => item.id === editing.id ? touchLocalRecord({ ...item, ...values }) : item));
      message.success('保存成功');
    } else {
      persist([...records, createLocalRecord<MarketRecord>(values)]);
      message.success('新增成功');
    }
    setVisible(false);
    form.resetFields();
  };

  const actionColumn: ColumnsType<MarketRecord>[number] = {
    title: '操作',
    width: 150,
    fixed: 'right',
    render: (_, record) => (
      <Space>
        <Button type="link" onClick={() => openEditor(record)}>编辑</Button>
        <Popconfirm title="确认删除该记录？" onConfirm={() => {
          persist(records.filter(item => item.id !== record.id));
          message.success('删除成功');
        }}>
          <Button type="link" danger>删除</Button>
        </Popconfirm>
      </Space>
    ),
  };

  return (
    <Card title={title} extra={<Button type="primary" onClick={() => openEditor()}>新增</Button>}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="搜索关键词"
          allowClear
          style={{ width: 260 }}
          onSearch={setKeyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        {statusOptions.length > 0 && (
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 140 }}
            value={status}
            onChange={setStatus}
            options={statusOptions}
          />
        )}
        <Button onClick={() => { setKeyword(''); setStatus(undefined); }}>重置</Button>
      </Space>
      <Table<MarketRecord>
        rowKey="id"
        dataSource={filteredRecords}
        columns={[
          ...columns,
          {
            title: '更新时间',
            dataIndex: 'updateTime',
            width: 170,
            render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-',
          },
          actionColumn,
        ]}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={`${editing ? '编辑' : '新增'}${title}`}
        open={visible}
        onOk={handleSubmit}
        onCancel={() => { setVisible(false); form.resetFields(); }}
        okText="保存"
        width={640}
      >
        <Form form={form} layout="vertical">
          {fields.map(field => (
            <Form.Item
              key={field.name}
              name={field.name}
              label={field.label}
              rules={field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined}
            >
              {renderField(field)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </Card>
  );
};

export const statusTag = (value: string) => {
  const colorMap: Record<string, string> = {
    启用: 'green',
    停用: 'default',
    待审核: 'orange',
    已通过: 'green',
    已驳回: 'red',
    正常: 'green',
    冻结: 'red',
  };
  return <Tag color={colorMap[value] || 'blue'}>{value || '-'}</Tag>;
};

export default MarketAdminPage;
