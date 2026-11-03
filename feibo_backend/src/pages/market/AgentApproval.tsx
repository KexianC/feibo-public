import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'applicant', label: '申请人', required: true },
  { name: 'company', label: '申请公司', required: true },
  { name: 'phone', label: '联系电话', required: true },
  { name: 'region', label: '申请区域', required: true },
  { name: 'status', label: '审核状态', type: 'select', required: true, options: [
    { value: '待审核', label: '待审核' },
    { value: '已通过', label: '已通过' },
    { value: '已驳回', label: '已驳回' },
  ] },
  { name: 'remark', label: '审核备注', type: 'textarea' },
];

const seed: MarketRecord[] = [
  { id: 'agent-approval-001', applicant: '示例申请人', company: '宁波示例公司', phone: '', region: '浙江宁波', status: '待审核', remark: '待补充营业执照', updateTime: '2026-01-15T10:00:00.000Z' },
];

const AgentApproval: React.FC = () => (
  <MarketAdminPage
    title="代理审核"
    storageKey="feibo-admin-agent-approval"
    seed={seed}
    fields={fields}
    keywordFields={['applicant', 'company', 'phone', 'region']}
    columns={[
      { title: '申请人', dataIndex: 'applicant', width: 120 },
      { title: '申请公司', dataIndex: 'company', width: 180 },
      { title: '联系电话', dataIndex: 'phone', width: 140 },
      { title: '申请区域', dataIndex: 'region', width: 140 },
      { title: '审核状态', dataIndex: 'status', width: 110, render: statusTag },
      { title: '备注', dataIndex: 'remark', ellipsis: true },
    ]}
  />
);

export default AgentApproval;
