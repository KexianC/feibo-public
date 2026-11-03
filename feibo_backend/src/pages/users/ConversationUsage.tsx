import { Collapse, Descriptions, Space, Tag, Typography } from 'antd';
import type { ConversationMessage } from '../../services/conversationService';

export const tokenText = (value?: number | null) => value == null ? '未记录' : value.toLocaleString('zh-CN');
export const costText = (value?: number | string | null) => {
  if (value == null || value === '') return '未计价';
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) return '未计价';
  if (amount > 0 && amount < 0.00000001) return '¥<0.00000001';
  return `¥${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 8 })}`;
};
const costLabels: Record<string, string> = {
  USAGE_NOT_REPORTED: '平台未返回用量', PRICE_UNCONFIRMED: '价格待确认',
  CACHE_NOT_REPORTED: '缓存用量未返回，暂不计价', INVALID_USAGE: '用量异常，暂不计价',
};
const providers: Record<string, string> = {
  deepseek: 'DeepSeek', qwen: '阿里云百炼', doubao: '火山方舟', kimik3: 'Kimi', glm52: '智谱',
  hy3: '腾讯混元', qianfan: '百度千帆', baichuan: '百川', ant: '蚂蚁安诊儿', wisediag: '智诊', szdoctor: '木吾木同',
};
export default function ConversationUsage({ message }: { message: ConversationMessage }) {
  const usage = message.usage;
  if (message.role !== 'assistant') return null;
  const measured = usage?.usageSource === 'PROVIDER';
  return <div style={{ margin: '8px 0 12px' }}>
    <Space wrap size={[8, 6]}>
      <Tag color="blue">{message.model || '模型未记录'}</Tag>
      {usage?.provider && <Tag>{providers[usage.provider] || usage.provider}</Tag>}
      <Tag color={measured ? 'green' : 'default'}>{measured ? '平台用量' : '用量未记录'}</Tag>
      <span>输入 {tokenText(usage?.inputTokens)}</span>
      <span>输出 {tokenText(usage?.outputTokens)}</span>
      <span>缓存命中 {tokenText(usage?.cachedTokens)}（含在输入中）</span>
      <span>合计 {tokenText(usage?.totalTokens)} Token</span>
      <Typography.Text strong>{usage?.costStatus === 'ESTIMATED'
        ? `预估费用 ${costText(usage.estimatedCost)}`
        : costLabels[usage?.costStatus || 'USAGE_NOT_REPORTED'] || '未计价'}</Typography.Text>
    </Space>
    {!usage && message.legacyEstimatedTokens != null && <div style={{ color: '#8c8c8c', marginTop: 4 }}>
      历史文本估算：{tokenText(message.legacyEstimatedTokens)} Token（仅回答文本，不用于计费）
    </div>}
    {usage && <Collapse size="small" ghost items={[{ key: 'price', label: '查看计费依据', children:
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="输入单价">{usage.inputPrice ?? '未确认'} 元/百万 Token</Descriptions.Item>
        <Descriptions.Item label="输出单价">{usage.outputPrice ?? '未确认'} 元/百万 Token</Descriptions.Item>
        <Descriptions.Item label="缓存命中单价">{usage.cachePrice ?? '未确认'} 元/百万 Token</Descriptions.Item>
        <Descriptions.Item label="推理 Token">{tokenText(usage.reasoningTokens)}（含在输出中）</Descriptions.Item>
        <Descriptions.Item label="价格快照">{usage.priceVersion || '未确认'}</Descriptions.Item>
        <Descriptions.Item label="请求时间">{usage.requestedAt ? new Date(usage.requestedAt).toLocaleString('zh-CN') : '未记录'}</Descriptions.Item>
        {usage.responseModel && <Descriptions.Item label="平台返回模型" span={2}>{usage.responseModel}</Descriptions.Item>}
        {usage.priceNote && <Descriptions.Item label="计价口径" span={2}>{usage.priceNote}</Descriptions.Item>}
        {usage.priceSource && /^https:\/\//.test(usage.priceSource) && <Descriptions.Item label="官方来源" span={2}>
          <a href={usage.priceSource} target="_blank" rel="noopener noreferrer">官方价格说明</a>
        </Descriptions.Item>}
        <Descriptions.Item label="实际余额扣款" span={2}>待平台账单核对；预估费用不代表现金余额扣款。</Descriptions.Item>
      </Descriptions>
    }]} />}
  </div>;
}
