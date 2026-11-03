import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'name', label: '代理名称', required: true },
  { name: 'owner', label: '负责人', required: true },
  { name: 'settlementAccount', label: '结算账户', required: true },
  { name: 'commissionRate', label: '佣金比例（%）', type: 'number', required: true },
  { name: 'status', label: '状态', type: 'select', required: true, options: [
    { value: '启用', label: '启用' },
    { value: '停用', label: '停用' },
  ] },
  { name: 'remark', label: '备注', type: 'textarea' },
];

const seed: MarketRecord[] = [
  { id: 'agent-manage-001', name: '杭州智诊代理', owner: '张经理', settlementAccount: 'HZ-2026-001', commissionRate: 12, status: '启用', remark: '华东区域重点代理', updateTime: '2026-01-12T10:00:00.000Z' },
];

const AgentManage: React.FC = () => (
  <MarketAdminPage
    title="代理维护"
    storageKey="feibo-admin-agent-manage"
    seed={seed}
    fields={fields}
    keywordFields={['name', 'owner', 'settlementAccount']}
    columns={[
      { title: '代理名称', dataIndex: 'name', width: 180 },
      { title: '负责人', dataIndex: 'owner', width: 120 },
      { title: '结算账户', dataIndex: 'settlementAccount', width: 180 },
      { title: '佣金比例', dataIndex: 'commissionRate', width: 110, render: (v) => `${v}%` },
      { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
      { title: '备注', dataIndex: 'remark', ellipsis: true },
    ]}
  />
);

export default AgentManage;
