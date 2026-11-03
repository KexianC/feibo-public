import { authenticatedRequest } from './auth';

export interface CopywritingOrganizationAdminAssignment {
  id: string;
  accountUserId: string;
  accountNickName: string;
  adminUserId: string;
  adminNickName: string;
  createTime: string;
}

export interface CopywritingOrganizationAdminOption {
  adminUserId: string;
  adminNickName: string;
  phoneNumber?: string;
}

export interface CopywritingOrganizationReviewAccountOption {
  accountUserId: string;
  nickName: string;
  phoneNumber?: string;
  status: boolean;
  createTime: string;
}

export interface CopywritingOrganizationReviewAccountPage {
  records: CopywritingOrganizationReviewAccountOption[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

export interface CopywritingOrganizationReviewAccountPageParams {
  page: number;
  size: number;
  keyword?: string;
}

type IdValue = string | number | null | undefined;

interface AssignmentPayload {
  id?: IdValue;
  accountUserId?: IdValue;
  accountNickName?: string | null;
  adminUserId?: IdValue;
  adminNickName?: string | null;
  createTime?: string | null;
}

interface AdminOptionPayload {
  adminUserId?: IdValue;
  userId?: IdValue;
  adminNickName?: string | null;
  nickName?: string | null;
  phoneNumber?: string | null;
}

interface AccountOptionPayload {
  accountUserId?: IdValue;
  nickName?: string | null;
  phoneNumber?: string | null;
  status?: boolean | number | string | null;
  createTime?: string | null;
}

interface AccountPagePayload {
  records?: AccountOptionPayload[];
  total?: number | string | null;
  pages?: number | string | null;
  size?: number | string | null;
  current?: number | string | null;
}

const normalizeAssignment = (
  record: AssignmentPayload,
): CopywritingOrganizationAdminAssignment => ({
  id: String(record?.id ?? ''),
  accountUserId: String(record?.accountUserId ?? ''),
  accountNickName: String(record?.accountNickName ?? ''),
  adminUserId: String(record?.adminUserId ?? ''),
  adminNickName: String(record?.adminNickName ?? ''),
  createTime: String(record?.createTime ?? ''),
});

const normalizeAdminOption = (
  record: AdminOptionPayload,
): CopywritingOrganizationAdminOption => ({
  adminUserId: String(record?.adminUserId ?? record?.userId ?? ''),
  adminNickName: String(record?.adminNickName ?? record?.nickName ?? ''),
  phoneNumber: record?.phoneNumber ? String(record.phoneNumber) : undefined,
});

const normalizeAccountOption = (
  record: AccountOptionPayload,
): CopywritingOrganizationReviewAccountOption => ({
  accountUserId: String(record?.accountUserId ?? ''),
  nickName: String(record?.nickName ?? ''),
  phoneNumber: record?.phoneNumber ? String(record.phoneNumber) : undefined,
  status: record?.status === true
    || record?.status === 1
    || record?.status === '1'
    || record?.status === 'true',
  createTime: String(record?.createTime ?? ''),
});

class CopywritingOrganizationAdminAssignmentService {
  async getAccounts(
    params: CopywritingOrganizationReviewAccountPageParams,
  ): Promise<CopywritingOrganizationReviewAccountPage> {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
    });
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim());

    const response = await authenticatedRequest<AccountPagePayload>(
      `/api/admin/copywriting-organization-admin-assignment/accounts?${query.toString()}`,
      { method: 'GET' },
    );
    const pageData = response.data ?? {};

    return {
      records: Array.isArray(pageData.records)
        ? pageData.records
          .map(normalizeAccountOption)
          .filter((record) => record.accountUserId)
        : [],
      total: Number(pageData.total) || 0,
      pages: Number(pageData.pages) || 0,
      size: Number(pageData.size) || params.size,
      current: Number(pageData.current) || params.page,
    };
  }

  async query(
    accountUserIds: string[],
  ): Promise<CopywritingOrganizationAdminAssignment[]> {
    if (accountUserIds.length === 0) return [];

    const response = await authenticatedRequest<AssignmentPayload[]>(
      '/api/admin/copywriting-organization-admin-assignment/query',
      {
        method: 'POST',
        body: JSON.stringify(accountUserIds),
      },
    );

    return (response.data ?? [])
      .map(normalizeAssignment)
      .filter((record) => record.accountUserId && record.adminUserId);
  }

  async getAdmins(keyword?: string): Promise<CopywritingOrganizationAdminOption[]> {
    const query = new URLSearchParams();
    if (keyword?.trim()) query.set('keyword', keyword.trim());
    const suffix = query.toString() ? `?${query.toString()}` : '';

    const response = await authenticatedRequest<AdminOptionPayload[]>(
      `/api/admin/copywriting-organization-admin-assignment/admins${suffix}`,
      { method: 'GET' },
    );

    return (response.data ?? [])
      .map(normalizeAdminOption)
      .filter((record) => record.adminUserId);
  }

  async update(
    accountUserId: string,
    adminUserIds: string[],
  ): Promise<CopywritingOrganizationAdminAssignment[]> {
    const response = await authenticatedRequest<AssignmentPayload[]>(
      `/api/admin/copywriting-organization-admin-assignment/${encodeURIComponent(accountUserId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(adminUserIds),
      },
    );

    return (response.data ?? [])
      .map(normalizeAssignment)
      .filter((record) => record.accountUserId && record.adminUserId);
  }
}

export default new CopywritingOrganizationAdminAssignmentService();
