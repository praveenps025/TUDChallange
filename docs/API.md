# CareTrack — Overview & API Contract

Mobile-first web app for a hospital OT equipment team to track loaned equipment
(wheelchairs, pressure cushions, sliding boards): scan → return → check → allocate,
plus audit walks, pre-discharge checks and monthly insights.

## Stack

- Vite 5, React 18.3, React Router 6 — no other runtime dependencies
- `src/api.js` — `get(path)` / `post(path, body)`
- `src/useApi.js` — `useApi(path)` → `{ data, error, reload }` (falsy path = no request)
- `src/mockData.js` — in-memory fake backend implementing the contract below
- `src/pages/` — 8 screens; `src/components/` — `Icon`, shared UI

### Mock vs real backend

With `VITE_API_URL` unset, every call is served by `mockData.js` (250 ms fake latency).
Set it in `.env.local` (e.g. `VITE_API_URL=http://localhost:8000/api`) and the same calls
go to `fetch(API_URL + path)` with JSON bodies.

Non-2xx responses throw `ApiError(status, message)` where `message` is the raw response
body text (or `statusText` if empty). Screens display `message` directly, so **error
responses should be plain, human-readable text**.

## Screens

| Route | Screen | API calls |
|---|---|---|
| `/` | Home — greeting, to-do tasks, recent activity | `GET /home` |
| `/scan` | Scan — camera preview (static), manual ID entry, recent IDs | — |
| `/item/:id` | Scan result — item card + one primary action per status | `GET /equipment/:id`, `POST /equipment/:id/inspect` |
| `/return/:id` | Return check — clean? → return, optionally allocate to next request | `GET /equipment/:id`, `GET /waitlist`, `POST /equipment/:id/return`, `POST /waitlist/:id/allocate` |
| `/waitlist` | Waitlist — filter by type; with `?allocate=ID`, pick a ward for that item | `GET /waitlist`, `GET /equipment/:id`, `POST /waitlist/:id/allocate` |
| `/audit` | Audit walk — progress and items still to find | `GET /audit` |
| `/discharge` | Discharge check by `?mrn=` — hold / ready / override | `GET /discharge/:mrn`, `POST /discharge/:mrn/override` |
| `/insights` | Monthly tiles and per-category breakdown | `GET /insights` |
| `*` | Redirect to `/` | — |

## Item lifecycle

```
available ──allocate──▶ issued ──return {clean:true}───▶ available
                          └─────return {clean:false}──▶ inspect ──inspect──▶ available
missing   (set outside the app; surfaced in Audit and Discharge)
```

Primary action on the scan result screen, by status:

| Status | Action |
|---|---|
| `issued` | Return and check → `/return/:id` |
| `available` | Issue to ward → `/waitlist?allocate=:id` |
| `inspect` | Mark inspected → `POST /equipment/:id/inspect` |
| `missing` | Open audit walk → `/audit` |

## API contract

Base URL: `VITE_API_URL`. Requests and responses are JSON.

### Equipment object

```json
{
  "id": "CU-024",
  "name": "Pressure cushion",
  "type": "Pressure cushion",
  "category": "Cushions",
  "status": "issued",
  "location": "Ward 2B",
  "note": "Loaned 28 Sep · Due today"
}
```

| Field | Notes |
|---|---|
| `id` | Label printed on the item. Lookups should be case-insensitive. |
| `type` | Must exactly match waitlist `type` values (`Pressure cushion`, `Wheelchair`, `Sliding board`). |
| `category` | Plural group (`Cushions`, `Wheelchairs`, `Sliding boards`). `Wheelchairs` selects the wheelchair icon. |
| `status` | `available` \| `issued` \| `inspect` \| `missing` |
| `note` | Free text shown under the item, e.g. due date or last seen. |

### Endpoints

#### `GET /home`

```json
{
  "user": "Alex",
  "team": "OT equipment team",
  "tasks": [
    { "icon": "layers", "title": "Allocate returned cushion", "detail": "CU-024 · Ward 3A is next", "to": "/return/CU-024" }
  ],
  "activity": [
    { "icon": "in", "title": "Sliding board returned", "detail": "SB-011 · OT store", "time": "09:32" }
  ]
}
```

- `tasks[].to` is an in-app route.
- `activity` is newest first, up to 5 entries. `icon`: `in` (return), `out` (issue), `clipboard` (inspect).

#### `GET /equipment/:id`

Returns an Equipment object. **404** if unknown.

#### `POST /equipment/:id/return`

Body: `{ "clean": true }`

Sets `location` to `OT store` and `status` to `available` (clean) or `inspect` (not clean).
Logs an activity entry. Returns the updated Equipment.

Errors: **404** unknown ID · **409** item is not `issued`.

#### `POST /equipment/:id/inspect`

No body. Sets `status` to `available`. Logs an activity entry. Returns the updated Equipment.

Errors: **404** unknown ID · **409** item is not `inspect`.

#### `GET /waitlist`

```json
[
  { "id": 1, "ward": "Ward 3A", "type": "Pressure cushion", "days": 6, "availableNow": true }
]
```

- Sorted oldest first (`days` descending).
- `availableNow`: walk the list oldest first; for each `type`, the first *N* requests are
  `true`, where *N* is the number of `available` items of that type.

#### `POST /waitlist/:id/allocate`

Body: `{ "equipmentId": "CU-009" }`

Sets the item to `status: issued`, `location: <request ward>`, removes the request from the
waitlist, logs an activity entry. Returns the updated Equipment.

Errors: **404** unknown request or item · **409** item not `available` or `type` mismatch.

#### `GET /audit`

```json
{
  "area": "Ward 3 · OT store",
  "started": "09:10",
  "by": "Alex Morgan",
  "total": 41,
  "found": 34,
  "missing": [ /* Equipment, status "missing" */ ]
}
```

`missing[].location` is shown as the expected location.

#### `GET /discharge/:mrn`

```json
{
  "mrn": "4821",
  "ward": "Ward 4C",
  "bed": "06",
  "items": [ /* Equipment loaned to this patient */ ],
  "override": null
}
```

- The mock matches on the last 4 digits of the input; the UI shows `••••<mrn>`, so return the masked/short form.
- The patient is **on hold** if any item's `status` is not `available` (computed client-side).
- `override` is `null` or `{ "reason": "...", "status": "requested" }`.

Errors: **404** no patient.

#### `POST /discharge/:mrn/override`

Body: `{ "reason": "Patient transferred with chair" }`

Records an override request. Returns the same shape as `GET /discharge/:mrn` with `override` set.

Errors: **400** empty reason · **404** no patient.

#### `GET /insights`

```json
{
  "month": "October",
  "tracked": 155,
  "unaccounted": 4,
  "waitlists": 7,
  "readyToAllocate": 3,
  "onTime": 86,
  "categories": [
    { "name": "Wheelchairs", "available": 18, "issued": 40, "check": 4, "waiting": 3, "advice": "Redistribute" }
  ]
}
```

- `unaccounted` = count of `missing` items; `waitlists` = open requests; `readyToAllocate` = requests with `availableNow`.
- `onTime` is a percentage of returns on time this month.
- `categories[].check` = items in `inspect` or `missing`; `advice` is a short label (`Redistribute`, `Consider buying`).

## Open issues

1. **Error format** — `api.js` shows the raw body. Either return plain text, or change `api.js` to read a JSON `message` field.
2. **Return + allocate isn't atomic** — Return check makes two POSTs; if allocate fails the item is returned but unallocated. Consider a combined endpoint.
3. **No audit write API** — `found`/`total` are static; no endpoint records scans during a walk. The offline/sync indicators are cosmetic.
4. **No override approval** — requests can be made but not approved or denied.
5. **Hardcoded mock values** — Insights counts, `onTime`, `tracked`, Home tasks and user. No authentication.
6. **UI-only controls** — static camera, torch, batch-scan toggle, Insights period selector, hardcoded recent IDs on Scan.
7. **Waitlist type filters** are hardcoded in `Waitlist.jsx` rather than coming from the API.
