import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'name', label: '代理名称', required: true },
  { name: 'contact', label: '联系人', required: true },
  { name: 'phone', label: '联系电话', required: true },
  { name: 'region', label: '所属区域', required: true },
  { name: 'level', label: '代理等级', type: 'select', required: true, options: [
    { value: '一级代理', label: '一级代理' },
    { value: '二级代理', label: '二级代理' },
    { value: '城市代理', label: '城市代理' },
  ] },
  { name: 'status', label: '状态', type: 'select', required: true, options: [
    { value: '正常', label: '正常' },
    { value: '冻结', label: '冻结' },
  ] },
];

const seed: MarketRecord[] = [
  { id: 'agent-001', name: '杭州示例代理', contact: '示例联系人甲', phone: '', region: '浙江杭州', level: '一级代理', status: '正常', updateTime: '2026-01-10T10:00:00.000Z' },
  { id: 'agent-002', name: '上海示例代理', contact: '示例联系人乙', phone: '', region: '上海', level: '城市代理', status: '正常', updateTime: '2026-01-11T10:00:00.000Z' },
];

const AgentList: React.FC = () => (
  <MarketAdminPage
    title="代理查询"
    storageKey="feibo-admin-agent-list"
    seed={seed}
    fields={fields}
    keywordFields={['name', 'contact', 'phone', 'region']}
    columns={[
      { title: '代理名称', dataIndex: 'name', width: 180 },
      { title: '联系人', dataIndex: 'contact', width: 120 },
      { title: '联系电话', dataIndex: 'phone', width: 140 },
      { title: '所属区域', dataIndex: 'region', width: 140 },
      { title: '代理等级', dataIndex: 'level', width: 120 },
      { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
    ]}
  />
);

export default AgentList;
