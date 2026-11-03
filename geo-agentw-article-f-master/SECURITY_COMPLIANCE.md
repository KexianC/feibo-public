# Security Compliance Remediation Notes

Scope: Spring Boot backend, Vue frontend, and the local Python publish service.

## Main Risks Found

- Secrets were stored in source-controlled configuration: database password, DashScope API key, admin password, Qdrant address, local storage path.
- Authentication used one shared cleartext password and passed it in the URL query string.
- The frontend persisted a token-like value in local storage, while the backend only used session authentication.
- CSRF protection was missing for state-changing APIs.
- Multiple delete/toggle operations used GET.
- `/chat` was anonymously accessible.
- File upload accepted untrusted filenames without path-boundary checks.
- Server absolute file paths were returned in document API responses and written into vector metadata.
- Python publish service allowed all CORS origins, listened on `0.0.0.0`, and trusted only a user-controlled `userid` header.
- Runtime artifacts such as cookies, logs, SQLite DB files, and uploaded medical documents were present in the workspace.

## Architecture Changes

- External entrypoint is the Spring Boot backend. The Python publish service is treated as a localhost-only internal component.
- Spring backend now supports internal service authentication to Python through `X-Internal-Token`.
- Secrets and storage paths are injected through environment variables. `application.yaml` no longer contains live secrets.
- Default backend bind address is `127.0.0.1`; expose externally through a TLS gateway or reverse proxy.
- Session authentication now returns a CSRF token after login. Unsafe requests must include `X-CSRF-Token`.
- Login rotates the session id, sets idle timeout, and applies login failure throttling.
- Security headers and audit logging are applied globally.
- Uploaded documents and assets are checked for size, extension, safe filename, and storage-directory containment.
- GET delete/toggle APIs were changed to `DELETE` or `POST`.

## Operational Requirements Not Solved By Code Alone

- Rotate the leaked DashScope key, database password, social platform cookies, and any other credentials that were present in the repository.
- Remove previously committed sensitive artifacts from version history if this repository has been pushed anywhere.
- Configure `APP_ADMIN_PASSWORD_HASH` with a BCrypt hash; avoid `APP_ADMIN_PASSWORD` outside local development.
- Set `SESSION_COOKIE_SECURE=true` behind HTTPS.
- Use TLS for MySQL and Qdrant in non-local environments.
- Store runtime uploads, logs, database files, and social cookies outside the source tree with restrictive filesystem permissions.
- Add real user accounts, roles, and per-doctor/per-hospital authorization before handling production medical data.
- Add vulnerability scanning, dependency SCA, penetration testing, backup/restore drills, and incident response procedures for formal MLPS Level 3 assessment.
