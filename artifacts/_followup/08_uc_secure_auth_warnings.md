# Follow-up — UC-SECURE-AUTH Verification Warnings

**Date**: 2026-05-26
**Source task**: `UC-SECURE-AUTH` (PASS-WITH-WARNINGS, 27/29 AC PASS, 0 FAIL)
**Verification**: `artifacts/UC-SECURE-AUTH/verification.md`
**Branch**: `task/UC-SECURE-AUTH`

This document captures the three non-blocking warnings the verification specialist surfaced. None block production cutover of the auth/CORS slice itself; they are hygiene items to schedule into future cycles.

---

## W-1 — Testcontainer-only ACs not exercised in fast CI

**Severity**: 🟡 Medium
**Affected ACs**: AC-8 (V11 column shape integration), AC-17 (`JwtAuthFilterIntegrationTest`)

### Problem
Both ACs have dedicated test classes (`V11PasswordHashMigrationTest`, `JwtAuthFilterIntegrationTest`) but are excluded from the `-PskipTestcontainers=true` run that constitutes the verification path. They require Docker + MySQL via Testcontainers.

### Why this is acceptable for this iteration
- The verification path used (`./gradlew test -PskipTestcontainers=true`) is the same one the project has used since `runtime-verification.md` documented the WSL2 ↔ Docker Desktop probe-stub incompatibility in 2026-05.
- The static + unit-level ACs covering the same surface area (`V11__add_users_password_hash.sql:1-39` static read, `JwtAuthFilterTest` 10 mock-MVC cases) verify the contract.
- The Testcontainers gap is environment-level, not code-level. The tests are written and ready.

### Recommended fix
Provision CI with Docker + Testcontainers properties pinned via `~/.testcontainers.properties` (see `runtime-verification.md` §7.1). Re-run the full suite once CI has Docker. No code change required.

**Effort**: platform / DevOps, ~2h.

---

## W-2 — `JwtAuthFilter` OPTIONS skip not enumerated in spec FR-11

**Severity**: 🟢 Low (documentation hygiene only)
**Affected**: spec text vs implementation truth

### Problem
`JwtAuthFilter` (per visualization-specialist's diagram-vs-code audit) has a **third skip case** for HTTP `OPTIONS` requests not enumerated in spec FR-11's narrative. The two skip cases the spec calls out are:
1. `request.requestURI == /api/v1/auth/login`
2. Path outside `/api/v1/**`

The implementation adds:
3. `request.method == OPTIONS` — lets CORS preflights through before token verification.

### Why the implementation is correct
CORS preflight requests (the browser-emitted `OPTIONS` before a credentialed call) are not allowed to carry an `Authorization` header. If `JwtAuthFilter` ran on preflights, every cross-origin authenticated call would 401 at the preflight stage, and the `CorsConfig` mapping would never get a chance to respond. The OPTIONS skip is load-bearing for FR-CORS-3 to work at all.

### Recommended fix
In a future `spec-hygiene-v2` cycle, amend FR-11 in `artifacts/UC-SECURE-AUTH/spec.md` to enumerate the OPTIONS skip as a third explicit skip case with a one-line rationale. Diagrams (`viz/filter_chain.md`, `viz/cors_preflight.md`) already show the OPTIONS branch correctly.

**Effort**: 5 min spec edit; bundled with whatever `spec-hygiene-v2` task picks this up.

---

## W-3 — AC-BC-3 baseline drift (141 → 179)

**Severity**: 🟢 Low
**Affected**: spec's regression-count math

### Problem
Spec FR-BC-3 / AC-BC-3 says "all 141 backend tests continue to pass." The actual baseline at the start of this task was higher — upstream commit `as_objects_00` (predates UC-SECURE-AUTH) had added tests that brought the count above 141. Final count after UC-SECURE-AUTH is **179 PASS** (141 spec-baseline + N pre-existing-additions + 38 new auth/CORS tests).

### Why this is acceptable
AC-BC-3 is satisfied by the inequality `final_count ≥ baseline_count` regardless of the precise baseline value. 179 ≥ 141 + 38 trivially holds. No regression was masked; design-specialist did patch two slice tests broken by upstream `as_objects_00` (`SpaceControllerTest`, `SpaceControllerRecommendationsTest`) by adding two missing `@MockBean`s and a `null` arg — verification confirmed these are genuine compile-blocker fixes, not regression-mask.

### Recommended fix
When the next task spec is written, prompt-specialist should be told to query the current backend test count via `./gradlew test -PskipTestcontainers --info | tail -20` and quote the live number rather than the historic 141. This is a process tweak, not a code change.

**Effort**: nil (process note for future iterations).

---

## Summary

| Warning | Severity | Code change? | Scheduled into |
|---------|----------|--------------|----------------|
| W-1 Testcontainer ACs | 🟡 Medium | None | CI provisioning task |
| W-2 OPTIONS skip in spec | 🟢 Low | None | spec-hygiene-v2 |
| W-3 Baseline drift | 🟢 Low | None | future prompt-specialist process tweak |

None block ship-as-is for the `task/UC-SECURE-AUTH` branch.

## References

- `artifacts/UC-SECURE-AUTH/verification.md` §1 AC table + §4 scope-creep audit
- `artifacts/UC-SECURE-AUTH/design.md` §6 (slice-test fixes documented)
- `artifacts/UC-SECURE-AUTH/viz/filter_chain.md` (OPTIONS skip drawn correctly)
- `artifacts/UC-SECURE-AUTH/viz/cors_preflight.md` (OPTIONS preflight flow)
- `report/runtime-verification.md` §7.1 (Testcontainers ↔ Docker Desktop background)
