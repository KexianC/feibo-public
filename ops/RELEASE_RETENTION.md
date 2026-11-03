# Production release retention

Release artifacts are intentionally kept outside Git under `release/`. The
directory is ignored because it contains large binary packages and isolated
build evidence. This manifest records the local artifacts retained after the
2026-08-21 repository cleanup.

| Role | Artifact | Size (bytes) | SHA-256 |
| --- | --- | ---: | --- |
| Current backend | `release/copywriting-audit-detail-binary-fix-20260818-130803/backend/digital-doctor-app.copywriting-audit-detail.candidate.jar` | 213869417 | `3d683e45f976189d545f8367c98dee487766e873fac0c052cc345d4df975565d` |
| Previous backend rollback | `release/copywriting-history-hotfix-20260818-111500/digital-doctor-app.copywriting-history-hotfix.candidate.jar` | 213838200 | `65368b3d5746d8284d8154bf70f073acad49b052c5bcc29071a0b1432d31ef68` |
| Current user frontend | `release/20260821-100250-digital-human-result-proxy/artifacts/dd-digital-human-result-proxy-20260821-100250.tar.gz` | 25727796 | `0db7fcbaade91e2a1d40cdf548ca56282816d057360aa405398a2d3a338307ee` |
| Previous user frontend rollback | `release/remove-copywriting-summary-20260818-134021/artifacts/dd-remove-copywriting-summary-20260818-134021.tar.gz` | 25727790 | `18175cd6c3bcf89648c8de422e350f26653c449566148b8957d21fdbbb7df991` |
| Current management frontend | `release/kimi-k3-20260817-171145/artifacts/manage-baseline-20260817-171145.tar.gz` | 1105836 | `4ddcccf283865580392aca8c5665f3af6081566fc3bab02d8b87873b940d1e7c` |

Before deploying or rolling back, recompute the artifact hash and require an
exact match. Do not rebuild a retained artifact in place.
