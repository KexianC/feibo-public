/**
 * Frontend guardrails for the role editor. The backend remains the security
 * boundary and must independently enforce every protected-role invariant.
 */
export const PROTECTED_ROLE_CODES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SUPERADMIN',
  'EXPERT',
  'ORG_ADMIN',
  'DOCTOR',
  'DOCTOR_ASSISTANT',
] as const;

const LEGACY_SUPER_ADMIN = 'SUPERADMIN';
const CANONICAL_SUPER_ADMIN = 'SUPER_ADMIN';
const VALID_ROLE_CODE = /^[A-Z][A-Z0-9_]{1,63}$/;

export const PROTECTED_ROLE_NOTICE =
  '系统内置角色 ADMIN、SUPER_ADMIN（兼容 SUPERADMIN）、EXPERT、ORG_ADMIN、DOCTOR、DOCTOR_ASSISTANT 不允许在此改名或删除；前端限制仅用于防误操作，最终以后端权限和数据校验为准。';

export const normalizeRoleCode = (value: unknown): string => {
  const normalized = String(value ?? '').trim().toUpperCase();
  return normalized === LEGACY_SUPER_ADMIN ? CANONICAL_SUPER_ADMIN : normalized;
};

const protectedCanonicalCodes = new Set(
  PROTECTED_ROLE_CODES.map((code) => normalizeRoleCode(code)),
);

export const isProtectedRoleCode = (value: unknown): boolean =>
  protectedCanonicalCodes.has(normalizeRoleCode(value));

export const isValidRoleCode = (value: unknown): boolean =>
  VALID_ROLE_CODE.test(normalizeRoleCode(value));
