# UC-03-admin-overview — HTTP client invocation samples

**NOTE (D-2 / FR-15 / AC-43 / AC-52):** There is **no admin client** in
this iteration. No React Native admin screen, no admin web SPA, no new
file under `src/mobile/` or `src/admin/`. The admin dashboard is
consumed by an HTTP tool (curl / Postman / HTTPie) or a future task
`UC-03-admin-client`. This file documents the wire shape the admin
endpoints expose so any future client (or verification script) can
drive them without re-reading the whole controller package.

Sources of truth:
- `src/backend/src/main/java/com/authenticself/admin/AdminController.java`
- `src/backend/src/main/java/com/authenticself/admin/dto/*`
- `artifacts/UC-03-admin-overview/api_contract.yaml`

---

## 1. Seed-size assumption for the sample bodies below

Two seed sizes matter for this task:

| Seed size                                  | Purpose                            | Spec anchor     |
|--------------------------------------------|------------------------------------|-----------------|
| **100 users / 500 spaces / 1000 wishlist** | integration-test fixtures          | design.md §"Test-only admin seed" (10% of NFR) |
| **1000 users / 5000 spaces / 10000 wishlist / 24 furniture** | NFR P95 latency target | spec §7 (AC-39) |

The response samples below are **illustrative for the NFR-target size**
(1000 / 5000 / 10000 / 24). Verification tests will use the smaller
integration-test seed where exact counts can be asserted.

---

## 2. curl sample — master overview (happy path, admin caller)

```bash
curl -s -X GET \
     -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/overview?window=LAST_30D' | jq .
```

### 200 response body

```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",

  "users": {
    "totalUsers":   42,
    "totalAdmins":   2,
    "newSignups":    7,
    "activeUsers":  12,
    "signupsByDay": [
      { "date": "2026-03-19", "count": 0 },
      { "date": "2026-03-20", "count": 1 },
      { "date": "2026-03-21", "count": 0 },
      "...28 more dense days...",
      { "date": "2026-04-18", "count": 2 }
    ]
  },

  "rooms": {
    "totalSpaces": 210,
    "statusDistribution": {
      "PENDING_ANALYSIS":   0,
      "ANALYZED":         198,
      "FAILED":            12
    },
    "styleDistribution": {
      "MODERN":        80,
      "SIMPLE":        40,
      "CLASSIC":       30,
      "SCANDINAVIAN":  35,
      "INDUSTRIAL":    13
    },
    "mainColorTop5": [
      { "color": "#E8D9B0", "count": 60 },
      { "color": "#FFFFFF", "count": 48 },
      { "color": "#2B2B2B", "count": 31 },
      { "color": "#C0B8A4", "count": 28 },
      { "color": "#4A6FA5", "count": 22 }
    ]
  },

  "wishlist": {
    "totalItems": 340,
    "statusDistribution": {
      "ACTIVE":    270,
      "PURCHASED":  70
    },
    "categoryDistribution": {
      "desk":     120,
      "bed":       45,
      "chair":     95,
      "lighting":  80
    },
    "conversionRate": 0.21
  },

  "sales": {
    "totalSalesKrw":       5940000,
    "purchasedItemCount":       33,
    "averageOrderKrw":       180000,
    "salesByCategory": {
      "desk":     1200000,
      "bed":      3200000,
      "chair":     540000,
      "lighting": 1000000
    },
    "salesByDay": [
      { "date": "2026-03-19", "krw":      0 },
      { "date": "2026-03-20", "krw": 240000 },
      "...28 more dense days...",
      { "date": "2026-04-18", "krw": 140000 }
    ]
  }
}
```

### Invariant checks (enforced by AdminControllerTest / AdminOverviewServiceTest)

- `sum(rooms.statusDistribution) == rooms.totalSpaces`      — AC-18 (exact)
- `sum(rooms.styleDistribution)  <= rooms.totalSpaces`      — AC-17 / AC-35 (NULL style excluded)
- `sum(wishlist.statusDistribution) == wishlist.totalItems` — AC-20 (exact)
- `sum(wishlist.categoryDistribution) == wishlist.totalItems` — AC-20 (exact)
- `sum(sales.salesByCategory) == sales.totalSalesKrw`       — AC-22 (exact)
- `0.0 <= wishlist.conversionRate <= 1.0`                    — AC-21
- `wishlist.conversionRate == 0.0` when `totalItems == 0`    — AC-21 (no divide-by-zero)

---

## 3. curl samples — per-tile endpoints × window matrix

### 3.1 Users tile

```bash
curl -s -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/users?window=LAST_7D' | jq .
```

```json
{
  "window": "LAST_7D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalUsers":   5,
  "totalAdmins":  1,
  "newSignups":   3,
  "activeUsers":  4,
  "signupsByDay": [
    { "date": "2026-04-11", "count": 0 },
    { "date": "2026-04-12", "count": 1 },
    { "date": "2026-04-13", "count": 0 },
    { "date": "2026-04-14", "count": 0 },
    { "date": "2026-04-15", "count": 1 },
    { "date": "2026-04-16", "count": 0 },
    { "date": "2026-04-17", "count": 1 },
    { "date": "2026-04-18", "count": 0 }
  ]
}
```

- `signupsByDay.length == 8` for `LAST_7D` (7 days back + today) — AC-15.
- `totalUsers` / `totalAdmins` are window-filtered on `users.created_at`.
  Open-question (spec §11.1): "active-user" is defined as "uploaded a
  photo in the window" via `spaces.uploaded_at`. The alternative
  definition (uploaded OR wishlisted OR purchased) is deferred.

### 3.2 Rooms tile

```bash
curl -s -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/rooms?window=ALL' | jq .
```

```json
{
  "window": "ALL",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalSpaces": 210,
  "statusDistribution": {
    "PENDING_ANALYSIS":   0,
    "ANALYZED":         198,
    "FAILED":            12
  },
  "styleDistribution": {
    "MODERN":        80,
    "SIMPLE":        40,
    "CLASSIC":       30,
    "SCANDINAVIAN":  35,
    "INDUSTRIAL":    13
  },
  "mainColorTop5": [
    { "color": "#E8D9B0", "count": 60 },
    { "color": "#FFFFFF", "count": 48 },
    { "color": "#2B2B2B", "count": 31 },
    { "color": "#C0B8A4", "count": 28 },
    { "color": "#4A6FA5", "count": 22 }
  ]
}
```

- Key set of `statusDistribution` is read from `Space.Status.values()`
  at test time — test does NOT hard-code the string list (AC-34).
- Tie-break in `mainColorTop5` is `cnt DESC, color ASC` — deterministic
  (AC-50). Overriding `app.admin.top-colors-limit=3` truncates the
  array to 3 entries (AC-49).
- NULL-style rows counted in `totalSpaces` but not bucketed — sum of
  styleDistribution MAY be less than totalSpaces (AC-35).

### 3.3 Wishlist tile

```bash
curl -s -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/wishlist?window=LAST_30D' | jq .
```

```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalItems": 340,
  "statusDistribution": {
    "ACTIVE":    270,
    "PURCHASED":  70
  },
  "categoryDistribution": {
    "desk":     120,
    "bed":       45,
    "chair":     95,
    "lighting":  80
  },
  "conversionRate": 0.21
}
```

- Wire casing is UPPER_SNAKE (`ACTIVE`, `PURCHASED`). DB casing
  (`Active`, `Purchased`) is translated in the service layer.
- `categoryDistribution` is always the exact 4-value set
  `{desk, bed, chair, lighting}` with zero-fill buckets — AC-20.
- `conversionRate` = `round(PURCHASED / totalItems, 2)`; exactly
  `0.0` when `totalItems == 0` (AC-21).

### 3.4 Sales tile

```bash
curl -s -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/sales?window=LAST_30D' | jq .
```

```json
{
  "window": "LAST_30D",
  "generatedAt": "2026-04-18T09:14:22+09:00",
  "totalSalesKrw":       5940000,
  "purchasedItemCount":       33,
  "averageOrderKrw":       180000,
  "salesByCategory": {
    "desk":     1200000,
    "bed":      3200000,
    "chair":     540000,
    "lighting": 1000000
  },
  "salesByDay": [
    { "date": "2026-03-19", "krw":      0 },
    { "date": "2026-03-20", "krw": 240000 },
    { "date": "2026-03-21", "krw":      0 },
    "...27 more dense days...",
    { "date": "2026-04-18", "krw": 140000 }
  ]
}
```

- `totalSalesKrw` uses `wishlist.price` (snapshot at add-time),
  NOT `furniture.price` (AC-23). Historical sales never re-value when
  the catalog price changes.
- Active rows (`purchased_at IS NULL`) excluded from every sales
  aggregate (AC-24).
- `salesByDay` is dense ascending (zero-fill missing days, AC-25);
  `window=LAST_30D` produces 31 entries (30 days back + today).

---

## 4. Error-envelope samples (every admin endpoint)

Every non-2xx response is the shared three-field `ErrorResponse`
envelope — byte-identical in structure to UC-01 / UC-02 (AC-47).

### 4.1 400 MISSING_USER_HEADER

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
     'http://localhost:8080/api/v1/admin/overview'
# 400
```

```json
{
  "errorCode": "MISSING_USER_HEADER",
  "message":   "사용자 인증 정보가 없습니다.",
  "correlationId": "b1e5e0d2-3e38-4d17-9d2a-1d0c6db5c2e3"
}
```

### 4.2 400 INVALID_TIME_WINDOW

```bash
curl -s -H "X-User-Id: u_admin_01" \
     'http://localhost:8080/api/v1/admin/overview?window=YESTERDAY' | jq .
```

```json
{
  "errorCode": "INVALID_TIME_WINDOW",
  "message":   "기간 필터 값이 올바르지 않습니다. (LAST_7D, LAST_30D, ALL)",
  "correlationId": "f2a7b3e1-29a4-4b19-9c60-55abc0b1d3e2"
}
```

- Only fires when the caller is already authorized (requireAdmin runs
  first). A non-admin caller with a bad window gets 403 NOT_ADMIN.

### 4.3 403 NOT_ADMIN

```bash
curl -s -H "X-User-Id: u_normal_42" \
     'http://localhost:8080/api/v1/admin/sales' | jq .
```

```json
{
  "errorCode": "NOT_ADMIN",
  "message":   "관리자 권한이 필요합니다.",
  "correlationId": "7c4b1a29-7f9d-4e33-b2c8-1a3e5f8d9b01"
}
```

- Fires when `users.role = 'USER'` for the authenticated `X-User-Id`.
  `users.role` default is `'USER'` per V7 migration.

### 4.4 404 USER_NOT_FOUND

```bash
curl -s -H "X-User-Id: u_ghost" \
     'http://localhost:8080/api/v1/admin/overview' | jq .
```

```json
{
  "errorCode": "USER_NOT_FOUND",
  "message":   "사용자를 찾을 수 없습니다.",
  "correlationId": "9a3c2f11-85e4-4870-a1c1-6fe6f0a2cf3b"
}
```

---

## 5. Postman collection outline

Minimal Postman structure for ops engineers (not required by any AC —
included as documentation aid).

```
Admin Overview (UC-03)
├── Environment
│   ├── baseUrl = http://localhost:8080
│   ├── userId  = u_admin_01
│   └── window  = LAST_30D
├── Requests
│   ├── GET  {{baseUrl}}/api/v1/admin/overview?window={{window}}
│   ├── GET  {{baseUrl}}/api/v1/admin/users?window={{window}}
│   ├── GET  {{baseUrl}}/api/v1/admin/rooms?window={{window}}
│   ├── GET  {{baseUrl}}/api/v1/admin/wishlist?window={{window}}
│   └── GET  {{baseUrl}}/api/v1/admin/sales?window={{window}}
└── Headers (inherited)
    └── X-User-Id: {{userId}}
```

For every request, pre-request scripts SHOULD NOT store the
`correlationId` — each error response carries a fresh UUID so ops can
correlate to backend logs.

---

## 6. Future admin client outline (NOT BUILT — for reference only)

When `UC-03-admin-client` is scheduled, the same wire shapes above will
back a tile-by-tile dashboard layout. This iteration deliberately ships
no layout, no stylesheet, no RN routing entry. The spec §6 UC-03
sentence "대시보드에서 확인" is satisfied at the **contract** level
only; the rendering layer is deferred.

```mermaid
flowchart LR
    subgraph Current [Current iteration - UC-03-admin-overview]
        direction TB
        HTTP[Ops HTTP client<br/>curl / Postman]
        Endpoints[5 admin endpoints<br/>/api/v1/admin/*]
        HTTP --> Endpoints
    end

    subgraph Future [Future iteration - UC-03-admin-client (NOT BUILT)]
        direction TB
        UsersTile[Users tile card]
        RoomsTile[Rooms tile card]
        WishTile[Wishlist tile card]
        SalesTile[Sales tile card]
        WindowToggle[window toggle:<br/>LAST_7D LAST_30D ALL]
        UsersTile ~~~ RoomsTile
        WishTile ~~~ SalesTile
        WindowToggle ~~~ UsersTile
    end

    Endpoints -. "contract-ready for future client" .-> Future

    classDef dim fill:#f8d7da,stroke:#b02a37,color:#4c1d1d,stroke-dasharray: 4 3
    class Future,UsersTile,RoomsTile,WishTile,SalesTile,WindowToggle dim
```
