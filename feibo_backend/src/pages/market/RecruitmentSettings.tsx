import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'policyName', label: '政策名称', required: true },
  { name: 'targetRole', label: '适用对象', required: true },
  { name: 'rewardAmount', label: '奖励金额', type: 'number', required: true },
  { name: 'threshold', label: '达标条件', required: true },
  { name: 'status', label: '状态', type: 'select', required: true, options: [
    { value: '启用', label: '启用' },
    { value: '停用', label: '停用' },
  ] },
  { name: 'description', label: '政策说明', type: 'textarea' },
];

const seed: MarketRecord[] = [
  { id: 'recruitment-001', policyName: '平台招商奖励', targetRole: '代理商', rewardAmount: 5000, threshold: '首月有效客户不少于 10 个', status: '启用', description: '用于激励代理商拓展区域市场', updateTime: '2026-01-16T10:00:00.000Z' },
];

const RecruitmentSettings: React.FC = () => (
  <MarketAdminPage
    title="招商设置"
    storageKey="feibo-admin-recruitment-settings"
    seed={seed}
    fields={fields}
    keywordFields={['policyName', 'targetRole', 'threshold', 'description']}
    columns={[
      { title: '政策名称', dataIndex: 'policyName', width: 180 },
      { title: '适用对象', dataIndex: 'targetRole', width: 120 },
      { title: '奖励金额', dataIndex: 'rewardAmount', width: 120, render: (v) => `￥${Number(v || 0).toLocaleString()}` },
      { title: '达标条件', dataIndex: 'threshold', width: 220, ellipsis: true },
      { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
      { title: '说明', dataIndex: 'description', ellipsis: true },
    ]}
  />
);

export default RecruitmentSettings;
