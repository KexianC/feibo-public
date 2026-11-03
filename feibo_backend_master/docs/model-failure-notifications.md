# Model failure notifications

Apply `docs/sql/model_failure_notifications_20260909.sql` to the application's MySQL database before deploying the backend and admin frontend. The migration only creates two new tables and may be repeated. It does not modify existing conversations, model settings, roles or users.

The chat gateway publishes `ModelFailureEvent` after a primary failure reaches its final fallback outcome. A dedicated executor persists one notification per request ID. The tables retain notifications across application restarts; read state is stored separately for each super administrator. The worker drains on orderly shutdown. Like other in-process application events, a forced process termination before the queued event is written can lose that event. Database or notification worker failures are logged with exception types only and do not break a completed answer.

All notification API methods, and the backing administrative service, require `ROLE_SUPER_ADMIN`. The caller ID comes from the authenticated user; clients cannot supply another administrator's read identity. Ordinary user notification lists do not contain these notices.

Endpoints under `/api/v1/admin/model-failure-notices` use the existing `Result` envelope:

- `GET ?pageNum=1&pageSize=20`: `{ records, total, current, size, unreadCount }`, maximum page size 100.
- `GET /unread-count`: numeric unread count.
- `POST /{id}/read`: mark one notification read for the current super administrator; repeating is safe.
- `POST /read-all`: mark existing notifications read for the current super administrator; repeating is safe.

Every record contains string identifiers (`id`, `requestId`, `userId`, `conversationId`, `originalModelConfigId`), provider/model names, safe `errorType`, fallback names, `outcome`, ISO UTC `createdAt`, and `read`. Optional IDs and fallback names may be null. Outcomes are `FALLBACK_SUCCEEDED`, `FALLBACK_FAILED`, `FALLBACK_UNAVAILABLE`, `PRIMARY_FAILED`, or `CANCELLED`. No prompt, answer, credential or raw provider response is stored.

Before release, verify a simulated provider failure creates one persisted notice, another super administrator retains their own unread state, and ordinary administrators receive access denied. Confirm the database migration has been applied before diagnosing empty notification results.

Rollback: restore the previous backend/frontend artifacts and retain the additive tables for diagnosis. There is no need to delete notification data or alter existing tables.
