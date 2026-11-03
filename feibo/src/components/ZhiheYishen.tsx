import React, { useState, useEffect, useRef } from 'react';
import { Search, Plus, FileText, CheckCircle, XCircle, Clock, Eye, X, RefreshCw, ChevronRight, ChevronLeft, Upload, Loader2, Sparkles, ClipboardCheck, History } from 'lucide-react';
import { getCurrentUser } from '../services/authApi';
import CachedAvatar from './CachedAvatar';
import { useAuthGuard } from '../hooks/useAuthGuard';
import { asrService } from '../services/asrApi';
import { AppRequestError } from '../services/http/errors';
import {
  submitCopywriting,
  getCopywritingListByStatus,
  getCopywritingDetail,
  APPROVAL_STATUS,
  APPROVAL_STATUS_TEXT,
  SubmitCopywritingRequest,
  GetCopywritingListParams,
  CopywritingItem,
  CopywritingDetail,
  getCopywritingCategories,
  getCopywritingSources,
  CopywritingCategory,
  CopywritingSource,
  PaginationData,
  confirmByAuthor,
  ConfirmByAuthorRequest,
  approveByExpert,
  ApproveByExpertRequest,
  claimByExpert,
  approveByOrganization,
  ApproveByOrganizationRequest,
  claimByOrganization,
  rejectCopywriting,
  RejectCopywritingRequest,
  getAuditRecords,
  getCopywritingAuditHistory,
  AuditRecord,
  GetAuditRecordsParams
} from '../services/copywritingApi';

interface Document {
  id: string;
  title: string;
  author: string;
  authorType: string;
  date: string;
  status: string;
  evaluate: string;
  createTime: string;
  approvalStatusCode: number | null;
  expertReviewAllowed?: boolean;
  organizationReviewAllowed?: boolean;
}

const LEGACY_APPROVAL_STATUS_TEXT: Record<string, number> = {
  '医生待审批': APPROVAL_STATUS.AUTHOR_PENDING,
  '作者待确认': APPROVAL_STATUS.AUTHOR_PENDING,
  '待作者确认': APPROVAL_STATUS.AUTHOR_PENDING,
  '专家待审批': APPROVAL_STATUS.EXPERT_PENDING,
  '专家组待专业审核': APPROVAL_STATUS.EXPERT_PENDING,
  'AI待审核': APPROVAL_STATUS.AI_PENDING,
  'AI审核中': APPROVAL_STATUS.AI_PENDING,
  'AI自动审核中': APPROVAL_STATUS.AI_PENDING,
  '平台待终审': APPROVAL_STATUS.ORGANIZATION_PENDING,
  '机构待终审': APPROVAL_STATUS.ORGANIZATION_PENDING,
  '所属医院/机构待终审': APPROVAL_STATUS.ORGANIZATION_PENDING,
};

const getApprovalStatusCodeFromValue = (value: unknown): number | null => {
  const numericStatus = Number(value);
  if (Number.isInteger(numericStatus) && numericStatus >= 0 && numericStatus <= 5) {
    return numericStatus;
  }

  const statusText = String(value ?? '');
  const matchedStatus = Object.entries(APPROVAL_STATUS_TEXT).find(
    ([, text]) => text === statusText,
  );
  if (matchedStatus) return Number(matchedStatus[0]);

  const legacyStatus = LEGACY_APPROVAL_STATUS_TEXT[statusText];
  if (typeof legacyStatus === 'number') return legacyStatus;
  return statusText.toUpperCase().includes('AI') ? APPROVAL_STATUS.AI_PENDING : null;
};

const getApprovalStatusCode = (item: CopywritingItem): number | null => {
  const explicitStatus = item.approvalFlow?.currentStatusCode;
  if (typeof explicitStatus === 'number') return explicitStatus;
  return getApprovalStatusCodeFromValue(item.approvalFlow?.currentStatus);
};

const getApprovalFlowId = (item: CopywritingItem): string | undefined => (
  item.approvalFlow?.copywritingApprovalFlowId || item.approvalFlow?.approvalFlowId
);

const hasUserRole = (user: unknown, expectedRole: string): boolean => {
  const normalizeRole = (value: unknown) => (
    String(value ?? '').trim().toUpperCase().replace(/^ROLE_/, '')
  );
  const userRecord = user != null && typeof user === 'object'
    ? user as Record<string, unknown>
    : {};
  const expected = normalizeRole(expectedRole);
  const roles = Array.isArray(userRecord.roles) ? userRecord.roles : [];
  if (roles.some((role: unknown) => {
    const roleRecord = role != null && typeof role === 'object'
      ? role as Record<string, unknown>
      : {};
    return normalizeRole(
      typeof role === 'string'
        ? role
        : roleRecord.name ?? roleRecord.roleName ?? roleRecord.code ?? roleRecord.roleCode ?? roleRecord.authority,
    ) === expected;
  })) return true;
  return [userRecord.role, userRecord.roleName, userRecord.roleCode, userRecord.authority].some(
    (role) => normalizeRole(role) === expected,
  );
};

// 审核记录显示接口
interface AuditRecordDisplay {
  id: string;
  copywritingId: string;
  copywritingApprovalFlowId?: string;
  copywritingTitle: string;
  auditTime: string;
  auditorName: string;
  auditorRole: string;
  auditResult: string;
  auditComment: string;
  previousStatus: string;
  currentStatus: string;
  statusChange: string;
}

const formatAuditTime = (value?: string | null): string => {
  if (!value) return '时间未记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Shanghai'
  });
};

const getAuditResultLabel = (record: AuditRecord): string => {
  const raw = String(record.auditResult || record.operation || '').trim();
  const normalized = raw.toUpperCase();
  if (normalized.includes('REJECT') || raw.includes('驳回') || raw.includes('拒绝')) return '驳回';
  if (normalized.includes('APPROVE') || normalized.includes('PASS') || raw.includes('通过')) return '通过';
  if (normalized.includes('SUBMIT') || raw.includes('提交')) return '提交';
  if (normalized.includes('CLAIM') || raw.includes('认领')) return '认领';
  return raw || '已处理';
};

const getAuditResultStyle = (result: string): string => {
  if (result.includes('驳回') || result.includes('拒绝')) return 'bg-red-100 text-red-700';
  if (result.includes('通过')) return 'bg-emerald-100 text-emerald-700';
  if (result.includes('待')) return 'bg-amber-100 text-amber-700';
  return 'bg-sky-100 text-sky-700';
};

const CopywritingContent: React.FC<{ item: CopywritingItem }> = ({ item }) => (
  <section className="space-y-4" aria-label="完整文案">
    <div>
      <div className="mb-2 text-xs font-semibold uppercase tracking-[0.12em] text-gray-500">文案标题</div>
      <h3 className="break-words text-xl font-semibold leading-8 text-gray-950">
        {item.copywriting.title || '未命名文案'}
      </h3>
    </div>
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="rounded-xl border border-gray-200 bg-gray-50 p-3">
        <div className="text-xs text-gray-500">提交人</div>
        <div className="mt-1 break-words text-sm font-medium text-gray-900">
          {item.submitterUser?.nickName || '未知提交人'}
        </div>
      </div>
      <div className="rounded-xl border border-gray-200 bg-gray-50 p-3">
        <div className="text-xs text-gray-500">文案分类</div>
        <div className="mt-1 break-words text-sm font-medium text-gray-900">{item.categoryName || '未分类'}</div>
      </div>
      <div className="rounded-xl border border-gray-200 bg-gray-50 p-3">
        <div className="text-xs text-gray-500">文案来源</div>
        <div className="mt-1 break-words text-sm font-medium text-gray-900">{item.sourceName || '未知来源'}</div>
      </div>
    </div>
    <div className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
      <div className="mb-2 text-sm font-semibold text-gray-900">摘要</div>
      <p className="whitespace-pre-wrap break-words text-sm leading-7 text-gray-600">
        {item.copywriting.summary || '暂无摘要'}
      </p>
    </div>
    <div className="rounded-2xl border border-teal-100 bg-teal-50/40 p-4 sm:p-5">
      <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-950">
        <FileText size={17} className="text-teal-600" />
        完整文案
      </div>
      <div className="max-h-[45vh] overflow-y-auto whitespace-pre-wrap break-words pr-1 text-[15px] leading-8 text-gray-800">
        {item.copywriting.content || '暂无文案内容'}
      </div>
    </div>
  </section>
);

const ApprovalProgress: React.FC<{ item: CopywritingItem; history: AuditRecord[] }> = ({ item, history }) => {
  const steps = ['作者提交', '作者自审/确认', 'AI 自动审核', '专家组专业审核', '所属医院/机构管理员终审'];
  const stageByStatus: Record<number, number> = {
    [APPROVAL_STATUS.AUTHOR_PENDING]: 1,
    [APPROVAL_STATUS.AI_PENDING]: 2,
    [APPROVAL_STATUS.EXPERT_PENDING]: 3,
    [APPROVAL_STATUS.ORGANIZATION_PENDING]: 4
  };
  const statusCode = getApprovalStatusCode(item);
  const rejectedRecord = [...history].reverse().find((record) => getAuditResultLabel(record) === '驳回');
  const rejectedFromStatus = rejectedRecord?.fromStatusCode ?? getApprovalStatusCodeFromValue(
    rejectedRecord?.fromStatus || rejectedRecord?.previousStatus,
  );
  const rejectedStage = statusCode === APPROVAL_STATUS.REJECTED
    ? (stageByStatus[Number(rejectedFromStatus)] ?? 1)
    : -1;
  const currentStage = stageByStatus[Number(statusCode)] ?? 1;
  const isApproved = statusCode === APPROVAL_STATUS.APPROVED;

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5" aria-label="审核流程">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-gray-950">
          <ClipboardCheck size={17} className="text-teal-600" />
          审核流程
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-medium ${
          statusCode === APPROVAL_STATUS.REJECTED
            ? 'bg-red-100 text-red-700'
            : isApproved
              ? 'bg-emerald-100 text-emerald-700'
              : 'bg-amber-100 text-amber-700'
        }`}>
          {statusCode == null
            ? String(item.approvalFlow?.currentStatus || '状态未知')
            : APPROVAL_STATUS_TEXT[statusCode as keyof typeof APPROVAL_STATUS_TEXT]}
        </span>
      </div>
      <ol className="space-y-0">
        {steps.map((step, index) => {
          const rejected = index === rejectedStage;
          const completed = isApproved || index < (rejectedStage >= 0 ? rejectedStage : currentStage);
          const active = rejectedStage < 0 && !isApproved && index === currentStage;
          return (
            <li key={step} className="relative flex min-h-12 gap-3 last:min-h-0">
              {index < steps.length - 1 && (
                <span className={`absolute left-[11px] top-6 h-[calc(100%-8px)] w-px ${completed ? 'bg-teal-300' : 'bg-gray-200'}`} />
              )}
              <span className={`relative z-10 mt-0.5 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full border text-xs ${
                rejected
                  ? 'border-red-500 bg-red-500 text-white'
                  : completed
                    ? 'border-teal-600 bg-teal-600 text-white'
                    : active
                      ? 'border-amber-500 bg-amber-50 text-amber-700'
                      : 'border-gray-300 bg-white text-gray-400'
              }`}>
                {completed ? <CheckCircle size={14} /> : rejected ? <X size={13} /> : index + 1}
              </span>
              <div className="min-w-0 pb-4">
                <div className={`text-sm font-medium ${rejected ? 'text-red-700' : active ? 'text-amber-700' : completed ? 'text-gray-900' : 'text-gray-400'}`}>
                  {step}
                </div>
                {rejected && <div className="mt-0.5 text-xs text-red-600">该环节已驳回</div>}
                {active && <div className="mt-0.5 text-xs text-amber-600">当前环节</div>}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
};

interface AuditTimelineProps {
  records: AuditRecord[];
  loading: boolean;
  error: string | null;
  onRetry?: () => void;
}

const AuditTimeline: React.FC<AuditTimelineProps> = ({ records, loading, error, onRetry }) => {
  const flowIds = Array.from(new Set(
    records.map((record) => record.copywritingApprovalFlowId).filter((flowId): flowId is string => Boolean(flowId)),
  ));

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5" aria-label="此前审核记录">
    <div className="mb-4 flex items-center justify-between gap-3">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-950">
        <History size={17} className="text-teal-600" />
        此前审核记录
      </div>
      {!loading && !error && <span className="text-xs text-gray-500">共 {records.length} 条</span>}
    </div>
    {loading ? (
      <div className="flex items-center justify-center gap-2 rounded-xl bg-gray-50 px-4 py-8 text-sm text-gray-500">
        <Loader2 size={18} className="animate-spin text-teal-600" />
        正在加载审核记录…
      </div>
    ) : error ? (
      <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
        <div>{error}</div>
        {onRetry && (
          <button type="button" onClick={onRetry} className="mt-3 rounded-full border border-red-200 bg-white px-3 py-1.5 text-xs font-medium hover:bg-red-100">
            重新加载
          </button>
        )}
      </div>
    ) : records.length === 0 ? (
      <div className="rounded-xl bg-gray-50 px-4 py-8 text-center text-sm text-gray-500">暂无历史审核记录</div>
    ) : (
      <ol className="space-y-0">
        {records.map((record, index) => {
          const result = getAuditResultLabel(record);
          const auditorName = record.auditorName || record.operatorName || '系统';
          const auditorRole = record.auditorRole || record.operatorRole || '审核人';
          const previousStatus = record.previousStatus || record.fromStatus;
          const currentStatus = record.currentStatus || record.toStatus;
          const comment = record.auditComment || record.comment;
          const flowRound = record.copywritingApprovalFlowId
            ? flowIds.indexOf(record.copywritingApprovalFlowId) + 1
            : 0;
          return (
            <li key={`${record.id}-${index}`} className="relative flex gap-3 pb-5 last:pb-0">
              {index < records.length - 1 && <span className="absolute left-[5px] top-3 h-[calc(100%-4px)] w-px bg-gray-200" />}
              <span className={`relative z-10 mt-1 h-3 w-3 flex-shrink-0 rounded-full ring-4 ring-white ${result === '驳回' ? 'bg-red-500' : result === '通过' ? 'bg-emerald-500' : 'bg-sky-500'}`} />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="break-words text-sm font-medium text-gray-900">{auditorName} · {auditorRole}</div>
                    {flowRound > 0 && (
                      <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-medium text-violet-700">
                        {flowIds.length > 1 ? `第 ${flowRound} 轮` : '本轮审核'}
                      </span>
                    )}
                  </div>
                  <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${getAuditResultStyle(result)}`}>{result}</span>
                </div>
                <div className="mt-1 text-xs text-gray-500">{formatAuditTime(record.auditTime || record.operationTime)}</div>
                {(previousStatus || currentStatus) && (
                  <div className="mt-2 break-words text-xs leading-5 text-gray-600">
                    状态：{previousStatus || '未知'} → {currentStatus || '未知'}
                  </div>
                )}
                {comment && (
                  <div className="mt-2 whitespace-pre-wrap break-words rounded-xl bg-gray-50 px-3 py-2 text-sm leading-6 text-gray-700">
                    {comment}
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    )}
    </section>
  );
};

interface CurrentUserInfo {
  userId?: string;
  nickName?: string;
  username?: string;
  profession?: string;
  avatar?: string | null;
  gender?: unknown;
  roles?: unknown[];
  role?: unknown;
  roleName?: unknown;
  roleCode?: unknown;
  authority?: unknown;
}

const getInitialCurrentUser = (): CurrentUserInfo => {
  const userInfo = getCurrentUser();
  return userInfo || {
    nickName: '刘文博',
    username: '刘文博',
    profession: '医生',
    avatar: null
  };
};

const ZhiheYishen: React.FC = () => {
  // 重要：所有Hooks必须在组件顶层调用，不能在条件语句中调用
  // 登录状态检测
  const { isAuthenticated, isChecking } = useAuthGuard({
    autoRedirect: true,
    checkInterval: 30000, // 30秒检查一次
    onUnauthorized: () => {
      console.log('ZhiheYishen: 检测到用户未登录');
    }
  });

  const [searchQuery, setSearchQuery] = useState('');
  const [newDocument, setNewDocument] = useState({ 
    title: '', 
    content: '', 
    category: '', 
    source: '' 
  });
  
  // 文案提交和查询相关状态
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoadingCopywritings, setIsLoadingCopywritings] = useState(false);
  const [copywritingError, setCopywritingError] = useState<string | null>(null);
  const [copywritings, setCopywritings] = useState<CopywritingItem[]>([]);
  const [selectedStatus, setSelectedStatus] = useState<number | number[]>(-1); // 初始选择全部状态
  const [showAddModal, setShowAddModal] = useState(false);
  
  // 文件转录状态
  const [isUploadingAudio, setIsUploadingAudio] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 动态获取的分类和来源数据
  const [categories, setCategories] = useState<CopywritingCategory[]>([]);
  const [sources, setSources] = useState<CopywritingSource[]>([]);
  const [isLoadingCategories, setIsLoadingCategories] = useState(false);
  const [isLoadingSources, setIsLoadingSources] = useState(false);
  
  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  
  // 审核记录状态管理
  const [auditRecords, setAuditRecords] = useState<AuditRecordDisplay[]>([]);
  const [isLoadingAuditRecords, setIsLoadingAuditRecords] = useState(false);
  const [hasMoreAuditRecords, setHasMoreAuditRecords] = useState(false);
  const [auditRecordsPage, setAuditRecordsPage] = useState(1);
  const [auditRecordsError, setAuditRecordsError] = useState<string | null>(null);
  const [showAuditRecordsModal, setShowAuditRecordsModal] = useState(false);
  
  // 侧边栏收起状态管理
  const [isAuditSidebarCollapsed, setIsAuditSidebarCollapsed] = useState(false);
  
  // 审核记录筛选状态
  const [auditRoleFilter, setAuditRoleFilter] = useState<string>('');
  const [auditResultFilter, setAuditResultFilter] = useState<string>('');
  const showLegacyAuditSidebar = false;
  
  // 用户信息状态管理
  const [currentUser] = useState<CurrentUserInfo>(() => getInitialCurrentUser());
  const isExpertUser = hasUserRole(currentUser, 'EXPERT');
  const canReviewAsExpert = (item: CopywritingItem): boolean => (
    item.expertReviewer ?? isExpertUser
  );
  const canReviewAsOrganization = (item: CopywritingItem): boolean => (
    item.organizationReviewer === true
  );

  // 审核相关状态管理
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [approvalComment, setApprovalComment] = useState('');
  const [currentApprovalItem, setCurrentApprovalItem] = useState<CopywritingItem | null>(null);
  const [isApproving, setIsApproving] = useState(false);
  const [reviewAuditHistory, setReviewAuditHistory] = useState<AuditRecord[]>([]);
  const [isLoadingReviewContext, setIsLoadingReviewContext] = useState(false);
  const [reviewContextError, setReviewContextError] = useState<string | null>(null);

  // 详情仅在点击列表项后加载；请求序号用于阻止关闭/切换后的迟到响应覆盖当前内容。
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [currentDetailItem, setCurrentDetailItem] = useState<CopywritingItem | null>(null);
  const [detailAuditHistory, setDetailAuditHistory] = useState<AuditRecord[]>([]);
  const [isLoadingDetailContext, setIsLoadingDetailContext] = useState(false);
  const [detailContextError, setDetailContextError] = useState<string | null>(null);
  const detailRequestSequence = useRef(0);
  const reviewRequestSequence = useRef(0);
  const detailAbortController = useRef<AbortController | null>(null);
  const reviewAbortController = useRef<AbortController | null>(null);
  const [totalAllItems, setTotalAllItems] = useState(0); // 全部文案总数

  useEffect(() => () => {
    detailAbortController.current?.abort('component-unmounted');
    reviewAbortController.current?.abort('component-unmounted');
  }, []);



  // 审批状态分类（用于筛选）- 改为状态管理
  const [statusCategories, setStatusCategories] = useState([
    { name: '作者待确认', status: APPROVAL_STATUS.AUTHOR_PENDING, count: 0 },
    { name: 'AI自动审核中', status: APPROVAL_STATUS.AI_PENDING, count: 0 },
    { name: '专家组专业审核', status: APPROVAL_STATUS.EXPERT_PENDING, count: 0 },
    { name: '医院/机构终审', status: APPROVAL_STATUS.ORGANIZATION_PENDING, count: 0 },
    { name: '审批完成', status: APPROVAL_STATUS.APPROVED, count: 0 },
    { name: '审批驳回', status: APPROVAL_STATUS.REJECTED, count: 0 }
  ]);

  // 动态生成的选项数据，添加空值检查
  const categoryOptions = [
    { value: '', label: '请选择分类' },
    ...(categories || []).map(cat => ({ value: cat.copywritingCategoryId, label: cat.name }))
  ];
  
  const sourceOptions = [
    { value: '', label: '请选择来源' },
    ...(sources || []).map(src => ({ value: src.copywritingSourceId, label: src.name }))
  ];
  
  // 加载文案分类数据
  const loadCategories = async () => {
    setIsLoadingCategories(true);
    try {
      const categoriesData = await getCopywritingCategories();
      setCategories(categoriesData);
    } catch (error) {
      console.error('加载文案分类失败:', error);
      setCopywritingError('加载文案分类失败，请稍后重试');
    } finally {
      setIsLoadingCategories(false);
    }
  };
  
  // 加载文案来源数据
  const loadSources = async () => {
    setIsLoadingSources(true);
    try {
      const sourcesData = await getCopywritingSources();
      setSources(sourcesData);
    } catch (error) {
      console.error('加载文案来源失败:', error);
      setCopywritingError('加载文案来源失败，请稍后重试');
    } finally {
      setIsLoadingSources(false);
    }
  };

  // 转换审核记录数据为显示格式
  const transformAuditRecordData = (apiData: AuditRecord[]): AuditRecordDisplay[] => {
    return apiData.map((item) => ({
      id: item.id,
      copywritingId: String(item.copywritingId || ''),
      copywritingApprovalFlowId: item.copywritingApprovalFlowId,
      copywritingTitle: item.copywritingTitle,
      auditTime: item.auditTime,
      auditorName: item.auditorName,
      auditorRole: item.auditorRole,
      auditResult: item.auditResult,
      auditComment: item.auditComment,
      previousStatus: item.previousStatus,
      currentStatus: item.currentStatus,
      statusChange: `${item.previousStatus} → ${item.currentStatus}`
    }));
  };

  // 加载审核记录
  const loadAuditRecords = async (refresh: boolean = false, pageOverride?: number) => {
    try {
      setIsLoadingAuditRecords(true);
      setAuditRecordsError(null);
      
      const targetPage = refresh ? 1 : (pageOverride ?? auditRecordsPage);
      const params: GetAuditRecordsParams = {
        page: targetPage,
        size: 10,
        auditorRole: auditRoleFilter || undefined,
        auditResult: auditResultFilter || undefined
      };
      
      const response = await getAuditRecords(params);
      const transformedData = transformAuditRecordData(response.data);
      
      if (refresh) {
        setAuditRecords(transformedData);
        setAuditRecordsPage(1);
      } else {
        setAuditRecords(prev => [...prev, ...transformedData]);
        setAuditRecordsPage(targetPage);
      }
      
      setHasMoreAuditRecords(response.hasNext);
    } catch (error) {
      console.error('加载审核记录失败:', error);
      if (error instanceof AppRequestError && error.status === 403) {
        setAuditRecordsError('当前账号无权查看审核记录');
      } else if (error instanceof AppRequestError && error.status === 401) {
        setAuditRecordsError('登录状态已过期，请重新登录');
      } else {
        setAuditRecordsError('审核记录服务暂时异常，请稍后重试');
      }
    } finally {
      setIsLoadingAuditRecords(false);
    }
  };

  // 刷新审核记录
  const handleRefreshAuditRecords = async () => {
    await loadAuditRecords(true);
  };

  // 加载更多审核记录
  const handleLoadMoreAuditRecords = async () => {
    if (!hasMoreAuditRecords || isLoadingAuditRecords) return;
    await loadAuditRecords(false, auditRecordsPage + 1);
  };

  // 切换侧边栏收起状态
  const handleToggleAuditSidebar = () => {
    setIsAuditSidebarCollapsed(!isAuditSidebarCollapsed);
  };

  // 添加初始化状态管理，防止重复调用
  const [isInitialized, setIsInitialized] = useState(false);
  const [isInitializing, setIsInitializing] = useState(false);

  const getStatusColor = (status: string) => {
    if (status.includes('通过') || status.includes('审批完成')) {
      return 'text-teal-600';
    } else if (status.includes('审核中') || status.includes('待审核') || status.includes('待审批')) {
      return 'text-yellow-600';
    } else if (status.includes('拒绝') || status.includes('未通过') || status.includes('驳回')) {
      return 'text-red-600';
    }
    return 'text-gray-600';
  };

  // 时间格式化函数 - 转换为北京时间标准格式
  const formatBeijingTime = (timeStr: string): string => {
    try {
      const date = new Date(timeStr);
      // 转换为北京时间 (UTC+8)
      const beijingTime = new Date(date.getTime());
      return beijingTime.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZone: 'Asia/Shanghai'
      });
    } catch (error) {
      console.error('时间格式化失败:', error);
      return timeStr;
    }
  };

  // 转换文案数据为文档格式的函数
  const transformCopywritingToDocument = (item: CopywritingItem): Document => {
    // 添加空值检查
    if (!item || typeof item !== 'object') {
      return {
        id: '0',
        title: '数据错误',
        author: isExpertUser ? '未知提交人' : currentUser?.nickName || '当前用户',
        authorType: '作者',
        date: formatBeijingTime(new Date().toISOString()),
        status: '未知',
        evaluate: '',
        createTime: formatBeijingTime(new Date().toISOString()),
        approvalStatusCode: null
      };
    }
    
    const copywriting = item.copywriting || {
      copywritingId: 0,
      title: '',
      summary: '',
      content: '',
      status: '',
      createTime: new Date().toISOString()
    };

    const approvalStatusCode = getApprovalStatusCode(item);
    const statusDisplay = (
      approvalStatusCode !== null
        ? APPROVAL_STATUS_TEXT[approvalStatusCode as keyof typeof APPROVAL_STATUS_TEXT]
        : undefined
    ) || String(item.approvalFlow?.currentStatus || '未知状态');
    const submitterName = item.submitterUser?.nickName?.trim();

    return {
      id: (copywriting.copywritingId ?? 0).toString(),
      title: copywriting.title || '未命名文案',
      author: submitterName || (isExpertUser ? '未知提交人' : currentUser?.nickName || '当前用户'),
      authorType: '作者',
      date: formatBeijingTime(copywriting.createTime || new Date().toISOString()),
      status: statusDisplay,
      evaluate: '',
      createTime: formatBeijingTime(copywriting.createTime || new Date().toISOString()),
      approvalStatusCode,
      expertReviewAllowed: canReviewAsExpert(item),
      organizationReviewAllowed: canReviewAsOrganization(item)
    };
  };
  
  // 使用API数据替代静态数据，添加更严格的空值检查
  const displayDocuments = React.useMemo(() => {
    // 确保copywritings是数组且不为null/undefined
    if (!Array.isArray(copywritings)) {
      return [];
    }
    
    // 过滤掉null/undefined项并进行转换
    return copywritings
      .filter(item => item != null)
      .map(item => transformCopywritingToDocument(item));
  }, [copywritings, currentUser, isExpertUser]);

  const filteredDocuments = React.useMemo(() => {
    // 确保displayDocuments是数组
    if (!Array.isArray(displayDocuments)) {
      return [];
    }
    
    return displayDocuments.filter(doc => {
      // 添加空值检查
      if (!doc || typeof doc !== 'object') {
        return false;
      }
      
      const title = doc.title || '';
      const author = doc.author || '';
      const query = searchQuery || '';
      
      return title.toLowerCase().includes(query.toLowerCase()) ||
             author.toLowerCase().includes(query.toLowerCase());
    });
  }, [displayDocuments, searchQuery]);

  // 计算分页数据
  const totalFilteredItems = totalItems || 0; // 使用API返回的总数，添加默认值
  const totalPages = Math.ceil(totalFilteredItems / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedDocuments = React.useMemo(() => {
    // 确保filteredDocuments是数组
    if (!Array.isArray(filteredDocuments)) {
      return [];
    }
    return filteredDocuments; // API已经处理了分页
  }, [filteredDocuments]);
  
  // 更新总条数
  React.useEffect(() => {
    try {
      // 确保totalFilteredItems是有效数字
      const validTotalItems = typeof totalFilteredItems === 'number' && !isNaN(totalFilteredItems) ? totalFilteredItems : 0;
      setTotalItems(validTotalItems);
      
      // 如果当前页超出范围，重置到第一页
      if (currentPage > totalPages && totalPages > 0) {
        setCurrentPage(1);
      }
    } catch (error) {
      console.error('更新总条数时出错:', error);
      setTotalItems(0);
    }
  }, [totalFilteredItems, currentPage, totalPages]);

  // 分页事件处理函数
  const handlePageChange = (page: number) => {
    try {
      // 验证页码是否有效
      if (typeof page === 'number' && !isNaN(page) && page > 0 && page <= totalPages) {
        setCurrentPage(page);
      }
    } catch (error) {
      console.error('页码切换时出错:', error);
    }
  };
  
  const handleItemsPerPageChange = (items: number) => {
    try {
      // 验证每页条数是否有效
      if (typeof items === 'number' && !isNaN(items) && items > 0) {
        setItemsPerPage(items);
        setCurrentPage(1); // 重置到第一页
      }
    } catch (error) {
      console.error('每页条数切换时出错:', error);
    }
  };
  
  const handlePreviousPage = () => {
    try {
      if (currentPage > 1) {
        setCurrentPage(currentPage - 1);
      }
    } catch (error) {
      console.error('上一页切换时出错:', error);
    }
  };
  
  const handleNextPage = () => {
    try {
      if (currentPage < totalPages) {
        setCurrentPage(currentPage + 1);
      }
    } catch (error) {
      console.error('下一页切换时出错:', error);
    }
  };

  const handleAddDocument = async () => {
    if (!newDocument.title.trim() || !newDocument.content.trim() || !newDocument.category.trim() || !newDocument.source.trim()) {
      setCopywritingError('请填写完整的标题、文案内容、分类和来源');
      return;
    }

    // 验证分类和来源是否为有效的ID
    const categoryId = newDocument.category;
    const sourceId = newDocument.source;
    
    if (!categoryId || categoryId.trim() === '') {
      setCopywritingError('请选择有效的文案分类');
      return;
    }
    
    if (!sourceId || sourceId.trim() === '') {
      setCopywritingError('请选择有效的文案来源');
      return;
    }

    setIsSubmitting(true);
    setCopywritingError(null);
    
    try {
      // 生成UUID
      const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
      
      const submitData: SubmitCopywritingRequest = {
        title: newDocument.title,
        content: newDocument.content,
        // 后端仍要求 summary 非空；添加表单移除摘要后，以正文开头生成兼容值。
        summary: Array.from(newDocument.content.trim().replace(/\s+/g, ' ')).slice(0, 200).join(''),
        copywritingCategoryId: categoryId,
        copywritingSourceId: sourceId
      };
      
      await submitCopywriting(submitData, uuid);
      
      // 提交成功后重置表单并关闭弹窗
      setNewDocument({ title: '', content: '', category: '', source: '' });
      setShowAddModal(false);
      
      // 刷新文案列表（状态统计会在需要时自动更新）
      await loadCopywritingList();
      
      alert('文案已提交，请在“作者待确认”中完成自审确认。');
    } catch (error) {
      console.error('提交文案失败:', error);
      setCopywritingError('提交文案失败，请稍后重试');
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * 处理音频文件上传转录
   */
  const handleAudioUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploadingAudio(true);
    try {
      const result = await asrService.uploadAudioFile(file);
      console.log('ASR处理完成，结果长度:', typeof result === 'string' ? result.length : 0);
      
      // 尝试解析 JSON 结果
      let text = result;
      try {
        const json = JSON.parse(result);
        if (json.sentences && Array.isArray(json.sentences)) {
          text = json.sentences.map((sentence: unknown) => {
            if (sentence == null || typeof sentence !== 'object') return '';
            return String((sentence as Record<string, unknown>).text ?? '');
          }).join('');
        }
      } catch (e) {
        // 忽略解析错误，直接使用原始文本
        console.warn('ASR结果非标准JSON，直接使用:', e);
      }

      // 追加到文案内容
      setNewDocument(prev => ({
        ...prev,
        content: prev.content ? prev.content + '\n' + text : text
      }));
    } catch (error) {
      console.error('文件转录失败:', error);
      alert('文件转录失败，请重试');
    } finally {
      setIsUploadingAudio(false);
      // 清空 input 以便下次选择同名文件
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleCloseModal = () => {
    setShowAddModal(false);
    setCopywritingError(null);
    setNewDocument({ title: '', content: '', category: '', source: '' });
  };
  
  // 优化后的状态统计函数，添加防重复调用机制
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  
  const updateStatusCounts = async () => {
    if (isUpdatingStatus) {
      console.log('状态统计正在更新中，跳过重复请求');
      return totalItems;
    }
    
    setIsUpdatingStatus(true);
    
    try {
      const allStatuses = [0, 1, 2, 3, 4, 5]; // 对应所有审批状态
      console.log('开始更新状态统计...');
      
      const countPromises = allStatuses.map(async (statusValue) => {
        try {
          const params: GetCopywritingListParams = {
            page: 1,
            size: 100, // 使用较大的size以获取所有数据进行准确统计
            status: statusValue
          };
          const response = await getCopywritingListByStatus(params);
          // console.log(`状态${statusValue}的API响应:`, response);
           
           // 解析响应获取总数
           let count = 0;
           if (Array.isArray(response)) {
             count = response.length;
             // console.log(`状态${statusValue}(数组格式)的数量: ${count}`);
           } else if (response && typeof response === 'object' && 'total' in response) {
             count = (response as PaginationData).total || 0;
             // console.log(`状态${statusValue}(分页格式)的数量: ${count}`);
           } else {
             console.log(`状态${statusValue}响应格式未知`);
           }
           return count;
        } catch (error) {
          console.error(`获取状态${statusValue}的数量失败:`, error);
          // 如果获取失败，返回0
          return 0;
        }
      });
      
      const counts = await Promise.all(countPromises);
      console.log('所有状态的统计结果:', counts);
      
      // 更新状态分类计数
      setStatusCategories(prev => {
        const updated = prev.map((category) => {
          let count = 0;
          if (Array.isArray(category.status)) {
            // 如果状态是数组，累加所有状态的数量
            count = category.status.reduce((sum, s) => sum + (counts[s] || 0), 0);
          } else {
            // 如果状态是单个数字
            count = counts[category.status] || 0;
          }
          return { ...category, count };
        });
        console.log('更新后的状态分类:', updated);
        return updated;
      });
      
      // 计算总数并更新totalItems
      const totalCount = counts.reduce((sum, count) => sum + count, 0);
      console.log('总文案数量:', totalCount);
      
      // 更新totalAllItems状态
      setTotalAllItems(totalCount);
      
      return totalCount;
    } catch (error) {
      console.error('更新状态统计失败:', error);
      return totalItems || 0;
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // 优化后的加载文案列表函数，避免循环调用
  const loadCopywritingList = async (status?: number | number[]) => {
    if (isLoadingCopywritings) {
      console.log('文案列表正在加载中，跳过重复请求');
      return;
    }
    
    setIsLoadingCopywritings(true);
    setCopywritingError(null);
    
    try {
      const targetStatus = status !== undefined ? status : selectedStatus;
      console.log('加载文案列表，状态:', targetStatus);
      
      // 如果是全部状态，调用所有状态(0-5)的接口并合并数据
      if (targetStatus === -1) {
        const allStatuses = [0, 1, 2, 3, 4, 5]; // 对应所有审批状态
        // 使用Promise.allSettled代替Promise.all，防止单个请求失败导致整体失败
        const allPromises = allStatuses.map(s => {
          const params: GetCopywritingListParams = {
            page: 1, // 获取所有数据时从第一页开始
            size: 10, // 设置较大的size以获取更多数据
            status: s
          };
          return getCopywritingListByStatus(params)
            .then(data => ({ status: 'fulfilled' as const, value: data }))
            .catch(error => ({ status: 'rejected' as const, reason: error }));
        });
        
        const allResults = await Promise.all(allPromises);
        
        // 合并所有成功状态的数据
        let allCopywritings: CopywritingItem[] = [];
        let totalCount = 0;
        let hasError = false;
        
        allResults.forEach((result) => {
          if (result.status === 'fulfilled') {
            const response = result.value;
            // 如果响应是数组（直接返回数据数组）
            if (Array.isArray(response)) {
              allCopywritings = [...allCopywritings, ...response];
              totalCount += response.length;
            }
            // 如果响应是PaginationData对象
            else if (response && typeof response === 'object' && 'data' in response) {
              const paginationData = response as PaginationData;
              if (paginationData.data && Array.isArray(paginationData.data)) {
                allCopywritings = [...allCopywritings, ...paginationData.data];
                totalCount += paginationData.total || 0;
              }
            }
          } else {
            console.warn('获取部分状态文案失败');
            hasError = true;
          }
        });
        
        // 只有当所有请求都失败时才报错
        if (hasError && allCopywritings.length === 0 && totalCount === 0) {
          console.error('所有文案状态请求均失败');
          setCopywritingError('加载文案列表失败，请稍后重试');
        } else {
           // 部分成功也算成功
           setCopywritingError(null);
        }
        
        // 按创建时间排序（最新的在前）
        allCopywritings.sort((a, b) => {
          const timeA = a.copywriting?.createTime || '';
          const timeB = b.copywriting?.createTime || '';
          return new Date(timeB).getTime() - new Date(timeA).getTime();
        });
        
        // 手动分页处理
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedData = allCopywritings.slice(startIndex, endIndex);
        
        setCopywritings(paginatedData);
        // 只有当totalCount大于0或者当前totalAllItems为0时才更新
        if (totalCount > 0 || totalAllItems === 0) {
          setTotalAllItems(totalCount);
        }
        // 对于"全部文案"状态，分页总数也等于总文案数
        setTotalItems(totalCount);
      } else if (Array.isArray(targetStatus)) {
        // 兼容组合状态筛选。
        const allPromises = targetStatus.map(s => {
          const params: GetCopywritingListParams = {
            page: 1, // 获取所有数据时从第一页开始
            size: 100, // 设置较大的size以获取更多数据
            status: s
          };
          return getCopywritingListByStatus(params)
            .then(data => ({ status: 'fulfilled' as const, value: data }))
            .catch(error => ({ status: 'rejected' as const, reason: error }));
        });
        
        const allResults = await Promise.all(allPromises);
        
        // 合并所有成功状态的数据
        let allCopywritings: CopywritingItem[] = [];
        let totalCount = 0;
        let hasError = false;
        
        allResults.forEach((result) => {
          if (result.status === 'fulfilled') {
            const response = result.value;
            // 如果响应是数组（直接返回数据数组）
            if (Array.isArray(response)) {
              allCopywritings = [...allCopywritings, ...response];
              totalCount += response.length;
            }
            // 如果响应是PaginationData对象
            else if (response && typeof response === 'object' && 'data' in response) {
              const paginationData = response as PaginationData;
              if (paginationData.data && Array.isArray(paginationData.data)) {
                allCopywritings = [...allCopywritings, ...paginationData.data];
                totalCount += paginationData.total || 0;
              }
            }
          } else {
            console.warn('获取部分状态文案失败');
            hasError = true;
          }
        });
        
        // 只有当所有请求都失败时才报错
        if (hasError && allCopywritings.length === 0 && totalCount === 0) {
          console.error('所有文案状态请求均失败');
          setCopywritingError('加载文案列表失败，请稍后重试');
        } else {
           // 部分成功也算成功
           setCopywritingError(null);
        }
        
        // 按创建时间排序（最新的在前）
        allCopywritings.sort((a, b) => {
          const timeA = a.copywriting?.createTime || '';
          const timeB = b.copywriting?.createTime || '';
          return new Date(timeB).getTime() - new Date(timeA).getTime();
        });
        
        // 手动分页处理
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedData = allCopywritings.slice(startIndex, endIndex);
        
        setCopywritings(paginatedData);
        setTotalItems(totalCount);
      } else {
        // 单个状态的正常查询
        const params: GetCopywritingListParams = {
          page: currentPage,
          size: itemsPerPage,
          status: targetStatus
        };
        
        const response = await getCopywritingListByStatus(params);
        
        // 检查响应格式
        if (Array.isArray(response)) {
          // 如果响应是数组（直接返回数据数组）
          setCopywritings(response);
        } else if (response && typeof response === 'object' && 'data' in response) {
          // 如果响应是PaginationData对象
          const paginationData = response as PaginationData;
          if (paginationData.data && Array.isArray(paginationData.data)) {
            setCopywritings(paginationData.data);
            setTotalItems(paginationData.total || 0); // 更新当前分类的总数
          } else {
            setCopywritings([]);
            setTotalItems(0);
          }
        } else {
          setCopywritings([]);
          setTotalItems(0);
        }
      }
      
      console.log('文案列表加载完成');
    } catch (error) {
      console.error('加载文案列表失败:', error);
      setCopywritingError('加载文案列表失败，请稍后重试');
      setCopywritings([]);
    } finally {
      setIsLoadingCopywritings(false);
    }
  };
  
  // 优化后的状态切换处理，避免重复调用
  const handleStatusChange = async (status: number | number[]) => {
    try {
      console.log('切换状态到:', status);
      setSelectedStatus(status);
      setCurrentPage(1); // 重置到第一页
      
      // 只加载对应状态的文案列表，不重复更新状态统计
      await loadCopywritingList(status);
    } catch (error) {
      console.error('状态切换失败:', error);
    }
  };

  const handleManualRefresh = async () => {
    try {
      await Promise.all([
        loadCopywritingList(selectedStatus),
        updateStatusCounts()
      ]);
    } catch (error) {
      console.error('手动刷新失败:', error);
      setCopywritingError('刷新失败，请稍后重试');
    }
  };

  const sortAuditHistory = (records: AuditRecord[]): AuditRecord[] => (
    [...records].sort((left, right) => {
      const leftTime = new Date(left.auditTime || left.operationTime || '').getTime();
      const rightTime = new Date(right.auditTime || right.operationTime || '').getTime();
      if (Number.isNaN(leftTime) || Number.isNaN(rightTime)) return 0;
      return leftTime - rightTime;
    })
  );

  const getContextErrorMessage = (error: unknown): string => {
    if (error instanceof AppRequestError) {
      if (error.status === 401) return '登录状态已过期，请重新登录后再试';
      if (error.status === 403) return '当前账号无权查看该文案详情';
      if (error.status === 404) return '文案不存在、已删除或当前不可访问';
      if ((error.status != null && error.status >= 500) || String(error.code || '') === 'E0012') {
        return '详情服务暂时异常，请稍后重试';
      }
      return error.message || '详情加载失败，请重试';
    }
    return error instanceof Error ? error.message : '详情加载失败，请重试';
  };

  const fetchCopywritingContext = async (item: CopywritingItem, signal?: AbortSignal): Promise<{
    item: CopywritingItem;
    history: AuditRecord[];
  }> => {
    const copywritingId = String(item.copywriting.copywritingId);
    try {
      const detail: CopywritingDetail = await getCopywritingDetail(copywritingId, signal);
      return {
        item: {
          ...item,
          ...detail,
          copywriting: {
            ...item.copywriting,
            ...detail.copywriting,
            copywritingId,
            content: detail.copywriting.content || item.copywriting.content || ''
          },
          approvalUser: detail.approvalUser || item.approvalUser,
          submitterUser: detail.submitterUser || item.submitterUser
        },
        history: sortAuditHistory(detail.auditHistory || [])
      };
    } catch (detailError) {
      // 仅对 HTTP 404 做短暂兼容；权限错误和服务异常必须原样反馈，不能伪装成空记录。
      if (!(detailError instanceof AppRequestError) || detailError.status !== 404) {
        throw detailError;
      }
      try {
        const history = await getCopywritingAuditHistory(copywritingId, signal);
        if (!item.copywriting.content?.trim()) {
          throw detailError;
        }
        return { item, history: sortAuditHistory(history) };
      } catch (historyError) {
        console.error('加载文案详情失败:', detailError, historyError);
        throw historyError;
      }
    }
  };

  const loadDetailContext = async (item: CopywritingItem) => {
    const requestSequence = ++detailRequestSequence.current;
    detailAbortController.current?.abort('replaced-by-new-detail');
    const abortController = new AbortController();
    detailAbortController.current = abortController;
    setIsLoadingDetailContext(true);
    setDetailContextError(null);
    setDetailAuditHistory([]);
    try {
      const context = await fetchCopywritingContext(item, abortController.signal);
      if (requestSequence !== detailRequestSequence.current) return;
      setCurrentDetailItem(context.item);
      setDetailAuditHistory(context.history);
    } catch (error) {
      if (requestSequence !== detailRequestSequence.current) return;
      setDetailContextError(getContextErrorMessage(error));
    } finally {
      if (requestSequence === detailRequestSequence.current) {
        setIsLoadingDetailContext(false);
        if (detailAbortController.current === abortController) detailAbortController.current = null;
      }
    }
  };

  const handleDetailClick = (item: CopywritingItem) => {
    setCurrentDetailItem(item);
    setShowDetailModal(true);
    void loadDetailContext(item);
  };

  const handleDetailCancel = () => {
    detailRequestSequence.current += 1;
    detailAbortController.current?.abort('detail-closed');
    detailAbortController.current = null;
    setShowDetailModal(false);
    setCurrentDetailItem(null);
    setDetailAuditHistory([]);
    setDetailContextError(null);
    setIsLoadingDetailContext(false);
  };

  const handleOpenAuditRecords = () => {
    setShowAuditRecordsModal(true);
    void loadAuditRecords(true);
  };

  const handleAuditRecordDetail = (record: AuditRecordDisplay) => {
    if (!record.copywritingId) return;
    const listItem = copywritings.find((item) => (
      String(item.copywriting.copywritingId) === record.copywritingId
    ));
    const detailSeed: CopywritingItem = listItem || {
      copywriting: {
        copywritingId: record.copywritingId,
        title: record.copywritingTitle || '未命名文案',
        summary: '',
        content: '',
        status: '',
        createTime: record.auditTime || ''
      },
      categoryName: '',
      sourceName: '',
      approvalFlow: {
        copywritingApprovalFlowId: record.copywritingApprovalFlowId,
        currentStatus: record.currentStatus || '',
        createTime: null
      },
      approvalUser: null,
      submitterUser: null
    };
    setShowAuditRecordsModal(false);
    handleDetailClick(detailSeed);
  };

  const loadReviewContext = async (item: CopywritingItem) => {
    const requestSequence = ++reviewRequestSequence.current;
    reviewAbortController.current?.abort('replaced-by-new-review');
    const abortController = new AbortController();
    reviewAbortController.current = abortController;
    setIsLoadingReviewContext(true);
    setReviewContextError(null);
    setReviewAuditHistory([]);
    try {
      const context = await fetchCopywritingContext(item, abortController.signal);
      if (requestSequence !== reviewRequestSequence.current) return;
      setCurrentApprovalItem(context.item);
      setReviewAuditHistory(context.history);
    } catch (error) {
      if (requestSequence !== reviewRequestSequence.current) return;
      setReviewContextError(getContextErrorMessage(error));
    } finally {
      if (requestSequence === reviewRequestSequence.current) {
        setIsLoadingReviewContext(false);
        if (reviewAbortController.current === abortController) reviewAbortController.current = null;
      }
    }
  };

  // 审核相关处理函数
  const canCurrentUserReview = (item: CopywritingItem): boolean => {
    const statusCode = getApprovalStatusCode(item);
    const currentUserId = String(currentUser?.userId ?? '').trim();
    const submitterUserId = String(item.submitterUser?.userId ?? '').trim();
    const isCurrentUserSubmitter = Boolean(
      currentUserId && submitterUserId && currentUserId === submitterUserId,
    );
    return (statusCode === APPROVAL_STATUS.AUTHOR_PENDING && isCurrentUserSubmitter)
      || (statusCode === APPROVAL_STATUS.EXPERT_PENDING && canReviewAsExpert(item))
      || (statusCode === APPROVAL_STATUS.ORGANIZATION_PENDING && canReviewAsOrganization(item));
  };

  const getClaimableReviewStage = (item: CopywritingItem): 'expert' | 'organization' | null => {
    const statusCode = getApprovalStatusCode(item);
    if (statusCode === APPROVAL_STATUS.EXPERT_PENDING && canReviewAsExpert(item)) return 'expert';
    if (statusCode === APPROVAL_STATUS.ORGANIZATION_PENDING && canReviewAsOrganization(item)) return 'organization';
    return null;
  };

  const claimReviewTaskIfNeeded = async (item: CopywritingItem): Promise<CopywritingItem> => {
    const reviewStage = getClaimableReviewStage(item);
    if (!reviewStage) {
      return item;
    }

    const currentUserId = String(currentUser?.userId ?? '').trim();
    if (!currentUserId) {
      throw new Error('当前审核账号缺少用户ID，请重新登录后重试');
    }

    const assignedUserId = String(item.approvalUser?.userId ?? '').trim();
    if (assignedUserId && assignedUserId !== currentUserId) {
      throw new Error('该任务已被其他审核人认领，请刷新列表');
    }
    if (assignedUserId === currentUserId) {
      return item;
    }

    const approvalFlowId = getApprovalFlowId(item);
    if (!approvalFlowId) {
      throw new Error('无法获取审批流程ID，请刷新页面重试');
    }

    if (reviewStage === 'expert') {
      await claimByExpert({ copywritingApprovalFlowId: approvalFlowId });
    } else {
      await claimByOrganization({ copywritingApprovalFlowId: approvalFlowId });
    }

    return {
      ...item,
      approvalUser: {
        userId: currentUserId,
        nickName: currentUser?.nickName || currentUser?.username || '当前审核人',
        avatar: currentUser?.avatar || undefined,
        gender: currentUser?.gender == null ? undefined : String(currentUser.gender)
      }
    };
  };

  const handleApprovalClick = async (item: CopywritingItem) => {
    const isClaimableTask = getClaimableReviewStage(item) !== null;

    if (isClaimableTask) setIsApproving(true);
    try {
      const approvalItem = await claimReviewTaskIfNeeded(item);
      setCopywritings((items) => items.map((candidate) => (
        candidate.copywriting.copywritingId === approvalItem.copywriting.copywritingId
          ? approvalItem
          : candidate
      )));
      setCurrentApprovalItem(approvalItem);
      setApprovalComment('');
      setShowApprovalModal(true);
      void loadReviewContext(approvalItem);
    } catch (error) {
      console.error('认领审核任务失败:', error);
      alert(error instanceof Error ? error.message : '认领审核任务失败，请稍后重试');
      await loadCopywritingList(selectedStatus);
    } finally {
      if (isClaimableTask) setIsApproving(false);
    }
  };

  const handleEnterReviewFromDetail = async () => {
    if (!currentDetailItem) return;
    const item = currentDetailItem;
    handleDetailCancel();
    await handleApprovalClick(item);
  };

  const handleApprovalSubmit = async () => {
    if (!currentApprovalItem) return;

    if (isLoadingReviewContext || reviewContextError || !currentApprovalItem.copywriting.content?.trim()) {
      alert('请先成功加载完整文案和此前审核记录');
      return;
    }

    if (!approvalComment.trim()) {
      alert(getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.AUTHOR_PENDING
        ? '确认说明为必填项，请填写后再提交'
        : '审核意见为必填项，请填写后再提交');
      return;
    }

    setIsApproving(true);
    try {
      const approvalItem = await claimReviewTaskIfNeeded(currentApprovalItem);
      setCurrentApprovalItem(approvalItem);

      const approvalFlowId = getApprovalFlowId(approvalItem);
      if (!approvalFlowId) {
        throw new Error('无法获取审批流程ID，请刷新页面重试');
      }

      const approvalData: ConfirmByAuthorRequest | ApproveByExpertRequest | ApproveByOrganizationRequest = {
        copywritingApprovalFlowId: approvalFlowId,
        copywritingId: approvalItem.copywriting.copywritingId,
        comment: approvalComment.trim()
      };
      const statusCode = getApprovalStatusCode(approvalItem);

      if (statusCode === APPROVAL_STATUS.AUTHOR_PENDING) {
        await confirmByAuthor(approvalData);
      } else if (statusCode === APPROVAL_STATUS.EXPERT_PENDING && canReviewAsExpert(approvalItem)) {
        await approveByExpert(approvalData);
      } else if (statusCode === APPROVAL_STATUS.ORGANIZATION_PENDING && canReviewAsOrganization(approvalItem)) {
        await approveByOrganization(approvalData);
      } else {
        throw new Error('当前账号无权处理该审核阶段');
      }

      setShowApprovalModal(false);
      setCurrentApprovalItem(null);
      setApprovalComment('');
      reviewRequestSequence.current += 1;
      reviewAbortController.current?.abort('review-completed');
      reviewAbortController.current = null;
      setReviewAuditHistory([]);
      setReviewContextError(null);

      await Promise.all([
        loadCopywritingList(selectedStatus),
        updateStatusCounts()
      ]);

      alert(statusCode === APPROVAL_STATUS.AUTHOR_PENDING
        ? '已确认并提交AI自动审核！'
        : '审核提交成功！');
    } catch (error) {
      console.error('审核提交失败:', error);
      alert(error instanceof Error ? '审核提交失败：' + error.message : '审核提交失败，请稍后重试');
    } finally {
      setIsApproving(false);
    }
  };

  const handleApprovalCancel = () => {
    reviewRequestSequence.current += 1;
    reviewAbortController.current?.abort('review-closed');
    reviewAbortController.current = null;
    setShowApprovalModal(false);
    setCurrentApprovalItem(null);
    setApprovalComment('');
    setReviewAuditHistory([]);
    setReviewContextError(null);
    setIsLoadingReviewContext(false);
  };

  const handleRejectSubmit = async () => {
    if (!currentApprovalItem) return;

    if (isLoadingReviewContext || reviewContextError || !currentApprovalItem.copywriting.content?.trim()) {
      alert('请先成功加载完整文案和此前审核记录');
      return;
    }

    if (!approvalComment.trim()) {
      alert('审核意见为必填项，请填写后再提交');
      return;
    }

    setIsApproving(true);
    try {
      const approvalItem = await claimReviewTaskIfNeeded(currentApprovalItem);
      setCurrentApprovalItem(approvalItem);

      const approvalFlowId = getApprovalFlowId(approvalItem);
      if (!approvalFlowId) {
        throw new Error('无法获取审批流程ID，请刷新页面重试');
      }

      const rejectData: RejectCopywritingRequest = {
        copywritingApprovalFlowId: approvalFlowId,
        copywritingId: approvalItem.copywriting.copywritingId,
        comment: approvalComment.trim()
      };

      await rejectCopywriting(rejectData);

      setShowApprovalModal(false);
      setCurrentApprovalItem(null);
      setApprovalComment('');
      reviewRequestSequence.current += 1;
      reviewAbortController.current?.abort('review-completed');
      reviewAbortController.current = null;
      setReviewAuditHistory([]);
      setReviewContextError(null);

      await Promise.all([
        loadCopywritingList(selectedStatus),
        updateStatusCounts()
      ]);

      alert('驳回提交成功！');
    } catch (error) {
      console.error('驳回提交失败:', error);
      alert(error instanceof Error ? '驳回提交失败：' + error.message : '驳回提交失败，请稍后重试');
    } finally {
      setIsApproving(false);
    }
  };


  
  // 统一的初始化逻辑，避免重复调用
  useEffect(() => {
    const initializeData = async () => {
      if (isInitializing || isInitialized) {
        return; // 防止重复初始化
      }
      
      setIsInitializing(true);
      
      try {
        console.log('开始初始化ZhiheYishen组件数据...');
        
        // 1. 并行加载基础数据。隐藏的全局审核侧栏不再在挂载时请求，详情历史按点击加载。
        await Promise.all([
          loadCategories(),
          loadSources()
        ]);
        
        // 2. 更新状态统计
        await updateStatusCounts();
        
        // 3. 加载文案列表
        await loadCopywritingList();
        
        console.log('ZhiheYishen组件初始化完成');
        setIsInitialized(true);
      } catch (error) {
        console.error('初始化数据失败:', error);
      } finally {
        setIsInitializing(false);
      }
    };
    
    initializeData();
  }, []); // 只在组件挂载时执行一次

  // 分页变化时重新加载文案列表
  useEffect(() => {
    if (isInitialized) {
      loadCopywritingList();
    }
  }, [currentPage, itemsPerPage, isInitialized]);

  // 如果正在检查登录状态，显示加载界面
  if (isChecking) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-teal-600 mx-auto mb-4"></div>
          <p className="text-gray-600">检查登录状态中...</p>
        </div>
      </div>
    );
  }

  // 如果未登录，显示登录提示
  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-800 mb-4">请先登录</h2>
          <p className="text-gray-600 mb-6">您需要登录后才能访问此页面</p>
          <button
            onClick={() => window.location.href = '/login'}
            className="px-6 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
          >
            前往登录
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 h-full bg-[#f4f7f6] transition-all overflow-hidden">
      <div className="h-full p-2 sm:p-3">
        <aside className="hidden">
          <div className="border-b border-gray-100 p-4">
            <div className="mb-4 flex items-start gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gray-950 text-white">
                <ClipboardCheck size={22} />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-base font-semibold text-gray-950">
                  <Sparkles size={17} className="text-teal-600" />
                  智核医审
                </div>
                <p className="mt-1 text-xs text-gray-500">医学内容审核工作台</p>
              </div>
            </div>
            <button
              onClick={() => setShowAddModal(true)}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-gray-950 px-4 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-gray-800"
            >
              <Plus size={16} />
              添加文案
            </button>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
            <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-gray-500">
              <Clock size={14} />
              审批状态
            </div>
            <div className="space-y-2">
              <button
                type="button"
                onClick={() => handleStatusChange(-1)}
                className={`flex w-full items-center justify-between rounded-xl border px-3 py-3 text-sm font-semibold transition-all ${
                  selectedStatus === -1
                    ? 'border-teal-500/70 bg-teal-50 text-teal-700 shadow-sm'
                    : 'border-transparent bg-gray-50/80 text-gray-700 hover:border-gray-200 hover:bg-white'
                }`}
              >
                <span>全部文案</span>
                <span>{totalAllItems}</span>
              </button>
              {statusCategories.map((category) => {
                const active = JSON.stringify(selectedStatus) === JSON.stringify(category.status);
                return (
                  <button
                    type="button"
                    key={category.name}
                    onClick={() => handleStatusChange(category.status)}
                    className={`flex w-full items-center justify-between rounded-xl border px-3 py-3 text-sm font-semibold transition-all ${
                      active
                        ? 'border-teal-500/70 bg-teal-50 text-teal-700 shadow-sm'
                        : 'border-transparent bg-gray-50/80 text-gray-700 hover:border-gray-200 hover:bg-white'
                    }`}
                  >
                    <span className="truncate">{category.name}</span>
                    <span>{category.count}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <footer className="m-3 flex-shrink-0 overflow-hidden rounded-2xl border border-gray-200 bg-gray-50 p-3">
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 overflow-hidden rounded-xl">
                <CachedAvatar
                  src={currentUser?.avatar}
                  alt="头像"
                  fallbackSrc="/img/飞驳.jpg"
                  className="h-full w-full object-contain p-0.5"
                  fallbackClassName="flex h-full w-full items-center justify-center bg-gray-200"
                  iconClassName="h-6 w-6 text-gray-400"
                />
              </div>
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-gray-900">{currentUser?.nickName || currentUser?.username || '用户'}</div>
                <div className="mt-1 inline-flex rounded-lg bg-white px-2 py-0.5 text-xs text-gray-500">{currentUser?.profession || '用户'}</div>
              </div>
            </div>
          </footer>
        </aside>

        {/* 主要内容区域 */}
        <main className="flex h-full w-full min-w-0 flex-col transition-all duration-300">
          <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
          {/* 用户信息展示 */}
          <div className="flex flex-shrink-0 flex-col items-stretch gap-3 border-b border-gray-100 px-4 py-4 lg:flex-row lg:items-center lg:justify-between lg:gap-4 lg:px-6">
            <div className="flex min-w-0 items-center gap-4">
              <div className="w-12 h-12 overflow-hidden rounded-2xl border border-gray-200 bg-white">
                <CachedAvatar
                  src={currentUser?.avatar}
                  alt="头像"
                  fallbackSrc="/img/飞驳.jpg"
                  className="h-full w-full object-contain p-1"
                  fallbackClassName="flex h-full w-full items-center justify-center bg-gray-200"
                  iconClassName="h-6 w-6 text-gray-400"
                />
              </div>
              <div className="min-w-0">
                <h1 className="truncate text-xl font-semibold text-gray-950">文案审核列表</h1>
                <div className="mt-1 text-xs text-gray-500">
                  共 {totalFilteredItems} 条，当前第 {currentPage} 页
                </div>
              </div>
            </div>
            
            {/* 搜索栏 - 移动到中间位置 */}
            <div className="w-full lg:max-w-md">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={18} />
                <input
                  type="text"
                  placeholder="请输入想要搜索的文案名称"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="h-11 w-full rounded-full border border-gray-200 bg-gray-50 pl-10 pr-4 text-sm text-gray-900 outline-none transition-colors focus:bg-white focus:ring-2 focus:ring-teal-100"
                />
              </div>
            </div>
            
            <div className="flex flex-shrink-0 flex-wrap items-center justify-end gap-2">
              <button
                type="button"
                onClick={handleOpenAuditRecords}
                className="inline-flex items-center gap-2 rounded-full border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition-colors hover:bg-gray-50"
              >
                <History size={16} className="text-teal-600" />
                审核记录
              </button>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-full border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                onClick={handleManualRefresh}
                disabled={isLoadingCopywritings}
                title="手动刷新"
              >
                <RefreshCw size={16} className={isLoadingCopywritings ? 'animate-spin' : ''} />
                刷新
              </button>
              <div 
                className="inline-flex cursor-pointer items-center gap-2 rounded-full bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-teal-700"
                onClick={() => setShowAddModal(true)}
              >
                <Plus size={16} />
                添加文案
              </div>
            </div>
          </div>

          {/* 文案列表统计 */}
          <div className="border-b border-gray-100 px-6 py-4">
            <div className="flex flex-wrap gap-2">
              {/* 全部文案按钮 */}
              <div 
                onClick={() => handleStatusChange(-1)}
                className={`cursor-pointer font-semibold rounded-xl flex justify-between items-center gap-3 px-4 py-2.5 transition-colors min-w-[8rem] ${
                  selectedStatus === -1
                    ? 'bg-teal-600 text-white shadow-sm'
                    : 'text-gray-600 bg-gray-50 border border-gray-200 hover:bg-white'
                }`}
              >
                <div className="text-sm">全部文案</div>
                <div className={`text-sm ${selectedStatus === -1 ? 'text-white' : 'text-gray-600'}`}>
                  {totalAllItems}
                </div>
              </div>
              
              {/* 其他状态按钮 */}
              {statusCategories.map((category) => (
                <div 
                  key={category.name}
                  onClick={() => handleStatusChange(category.status)}
                  className={`cursor-pointer font-semibold rounded-xl flex justify-between items-center gap-3 px-4 py-2.5 transition-colors min-w-[8rem] ${
                    JSON.stringify(selectedStatus) === JSON.stringify(category.status)
                      ? 'bg-teal-600 text-white shadow-sm'
                      : 'text-gray-600 bg-gray-50 border border-gray-200 hover:bg-white'
                  }`}
                >
                  <div className="text-sm">{category.name}</div>
                  <div className={`text-sm ${JSON.stringify(selectedStatus) === JSON.stringify(category.status) ? 'text-white' : 'text-gray-600'}`}>
                    {category.count}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* 数据表格 */}
          <div className="min-h-0 flex-1 overflow-hidden p-4">
            <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white">
              {/* 表头 */}
              <div className="hidden flex-shrink-0 gap-4 border-b border-gray-200 bg-gray-50 px-4 py-3 text-xs font-semibold uppercase tracking-[0.08em] text-gray-500 lg:grid" style={{gridTemplateColumns: '1fr 0.5fr 0.5fr 2fr 1fr 1fr 1fr'}}>
                <div>时间</div>
                <div>提交人</div>
                <div>身份</div>
                <div>标题</div>
                <div>审核状态</div>
                <div>评定</div>
                <div>操作</div>
              </div>
              
              {/* 表格内容 */}
              <div className="min-h-0 flex-1 divide-y divide-gray-200 overflow-y-auto">
                {isLoadingCopywritings ? (
                  <div className="flex items-center justify-center gap-2 py-12 text-gray-500">
                    <Loader2 size={18} className="animate-spin text-teal-600" />
                    <div>正在加载文案列表…</div>
                  </div>
                ) : copywritingError ? (
                  <div className="flex flex-col items-center justify-center gap-3 px-4 py-12 text-center">
                    <div className="text-sm text-red-600">{copywritingError}</div>
                    <button 
                      onClick={() => loadCopywritingList()}
                      className="rounded-full bg-teal-600 px-4 py-2 text-sm text-white hover:bg-teal-700"
                    >
                      重新加载
                    </button>
                  </div>
                ) : paginatedDocuments.length === 0 ? (
                  <div className="flex justify-center items-center py-12">
                    <div className="text-gray-500">暂无数据</div>
                  </div>
                ) : (
                  paginatedDocuments.map((doc, index) => {
                    const copywritingItem = copywritings.find((item) => (
                      String(item.copywriting.copywritingId) === doc.id
                    ));
                    if (!copywritingItem) return null;
                    const openDetail = () => handleDetailClick(copywritingItem);
                    const openApproval = (event: React.MouseEvent<HTMLButtonElement>) => {
                      event.stopPropagation();
                      void handleApprovalClick(copywritingItem);
                    };
                    return (
                      <div
                        key={doc.id}
                        role="button"
                        tabIndex={0}
                        aria-label={`查看文案详情：${doc.title}`}
                        onClick={openDetail}
                        onKeyDown={(event) => {
                          if (event.target !== event.currentTarget) return;
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            openDetail();
                          }
                        }}
                        className={`cursor-pointer gap-4 px-4 py-4 outline-none transition-colors hover:bg-teal-50/50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-teal-500 lg:grid lg:min-h-16 lg:items-center lg:py-3 ${
                          index % 2 === 1 ? 'bg-gray-50/70' : 'bg-white'
                        }`}
                        style={{gridTemplateColumns: '1fr 0.5fr 0.5fr 2fr 1fr 1fr 1fr'}}
                      >
                        <div className="lg:hidden">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="break-words text-base font-semibold leading-6 text-gray-950">{doc.title}</div>
                              <div className="mt-1 text-xs text-gray-500">{doc.createTime}</div>
                            </div>
                            <span className={`flex-shrink-0 rounded-full bg-white px-2.5 py-1 text-xs font-medium ${getStatusColor(doc.status)}`}>
                              {doc.status}
                            </span>
                          </div>
                          <div className="mt-3 text-sm text-gray-600">提交人：{doc.author}</div>
                          <div className="mt-4 flex flex-wrap items-center gap-2">
                            <button
                              type="button"
                              onClick={(event) => { event.stopPropagation(); openDetail(); }}
                              className="inline-flex items-center gap-1.5 rounded-full border border-teal-200 bg-white px-3 py-1.5 text-xs font-medium text-teal-700 hover:bg-teal-50"
                            >
                              <Eye size={14} />
                              查看详情
                            </button>
                            {canCurrentUserReview(copywritingItem) && (
                              <button
                                type="button"
                                onClick={openApproval}
                                disabled={isApproving}
                                className="rounded-full bg-gray-950 px-4 py-1.5 text-xs font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {doc.approvalStatusCode === APPROVAL_STATUS.AUTHOR_PENDING ? '自审确认' : '审核'}
                              </button>
                            )}
                          </div>
                        </div>
                        <div className="hidden truncate text-sm text-gray-600 lg:block">{doc.createTime}</div>
                        <div className="hidden truncate text-sm text-gray-800 lg:block">{doc.author}</div>
                        <div className="hidden truncate text-sm text-gray-800 lg:block">{doc.authorType}</div>
                        <div className="hidden truncate text-sm font-medium text-gray-900 lg:block" title={doc.title}>{doc.title}</div>
                        <div className={`hidden truncate text-sm lg:block ${getStatusColor(doc.status)}`}>{doc.status}</div>
                        <div className="hidden truncate text-sm text-gray-600 lg:block">{doc.evaluate || '-'}</div>
                        <div className="hidden items-center gap-2 lg:flex">
                          <button
                            type="button"
                            onClick={(event) => { event.stopPropagation(); openDetail(); }}
                            className="inline-flex items-center gap-1 rounded-full border border-teal-200 bg-white px-3 py-1.5 text-xs font-medium text-teal-700 transition-colors hover:bg-teal-50"
                          >
                            <Eye size={13} />
                            详情
                          </button>
                          {canCurrentUserReview(copywritingItem) && (
                            <button
                              type="button"
                              onClick={openApproval}
                              disabled={isApproving}
                              className="rounded-full bg-gray-950 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              {doc.approvalStatusCode === APPROVAL_STATUS.AUTHOR_PENDING ? '自审确认' : '审核'}
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
              
              {/* 分页组件 */}
              <div className="flex flex-shrink-0 flex-col gap-3 border-t border-gray-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6 sm:py-4">
                <div className="flex flex-wrap items-center gap-3 sm:gap-4">
                  <span className="text-sm text-gray-700">
                    显示 {totalFilteredItems === 0 ? 0 : startIndex + 1}-{Math.min(endIndex, totalFilteredItems)} 条，共 {totalFilteredItems} 条
                  </span>
                  <div className="flex items-center space-x-2">
                    <span className="text-sm text-gray-700">每页显示</span>
                    <select
                      value={itemsPerPage}
                      onChange={(e) => handleItemsPerPageChange(Number(e.target.value))}
                      className="rounded-lg border border-gray-200 px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-teal-100"
                    >
                      <option value={5}>5</option>
                      <option value={10}>10</option>
                      <option value={20}>20</option>
                      <option value={50}>50</option>
                    </select>
                    <span className="text-sm text-gray-700">条</span>
                  </div>
                </div>
                
                <div className="flex items-center justify-between gap-2 sm:justify-start">
                  {/* 上一页按钮 */}
                  <button
                    onClick={handlePreviousPage}
                    disabled={currentPage === 1}
                    className="flex items-center space-x-1 rounded-full border border-gray-200 px-3 py-1 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <ChevronLeft size={16} />
                    <span>上一页</span>
                  </button>
                  
                  {/* 页码按钮 */}
                  <div className="hidden items-center space-x-1 sm:flex">
                    {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                      let pageNumber;
                      if (totalPages <= 7) {
                        pageNumber = i + 1;
                      } else if (currentPage <= 4) {
                        pageNumber = i + 1;
                      } else if (currentPage >= totalPages - 3) {
                        pageNumber = totalPages - 6 + i;
                      } else {
                        pageNumber = currentPage - 3 + i;
                      }
                      
                      return (
                        <button
                          key={pageNumber}
                          onClick={() => handlePageChange(pageNumber)}
                           className={`rounded-full border px-3 py-1 text-sm ${
                             currentPage === pageNumber
                               ? 'bg-teal-600 text-white border-teal-600'
                               : 'border-gray-200 hover:bg-gray-50'
                           }`}
                        >
                          {pageNumber}
                        </button>
                      );
                    })}
                  </div>
                  
                  {/* 下一页按钮 */}
                  <button
                    onClick={handleNextPage}
                    disabled={totalPages === 0 || currentPage >= totalPages}
                    className="flex items-center space-x-1 rounded-full border border-gray-200 px-3 py-1 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span>下一页</span>
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        </main>

        <aside className="hidden">
          <div className="border-b border-gray-100 p-4">
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2 text-sm font-semibold text-gray-950">
                  <History size={16} className="text-teal-600" />
                  审核记录
                </div>
                <p className="mt-1 text-xs leading-5 text-gray-500">查看最近的审批处理与状态流转。</p>
              </div>
              <button
                onClick={handleRefreshAuditRecords}
                disabled={isLoadingAuditRecords}
                className="rounded-xl p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50"
                title="刷新审核记录"
              >
                <RefreshCw size={16} className={isLoadingAuditRecords ? 'animate-spin' : ''} />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <select
                value={auditRoleFilter}
                onChange={(e) => setAuditRoleFilter(e.target.value)}
                className="rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-xs outline-none focus:bg-white focus:ring-2 focus:ring-teal-100"
              >
                <option value="">全部角色</option>
                <option value="作者">作者</option>
                <option value="AI">AI</option>
                <option value="专家组">专家组</option>
                <option value="机构管理员">机构管理员</option>
              </select>
              <select
                value={auditResultFilter}
                onChange={(e) => setAuditResultFilter(e.target.value)}
                className="rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-xs outline-none focus:bg-white focus:ring-2 focus:ring-teal-100"
              >
                <option value="">全部结果</option>
                <option value="通过">通过</option>
                <option value="驳回">驳回</option>
              </select>
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            {auditRecordsError && (
              <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 text-center text-sm text-gray-500">
                暂无审核记录
                <button onClick={handleRefreshAuditRecords} className="mt-2 block w-full text-xs text-teal-600 hover:text-teal-700">
                  刷新
                </button>
              </div>
            )}
            {isLoadingAuditRecords && auditRecords.length === 0 && (
              <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 text-center text-sm text-gray-500">
                <Loader2 className="mx-auto mb-2 h-5 w-5 animate-spin text-teal-600" />
                加载中...
              </div>
            )}
            {!isLoadingAuditRecords && auditRecords.length === 0 && !auditRecordsError && (
              <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 text-center text-sm text-gray-500">暂无审核记录</div>
            )}
            <div className="space-y-2">
              {auditRecords.map((record) => (
                <div key={record.id} className="rounded-2xl border border-gray-200 bg-white p-3 transition-colors hover:bg-teal-50/40">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <span className="truncate text-xs text-gray-500">{record.auditTime}</span>
                    <span className={`rounded-full px-2 py-1 text-xs ${
                      record.auditResult === '通过' ? 'bg-green-100 text-green-700' :
                      record.auditResult === '驳回' ? 'bg-red-100 text-red-700' :
                      'bg-yellow-100 text-yellow-700'
                    }`}>
                      {record.auditResult}
                    </span>
                  </div>
                  <div className="truncate text-sm font-semibold text-gray-900">{record.copywritingTitle}</div>
                  <div className="mt-2 text-xs leading-5 text-gray-600">审核人：{record.auditorName}（{record.auditorRole}）</div>
                  <div className="text-xs leading-5 text-gray-500">状态：{record.statusChange}</div>
                  {record.auditComment && (
                    <div className="mt-2 rounded-xl bg-gray-50 p-2 text-xs leading-5 text-gray-600">意见：{record.auditComment}</div>
                  )}
                </div>
              ))}
              {hasMoreAuditRecords && (
                <button
                  onClick={handleLoadMoreAuditRecords}
                  disabled={isLoadingAuditRecords}
                  className="w-full rounded-xl border border-gray-200 px-3 py-2 text-xs font-medium text-teal-600 transition-colors hover:bg-teal-50 disabled:opacity-50"
                >
                  {isLoadingAuditRecords ? '加载中...' : '加载更多'}
                </button>
              )}
            </div>
          </div>
        </aside>
      </div>

       {/* 审核记录侧边栏 */}
       {showLegacyAuditSidebar && (<aside className={`fixed right-0 top-[120px] bg-white shadow-lg border-l border-gray-200 z-5 transition-all duration-300 m-3 rounded-l-[10px] ${
         isAuditSidebarCollapsed ? 'w-0 overflow-hidden' : 'w-80'
       }`} style={{ height: 'calc(100vh - 120px)' }}>
         {!isAuditSidebarCollapsed && (
          <div className="h-full flex flex-col overflow-hidden">
            {/* 侧边栏头部 */}
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleToggleAuditSidebar}
                    className="p-1 text-gray-500 hover:text-gray-700 transition-colors"
                    title="收起侧边栏"
                  >
                    <ChevronRight size={16} className="text-gray-600" />
                  </button>
                  <div className="text-[18px] font-semibold text-gray-700">审核记录</div>
                </div>
                <div className="flex items-center gap-1">
                  <button
                    onClick={handleRefreshAuditRecords}
                    disabled={isLoadingAuditRecords}
                    className="p-1 text-gray-500 hover:text-gray-700 transition-colors disabled:opacity-50"
                    title="刷新审核记录"
                  >
                    <RefreshCw size={16} className={isLoadingAuditRecords ? 'animate-spin' : ''} />
                  </button>
                </div>
              </div>

            {/* 筛选区域 */}
            <div className="p-4 border-b border-gray-200 space-y-3">
              <div>
                <label className="block text-xs text-gray-600 mb-1">审核人角色</label>
                <select
                  value={auditRoleFilter}
                  onChange={(e) => setAuditRoleFilter(e.target.value)}
                  className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-teal-500"
                  >
                    <option value="">全部</option>
                    <option value="作者">作者</option>
                    <option value="AI">AI</option>
                    <option value="专家组">专家组</option>
                    <option value="机构管理员">机构管理员</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">审核结果</label>
                <select
                  value={auditResultFilter}
                  onChange={(e) => setAuditResultFilter(e.target.value)}
                  className="w-full px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:border-teal-500"
                >
                  <option value="">全部</option>
                  <option value="通过">通过</option>
                  <option value="驳回">驳回</option>
                </select>
              </div>
            </div>
           <div className="overflow-y-auto" style={{ maxHeight: 'calc(100% - 120px)' }}>
             {/* 错误状态显示 */}
             {auditRecordsError && (
               <div className="p-3 text-center text-gray-500 text-sm">
                 暂无审核记录
                 <button 
                   onClick={handleRefreshAuditRecords}
                   className="block mx-auto mt-2 text-xs text-blue-500 hover:text-blue-700"
                 >
                   刷新
                 </button>
               </div>
             )}
             
             {/* 加载状态显示 */}
             {isLoadingAuditRecords && auditRecords.length === 0 && (
               <div className="p-3 text-center text-gray-500 text-sm">
                 <div className="animate-spin inline-block w-4 h-4 border-2 border-gray-300 border-t-gray-600 rounded-full mb-2"></div>
                 <div>加载中...</div>
               </div>
             )}
             
             {/* 空状态显示 */}
             {!isLoadingAuditRecords && auditRecords.length === 0 && !auditRecordsError && (
               <div className="p-3 text-center text-gray-500 text-sm">
                 暂无审核记录
               </div>
             )}
             
             {/* 审核记录列表 */}
             <div className="space-y-2">
               {auditRecords.map((record) => (
                 <div key={record.id} className="p-3 border-b border-gray-100 hover:bg-gray-50 transition-colors">
                   <div className="space-y-2">
                     <div className="flex items-center justify-between">
                       <span className="text-xs text-gray-500">{record.auditTime}</span>
                       <span className={`text-xs px-2 py-1 rounded-full ${
                         record.auditResult === '通过' ? 'bg-green-100 text-green-700' :
                         record.auditResult === '驳回' ? 'bg-red-100 text-red-700' :
                         'bg-yellow-100 text-yellow-700'
                       }`}>
                         {record.auditResult}
                       </span>
                     </div>
                     <div className="text-sm font-medium text-gray-900 truncate">
                       {record.copywritingTitle}
                     </div>
                     <div className="text-xs text-gray-600">
                       审核人：{record.auditorName}（{record.auditorRole}）
                     </div>
                     <div className="text-xs text-gray-500">
                       状态变更：{record.statusChange}
                     </div>
                     {record.auditComment && (
                       <div className="text-xs text-gray-600 bg-gray-50 p-2 rounded">
                         审核意见：{record.auditComment}
                       </div>
                     )}
                   </div>
                 </div>
               ))}
               
               {/* 加载更多按钮 */}
               {hasMoreAuditRecords && (
                 <div className="p-3 text-center">
                   <button
                     onClick={handleLoadMoreAuditRecords}
                     disabled={isLoadingAuditRecords}
                     className="text-xs text-blue-500 hover:text-blue-700 disabled:opacity-50"
                   >
                     {isLoadingAuditRecords ? '加载中...' : '加载更多'}
                   </button>
                 </div>
               )}
             </div>
           </div>
          </div>
         )}
       </aside>)}

       {/* 展开按钮 - 当侧边栏收起时显示在主内容区域右侧 */}
       {showLegacyAuditSidebar && isAuditSidebarCollapsed && (
         <button
           onClick={handleToggleAuditSidebar}
           className="fixed right-4 p-3 bg-white shadow-lg border border-gray-200 rounded-full text-gray-500 hover:text-gray-700 hover:shadow-xl transition-all duration-300 z-30"
           style={{ top: 'calc(50vh + 60px)' }}
           title="展开审核记录"
         >
           <ChevronLeft size={20} className="text-gray-600" />
         </button>
       )}

       {/* 添加文案弹窗 */}
        {showAddModal && (
         <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
           <div className="bg-white rounded-lg shadow-xl w-[90vw] max-w-2xl max-h-[90vh] flex flex-col">
             {/* 弹窗头部 */}
             <div className="flex items-center justify-between p-6 border-b border-gray-200 flex-shrink-0">
               <h2 className="text-xl font-semibold text-gray-800">添加文案</h2>
               <button 
                 onClick={handleCloseModal}
                 className="text-gray-400 hover:text-gray-600 transition-colors"
               >
                 <X size={24} />
               </button>
             </div>
             
             {/* 弹窗内容 - 可滚动区域 */}
             <div className="p-6 space-y-4 flex-1 overflow-y-auto">
               {/* 错误信息显示 */}
               {copywritingError && (
                 <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
                   <div className="flex items-center">
                     <XCircle className="h-5 w-5 text-red-400 mr-2" />
                     <p className="text-sm text-red-700">{copywritingError}</p>
                   </div>
                 </div>
               )}
               
               {/* 标题输入框 */}
               <div>
                 <label className="block text-sm font-medium text-gray-700 mb-2">
                   文案标题 <span className="text-red-500">*</span>
                 </label>
                 <input
                   type="text"
                   value={newDocument.title}
                   onChange={(e) => setNewDocument({ ...newDocument, title: e.target.value })}
                   placeholder="请输入文案标题"
                   className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors"
                 />
               </div>
               
               {/* 文案分类下拉选框 */}
               <div>
                 <label className="block text-sm font-medium text-gray-700 mb-2">
                   文案分类 <span className="text-red-500">*</span>
                   {isLoadingCategories && <span className="text-xs text-gray-500 ml-2">(加载中...)</span>}
                 </label>
                 <select
                   value={newDocument.category}
                   onChange={(e) => setNewDocument({ ...newDocument, category: e.target.value })}
                   disabled={isLoadingCategories}
                   className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors bg-white disabled:opacity-50 disabled:cursor-not-allowed"
                 >
                   {isLoadingCategories ? (
                     <option value="">加载中...</option>
                   ) : (
                     categoryOptions.map((option) => (
                       <option key={option.value} value={option.value}>
                         {option.label}
                       </option>
                     ))
                   )}
                 </select>
               </div>
               
               {/* 文案来源下拉选框 */}
               <div>
                 <label className="block text-sm font-medium text-gray-700 mb-2">
                   文案来源 <span className="text-red-500">*</span>
                   {isLoadingSources && <span className="text-xs text-gray-500 ml-2">(加载中...)</span>}
                 </label>
                 <select
                   value={newDocument.source}
                   onChange={(e) => setNewDocument({ ...newDocument, source: e.target.value })}
                   disabled={isLoadingSources}
                   className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors bg-white disabled:opacity-50 disabled:cursor-not-allowed"
                 >
                   {isLoadingSources ? (
                     <option value="">加载中...</option>
                   ) : (
                     sourceOptions.map((option) => (
                       <option key={option.value} value={option.value}>
                         {option.label}
                       </option>
                     ))
                   )}
                 </select>
               </div>
               
               {/* 文案内容输入框 */}
              <div>
                <div className="flex justify-between items-center mb-2">
                  <label className="block text-sm font-medium text-gray-700">
                    文案内容 <span className="text-red-500">*</span>
                  </label>
                  <div className="flex items-center">
                    <input
                      type="file"
                      ref={fileInputRef}
                      onChange={handleAudioUpload}
                      accept="audio/*"
                      className="hidden"
                    />
                    <button
                      onClick={() => fileInputRef.current?.click()}
                      disabled={isUploadingAudio}
                      className="flex items-center text-xs text-teal-600 hover:text-teal-700 transition-colors disabled:opacity-50"
                    >
                      {isUploadingAudio ? (
                        <>
                          <Loader2 size={14} className="mr-1 animate-spin" />
                          转录中...
                        </>
                      ) : (
                        <>
                          <Upload size={14} className="mr-1" />
                          上传录音转文字
                        </>
                      )}
                    </button>
                  </div>
                </div>
                <textarea
                   value={newDocument.content}
                   onChange={(e) => setNewDocument({ ...newDocument, content: e.target.value })}
                   placeholder="请输入文案内容"
                   rows={8}
                   className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors resize-none"
                 />
               </div>
             </div>
             
             {/* 弹窗底部按钮 - 固定在底部 */}
             <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200 bg-gray-50 flex-shrink-0">
               <button
                 onClick={handleCloseModal}
                 className="px-6 py-2 text-gray-600 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
               >
                 取消
               </button>
               <button
                 onClick={handleAddDocument}
                 disabled={isSubmitting}
                 className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
               >
                 {isSubmitting ? '提交中...' : '提交'}
               </button>
             </div>
           </div>
         </div>
       )}

       {/* 可见的审核记录入口：打开时才加载，每条记录可进入文案详情 */}
       {showAuditRecordsModal && (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-0 sm:p-4" role="dialog" aria-modal="true" aria-label="审核记录">
           <div className="flex h-full w-full max-w-3xl flex-col overflow-hidden bg-white shadow-2xl sm:max-h-[90vh] sm:rounded-2xl">
             <div className="flex flex-shrink-0 items-center justify-between gap-4 border-b border-gray-200 px-4 py-4 sm:px-6">
               <div>
                 <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-950">
                   <History size={20} className="text-teal-600" />
                   审核记录
                 </h2>
                 <p className="mt-1 text-xs text-gray-500">点击任一记录可查看完整文案和该文案当前审核流程。</p>
               </div>
               <button type="button" onClick={() => setShowAuditRecordsModal(false)} className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700" aria-label="关闭审核记录">
                 <X size={22} />
               </button>
             </div>

             <div className="flex flex-shrink-0 flex-col gap-3 border-b border-gray-200 bg-gray-50 px-4 py-3 sm:flex-row sm:items-center sm:px-6">
               <select
                 value={auditRoleFilter}
                 onChange={(event) => setAuditRoleFilter(event.target.value)}
                 className="h-10 flex-1 rounded-xl border border-gray-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-teal-100"
               >
                 <option value="">全部审核角色</option>
                 <option value="作者">作者</option>
                 <option value="AI">AI</option>
                 <option value="专家组">专家组</option>
                 <option value="机构管理员">机构管理员</option>
               </select>
               <select
                 value={auditResultFilter}
                 onChange={(event) => setAuditResultFilter(event.target.value)}
                 className="h-10 flex-1 rounded-xl border border-gray-200 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-teal-100"
               >
                 <option value="">全部审核结果</option>
                 <option value="通过">通过</option>
                 <option value="驳回">驳回</option>
               </select>
               <button
                 type="button"
                 onClick={() => void loadAuditRecords(true)}
                 disabled={isLoadingAuditRecords}
                 className="inline-flex h-10 items-center justify-center gap-2 rounded-full bg-teal-600 px-4 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
               >
                 <RefreshCw size={15} className={isLoadingAuditRecords ? 'animate-spin' : ''} />
                 应用筛选
               </button>
             </div>

             <div className="min-h-0 flex-1 overflow-y-auto bg-gray-50/60 p-4 sm:p-6">
               {isLoadingAuditRecords && auditRecords.length === 0 ? (
                 <div className="flex items-center justify-center gap-2 rounded-2xl bg-white px-4 py-12 text-sm text-gray-500">
                   <Loader2 size={18} className="animate-spin text-teal-600" />
                   正在加载审核记录…
                 </div>
               ) : auditRecordsError ? (
                 <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-6 text-center text-sm text-red-700">
                   <div>{auditRecordsError}</div>
                   <button type="button" onClick={() => void loadAuditRecords(true)} className="mt-3 rounded-full border border-red-200 bg-white px-4 py-2 text-xs font-medium hover:bg-red-100">
                     重新加载
                   </button>
                 </div>
               ) : auditRecords.length === 0 ? (
                 <div className="rounded-2xl bg-white px-4 py-12 text-center text-sm text-gray-500">暂无符合条件的审核记录</div>
               ) : (
                 <div className="space-y-3">
                   {auditRecords.map((record, index) => (
                     <button
                       type="button"
                       key={`${record.id}-${index}`}
                       onClick={() => handleAuditRecordDetail(record)}
                       disabled={!record.copywritingId}
                       className="w-full rounded-2xl border border-gray-200 bg-white p-4 text-left shadow-sm transition hover:border-teal-200 hover:bg-teal-50/30 disabled:cursor-not-allowed disabled:opacity-60"
                     >
                       <div className="flex flex-wrap items-start justify-between gap-3">
                         <div className="min-w-0 flex-1">
                           <div className="break-words text-sm font-semibold leading-6 text-gray-950">{record.copywritingTitle || '未命名文案'}</div>
                           <div className="mt-1 text-xs text-gray-500">{formatAuditTime(record.auditTime)}</div>
                         </div>
                         <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${getAuditResultStyle(record.auditResult)}`}>{record.auditResult || '已处理'}</span>
                       </div>
                       <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs leading-5 text-gray-600">
                         <span>审核人：{record.auditorName || '系统'}（{record.auditorRole || '审核人'}）</span>
                         {record.copywritingApprovalFlowId && (
                           <span className="text-violet-700">审核轮次：…{record.copywritingApprovalFlowId.slice(-8)}</span>
                         )}
                       </div>
                       <div className="mt-2 break-words text-xs leading-5 text-gray-500">状态：{record.statusChange}</div>
                       {record.auditComment && (
                         <div className="mt-3 line-clamp-3 whitespace-pre-wrap break-words rounded-xl bg-gray-50 px-3 py-2 text-sm leading-6 text-gray-700">{record.auditComment}</div>
                       )}
                       <div className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-teal-700">
                         查看完整文案与流程 <ChevronRight size={14} />
                       </div>
                     </button>
                   ))}
                   {hasMoreAuditRecords && (
                     <button
                       type="button"
                       onClick={() => void handleLoadMoreAuditRecords()}
                       disabled={isLoadingAuditRecords}
                       className="flex w-full items-center justify-center gap-2 rounded-full border border-gray-200 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                     >
                       {isLoadingAuditRecords && <Loader2 size={15} className="animate-spin" />}
                       {isLoadingAuditRecords ? '加载中…' : '加载更多'}
                     </button>
                   )}
                 </div>
               )}
             </div>
           </div>
         </div>
       )}

       {/* 文案只读详情弹窗：查看不会认领审核任务 */}
       {showDetailModal && currentDetailItem && (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-0 sm:p-4" role="dialog" aria-modal="true" aria-label="文案详情">
           <div className="flex h-full w-full max-w-6xl flex-col overflow-hidden bg-white shadow-2xl sm:max-h-[92vh] sm:rounded-2xl">
             <div className="flex flex-shrink-0 items-center justify-between gap-4 border-b border-gray-200 px-4 py-4 sm:px-6">
               <div className="min-w-0">
                 <div className="flex items-center gap-2 text-lg font-semibold text-gray-950">
                   <Eye size={20} className="flex-shrink-0 text-teal-600" />
                   <span className="truncate">文案详情</span>
                 </div>
                 <div className="mt-1 truncate text-xs text-gray-500">文案ID：{String(currentDetailItem.copywriting.copywritingId)}</div>
               </div>
               <button type="button" onClick={handleDetailCancel} className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700" aria-label="关闭详情">
                 <X size={22} />
               </button>
             </div>

             <div className="min-h-0 flex-1 overflow-y-auto bg-gray-50/60 p-4 sm:p-6">
               {isLoadingDetailContext && (
                 <div className="mb-4 flex items-center gap-2 rounded-xl border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700">
                   <Loader2 size={17} className="animate-spin" />
                   正在获取最新完整文案和审核记录…
                 </div>
               )}
               {detailContextError && (
                 <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                   <div>{detailContextError}</div>
                   <button type="button" onClick={() => void loadDetailContext(currentDetailItem)} className="mt-2 rounded-full border border-red-200 bg-white px-3 py-1.5 text-xs font-medium hover:bg-red-100">
                     重新加载详情
                   </button>
                 </div>
               )}
               <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(300px,0.65fr)]">
                 <CopywritingContent item={currentDetailItem} />
                 <div className="space-y-5">
                   <ApprovalProgress item={currentDetailItem} history={detailAuditHistory} />
                   <AuditTimeline
                     records={detailAuditHistory}
                     loading={isLoadingDetailContext}
                     error={detailContextError}
                     onRetry={() => void loadDetailContext(currentDetailItem)}
                   />
                 </div>
               </div>
             </div>

             <div className="flex flex-shrink-0 flex-col-reverse gap-2 border-t border-gray-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-end sm:px-6">
               <button type="button" onClick={handleDetailCancel} className="rounded-full border border-gray-300 bg-white px-5 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50">
                 关闭
               </button>
               {canCurrentUserReview(currentDetailItem) && (
                 <button
                   type="button"
                   onClick={() => void handleEnterReviewFromDetail()}
                   disabled={isLoadingDetailContext || Boolean(detailContextError) || !currentDetailItem.copywriting.content?.trim() || isApproving}
                   className="rounded-full bg-gray-950 px-5 py-2.5 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
                 >
                   {getApprovalStatusCode(currentDetailItem) === APPROVAL_STATUS.AUTHOR_PENDING ? '进入自审确认' : '进入审核'}
                 </button>
               )}
             </div>
           </div>
         </div>
       )}

       {/* 审核弹窗：完整文案、流程和此前记录均在审核意见之前 */}
       {showApprovalModal && currentApprovalItem && (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-0 sm:p-4" role="dialog" aria-modal="true" aria-label="文案审核">
           <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl sm:max-h-[94vh] sm:rounded-2xl">
             <div className="flex flex-shrink-0 items-center justify-between gap-4 border-b border-gray-200 px-4 py-4 sm:px-6">
               <div className="min-w-0">
                 <h2 className="truncate text-lg font-semibold text-gray-950 sm:text-xl">
                   {getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.AUTHOR_PENDING
                     ? '作者自审/确认'
                     : getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.EXPERT_PENDING
                       ? '专家组专业审核'
                       : '所属医院/机构管理员终审'}
                 </h2>
                 <p className="mt-1 text-xs text-gray-500">请先核对完整文案、审核流程和此前记录，再填写审核意见。</p>
               </div>
               <button
                 type="button"
                 onClick={handleApprovalCancel}
                 disabled={isApproving}
                 className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-50"
                 aria-label="关闭审核"
               >
                 <X size={22} />
               </button>
             </div>

             <div className="min-h-0 flex-1 space-y-5 overflow-y-auto bg-gray-50/60 p-4 sm:p-6">
               {isLoadingReviewContext && (
                 <div className="flex items-center gap-2 rounded-xl border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700">
                   <Loader2 size={17} className="animate-spin" />
                   正在加载完整文案和此前审核记录，加载完成后方可提交…
                 </div>
               )}
               <CopywritingContent item={currentApprovalItem} />
               <ApprovalProgress item={currentApprovalItem} history={reviewAuditHistory} />
               <AuditTimeline
                 records={reviewAuditHistory}
                 loading={isLoadingReviewContext}
                 error={reviewContextError}
                 onRetry={() => void loadReviewContext(currentApprovalItem)}
               />

               <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5" aria-label="审核意见">
                 <label className="mb-2 block text-sm font-semibold text-gray-900">
                   {getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.AUTHOR_PENDING ? '确认说明' : '审核意见'}
                   <span className="ml-1 text-red-500">*</span>
                 </label>
                 <textarea
                   value={approvalComment}
                   onChange={(e) => setApprovalComment(e.target.value)}
                   disabled={isLoadingReviewContext || Boolean(reviewContextError)}
                   placeholder={isLoadingReviewContext
                     ? '完整文案和审核记录加载完成后可填写'
                     : getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.AUTHOR_PENDING
                       ? '请输入自审确认说明（必填）'
                       : '请输入审核意见（必填）'}
                   rows={5}
                   className="w-full resize-y rounded-xl border border-gray-300 px-4 py-3 text-sm leading-6 outline-none transition-colors focus:border-transparent focus:ring-2 focus:ring-teal-500 disabled:cursor-not-allowed disabled:bg-gray-100"
                 />
                 {reviewContextError && (
                   <p className="mt-2 text-xs text-red-600">审核上下文未完整加载，暂不能提交审核操作。</p>
                 )}
               </section>
             </div>

             <div className="flex flex-shrink-0 flex-col-reverse gap-2 border-t border-gray-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-end sm:px-6">
               <button
                 type="button"
                 onClick={handleApprovalCancel}
                 disabled={isApproving}
                 className="rounded-full border border-gray-300 bg-white px-5 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
               >
                 取消
               </button>
               <button
                 type="button"
                 onClick={handleApprovalSubmit}
                 disabled={isApproving || isLoadingReviewContext || Boolean(reviewContextError) || !currentApprovalItem.copywriting.content?.trim() || !approvalComment.trim()}
                 className="rounded-full bg-teal-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-50"
               >
                 {isApproving
                   ? '提交中…'
                   : getApprovalStatusCode(currentApprovalItem) === APPROVAL_STATUS.AUTHOR_PENDING
                     ? '确认并提交 AI 审核'
                     : '审核通过'}
               </button>
               {getApprovalStatusCode(currentApprovalItem) !== APPROVAL_STATUS.AUTHOR_PENDING && (
                 <button
                   type="button"
                   onClick={handleRejectSubmit}
                   disabled={isApproving || isLoadingReviewContext || Boolean(reviewContextError) || !currentApprovalItem.copywriting.content?.trim() || !approvalComment.trim()}
                   className="rounded-full bg-red-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                 >
                   {isApproving ? '提交中…' : '驳回'}
                 </button>
               )}
             </div>
           </div>
         </div>
       )}


    </div>
  );
};

export default ZhiheYishen;
