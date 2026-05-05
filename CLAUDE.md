# AuthenticSelf — Agent Iteration Engine

공간 분석 및 가구 추천 서비스 (React Native + Unity AR / Spring + MySQL / Python AI). 이 프로젝트는 4개의 서브에이전트가 반복 루프로 코드를 생성하는 방식으로 개발됩니다.

PRD: `공간 분석 및 가구 추천 프로젝트 구조화.pdf` (루트)

## Agent Roles

| # | Agent | 역할 | 산출물 |
|---|-------|------|--------|
| 1 | `prompt-specialist` | PRD → 작업 스펙 + 수용 기준 | `artifacts/<task-id>/spec.md`, `acceptance_criteria.json` |
| 2 | `design-specialist` | 스펙 → 아키텍처 + 실제 코드 | `src/**/*`, `artifacts/<task-id>/design.md`, `api_contract.yaml` |
| 3 | `visualization-specialist` | 설계 → 다이어그램/UI/AR 뷰 | `artifacts/<task-id>/viz/*` (Mermaid, UI mock, Unity scene) |
| 4 | `verification-specialist` | 산출물 ↔ 수용 기준 검증 | `artifacts/<task-id>/verification.md` (PASS/FAIL + 블로커) |

## Iteration Loop (Orchestrator = main Claude session)

```
For each task-id in the queue:
  iteration = 1
  loop:
    1. Invoke Task(subagent_type="prompt-specialist",  prompt=<task-id, PRD ref, prev failures>)
    2. Invoke Task(subagent_type="design-specialist",   prompt=<task-id, spec path>)
    3. Invoke Task(subagent_type="visualization-specialist", prompt=<task-id>)
    4. Invoke Task(subagent_type="verification-specialist",  prompt=<task-id>)
    if verdict == PASS or PASS-WITH-WARNINGS: break
    if iteration >= MAX_ITER (default 3): escalate to user
    iteration += 1
    # next loop: prompt-specialist receives verification blockers and updates spec
```

Parallelism rule: **steps 1–4 must run sequentially** (each depends on the previous). But when multiple **independent task-ids** are in the queue (e.g., different UCs with no shared files), their loops can run in parallel by invoking multiple `Task` calls in a single message.

## Task Queue (priority order)

Derived from PRD §6 use-case specs.

| Priority | Task ID | PRD |
|----------|---------|-----|
| 1 | `DB-schema-init` | §3 (User/Space/Furniture/Wishlist tables + Flyway V1) |
| 2 | `UC-01-photo-upload` | §6 UC-01 steps 1 (upload endpoint + object storage) |
| 3 | `UC-01-space-analysis` | §6 UC-01 step 2 (Python AI service: OpenCV/LayoutNet/YOLO) |
| 4 | `UC-01-style-selection` | §6 UC-01 step 3 (RN style picker UI) |
| 5 | `UC-01-recommendation` | §6 UC-01 steps 4–5 (cross-validate + recommend) |
| 6 | `UC-02-wishlist` | §6 UC-02 (Active ↔ Purchased) |
| 7 | `UC-03-admin-overview` | §6 UC-03 (Users/Rooms/Wishlist/Sales dashboard) |
| 8 | `AR-furniture-placement` | §4 (Unity AR Foundation 뷰) |

Dependencies: 2–5 depend on 1; 6 depends on 5; 7 depends on 1 & 6; 8 depends on 5.

## Conventions

- **Artifacts**: every task writes to `artifacts/<task-id>/`. Never share a directory between tasks.
- **Code layout**: follow `design-specialist.md` §"Project layout".
- **Branch per task**: `git checkout -b task/<task-id>` before design-specialist writes code (the orchestrator handles this when asked).
- **Retry**: max 3 iterations per task. If still failing, escalate — do not loop forever.
- **Model**: all 4 agents use Opus (complex reasoning). If cost becomes a concern, downgrade `visualization-specialist` to Sonnet first.

## How to start an iteration

User types: **"Run UC-01-photo-upload"** (or any task-id).
Orchestrator (main session) then invokes the 4 agents in sequence per the loop above.

Auto-run entire queue: **"Run the full queue"** — orchestrator walks tasks in priority order, respecting dependencies. Escalates to user on any task that fails 3 iterations.

# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
