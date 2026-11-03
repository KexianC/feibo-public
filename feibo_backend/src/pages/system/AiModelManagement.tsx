import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  AiModelAdminService,
  DEFAULT_SUPPORTED_MODEL_CODES,
  type AiModelAdminItem,
  type SupportedModelCodeOption,
} from '@/services/aiModelAdminService';

const { Paragraph, Text } = Typography;

interface CreateModelFormValues {
  name: string;
  modelCode: string;
  modelName: string;
  description?: string;
  order?: number;
  apiKey?: string;
  confirmApiKey?: string;
}

interface EditModelFormValues {
  name: string;
  modelName: string;
  description?: string;
  order: number;
}

interface ApiKeyFormValues {
  apiKey: string;
  confirmApiKey: string;
}

interface ProviderOrderFormValues {
  order: number;
}

type VisibilityFilter = 'all' | 'visible' | 'hidden';

interface ProviderModelGroup {
  key: string;
  providerId?: string;
  modelCode: string;
  providerName: string;
  providerOrder?: number;
  sourceIndex: number;
  models: AiModelAdminItem[];
}

const UNCONFIGURED_PROVIDER_CODE = '__unconfigured__';

const compareText = (left: string, right: string): number => (
  left.localeCompare(right, 'zh-CN', { numeric: true, sensitivity: 'base' })
);

const compareNullableOrder = (left?: number, right?: number): number => {
  if (left === undefined && right === undefined) return 0;
  if (left === undefined) return 1;
  if (right === undefined) return -1;
  return left - right;
};

const compareBusinessId = (left: string, right: string): number => (
  left.length - right.length || left.localeCompare(right)
);

const compareModelVersions = (left: AiModelAdminItem, right: AiModelAdminItem): number => (
  compareNullableOrder(left.order, right.order)
  || compareBusinessId(left.id, right.id)
);

const buildProviderGroups = (
  sourceModels: AiModelAdminItem[],
  supportedModelCodes: SupportedModelCodeOption[],
): ProviderModelGroup[] => {
  const providerLabels = new Map(supportedModelCodes.map(option => [option.value, option.label]));
  const providerFallbackOrder = new Map(supportedModelCodes.map((option, index) => [option.value, index]));
  const groupedModels = new Map<string, AiModelAdminItem[]>();

  sourceModels.forEach(model => {
    // providerId is the physical provider-category boundary. Never merge by
    // modelCode: different provider categories may intentionally share a
    // runtime strategy while using independent credentials and ordering.
    const groupKey = model.providerId ? `provider:${model.providerId}` : `model:${model.id}`;
    const current = groupedModels.get(groupKey) ?? [];
    current.push(model);
    groupedModels.set(groupKey, current);
  });

  const groups = Array.from(groupedModels.entries()).map(([groupKey, groupModels], sourceIndex): ProviderModelGroup => {
    const providerOrders = groupModels
      .map(model => model.providerOrder)
      .filter((value): value is number => value !== undefined);
    const providerId = groupModels.find(model => model.providerId)?.providerId;
    const modelCodes = Array.from(new Set(groupModels
      .map(model => model.modelCode?.trim().toLowerCase())
      .filter((value): value is string => Boolean(value))));
    const modelCode = modelCodes.length === 1 ? modelCodes[0] : UNCONFIGURED_PROVIDER_CODE;
    const providerName = groupModels.find(model => model.providerName?.trim())?.providerName?.trim()
      || providerLabels.get(modelCode)
      || (modelCode === UNCONFIGURED_PROVIDER_CODE ? '未配置供应商' : modelCode);
    return {
      key: groupKey,
      providerId,
      modelCode,
      providerName,
      providerOrder: providerOrders.length > 0
        ? providerOrders.reduce((lowest, current) => Math.min(lowest, current))
        : undefined,
      sourceIndex,
      models: [...groupModels].sort(compareModelVersions),
    };
  });

  return groups.sort((left, right) => {
    const providerOrderComparison = compareNullableOrder(left.providerOrder, right.providerOrder);
    if (providerOrderComparison !== 0) {
      return providerOrderComparison;
    }
    if (left.providerId && right.providerId) {
      const providerIdComparison = compareBusinessId(left.providerId, right.providerId);
      if (providerIdComparison !== 0) {
        return providerIdComparison;
      }
    }
    const leftFallbackOrder = providerFallbackOrder.get(left.modelCode) ?? supportedModelCodes.length;
    const rightFallbackOrder = providerFallbackOrder.get(right.modelCode) ?? supportedModelCodes.length;
    return leftFallbackOrder - rightFallbackOrder
      || compareText(left.providerName, right.providerName)
      || compareText(left.modelCode, right.modelCode)
      || left.sourceIndex - right.sourceIndex;
  });
};

const buildPositionOptions = (count: number): Array<{ label: string; value: number }> => (
  Array.from({ length: Math.max(count, 0) }, (_, index) => {
    const position = index + 1;
    const suffix = count === 1
      ? '（唯一位置）'
      : position === 1
        ? '（最前）'
        : position === count
          ? '（最后）'
          : '';
    return { label: `第 ${position} 位${suffix}`, value: position };
  })
);

const trimmedRequiredRule = (label: string, max: number) => ({
  validator: (_: unknown, value: unknown) => {
    if (typeof value !== 'string' || !value.trim()) {
      return Promise.reject(new Error(`请输入${label}`));
    }
    if (value.trim().length > max) {
      return Promise.reject(new Error(`${label}不能超过 ${max} 个字符`));
    }
    return Promise.resolve();
  },
});

const createApiKeyRules = [
  { min: 8, message: 'API Key 长度不能少于 8 个字符' },
  { max: 4096, message: 'API Key 长度不能超过 4096 个字符' },
  { pattern: /^\S+$/, message: 'API Key 不能包含空白字符' },
];

const updateApiKeyRules = [
  { required: true, message: '请输入 API Key' },
  { min: 8, message: 'API Key 长度不能少于 8 个字符' },
  { max: 4096, message: 'API Key 长度不能超过 4096 个字符' },
  { pattern: /^\S+$/, message: 'API Key 不能包含空白字符' },
];

const AiModelManagement: React.FC = () => {
  const [createForm] = Form.useForm<CreateModelFormValues>();
  const [editForm] = Form.useForm<EditModelFormValues>();
  const [apiKeyForm] = Form.useForm<ApiKeyFormValues>();
  const [providerOrderForm] = Form.useForm<ProviderOrderFormValues>();
  const [messageApi, messageContextHolder] = message.useMessage();
  const [modalApi, modalContextHolder] = Modal.useModal();

  const [models, setModels] = useState<AiModelAdminItem[]>([]);
  const [supportedModelCodes, setSupportedModelCodes] = useState<SupportedModelCodeOption[]>(DEFAULT_SUPPORTED_MODEL_CODES);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('all');

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editingModel, setEditingModel] = useState<AiModelAdminItem>();
  const [savingEdit, setSavingEdit] = useState(false);
  const [deletingModelId, setDeletingModelId] = useState<string>();
  const [apiKeyModel, setApiKeyModel] = useState<AiModelAdminItem>();
  const [savingApiKey, setSavingApiKey] = useState(false);
  const [orderingProvider, setOrderingProvider] = useState<ProviderModelGroup>();
  const [savingProviderOrder, setSavingProviderOrder] = useState(false);
  const [visibilityUpdatingId, setVisibilityUpdatingId] = useState<string>();

  const loadModels = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      const result = await AiModelAdminService.list();
      setModels(result.models);
      setSupportedModelCodes(result.supportedModelCodes);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '加载模型列表失败';
      setLoadError(errorMessage);
      messageApi.error(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [messageApi]);

  useEffect(() => {
    void loadModels();
  }, [loadModels]);

  const filteredModels = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return models.filter(model => {
      const matchesKeyword = !normalizedKeyword || [model.name, model.modelName, model.modelCode, model.providerName, model.description]
        .some(value => value?.toLowerCase().includes(normalizedKeyword));
      const matchesVisibility = visibilityFilter === 'all'
        || (visibilityFilter === 'visible' && model.visible)
        || (visibilityFilter === 'hidden' && !model.visible);
      return matchesKeyword && matchesVisibility;
    });
  }, [keyword, models, visibilityFilter]);

  const allProviderGroups = useMemo(
    () => buildProviderGroups(models, supportedModelCodes),
    [models, supportedModelCodes],
  );
  const providerGroups = useMemo(
    () => buildProviderGroups(filteredModels, supportedModelCodes),
    [filteredModels, supportedModelCodes],
  );
  const providerPositionByKey = useMemo(() => new Map(
    allProviderGroups.map((group, index) => [group.key, index + 1]),
  ), [allProviderGroups]);
  const versionPositionById = useMemo(() => {
    const positions = new Map<string, number>();
    allProviderGroups.forEach(group => {
      group.models.forEach((model, index) => positions.set(model.id, index + 1));
    });
    return positions;
  }, [allProviderGroups]);
  const providerPositionOptions = useMemo(
    () => buildPositionOptions(allProviderGroups.length),
    [allProviderGroups.length],
  );
  const selectedCreateModelCode = Form.useWatch('modelCode', createForm);
  const selectedCreateProviderGroups = useMemo(() => {
    if (!selectedCreateModelCode) return [];
    const normalizedCode = selectedCreateModelCode.trim().toLowerCase();
    return allProviderGroups.filter(group => group.modelCode === normalizedCode);
  }, [allProviderGroups, selectedCreateModelCode]);
  const selectedCreateProvider = selectedCreateProviderGroups.length === 1
    ? selectedCreateProviderGroups[0]
    : undefined;
  const createProviderSelectionError = selectedCreateModelCode && selectedCreateProviderGroups.length === 0
    ? '该服务商尚未配置基础模型，暂时无法添加版本。'
    : selectedCreateProviderGroups.length > 1
      ? '该调用策略对应多个供应商分组，无法确定新增版本归属。'
      : undefined;
  const createVersionPositionOptions = useMemo(() => {
    if (!selectedCreateProvider) return [];
    return buildPositionOptions(selectedCreateProvider.models.length + 1);
  }, [selectedCreateProvider]);
  const editVersionPositionOptions = useMemo(() => {
    if (!editingModel) return [];
    const siblingCount = editingModel.providerId
      ? models.filter(model => model.providerId === editingModel.providerId).length
      : 1;
    return buildPositionOptions(Math.max(siblingCount, 1));
  }, [editingModel, models]);

  const closeCreateModal = () => {
    setCreateOpen(false);
    createForm.resetFields();
  };

  const closeEditModal = () => {
    setEditingModel(undefined);
    editForm.resetFields();
  };

  const closeApiKeyModal = () => {
    setApiKeyModel(undefined);
    apiKeyForm.resetFields();
  };

  const closeProviderOrderModal = () => {
    setOrderingProvider(undefined);
    providerOrderForm.resetFields();
  };

  const openProviderOrderModal = (provider: ProviderModelGroup) => {
    if (!provider.providerId) {
      messageApi.error('供应商 ID 缺失，无法更新排序，请刷新后重试');
      return;
    }
    const currentPosition = providerPositionByKey.get(provider.key);
    if (!currentPosition) {
      messageApi.error('无法确定供应商当前位置，请刷新后重试');
      return;
    }
    providerOrderForm.setFieldsValue({ order: currentPosition });
    setOrderingProvider(provider);
  };

  const openEditModal = (model: AiModelAdminItem) => {
    const currentPosition = versionPositionById.get(model.id) ?? 1;
    setEditingModel(model);
    editForm.setFieldsValue({
      name: model.name,
      modelName: model.modelName,
      description: model.description,
      order: currentPosition,
    });
  };

  const openApiKeyModal = (model: AiModelAdminItem) => {
    apiKeyForm.resetFields();
    setApiKeyModel(model);
  };

  const handleCreate = async (values: CreateModelFormValues) => {
    setCreating(true);
    try {
      await AiModelAdminService.create({
        name: values.name.trim(),
        modelCode: values.modelCode,
        modelName: values.modelName.trim(),
        description: values.description?.trim() ?? '',
        visible: false,
        order: values.order,
        apiKey: values.apiKey?.trim() || undefined,
      });
      messageApi.success('模型添加成功');
      closeCreateModal();
      await loadModels();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '添加模型失败');
    } finally {
      setCreating(false);
      // A secret must not remain in form state after any submission attempt.
      createForm.setFieldsValue({ apiKey: '', confirmApiKey: '' });
    }
  };

  const handleEdit = async (values: EditModelFormValues) => {
    if (!editingModel || deletingModelId) return;
    setSavingEdit(true);
    try {
      await AiModelAdminService.update(editingModel.id, {
        name: values.name.trim(),
        modelName: values.modelName.trim(),
        description: values.description?.trim() ?? '',
        order: values.order,
      });
      messageApi.success('模型信息已更新');
      closeEditModal();
      await loadModels();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '更新模型信息失败');
    } finally {
      setSavingEdit(false);
    }
  };

  const requestDeleteModel = (model: AiModelAdminItem) => {
    if (!model.providerId) {
      messageApi.error('供应商分组不可删除，已拒绝此操作');
      return;
    }
    if (model.visible) {
      messageApi.warning('请先隐藏该模型，再执行删除');
      return;
    }

    modalApi.confirm({
      title: '确认删除模型？',
      content: (
        <Space direction="vertical" size={4}>
          <Text>将从管理列表移除以下模型配置：</Text>
          <Text strong>{model.name}</Text>
          <Text type="secondary">模型 ID：{model.id}</Text>
          <Text type="danger">
            历史记录占位会保留，已配置的 API Key 覆盖将被清除；页面内不可恢复。
          </Text>
        </Space>
      ),
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setDeletingModelId(model.id);
        try {
          await AiModelAdminService.deleteModel(model.id);
          setModels(current => current.filter(item => item.id !== model.id));
          messageApi.success(`模型“${model.name}”已从管理列表移除`);
          closeEditModal();
          await loadModels();
        } catch (error) {
          messageApi.error(error instanceof Error ? error.message : '移除模型失败');
          throw error;
        } finally {
          setDeletingModelId(undefined);
        }
      },
    });
  };

  const handleApiKeyUpdate = async (values: ApiKeyFormValues) => {
    if (!apiKeyModel) return;
    setSavingApiKey(true);
    try {
      await AiModelAdminService.updateApiKey(apiKeyModel.id, values.apiKey);
      setModels(current => current.map(model => (
        model.id === apiKeyModel.id ? { ...model, apiKeyConfigured: true } : model
      )));
      messageApi.success('API Key 已安全更新');
      closeApiKeyModal();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'API Key 更新失败');
    } finally {
      setSavingApiKey(false);
      apiKeyForm.resetFields();
    }
  };

  const handleProviderOrderUpdate = async (values: ProviderOrderFormValues) => {
    const providerId = orderingProvider?.providerId;
    if (!providerId) {
      messageApi.error('供应商 ID 缺失，已拒绝更新排序');
      closeProviderOrderModal();
      return;
    }

    setSavingProviderOrder(true);
    try {
      await AiModelAdminService.updateProviderOrder(providerId, values.order);
      messageApi.success('供应商排序已更新');
      closeProviderOrderModal();
      await loadModels();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '供应商排序更新失败');
    } finally {
      setSavingProviderOrder(false);
    }
  };

  const updateVisibility = async (model: AiModelAdminItem, visible: boolean) => {
    setVisibilityUpdatingId(model.id);
    try {
      await AiModelAdminService.updateVisibility(model.id, visible);
      setModels(current => current.map(item => (
        item.id === model.id ? { ...item, visible } : item
      )));
      messageApi.success(visible ? '模型已显示' : '模型已隐藏');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '显示状态更新失败');
    } finally {
      setVisibilityUpdatingId(undefined);
    }
  };

  const requestVisibilityChange = (model: AiModelAdminItem, visible: boolean) => {
    if (visible) {
      void updateVisibility(model, true);
      return;
    }
    modalApi.confirm({
      title: `隐藏“${model.name}”？`,
      content: '隐藏后，智笔医言页面将不再向用户展示该模型。',
      okText: '确认隐藏',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => updateVisibility(model, false),
    });
  };

  const columns: TableColumnsType<AiModelAdminItem> = [
    {
      title: '展示名称',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (name: string, model) => (
        <Space direction="vertical" size={2}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>ID：{model.id}</Text>
        </Space>
      ),
    },
    {
      title: '供应商模型 ID / 版本',
      dataIndex: 'modelName',
      key: 'providerModel',
      width: 230,
      render: (modelName: string, model) => (
        <Space direction="vertical" size={4}>
          <Text>{modelName}</Text>
          {model.modelCode ? <Tag color="blue">{model.modelCode}</Tag> : <Tag>未配置策略</Tag>}
        </Space>
      ),
    },
    {
      title: '版本位置',
      dataIndex: 'order',
      key: 'order',
      width: 100,
      align: 'center',
      render: (_order: number | undefined, model) => {
        const position = versionPositionById.get(model.id);
        return position ? `第 ${position} 位` : <Text type="secondary">未设置</Text>;
      },
    },
    {
      title: '简介',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: string) => description
        ? <Tooltip title={description}><span>{description}</span></Tooltip>
        : <Text type="secondary">未填写</Text>,
    },
    {
      title: '页面显示',
      dataIndex: 'visible',
      key: 'visible',
      width: 130,
      align: 'center',
      render: (visible: boolean, model) => (
        <Switch
          checked={visible}
          checkedChildren="显示"
          unCheckedChildren="隐藏"
          loading={visibilityUpdatingId === model.id}
          disabled={Boolean(visibilityUpdatingId)}
          onChange={nextVisible => requestVisibilityChange(model, nextVisible)}
        />
      ),
    },
    {
      title: 'API Key',
      dataIndex: 'apiKeyConfigured',
      key: 'apiKeyConfigured',
      width: 165,
      render: (configured: boolean) => configured
        ? <Tag color="green" icon={<LockOutlined />}>已配置 ·••••••••</Tag>
        : <Tag color="orange">未配置</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 210,
      render: (_, model) => (
        <Space size="small">
          <Button type="link" icon={<EditOutlined />} onClick={() => openEditModal(model)}>
            编辑
          </Button>
          <Button type="link" icon={<KeyOutlined />} onClick={() => openApiKeyModal(model)}>
            更新 Key
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {messageContextHolder}
      {modalContextHolder}
      <Card
        title={(
          <Space>
            <SafetyCertificateOutlined />
            <span>智笔医言管理</span>
          </Space>
        )}
        extra={(
          <Space>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadModels()}>
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                createForm.resetFields();
                setCreateOpen(true);
              }}
            >
              添加模型
            </Button>
          </Space>
        )}
      >
        <Alert
          showIcon
          type="info"
          message="API Key 安全说明"
          description="系统只显示是否已配置，不会回显 API Key。更新后旧 Key 将立即被替换，请妥善保管新 Key。"
          style={{ marginBottom: 16 }}
        />
        {loadError && (
          <Alert
            showIcon
            type="error"
            message="模型列表加载失败"
            description={loadError}
            action={<Button size="small" onClick={() => void loadModels()}>重试</Button>}
            style={{ marginBottom: 16 }}
          />
        )}

        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="搜索展示名称、供应商模型 ID、策略或简介"
            value={keyword}
            onChange={event => setKeyword(event.target.value)}
            style={{ width: 320 }}
          />
          <Select<VisibilityFilter>
            value={visibilityFilter}
            onChange={setVisibilityFilter}
            style={{ width: 140 }}
            options={[
              { label: '全部状态', value: 'all' },
              { label: '仅显示', value: 'visible' },
              { label: '仅隐藏', value: 'hidden' },
            ]}
          />
          <Text type="secondary">共 {providerGroups.length} 个供应商、{filteredModels.length} 个版本</Text>
        </Space>

        <Spin spinning={loading}>
          {providerGroups.length > 0 ? (
            <Collapse
              key={providerGroups.map(group => group.key).join('|')}
              defaultActiveKey={providerGroups.length === 1 ? [providerGroups[0].key] : []}
              items={providerGroups.map(group => {
                const visibleCount = group.models.filter(model => model.visible).length;
                return {
                  key: group.key,
                  label: (
                    <Space wrap>
                      <Text strong>{group.providerName}</Text>
                      <Tag color="blue">
                        {group.modelCode === UNCONFIGURED_PROVIDER_CODE ? '未配置策略' : group.modelCode}
                      </Tag>
                      <Tag>{group.models.length} 个版本</Tag>
                      <Text type="secondary">{visibleCount} 个已显示</Text>
                      {providerPositionByKey.has(group.key) && (
                        <Text type="secondary">供应商位置：第 {providerPositionByKey.get(group.key)} 位</Text>
                      )}
                    </Space>
                  ),
                  extra: (
                    <Tooltip title={group.providerId ? '调整供应商卡片顺序' : '供应商 ID 缺失，无法调整排序'}>
                      <Button
                        size="small"
                        icon={<EditOutlined />}
                        disabled={!group.providerId}
                        onClick={event => {
                          event.stopPropagation();
                          openProviderOrderModal(group);
                        }}
                      >
                        供应商排序
                      </Button>
                    </Tooltip>
                  ),
                  children: (
                    <Table<AiModelAdminItem>
                      rowKey={model => model.id}
                      columns={columns}
                      dataSource={group.models}
                      pagination={false}
                      size="small"
                      locale={{ emptyText: '该供应商暂无匹配版本' }}
                      scroll={{ x: 1300 }}
                    />
                  ),
                };
              })}
            />
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={loadError ? '暂时无法加载数据' : '暂无匹配模型，请先添加'}
            />
          )}
        </Spin>
      </Card>

      <Modal
        title="添加智笔医言模型"
        open={createOpen}
        confirmLoading={creating}
        okText="添加"
        cancelText="取消"
        destroyOnHidden
        maskClosable={false}
        onOk={() => createForm.submit()}
        onCancel={closeCreateModal}
      >
        <Form<CreateModelFormValues>
          form={createForm}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item name="name" label="展示名称" rules={[trimmedRequiredRule('展示名称', 128)]}>
            <Input maxLength={128} showCount placeholder="智笔医言页面展示的名称，例如：Kimi K3" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="modelCode"
            label="服务商 / 调用策略"
            rules={[{ required: true, message: '请选择服务商或调用策略' }]}
            extra="模型创建后不可在后台修改调用策略，避免 API Key 与策略错配。"
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="请选择调用策略"
              options={supportedModelCodes}
              onChange={modelCode => {
                const normalizedCode = modelCode.trim().toLowerCase();
                const matchingProviders = allProviderGroups.filter(group => (
                  group.modelCode === normalizedCode
                ));
                createForm.setFieldValue(
                  'order',
                  matchingProviders.length === 1
                    ? matchingProviders[0].models.length + 1
                    : undefined,
                );
              }}
            />
          </Form.Item>
          <Form.Item
            name="modelName"
            label="供应商模型 ID"
            rules={[trimmedRequiredRule('供应商模型 ID', 100)]}
            extra="填写实际发送给模型服务商的模型标识，不是页面展示名称。"
          >
            <Input maxLength={100} showCount placeholder="例如：kimi-k3" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="order"
            label="版本位置"
            rules={[{ required: true, message: '请选择版本位置' }]}
            extra={createProviderSelectionError
              ?? '选择新版本在该供应商子菜单中的位置；其他版本会自动顺移。'}
          >
            <Select
              disabled={!selectedCreateProvider}
              status={createProviderSelectionError ? 'error' : undefined}
              placeholder={createProviderSelectionError
                ?? (selectedCreateModelCode ? '请选择版本位置' : '请先选择服务商')}
              options={createVersionPositionOptions}
            />
          </Form.Item>
          <Form.Item
            name="description"
            label="模型简介"
            rules={[{ max: 1000, message: '模型简介不能超过 1000 个字符' }]}
          >
            <Input.TextArea maxLength={1000} showCount autoSize={{ minRows: 3, maxRows: 6 }} placeholder="介绍模型特点和适用场景" />
          </Form.Item>
          <Alert
            showIcon
            type="info"
            message="新模型将保持隐藏"
            description="创建后请先配置并验证 API Key，再通过列表中的显示开关开放给用户。"
            style={{ marginBottom: 16 }}
          />
          <Form.Item name="apiKey" label="API Key（选填）" rules={createApiKeyRules}>
            <Input.Password maxLength={4096} autoComplete="new-password" placeholder="可稍后通过“更新 Key”单独配置" />
          </Form.Item>
          <Form.Item
            name="confirmApiKey"
            label="确认 API Key"
            dependencies={['apiKey']}
            rules={[
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  const apiKey = getFieldValue('apiKey');
                  if (!apiKey && !value) return Promise.resolve();
                  if (apiKey && !value) return Promise.reject(new Error('请再次输入 API Key'));
                  return apiKey === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('两次输入的 API Key 不一致'));
                },
              }),
            ]}
          >
            <Input.Password maxLength={4096} autoComplete="new-password" placeholder="再次输入 API Key" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑模型信息"
        open={Boolean(editingModel)}
        confirmLoading={savingEdit}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
        closable={!deletingModelId}
        maskClosable={!deletingModelId}
        okButtonProps={{ disabled: Boolean(deletingModelId) }}
        cancelButtonProps={{ disabled: Boolean(deletingModelId) }}
        onOk={() => editForm.submit()}
        onCancel={closeEditModal}
      >
        <Form<EditModelFormValues> form={editForm} layout="vertical" onFinish={handleEdit}>
          <Form.Item label="服务商 / 调用策略">
            <Input value={editingModel?.modelCode || '未配置'} disabled />
          </Form.Item>
          <Form.Item name="name" label="展示名称" rules={[trimmedRequiredRule('展示名称', 128)]}>
            <Input maxLength={128} showCount placeholder="请输入页面展示名称" />
          </Form.Item>
          <Form.Item
            name="modelName"
            label="供应商模型 ID"
            rules={[trimmedRequiredRule('供应商模型 ID', 100)]}
          >
            <Input maxLength={100} showCount placeholder="例如：kimi-k3" />
          </Form.Item>
          <Form.Item
            name="order"
            label="版本位置"
            rules={[{ required: true, message: '请选择版本位置' }]}
            extra="选择该版本在当前供应商子菜单中的目标位置；其他版本会自动顺移。"
          >
            <Select placeholder="请选择版本位置" options={editVersionPositionOptions} />
          </Form.Item>
          <Form.Item
            name="description"
            label="模型简介"
            rules={[{ max: 1000, message: '模型简介不能超过 1000 个字符' }]}
          >
            <Input.TextArea maxLength={1000} showCount autoSize={{ minRows: 3, maxRows: 6 }} placeholder="介绍模型特点和适用场景" />
          </Form.Item>
          {editingModel?.providerId && (
            <div style={{ borderTop: '1px solid #f0f0f0', marginTop: 8, paddingTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Text type="danger" strong>危险操作</Text>
                <Text type="secondary">
                  只能移除已隐藏的模型；供应商下唯一版本会被拒绝。历史引用会保留，API Key 覆盖将被清除。
                </Text>
                <Tooltip title={editingModel.visible ? '请先在列表中隐藏该模型' : undefined}>
                  <span style={{ width: 'fit-content' }}>
                    <Button
                      danger
                      icon={<DeleteOutlined />}
                      loading={deletingModelId === editingModel.id}
                      disabled={editingModel.visible || savingEdit || Boolean(deletingModelId)}
                      onClick={() => requestDeleteModel(editingModel)}
                    >
                      删除当前模型
                    </Button>
                  </span>
                </Tooltip>
              </Space>
            </div>
          )}
        </Form>
      </Modal>

      <Modal
        title={`调整供应商排序${orderingProvider ? ` · ${orderingProvider.providerName}` : ''}`}
        open={Boolean(orderingProvider)}
        confirmLoading={savingProviderOrder}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
        onOk={() => providerOrderForm.submit()}
        onCancel={closeProviderOrderModal}
      >
        <Alert
          showIcon
          type="info"
          message="选择供应商目标位置"
          description="系统会自动顺移其他供应商，保持位置连续且不重复；不会修改名称、显示状态、模型版本或 API Key。"
          style={{ marginBottom: 16 }}
        />
        <Form<ProviderOrderFormValues>
          form={providerOrderForm}
          layout="vertical"
          onFinish={handleProviderOrderUpdate}
        >
          <Form.Item
            name="order"
            label="供应商位置"
            rules={[{ required: true, message: '请选择供应商位置' }]}
          >
            <Select placeholder="请选择供应商位置" options={providerPositionOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`更新 API Key${apiKeyModel ? ` · ${apiKeyModel.name}` : ''}`}
        open={Boolean(apiKeyModel)}
        confirmLoading={savingApiKey}
        okText="确认更新"
        cancelText="取消"
        destroyOnHidden
        maskClosable={false}
        onOk={() => apiKeyForm.submit()}
        onCancel={closeApiKeyModal}
      >
        <Alert
          showIcon
          type="warning"
          message="当前 Key 不会回显"
          description="本次更新仅影响当前模型。提交新 Key 后会立即替换该模型的现有配置，旧 Key 无法从后台找回。"
          style={{ marginBottom: 16 }}
        />
        <Form<ApiKeyFormValues> form={apiKeyForm} layout="vertical" onFinish={handleApiKeyUpdate}>
          <Form.Item name="apiKey" label="新 API Key" rules={updateApiKeyRules}>
            <Input.Password maxLength={4096} autoComplete="new-password" placeholder="输入新的 API Key" />
          </Form.Item>
          <Form.Item
            name="confirmApiKey"
            label="确认新 API Key"
            dependencies={['apiKey']}
            rules={[
              { required: true, message: '请再次输入新 API Key' },
              ({ getFieldValue }) => ({
                validator: (_, value) => !value || getFieldValue('apiKey') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的 API Key 不一致')),
              }),
            ]}
          >
            <Input.Password maxLength={4096} autoComplete="new-password" placeholder="再次输入新的 API Key" />
          </Form.Item>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            建议先在模型服务商控制台创建新 Key，更新成功并验证调用后，再废止旧 Key。
          </Paragraph>
        </Form>
      </Modal>
    </div>
  );
};

export default AiModelManagement;
