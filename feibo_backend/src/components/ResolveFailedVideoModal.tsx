import { useEffect, useState } from 'react';
import { Alert, Checkbox, Form, Input, Modal, Typography, message } from 'antd';

export interface ResolveFailedVideoInput {
  reason: string;
  upstreamConfirmedFailed: true;
}

interface Props {
  open: boolean;
  taskId?: string;
  onCancel: () => void;
  onResolve: (input: ResolveFailedVideoInput) => Promise<void>;
  onResolved: () => void;
}

export default function ResolveFailedVideoModal({ open, taskId, onCancel, onResolve, onResolved }: Props) {
  const [form] = Form.useForm<{ reason: string; upstreamConfirmedFailed: boolean }>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { if (open) { form.resetFields(); setError(''); } }, [open, taskId, form]);
  const submit = async () => {
    if (saving) return;
    try {
      const values = await form.validateFields();
      setSaving(true); setError('');
      await onResolve({ reason: values.reason.trim(), upstreamConfirmedFailed: true });
      message.success('任务已核销，尚未消耗的预占额度已释放');
      onResolved();
    } catch (failure) {
      if (!(failure && typeof failure === 'object' && 'errorFields' in failure)) {
        setError(failure instanceof Error ? failure.message : '核销失败，请重试');
      }
    } finally { setSaving(false); }
  };
  return <Modal title="确认供应商失败并退还预占额度" open={open} width={640}
    onCancel={() => { if (!saving) onCancel(); }} onOk={submit} confirmLoading={saving}
    okText="确认失败并退还额度" cancelText="取消" maskClosable={false} closable={!saving}
    cancelButtonProps={{ disabled: saving }}>
    <Typography.Paragraph>任务编号：<Typography.Text code copyable>{taskId || '—'}</Typography.Text></Typography.Paragraph>
    <Alert type="warning" showIcon style={{ marginBottom: 16 }}
      message="先在供应商侧核实该任务已失败或未受理，再执行核销。"
      description="核销会将任务标记为失败并释放预占额度。已成功或仍在生成的任务不可通过此操作取消；原因与操作人会保留在审计记录中。" />
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={saving}>
      <Form.Item name="reason" label="核销原因与核查依据"
        rules={[{ required: true, whitespace: true, message: '请填写核销原因和核查依据' }, { max: 500, message: '最多 500 个字符' }]}>
        <Input.TextArea rows={4} maxLength={500} showCount placeholder="填写供应商返回的失败原因、核查时间或工单编号" />
      </Form.Item>
      <Form.Item name="upstreamConfirmedFailed" valuePropName="checked"
        rules={[{ validator: (_, value) => value === true ? Promise.resolve() : Promise.reject(new Error('请先确认供应商侧核查结果')) }]}>
        <Checkbox>我已确认供应商任务失败或未受理，且不会继续生成</Checkbox>
      </Form.Item>
    </Form>
  </Modal>;
}
