import React from 'react';
import MarketAdminPage, { MarketField, MarketRecord, statusTag } from './MarketAdminPage';

const fields: MarketField[] = [
  { name: 'agentName', label: '代理名称', required: true },
  { name: 'parentName', label: '上级代理' },
  { name: 'region', label: '业务区域', required: true },
  { name: 'relationType', label: '关系类型', type: 'select', required: true, options: [
    { value: '直营', label: '直营' },
    { value: '分销', label: '分销' },
    { value: '合作', label: '合作' },
  ] },
  { name: 'status', label: '状态', type: 'select', required: true, options: [
    { value: '启用', label: '启用' },
    { value: '停用', label: '停用' },
  ] },
];

const seed: MarketRecord[] = [
  { id: 'agent-relation-001', agentName: '杭州智诊代理', parentName: '华东大区', region: '浙江杭州', relationType: '直营', status: '启用', updateTime: '2026-01-14T10:00:00.000Z' },
];

const AgentRelation: React.FC = () => (
  <MarketAdminPage
    title="代理关系"
    storageKey="feibo-admin-agent-relation"
    seed={seed}
    fields={fields}
    keywordFields={['agentName', 'parentName', 'region']}
    columns={[
      { title: '代理名称', dataIndex: 'agentName', width: 180 },
      { title: '上级代理', dataIndex: 'parentName', width: 160 },
      { title: '业务区域', dataIndex: 'region', width: 140 },
      { title: '关系类型', dataIndex: 'relationType', width: 110 },
      { title: '状态', dataIndex: 'status', width: 100, render: statusTag },
    ]}
  />
);

export default AgentRelation;
