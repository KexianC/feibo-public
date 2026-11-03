import { authenticatedRequest } from './auth';

export interface CopywritingExpertAssignment {
  id: string;
  accountUserId: string;
  accountNickName: string;
  expertUserId: string;
  expertNickName: string;
  createTime: string;
}

export interface CopywritingExpertOption {
  expertUserId: string;
  expertNickName: string;
  phoneNumber?: string;
}

export interface CopywritingReviewAccountOption {
  accountUserId: string;
  nickName: string;
  phoneNumber?: string;
  status: boolean;
  createTime: string;
}

export interface CopywritingReviewAccountPage {
  records: CopywritingReviewAccountOption[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

export interface CopywritingReviewAccountPageParams {
  page: number;
  size: number;
  keyword?: string;
}

type IdValue = string | number | null | undefined;

interface CopywritingExpertAssignmentPayload {
  id?: IdValue;
  accountUserId?: IdValue;
  accountNickName?: string | null;
  expertUserId?: IdValue;
  expertNickName?: string | null;
  createTime?: string | null;
}

interface CopywritingExpertOptionPayload {
  expertUserId?: IdValue;
  userId?: IdValue;
  expertNickName?: string | null;
  nickName?: string | null;
  phoneNumber?: string | null;
}

interface CopywritingReviewAccountOptionPayload {
  accountUserId?: IdValue;
  nickName?: string | null;
  phoneNumber?: string | null;
  status?: boolean | number | string | null;
  createTime?: string | null;
}

interface CopywritingReviewAccountPagePayload {
  records?: CopywritingReviewAccountOptionPayload[];
  total?: number | string | null;
  pages?: number | string | null;
  size?: number | string | null;
  current?: number | string | null;
}

const normalizeAssignment = (
  record: CopywritingExpertAssignmentPayload,
): CopywritingExpertAssignment => ({
  id: String(record?.id ?? ''),
  accountUserId: String(record?.accountUserId ?? ''),
  accountNickName: String(record?.accountNickName ?? ''),
  expertUserId: String(record?.expertUserId ?? ''),
  expertNickName: String(record?.expertNickName ?? ''),
  createTime: String(record?.createTime ?? ''),
});

const normalizeExpertOption = (
  record: CopywritingExpertOptionPayload,
): CopywritingExpertOption => ({
  expertUserId: String(record?.expertUserId ?? record?.userId ?? ''),
  expertNickName: String(record?.expertNickName ?? record?.nickName ?? ''),
  phoneNumber: record?.phoneNumber ? String(record.phoneNumber) : undefined,
});

const normalizeAccountOption = (
  record: CopywritingReviewAccountOptionPayload,
): CopywritingReviewAccountOption => ({
  accountUserId: String(record?.accountUserId ?? ''),
  nickName: String(record?.nickName ?? ''),
  phoneNumber: record?.phoneNumber ? String(record.phoneNumber) : undefined,
  status: record?.status === true
    || record?.status === 1
    || record?.status === '1'
    || record?.status === 'true',
  createTime: String(record?.createTime ?? ''),
});

class CopywritingExpertAssignmentService {
  async getAccounts(
    params: CopywritingReviewAccountPageParams,
  ): Promise<CopywritingReviewAccountPage> {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
    });
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim());

    const response = await authenticatedRequest<CopywritingReviewAccountPagePayload>(
      `/api/admin/copywriting-expert-assignment/accounts?${query.toString()}`,
      { method: 'GET' },
    );
    const pageData: CopywritingReviewAccountPagePayload = response.data ?? {};

    return {
      records: Array.isArray(pageData.records)
        ? pageData.records
          .map(normalizeAccountOption)
          .filter((record: CopywritingReviewAccountOption) => record.accountUserId)
        : [],
      total: Number(pageData.total) || 0,
      pages: Number(pageData.pages) || 0,
      size: Number(pageData.size) || params.size,
      current: Number(pageData.current) || params.page,
    };
  }

  async query(accountUserIds: string[]): Promise<CopywritingExpertAssignment[]> {
    if (accountUserIds.length === 0) return [];

    const response = await authenticatedRequest<CopywritingExpertAssignmentPayload[]>(
      '/api/admin/copywriting-expert-assignment/query',
      {
        method: 'POST',
        body: JSON.stringify(accountUserIds),
      },
    );

    return (response.data ?? [])
      .map(normalizeAssignment)
      .filter((record: CopywritingExpertAssignment) => (
        record.accountUserId && record.expertUserId
      ));
  }

  async getExperts(keyword?: string): Promise<CopywritingExpertOption[]> {
    const query = new URLSearchParams();
    if (keyword?.trim()) query.set('keyword', keyword.trim());
    const suffix = query.toString() ? `?${query.toString()}` : '';

    const response = await authenticatedRequest<CopywritingExpertOptionPayload[]>(
      `/api/admin/copywriting-expert-assignment/experts${suffix}`,
      { method: 'GET' },
    );

    return (response.data ?? [])
      .map(normalizeExpertOption)
      .filter((record: CopywritingExpertOption) => record.expertUserId);
  }

  async update(
    accountUserId: string,
    expertUserIds: string[],
  ): Promise<CopywritingExpertAssignment[]> {
    const response = await authenticatedRequest<CopywritingExpertAssignmentPayload[]>(
      `/api/admin/copywriting-expert-assignment/${encodeURIComponent(accountUserId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(expertUserIds),
      },
    );

    return (response.data ?? [])
      .map(normalizeAssignment)
      .filter((record: CopywritingExpertAssignment) => (
        record.accountUserId && record.expertUserId
      ));
  }
}

export default new CopywritingExpertAssignmentService();
