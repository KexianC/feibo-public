import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'name', label: '等级名称', required: true },
  { name: 'minPerformance', label: '最低业绩', type: 'number', required: true },
  { name: 'commissionRate', label: '佣金比例（%）', type: 'number', required: true },
  { name: 'status', label: '状态', type: 'select', required: true, options: [
    { value: '启用', label: '启用' },
    { value: '停用', label: '停用' },
  ] },
  { name: 'description', label: '等级说明', type: 'textarea' },
];

const seed: MarketRecord[] = [
  { id: 'agent-level-001', name: '一级代理', minPerformance: 300000, commissionRate: 15, status: '启用', description: '面向省级或重点城市代理', updateTime: '2026-01-13T10:00:00.000Z' },
  { id: 'agent-level-002', name: '城市代理', minPerformance: 80000, commissionRate: 8, status: '启用', description: '面向单城市代理', updateTime: '2026-01-13T10:00:00.000Z' },
];

const AgentLevel: React.FC = () => (
  <MarketAdminPage
    title="代理等级"
    storageKey="feibo-admin-agent-level"
    seed={seed}
    fields={fields}
    keywordFields={['name', 'description']}
    columns={[
      { title: '等级名称', dataIndex: 'name', width: 160 },
      { title: '最低业绩', dataIndex: 'minPerformance', width: 140, render: (v) => Number(v || 0).toLocaleString() },
      { title: '佣金比例', dataIndex: 'commissionRate', width: 110, render: (v) => `${v}%` },
      { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
      { title: '说明', dataIndex: 'description', ellipsis: true },
    ]}
  />
);

export default AgentLevel;
