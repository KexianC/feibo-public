import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert, Button, Card, Col, Descriptions, Empty, Form, Input, InputNumber,
  Modal, Row, Select, Space, Spin, Switch, Table, Tabs, Tag, Typography, message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { EditOutlined, GiftOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { v4 as uuidv4 } from 'uuid';
import UserService, { type User } from '@/services/userService';
import { filterEntitlementProducts, type ProductFilters } from '@/services/packageContracts';
import {
  EntitlementAdminService,
  type EntitlementAccount, type EntitlementConfig, type EntitlementGrantInput, type EntitlementGrantRecord,
  type EntitlementModel, type EntitlementProduct, type EntitlementProductInput,
  type FreePlanEntitlements,
} from '@/services/entitlementAdminService';

const { Text, Paragraph } = Typography;
const fullWidth = { width: '100%' };
const required = [{ required: true, message: '请填写此项' }];
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试';
const isFormError = (error: unknown) => Boolean(error && typeof error === 'object' && 'errorFields' in error);
const dailyLimit = (value: number) => value === -1 ? '不限' : `${value} 次 / 天`;
const duration = (days: number) => days === -1 ? '永久' : `${days} 天`;
const dateTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '永久';
const seconds = (value: number) => {
  if (!Number.isFinite(Number(value))) return '—';
  const amount = Number(value);
  if (amount < 0) return '不限';
  const minutes = Math.floor(amount / 60);
  const rest = amount % 60;
  return minutes > 0 ? `${minutes} 分钟${rest ? ` ${rest} 秒` : ''}` : `${rest} 秒`;
};

const integerRule = (minimum: number) => ({
  validator: (_: unknown, value: unknown) => (
    value === undefined || value === null || (Number.isSafeInteger(value) && Number(value) >= minimum)
      ? Promise.resolve()
      : Promise.reject(new Error(`请输入不小于 ${minimum} 的整数`))
  ),
});

const QuotaFields: React.FC<{ daily?: boolean; credits?: boolean; optional?: boolean }> = ({ daily, credits, optional }) => (
  <Row gutter={16}>
    {daily && <Col xs={24} md={12}>
      <Form.Item label="高级模型每日对话" name="premiumChatDailyLimit"
        extra={optional ? '留空沿用现有权益；与免费版及已有会员取较高值；-1 表示不限' : '-1 表示不限；0 表示本项不提供每日赠送次数'}
        rules={[...(optional ? [] : required), integerRule(-1)]}>
        <InputNumber min={-1} max={1000000} precision={0} addonAfter="次 / 天" style={fullWidth} />
      </Form.Item>
    </Col>}
    {credits && <Col xs={24} md={12}>
      <Form.Item label="高级模型额外对话次数" name="premiumChatCredits"
        extra="每日赠送次数耗尽后使用额外次数" rules={[...(optional ? [] : required), integerRule(0)]}>
        <InputNumber min={0} max={1000000000} precision={0} addonAfter="次" style={fullWidth} />
      </Form.Item>
    </Col>}
    <Col xs={24} md={12}>
      <Form.Item label="数字人视频合成额度" name="digitalHumanSeconds"
        extra="单位为秒；300 秒 = 5 分钟，按实际生成累计扣减"
        rules={[...(optional ? [] : required), integerRule(0)]}>
        <InputNumber min={0} max={1000000000} precision={0} addonAfter="秒" style={fullWidth} />
      </Form.Item>
    </Col>
    <Col xs={24} md={12}>
      <Form.Item label="AI 生成视频额度" name="aiVideoSeconds"
        extra="单位为秒；60 秒 = 1 分钟，按实际生成累计扣减"
        rules={[...(optional ? [] : required), integerRule(0)]}>
        <InputNumber min={0} max={1000000000} precision={0} addonAfter="秒" style={fullWidth} />
      </Form.Item>
    </Col>
  </Row>
);

const ProductTable: React.FC<{
  kind: 'plans' | 'packs'; products: EntitlementProduct[]; onEdit: (item?: EntitlementProduct) => void;
}> = ({ kind, products, onEdit }) => {
  const [filters, setFilters] = useState<ProductFilters>({});
  const [appliedFilters, setAppliedFilters] = useState<ProductFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const filteredProducts = filterEntitlementProducts(products, appliedFilters);
  const applyFilters = () => {
    if (filters.minPrice !== undefined && filters.maxPrice !== undefined && filters.minPrice > filters.maxPrice) {
      message.error('最低价不能大于最高价'); return;
    }
    setAppliedFilters({ ...filters }); setPage(1);
  };
  const resetFilters = () => { setFilters({}); setAppliedFilters({}); setPage(1); };
  const columns: TableColumnsType<EntitlementProduct> = [
    { title: '名称', dataIndex: 'name', width: 180,
      render: (name, item) => <Space direction="vertical" size={0}><Text strong>{name}</Text><Text type="secondary" style={{ fontSize: 12 }}>{item.productId}</Text></Space> },
    { title: '价格', dataIndex: 'price', width: 100, render: value => `¥${Number(value).toFixed(2)}` },
    { title: '有效期', dataIndex: 'durationDays', width: 100, render: duration },
    ...(kind === 'plans' ? [{ title: '高级模型每日对话', dataIndex: 'premiumChatDailyLimit', width: 145, render: dailyLimit }] : []),
    { title: '额外对话', dataIndex: 'premiumChatCredits', width: 100, render: value => `${value} 次` },
    { title: '数字人视频', dataIndex: 'digitalHumanSeconds', width: 140, render: seconds },
    { title: 'AI 视频', dataIndex: 'aiVideoSeconds', width: 140, render: seconds },
    { title: '状态', dataIndex: 'active', width: 130, render: (active, item) => item.configured === false
      ? <Tag color="orange">待配置权益</Tag> : <Tag color={active ? 'green' : 'default'}>{active ? '已启用' : '已停用'}</Tag> },
    { title: '操作', key: 'action', fixed: 'right', width: 100,
      render: (_, item) => <Button type="link" icon={<EditOutlined />} onClick={() => onEdit(item)}>编辑</Button> },
  ];
  return <>
    <Space style={{ marginBottom: 16 }} wrap>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => onEdit()}>{kind === 'plans' ? '新增会员类型' : '新增充值包'}</Button>
      <Text type="secondary">{kind === 'plans' ? '普通模型对所有会员免费不限次；每日高级对话取免费版及有效会员的最高上限。' : '充值包提供额外次数和视频时长，付款成功后由服务端发放。'}</Text>
    </Space>
    <Paragraph type="secondary">配置保存后用于后续购买与发放，已获得的权益保留发放时的额度及有效期。历史商品需先保存权益配置。</Paragraph>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input placeholder="搜索名称或商品 ID" aria-label="商品名称或 ID" value={filters.keyword ?? ''} allowClear maxLength={64}
        onChange={event => setFilters({ ...filters, keyword: event.target.value })} onPressEnter={applyFilters} style={{ width: 230 }} />
      <Select placeholder="启用状态" aria-label="商品启用状态" value={filters.active} allowClear style={{ width: 130 }}
        onChange={active => setFilters({ ...filters, active })} options={[{ label: '已启用', value: true }, { label: '已停用', value: false }]} />
      <InputNumber aria-label="最低价" placeholder="最低价" min={0} precision={2} value={filters.minPrice}
        onChange={value => setFilters({ ...filters, minPrice: value ?? undefined })} />
      <InputNumber aria-label="最高价" placeholder="最高价" min={0} precision={2} value={filters.maxPrice}
        onChange={value => setFilters({ ...filters, maxPrice: value ?? undefined })} />
      <Button icon={<SearchOutlined />} type="primary" onClick={applyFilters}>搜索</Button>
      <Button icon={<ReloadOutlined />} onClick={resetFilters}>重置</Button>
    </Space>
    <Table columns={columns} dataSource={filteredProducts} rowKey="productId" scroll={{ x: 1100 }}
      pagination={{ current: page, pageSize, showSizeChanger: true, total: filteredProducts.length,
        onChange: (nextPage, nextSize) => { setPage(nextSize === pageSize ? nextPage : 1); setPageSize(nextSize); } }} />
  </>;
};

interface GrantFormValues extends Omit<EntitlementGrantInput, 'requestId'> { mode: 'plan' | 'custom' }

const AccountGrants: React.FC<{ plans: EntitlementProduct[] }> = ({ plans }) => {
  const [keyword, setKeyword] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User>();
  const [account, setAccount] = useState<EntitlementAccount>();
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [grantVisible, setGrantVisible] = useState(false);
  const [grantSaving, setGrantSaving] = useState(false);
  const [grantError, setGrantError] = useState('');
  const [form] = Form.useForm<GrantFormValues>();
  const mode = Form.useWatch('mode', form);
  const selectedPlanId = Form.useWatch('planId', form);
  const selectedPlan = plans.find(plan => plan.productId === selectedPlanId);
  const searchGeneration = useRef(0);
  const accountGeneration = useRef(0);
  const pendingGrant = useRef<{ userId: string; input: EntitlementGrantInput }>();

  useEffect(() => () => { searchGeneration.current++; accountGeneration.current++; }, []);

  const searchUsers = async (nextPage = 1) => {
    const generation = ++searchGeneration.current;
    setSearching(true);
    try {
      const result = await UserService.getUsersPage({ page: nextPage, size: 10, keyword: keyword.trim() || undefined });
      if (generation !== searchGeneration.current) return;
      setUsers(result.records); setPage(nextPage); setTotal(result.total);
    } catch (error) {
      if (generation === searchGeneration.current) message.error(errorText(error));
    } finally {
      if (generation === searchGeneration.current) setSearching(false);
    }
  };

  const loadAccount = async (user: User) => {
    const generation = ++accountGeneration.current;
    setSelectedUser(user); setAccount(undefined); setAccountError(''); setAccountLoading(true);
    try {
      const result = await EntitlementAdminService.getAccount(String(user.userId));
      if (generation === accountGeneration.current) setAccount(result);
    } catch (error) {
      if (generation === accountGeneration.current) setAccountError(errorText(error));
    } finally {
      if (generation === accountGeneration.current) setAccountLoading(false);
    }
  };

  const openGrant = () => {
    pendingGrant.current = undefined;
    setGrantError('');
    form.resetFields();
    form.setFieldsValue({ mode: 'custom', durationDays: -1, digitalHumanSeconds: 0, aiVideoSeconds: 0, premiumChatCredits: 0 });
    setGrantVisible(true);
  };

  const saveGrant = async () => {
    if (!selectedUser || grantSaving) return;
    try {
      const values = await form.validateFields();
      const userId = String(selectedUser.userId);
      if (!pendingGrant.current) {
        const input: EntitlementGrantInput = {
          requestId: uuidv4(), reason: values.reason.trim(),
          ...(values.mode === 'plan' ? { planId: values.planId } : {
            durationDays: values.durationDays,
            ...(values.premiumChatDailyLimit !== undefined && values.premiumChatDailyLimit !== null ? { premiumChatDailyLimit: values.premiumChatDailyLimit } : {}),
            digitalHumanSeconds: values.digitalHumanSeconds ?? 0,
            aiVideoSeconds: values.aiVideoSeconds ?? 0,
            premiumChatCredits: values.premiumChatCredits ?? 0,
          }),
        };
        if (values.mode === 'custom' && input.premiumChatDailyLimit === undefined
          && !input.digitalHumanSeconds && !input.aiVideoSeconds && !input.premiumChatCredits) {
          setGrantError('请至少填写一项要发放的权益。'); return;
        }
        pendingGrant.current = { userId, input };
      }
      setGrantSaving(true); setGrantError('');
      await EntitlementAdminService.grant(pendingGrant.current.userId, pendingGrant.current.input);
      pendingGrant.current = undefined;
      setGrantVisible(false);
      message.success('权益已发放');
      await loadAccount(selectedUser);
    } catch (error) {
      if (!isFormError(error)) {
        // A timeout can happen after the transaction commits. Reconcile the
        // durable request ID before offering a retry or reporting failure.
        if (pendingGrant.current) {
          try {
            const result = await EntitlementAdminService.getAccount(pendingGrant.current.userId);
            const requestId = pendingGrant.current.input.requestId;
            if (result.grants.some(grant => grant.requestId === requestId || grant.requestId === `admin:${requestId}`)) {
              pendingGrant.current = undefined;
              setAccount(result); setGrantVisible(false);
              message.success('已核对发放记录，权益已到账');
              return;
            }
          } catch { /* Preserve the original request and original error. */ }
        }
        setGrantError(errorText(error));
      }
    } finally { setGrantSaving(false); }
  };

  const accountValue = (name: keyof EntitlementAccount): React.ReactNode => {
    const value = account?.[name];
    return value === undefined || value === null ? '—' : String(value);
  };
  const columns: TableColumnsType<User> = [
    { title: '昵称', dataIndex: 'nickName' },
    { title: '手机号', dataIndex: 'phoneNumber' },
    { title: '账号 ID', dataIndex: 'userId', render: String },
    { title: '操作', width: 130, render: (_, user) => <Button type="link" onClick={() => loadAccount(user)}>查看 / 发放权益</Button> },
  ];
  const grantColumns: TableColumnsType<EntitlementGrantRecord> = [
    { title: '发放时间（北京时间）', dataIndex: 'createdAt', width: 180, render: dateTime },
    { title: '会员类型', dataIndex: 'planName', width: 130, render: value => value || '额外权益' },
    { title: '每日高级对话', dataIndex: 'premiumChatDailyLimit', width: 130, render: value => value === undefined || value === null ? '沿用现有' : dailyLimit(value) },
    { title: '额外对话', dataIndex: 'premiumChatCredits', width: 100, render: value => `${value ?? 0} 次` },
    { title: '数字人视频', dataIndex: 'digitalHumanSeconds', width: 120, render: value => seconds(value ?? 0) },
    { title: 'AI 视频', dataIndex: 'aiVideoSeconds', width: 110, render: value => seconds(value ?? 0) },
    { title: '到期时间（北京时间）', dataIndex: 'expiresAt', width: 180, render: dateTime },
    { title: '发放原因', dataIndex: 'reason', width: 220 },
  ];

  return <Space direction="vertical" size={20} style={fullWidth}>
    <Alert type="info" showIcon message="按账号查看当前权益并发放会员或额外额度。管理员账号始终不限额。" />
    <Space wrap>
      <Input placeholder="搜索昵称或手机号" value={keyword} maxLength={64} allowClear
        onChange={event => setKeyword(event.target.value)} onPressEnter={() => searchUsers()} style={{ width: 280 }} />
      <Button type="primary" icon={<SearchOutlined />} loading={searching} onClick={() => searchUsers()}>查询账号</Button>
    </Space>
    <Table columns={columns} rowKey={user => String(user.userId)} dataSource={users} loading={searching}
      locale={{ emptyText: '输入昵称或手机号后查询，也可直接查询全部账号' }} scroll={{ x: 650 }}
      pagination={{ current: page, total, pageSize: 10, showSizeChanger: false, onChange: next => searchUsers(next) }} />
    {selectedUser && <Card size="small" title={`${selectedUser.nickName || '用户'} · ${selectedUser.phoneNumber || selectedUser.userId}`}
      extra={<Space><Button icon={<ReloadOutlined />} onClick={() => loadAccount(selectedUser)} disabled={accountLoading}>刷新权益</Button>
        <Button type="primary" icon={<GiftOutlined />} onClick={openGrant} disabled={!account || accountLoading}>发放权益</Button></Space>}>
      <Spin spinning={accountLoading}>
        {accountError && <Alert type="error" showIcon message={accountError} />}
        {account && <Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 3 }}>
          <Descriptions.Item label="账号 ID">{String(selectedUser.userId)}</Descriptions.Item>
          <Descriptions.Item label="会员类型">{accountValue('planName')}</Descriptions.Item>
          <Descriptions.Item label="管理员不限额">{account.unlimited ? '是' : '否'}</Descriptions.Item>
          <Descriptions.Item label="会员到期（北京时间）">{dateTime(account.expiresAt)}</Descriptions.Item>
          <Descriptions.Item label="高级模型每日上限">{account.premiumChatDailyLimit === undefined ? '—' : dailyLimit(Number(account.premiumChatDailyLimit))}</Descriptions.Item>
          <Descriptions.Item label="今日已用高级对话">{accountValue('premiumChatTodayUsed')}</Descriptions.Item>
          <Descriptions.Item label="额外对话剩余">{account.unlimited ? '不限' : accountValue('premiumChatCreditsRemaining')}</Descriptions.Item>
          <Descriptions.Item label="数字人视频剩余">{seconds(Number(account.digitalHumanSecondsRemaining))}</Descriptions.Item>
          <Descriptions.Item label="AI 视频剩余">{seconds(Number(account.aiVideoSecondsRemaining))}</Descriptions.Item>
        </Descriptions>}
        {account && <>
          <Paragraph type="secondary" style={{ marginTop: 12 }}>剩余额度以服务端结果为准，正在生成的任务会预占额度。</Paragraph>
          <Table size="small" columns={grantColumns} dataSource={account.grants ?? []} rowKey="requestId"
            title={() => '权益发放记录'} scroll={{ x: 1200 }} pagination={{ pageSize: 5, showSizeChanger: true }} />
        </>}
      </Spin>
    </Card>}
    <Modal title={`向 ${selectedUser?.nickName || selectedUser?.phoneNumber || '账号'} 发放权益`} open={grantVisible} width={720}
      onCancel={() => { if (!grantSaving) setGrantVisible(false); }} onOk={saveGrant} confirmLoading={grantSaving}
      okText={pendingGrant.current ? '重试同一笔发放' : '确认发放'} cancelText="取消" maskClosable={false} closable={!grantSaving}
      cancelButtonProps={{ disabled: grantSaving }}>
      <Paragraph type="secondary">账号 ID：{String(selectedUser?.userId ?? '')}。发放原因会随记录保存。</Paragraph>
      {grantError && <Alert style={{ marginBottom: 16 }} type="error" showIcon message={grantError}
        description={pendingGrant.current ? '本次请求已保留。重试会沿用同一笔发放编号，避免重复增加额度。' : undefined} />}
      <Form form={form} layout="vertical" disabled={grantSaving || Boolean(pendingGrant.current)}>
        <Form.Item label="发放方式" name="mode" rules={required}>
          <Select options={[{ label: '发放额外权益', value: 'custom' }, { label: '发放会员', value: 'plan' }]} />
        </Form.Item>
        {mode === 'plan' ? <>
          <Form.Item name="planId" label="会员类型" rules={required}>
            <Select placeholder="选择已启用会员" options={plans.filter(plan => plan.active && plan.configured !== false).map(plan => ({ label: `${plan.name}（${duration(plan.durationDays)}）`, value: plan.productId }))} />
          </Form.Item>
          {selectedPlan && <Descriptions size="small" bordered column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="每日高级对话">{dailyLimit(selectedPlan.premiumChatDailyLimit)}</Descriptions.Item>
            <Descriptions.Item label="额外对话">{selectedPlan.premiumChatCredits} 次</Descriptions.Item>
            <Descriptions.Item label="数字人视频">{seconds(selectedPlan.digitalHumanSeconds)}</Descriptions.Item>
            <Descriptions.Item label="AI 视频">{seconds(selectedPlan.aiVideoSeconds)}</Descriptions.Item>
          </Descriptions>}
          <Paragraph type="secondary">按所选会员配置发放有效期和权益；相同会员在有效期内再次发放会延长到期时间。</Paragraph>
        </> : <>
          <Form.Item label="权益有效期" name="durationDays" rules={[...required, integerRule(-1), { validator: (_, value) => value === 0 ? Promise.reject(new Error('有效期应大于 0，或填 -1 表示永久')) : Promise.resolve() }]}
            extra="-1 表示永久；视频额度在有效期内累计使用">
            <InputNumber min={-1} max={36500} precision={0} addonAfter="天" style={fullWidth} />
          </Form.Item>
          <QuotaFields daily credits optional />
        </>}
        <Form.Item label="发放原因" name="reason" rules={[{ required: true, whitespace: true, message: '请填写发放原因' }, { max: 500, message: '最多 500 个字符' }]}>
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="例如：活动奖励、客户补偿或会员赠送" />
        </Form.Item>
      </Form>
    </Modal>
  </Space>;
};

const MembershipEntitlements: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [config, setConfig] = useState<EntitlementConfig>();
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [saving, setSaving] = useState(false);
  const [modelSaving, setModelSaving] = useState<string>();
  const [editor, setEditor] = useState<{ kind: 'plans' | 'packs'; product?: EntitlementProduct }>();
  const [editorError, setEditorError] = useState('');
  const [freeForm] = Form.useForm<FreePlanEntitlements>();
  const [productForm] = Form.useForm<EntitlementProductInput>();
  const generation = useRef(0);
  const tab = location.pathname.endsWith('/plans') ? 'plans' : location.pathname.endsWith('/compute') ? 'packs'
    : location.pathname.endsWith('/grants') ? 'accounts' : 'free';
  const [activeTab, setActiveTab] = useState(tab);

  useEffect(() => setActiveTab(tab), [tab]);

  const loadConfig = useCallback(async () => {
    const current = ++generation.current;
    setLoading(true); setLoadError('');
    try {
      const result = await EntitlementAdminService.getConfig();
      if (current !== generation.current) return;
      setConfig(result); freeForm.setFieldsValue(result.freePlan);
    } catch (error) {
      if (current === generation.current) setLoadError(errorText(error));
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }, [freeForm]);

  useEffect(() => { void loadConfig(); return () => { generation.current++; }; }, [loadConfig]);

  const saveFree = async () => {
    try {
      const values = await freeForm.validateFields();
      setSaving(true);
      await EntitlementAdminService.saveFreePlan(values);
      message.success('免费版权益已保存');
      await loadConfig();
    } catch (error) { if (!isFormError(error)) message.error(errorText(error)); }
    finally { setSaving(false); }
  };

  const editProduct = (kind: 'plans' | 'packs', product?: EntitlementProduct) => {
    setEditorError(''); productForm.resetFields();
    productForm.setFieldsValue(product ?? {
      name: '', price: 0, durationDays: kind === 'plans' ? 30 : -1, active: false,
      premiumChatDailyLimit: 0, premiumChatCredits: 0, digitalHumanSeconds: 0, aiVideoSeconds: 0,
    });
    setEditor({ kind, product });
  };

  const saveProduct = async () => {
    if (!editor || saving) return;
    try {
      const values = await productForm.validateFields();
      const input: EntitlementProductInput = { ...values, name: values.name.trim(), premiumChatDailyLimit: editor.kind === 'packs' ? 0 : values.premiumChatDailyLimit };
      if (editor.kind === 'packs' && !input.digitalHumanSeconds && !input.aiVideoSeconds && !input.premiumChatCredits) {
        setEditorError('充值包至少需要提供一项额外次数或视频额度。'); return;
      }
      setSaving(true); setEditorError('');
      await EntitlementAdminService.saveProduct(editor.kind, input, editor.product?.productId);
      setEditor(undefined); message.success('权益配置已保存');
      await loadConfig();
    } catch (error) { if (!isFormError(error)) setEditorError(errorText(error)); }
    finally { setSaving(false); }
  };

  const setModelPremium = async (model: EntitlementModel, premium: boolean) => {
    if (modelSaving) return;
    setModelSaving(model.modelId);
    try {
      await EntitlementAdminService.saveModel(model.modelId, premium);
      message.success(`${model.name} 已设为${premium ? '高级' : '普通'}模型`);
      await loadConfig();
    } catch (error) { message.error(errorText(error)); }
    finally { setModelSaving(undefined); }
  };

  const modelColumns: TableColumnsType<EntitlementModel> = [
    { title: '模型名称', dataIndex: 'name' },
    { title: '模型 ID', dataIndex: 'modelId' },
    { title: '分类', dataIndex: 'premium', render: premium => <Tag color={premium ? 'gold' : 'green'}>{premium ? '高级模型' : '普通模型'}</Tag> },
    { title: '额度规则', dataIndex: 'premium', render: premium => premium ? '使用会员每日次数和额外次数' : '所有账号不限次' },
    { title: '设为高级模型', key: 'action', width: 140, render: (_, model) => <Switch checked={model.premium}
      checkedChildren="高级" unCheckedChildren="普通" loading={modelSaving === model.modelId}
      disabled={Boolean(modelSaving) || loading} onChange={value => setModelPremium(model, value)} /> },
  ];

  const changeTab = (key: string) => {
    setActiveTab(key);
    const paths: Record<string, string> = { free: '/packages/entitlements', plans: '/packages/plans', packs: '/packages/compute', accounts: '/packages/grants' };
    if (paths[key]) navigate(paths[key]);
  };

  return <Card title="会员与充值权益" extra={<Button icon={<ReloadOutlined />} loading={loading} disabled={saving} onClick={loadConfig}>刷新</Button>}>
    <Alert type="info" showIcon style={{ marginBottom: 20 }} message="所有账号默认拥有免费版；普通模型不限次，管理员账号不限额。"
      description="高级模型的每日次数按北京时间每日重置；免费版视频时长为累计额度。会员、充值和单账号发放均由服务端校验及扣减。" />
    {loadError && <Alert type="error" showIcon message="读取权益配置失败" description={loadError} style={{ marginBottom: 16 }} />}
    <Spin spinning={loading}>
      {config ? <Tabs activeKey={activeTab} onChange={changeTab} items={[
        { key: 'free', label: '免费版权益', children: <Form form={freeForm} layout="vertical" style={{ maxWidth: 850 }} disabled={saving || loading}>
          <Paragraph>免费版自动覆盖所有账号。调整累计视频额度后，历史已使用量仍然保留。</Paragraph>
          <Descriptions bordered size="small" style={{ marginBottom: 24 }}><Descriptions.Item label="普通模型 AI 对话">不限次数</Descriptions.Item></Descriptions>
          <QuotaFields daily />
          <Form.Item><Button type="primary" loading={saving} onClick={saveFree}>保存免费版权益</Button></Form.Item>
        </Form> },
        { key: 'plans', label: '会员类型', children: <ProductTable kind="plans" products={config.plans} onEdit={item => editProduct('plans', item)} /> },
        { key: 'packs', label: '充值包', children: <ProductTable kind="packs" products={config.rechargePacks} onEdit={item => editProduct('packs', item)} /> },
        { key: 'accounts', label: '账号权益发放', children: <AccountGrants plans={config.plans} /> },
        { key: 'models', label: '模型分级', children: <>
          <Paragraph>普通模型对所有账号不限次。启用高级分类后，该模型开始使用高级对话额度。首次启用权益体系时，所有模型均为普通模型。</Paragraph>
          <Table columns={modelColumns} rowKey="modelId" dataSource={config.models} pagination={{ pageSize: 15, showSizeChanger: true }} scroll={{ x: 750 }} />
        </> },
      ]} /> : !loading && !loadError ? <Empty description="暂无权益配置" /> : null}
    </Spin>
    <Modal open={Boolean(editor)} title={`${editor?.product ? '编辑' : '新增'}${editor?.kind === 'plans' ? '会员类型' : '充值包'}`}
      width={780} onCancel={() => { if (!saving) setEditor(undefined); }} onOk={saveProduct} confirmLoading={saving}
      okText="保存" cancelText="取消" maskClosable={false} closable={!saving} cancelButtonProps={{ disabled: saving }}>
      {editorError && <Alert type="error" showIcon message={editorError} style={{ marginBottom: 16 }} />}
      <Form form={productForm} layout="vertical" disabled={saving}>
        <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 64, message: '最多 64 个字符' }]}>
          <Input maxLength={64} />
        </Form.Item>
        <Row gutter={16}><Col xs={24} md={12}>
          <Form.Item label="售价" name="price" rules={required} extra="售价为 0 时不展示在购买列表；0 元会员可由管理员定向发放。"><InputNumber min={0} max={1000000} precision={2} addonBefore="¥" style={fullWidth} /></Form.Item>
        </Col><Col xs={24} md={12}>
          <Form.Item label="有效期" name="durationDays" extra="-1 表示永久" rules={[...required, integerRule(-1), { validator: (_, value) => value === 0 ? Promise.reject(new Error('有效期应大于 0，或填 -1 表示永久')) : Promise.resolve() }]}>
            <InputNumber min={-1} max={36500} precision={0} addonAfter="天" style={fullWidth} />
          </Form.Item>
        </Col></Row>
        <QuotaFields daily={editor?.kind === 'plans'} credits />
        <Form.Item label="启用状态" name="active" valuePropName="checked" extra="启用后可购买或发放；停用保留记录。">
          <Switch checkedChildren="启用" unCheckedChildren="停用" />
        </Form.Item>
      </Form>
    </Modal>
  </Card>;
};

export default MembershipEntitlements;
