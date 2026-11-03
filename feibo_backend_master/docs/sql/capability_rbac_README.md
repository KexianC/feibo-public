# Capability RBAC v1 migration

This migration introduces the permission catalog (`t_permission`) and the
role-to-permission relation (`t_role_permission_map`). It does not grant roles
to users and it must not be run against production without an approved change
window and a verified backup.

Run, in order, with the same explicitly selected database:

1. `capability_rbac_00_preflight.sql` (read the final `PASS` result).
2. `capability_rbac_10_apply_schema.sql`.
3. `capability_rbac_20_seed.sql`.
4. `capability_rbac_30_verify.sql` (all checks must be `PASS`).

Prerequisite: the user/role constraint migration must already have verified a
single-column unique key on `t_role.role_id`; the capability preflight refuses
to continue without it.

The seed is additive and rerunnable. It grants every v1 capability to
`SUPER_ADMIN`/legacy `SUPERADMIN`, and only `admin.console`, `user.read`, and
`content.manage` to `ADMIN`. It never removes an existing mapping.

## Enforcement matrix

| Capability | ADMIN seed | Backend/frontend enforcement |
| --- | --- | --- |
| `admin.console` | yes | Dashboard and ordinary console routes; `SUPER_ADMIN` role is the fallback. |
| `user.read` | yes | User, conversation, feedback, and work query routes; `SUPER_ADMIN` role is the fallback. Delete/update operations keep a method-level super-admin hard gate. |
| `content.manage` | yes | Background images, case videos, copywriting taxonomy/admin, digital models/templates/music, illustrations, videos, voices, and content-assignment routes; `SUPER_ADMIN` role is the fallback. |
| `user.manage` | no | Original acceptance requires the `SUPER_ADMIN` role for user mutations; the capability is non-delegable classification metadata, not a substitute for that role. |
| `identity.manage` | no | Role, user-role, and role-permission management retain the `SUPER_ADMIN` hard gate. |
| `audit.read` | no | Login/operation audit retains the `SUPER_ADMIN` hard gate. |
| `system.manage` | no | System/settings routes retain the `SUPER_ADMIN` hard gate. |
| `finance.manage` | no | Financial reads require this capability (or the super-admin fallback), while every mutation retains a method-level `SUPER_ADMIN` hard gate; the separately disabled finance module stays hidden. |

The assignment service rejects the five non-delegable high-risk capabilities
(`user.manage`, `identity.manage`, `audit.read`, `system.manage`, and
`finance.manage`) for every non-super role, even when a caller tampers with the
UI payload. A capability in this set is intentionally not sufficient to cross
its super-admin hard gate. Protected roles
can still have their allowed capability set changed; protected-role
rename/deletion rules are independent. This preserves the accepted
"super-admin only" boundary rather than turning a client-side assignment into
privilege escalation.

Authentication resolves permission codes from the same service for both the
login response and every JWT-authenticated request. Codes are restricted to a
lower-case dotted grammar, so a catalog row cannot inject `ROLE_*` authorities.
If the capability tables are missing or unavailable, authentication fails
closed; deploy the verified schema/seed before the application binary.

The API uses a SHA-256 snapshot version and a row lock on `t_role` before it
replaces a role's assignment set. An empty set additionally requires
`confirmEmpty=true`.

Rollback is intentionally gated. Review the inventory emitted by
`capability_rbac_90_rollback.sql`, take a backup, and set the confirmation
session variable in that file. Rollback refuses to run after the catalog has
been extended beyond the exact v1 manifest. DDL in MySQL implicitly commits;
rollback therefore cannot be treated as an application transaction.
