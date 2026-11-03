import { v4 as uuidv4 } from 'uuid';
import { backendRequest } from './http/backendClient';

// API响应类型
interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

// 带大整数处理的认证请求函数
const authenticatedRequestWithBigInt = async <T>(
  url: string,
  options: RequestInit = {},
  uuid?: string
): Promise<ApiResponse<T>> => {
  return backendRequest<T>(url, options, {
    auth: true,
    idempotent: Boolean(uuid),
    idempotencyKey: uuid,
  });
};

// 文案提交接口参数类型
export interface SubmitCopywritingRequest {
  title: string;
  content: string;
  summary: string;
  copywritingCategoryId: string;
  copywritingSourceId: string;
}

// 文案查询接口参数类型
export interface GetCopywritingListParams {
  page?: number;
  size?: number;
  status: number; // 0-作者待确认, 1-专家组专业审核, 2-AI自动审核, 3-机构管理员终审, 4-审批完成, 5-审批驳回
}

// 文案对象类型（嵌套在API响应中）
export interface CopywritingObject {
  copywritingId: string; // 改为字符串类型防止精度丢失
  title: string;
  summary: string;
  content: string;
  status: string;
  createTime: string;
}

// 审批流程类型
export interface ApprovalFlow {
  copywritingApprovalFlowId?: string; // 改为字符串类型防止精度丢失
  approvalFlowId?: string; // 兼容旧字段名
  currentStatus: string | number;
  currentStatusCode?: number;
  createTime: string | null;
}

// 审批用户类型
export interface ApprovalUser {
  userId: string; // 改为字符串类型防止精度丢失
  nickName: string;
  avatar?: string;
  gender?: string;
}

// 文案列表项类型（实际API返回的数据结构）
export interface CopywritingItem {
  copywriting: CopywritingObject;
  categoryName: string;
  sourceName: string;
  approvalFlow: ApprovalFlow;
  approvalUser?: ApprovalUser | null;
  submitterUser?: ApprovalUser | null;
  expertReviewer?: boolean;
  organizationReviewer?: boolean;
}

// 文案详情按需加载，避免审核列表携带过大的全文和历史记录。
export interface CopywritingDetail extends CopywritingItem {
  auditHistory: AuditRecord[];
}

// 分页数据类型
export interface PaginationData {
  data: CopywritingItem[];
  hasNext: boolean;
  total: number;
  page: number;
  size: number;
}

// 文案列表响应类型（完整的API响应结构）
export interface CopywritingListResponse {
  code: string;
  info: string;
  data: PaginationData;
}

const toIdString = (value: unknown, fallback = ''): string => (
  value == null ? fallback : String(value)
);

type UnknownRecord = Record<string, unknown>;

const asRecord = (value: unknown): UnknownRecord => (
  value != null && typeof value === 'object' ? value as UnknownRecord : {}
);

const normalizeApprovalUser = (value: unknown): ApprovalUser | null => {
  if (!value || typeof value !== 'object') return null;
  const record = asRecord(value);
  return {
    userId: toIdString(record.userId),
    nickName: String(record.nickName ?? ''),
    avatar: record.avatar == null ? undefined : String(record.avatar),
    gender: record.gender == null ? undefined : String(record.gender)
  };
};

const normalizeCopywritingItem = (value: unknown, fallbackCopywritingId = ''): CopywritingItem => {
  const record = asRecord(value);
  const copywriting = asRecord(record.copywriting);
  const approvalFlow = record.approvalFlow == null ? null : asRecord(record.approvalFlow);

  return {
    copywriting: {
      copywritingId: toIdString(copywriting.copywritingId, fallbackCopywritingId),
      title: String(copywriting.title ?? ''),
      summary: String(copywriting.summary ?? ''),
      content: String(copywriting.content ?? ''),
      status: String(copywriting.status ?? ''),
      createTime: String(copywriting.createTime ?? '')
    },
    categoryName: String(record.categoryName ?? ''),
    sourceName: String(record.sourceName ?? ''),
    approvalFlow: approvalFlow ? {
      ...approvalFlow,
      copywritingApprovalFlowId: approvalFlow.copywritingApprovalFlowId == null
        ? undefined
        : toIdString(approvalFlow.copywritingApprovalFlowId),
      approvalFlowId: approvalFlow.approvalFlowId == null
        ? undefined
        : toIdString(approvalFlow.approvalFlowId),
      currentStatus: typeof approvalFlow.currentStatus === 'number'
        ? approvalFlow.currentStatus
        : String(approvalFlow.currentStatus ?? ''),
      currentStatusCode: approvalFlow.currentStatusCode == null
        ? undefined
        : Number(approvalFlow.currentStatusCode),
      createTime: approvalFlow.createTime == null ? null : String(approvalFlow.createTime)
    } : {
      currentStatus: '',
      createTime: null
    },
    approvalUser: normalizeApprovalUser(record.approvalUser),
    submitterUser: normalizeApprovalUser(record.submitterUser),
    expertReviewer: record.expertReviewer == null ? undefined : record.expertReviewer === true,
    organizationReviewer: record.organizationReviewer == null ? undefined : record.organizationReviewer === true
  };
};

interface CopywritingListPayload {
  data?: unknown;
  list?: unknown;
  hasNext?: boolean;
  total?: number | string;
  page?: number | string;
  size?: number | string;
}

// 提交文案
export const submitCopywriting = async (data: SubmitCopywritingRequest, uuid: string): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/submit', {
    method: 'POST',
    body: JSON.stringify(data)
  }, uuid);
};

// 查询文案列表（按审批状态）
export const getCopywritingListByStatus = async (params: GetCopywritingListParams): Promise<PaginationData> => {
  const { page = 1, size = 10, status } = params;
  const response = await authenticatedRequestWithBigInt<CopywritingListPayload>(
    `/copywriting/list/by-approval-status?page=${page}&size=${size}&status=${status}`,
    { method: 'GET' }
  );
  const payload = response.data || {};
  const list = Array.isArray(payload.data)
    ? payload.data
    : Array.isArray(payload.list)
      ? payload.list
      : [];
  return {
    data: list.map((item: unknown) => normalizeCopywritingItem(item)),
    hasNext: !!payload.hasNext,
    total: Number(payload.total || 0),
    page: Number(payload.page || page),
    size: Number(payload.size || size)
  };
};

// 审批状态映射
export const APPROVAL_STATUS = {
  AUTHOR_PENDING: 0, // 待作者自审/确认
  EXPERT_PENDING: 1, // 专家组专业审核
  AI_PENDING: 2, // AI自动审核
  ORGANIZATION_PENDING: 3, // 所属医院/机构管理员终审
  APPROVED: 4, // 审批完成
  REJECTED: 5 // 审批驳回
} as const;

// 审批状态文本映射
export const APPROVAL_STATUS_TEXT = {
  [APPROVAL_STATUS.AUTHOR_PENDING]: '待作者确认',
  [APPROVAL_STATUS.EXPERT_PENDING]: '专家组待专业审核',
  [APPROVAL_STATUS.AI_PENDING]: 'AI自动审核中',
  [APPROVAL_STATUS.ORGANIZATION_PENDING]: '所属医院/机构待终审',
  [APPROVAL_STATUS.APPROVED]: '审批完成',
  [APPROVAL_STATUS.REJECTED]: '审批驳回'
} as const;

// 文案分类类型
export interface CopywritingCategory {
  copywritingCategoryId: string;
  name: string;
}

// 文案来源类型
export interface CopywritingSource {
  copywritingSourceId: string;
  name: string;
}

// 获取文案分类列表
export const getCopywritingCategories = async (): Promise<CopywritingCategory[]> => {
  const response = await authenticatedRequestWithBigInt<CopywritingCategory[]>(
    '/copywriting/categories/all',
    { method: 'GET' }
  );
  return (response.data || []).map((item) => ({
    ...item,
    copywritingCategoryId: toIdString(item.copywritingCategoryId)
  }));
};

// 获取文案来源列表
export const getCopywritingSources = async (): Promise<CopywritingSource[]> => {
  const response = await authenticatedRequestWithBigInt<CopywritingSource[]>(
    '/copywriting/sources/all',
    { method: 'GET' }
  );
  return (response.data || []).map((item) => ({
    ...item,
    copywritingSourceId: toIdString(item.copywritingSourceId)
  }));
};

// 作者自审/确认接口参数类型
export interface ConfirmByAuthorRequest {
  copywritingApprovalFlowId: string; // 改为字符串类型防止精度丢失
  copywritingId: string; // 改为字符串类型防止精度丢失
  comment: string;
}

export interface RejectCopywritingRequest {
  copywritingApprovalFlowId: string; // 改为字符串类型防止精度丢失
  copywritingId: string; // 改为字符串类型防止精度丢失
  comment: string;
}
export type ApproveByExpertRequest = ConfirmByAuthorRequest;
export type ApproveByOrganizationRequest = ConfirmByAuthorRequest;

export interface ClaimByExpertRequest {
  copywritingApprovalFlowId: string;
}
export type ClaimByOrganizationRequest = ClaimByExpertRequest;

// 作者确认后进入 AI 自动审核。
export const confirmByAuthor = async (data: ConfirmByAuthorRequest): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/confirm/by-author', {
    method: 'POST',
    body: JSON.stringify(data)
  });
};

export const claimByExpert = async (data: ClaimByExpertRequest): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/claim/by-expert', {
    method: 'POST',
    body: JSON.stringify(data)
  });
};

export const approveByExpert = async (data: ApproveByExpertRequest): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/approve/by-expert', {
    method: 'POST',
    body: JSON.stringify(data)
  });
};

export const claimByOrganization = async (data: ClaimByOrganizationRequest): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/claim/by-platform', {
    method: 'POST',
    body: JSON.stringify(data)
  });
};

export const approveByOrganization = async (data: ApproveByOrganizationRequest): Promise<void> => {
  await authenticatedRequestWithBigInt('/copywriting/approve/by-platform', {
    method: 'POST',
    body: JSON.stringify(data)
  });
};

// 各人工审核阶段共用驳回接口，后端根据流程状态校验操作人权限。
export const rejectCopywriting = async (data: RejectCopywritingRequest): Promise<void> => {
  const uuid = uuidv4();
  
  await authenticatedRequestWithBigInt('/copywriting/reject', {
    method: 'POST',
    body: JSON.stringify(data)
  }, uuid);
};

/** @deprecated 请使用 rejectCopywriting。 */
export const rejectByDoctor = rejectCopywriting;

// 审核记录相关类型定义
export interface AuditRecord {
  id: string;
  copywritingId: string;
  copywritingTitle: string;
  auditTime: string;
  auditorName: string;
  auditorRole: string; // '作者' | 'AI' | '专家组' | '机构管理员'
  auditResult: string; // '通过' | '驳回' | '待审核'
  auditComment: string;
  previousStatus: string;
  currentStatus: string;
  copywritingApprovalHistoryId?: string;
  copywritingApprovalFlowId?: string;
  operationTime?: string;
  operatorId?: string;
  operatorName?: string;
  operatorRole?: string;
  operation?: string;
  comment?: string;
  fromStatus?: string;
  fromStatusCode?: number | null;
  toStatus?: string;
  toStatusCode?: number | null;
}

const normalizeAuditRecord = (value: unknown, index: number = 0): AuditRecord => {
  const record = asRecord(value);
  const historyId = record.id ?? record.copywritingApprovalHistoryId;
  const auditTime = record.auditTime ?? record.operationTime ?? record.createTime ?? '';
  const auditorName = record.auditorName ?? record.operatorName ?? '';
  const auditorRole = record.auditorRole ?? record.operatorRole ?? '';
  const auditResult = record.auditResult ?? record.operation ?? record.operationStatus ?? '';
  const auditComment = record.auditComment ?? record.comment ?? '';
  const previousStatus = record.previousStatus ?? record.fromStatus ?? '';
  const currentStatus = record.currentStatus ?? record.toStatus ?? '';

  return {
    id: toIdString(historyId, `history-${index}`),
    copywritingApprovalHistoryId: record.copywritingApprovalHistoryId == null
      ? undefined
      : toIdString(record.copywritingApprovalHistoryId),
    copywritingApprovalFlowId: record.copywritingApprovalFlowId == null
      ? undefined
      : toIdString(record.copywritingApprovalFlowId),
    copywritingId: toIdString(record.copywritingId),
    copywritingTitle: String(record.copywritingTitle ?? ''),
    auditTime: String(auditTime),
    auditorName: String(auditorName),
    auditorRole: String(auditorRole),
    auditResult: String(auditResult),
    auditComment: String(auditComment),
    previousStatus: String(previousStatus),
    currentStatus: String(currentStatus),
    operationTime: record.operationTime == null ? String(auditTime) : String(record.operationTime),
    operatorId: record.operatorId == null ? undefined : toIdString(record.operatorId),
    operatorName: record.operatorName == null ? String(auditorName) : String(record.operatorName),
    operatorRole: record.operatorRole == null ? String(auditorRole) : String(record.operatorRole),
    operation: record.operation == null ? String(auditResult) : String(record.operation),
    comment: record.comment == null ? String(auditComment) : String(record.comment),
    fromStatus: record.fromStatus == null ? String(previousStatus) : String(record.fromStatus),
    fromStatusCode: record.fromStatusCode == null ? null : Number(record.fromStatusCode),
    toStatus: record.toStatus == null ? String(currentStatus) : String(record.toStatus),
    toStatusCode: record.toStatusCode == null ? null : Number(record.toStatusCode)
  };
};

// 审核记录查询参数
export interface GetAuditRecordsParams {
  page?: number;
  size?: number;
  copywritingId?: string;
  auditorRole?: string;
  auditResult?: string;
}

// 审核记录分页响应
export interface AuditRecordsResponse {
  data: AuditRecord[];
  hasNext: boolean;
  total: number;
  page: number;
  size: number;
}

// 获取审核记录列表
export const getAuditRecords = async (params: GetAuditRecordsParams = {}): Promise<AuditRecordsResponse> => {
  const { page = 1, size = 10, copywritingId, auditorRole, auditResult } = params;
  
  const queryParams = new URLSearchParams({
    page: page.toString(),
    size: size.toString()
  });
  
  if (copywritingId) queryParams.append('copywritingId', copywritingId);
  if (auditorRole) queryParams.append('auditorRole', auditorRole);
  if (auditResult) queryParams.append('auditResult', auditResult);
  
  const response = await authenticatedRequestWithBigInt<AuditRecordsResponse>(
    `/copywriting/audit-records?${queryParams.toString()}`,
    { method: 'GET' }
  );
  const payload = response.data || {} as AuditRecordsResponse;
  const records = Array.isArray(payload.data) ? payload.data : [];
  return {
    data: records.map(normalizeAuditRecord),
    hasNext: Boolean(payload.hasNext),
    total: Number(payload.total || 0),
    page: Number(payload.page || page),
    size: Number(payload.size || size)
  };
};

// 获取指定文案的审核记录
export const getCopywritingAuditHistory = async (copywritingId: string, signal?: AbortSignal): Promise<AuditRecord[]> => {
  const response = await authenticatedRequestWithBigInt<AuditRecord[]>(
    `/copywriting/${encodeURIComponent(toIdString(copywritingId))}/audit-history`,
    { method: 'GET', signal }
  );
  return (Array.isArray(response.data) ? response.data : []).map(normalizeAuditRecord);
};

// 获取完整文案详情及审核历史。仅在用户打开详情/审核弹窗时调用。
export const getCopywritingDetail = async (copywritingId: string, signal?: AbortSignal): Promise<CopywritingDetail> => {
  const normalizedId = toIdString(copywritingId);
  const response = await authenticatedRequestWithBigInt<unknown>(
    `/copywriting/${encodeURIComponent(normalizedId)}/detail`,
    { method: 'GET', signal }
  );
  const payload = asRecord(response.data);
  return {
    ...normalizeCopywritingItem(payload, normalizedId),
    auditHistory: (Array.isArray(payload.auditHistory) ? payload.auditHistory : []).map(normalizeAuditRecord)
  };
};
