// Sample data and a fake backend, used until VITE_API_URL is set.
// It follows the real API (docs/API.md) closely enough for every screen:
//
//   GET  /home                              greeting, to-do tasks, recent activity
//   GET  /equipment?status&type&category&search&page&size   search, paged
//   GET  /equipment/recent                  items the signed-in user last worked on
//   GET  /equipment/:id                     one item
//   POST /equipment/:id/return              { clean: true|false }
//   POST /equipment/:id/inspect
//   POST /equipment/:id/return-and-allocate { requestId }
//   POST /equipment                         register (managers) { type, pool, location, details }
//   GET  /equipment/:id/details             everything about one item
//   PUT  /equipment/:id                     edit details (managers) { pool, details }
//   POST /equipment/:id/label-printed       (managers)
//   GET  /equipment-types, /categories, /locations?kind=ward, /pools
//   POST /equipment-types                   (managers) { name, category, labelPrefix, defaultLoanDays }
//   GET  /waitlist                          requests, oldest first
//   POST /waitlist                          { type, ward, mrn, priority, bed?, note? }
//   DELETE /waitlist/:id
//   POST /waitlist/:id/allocate             { equipmentId }
//   GET  /audit                             current audit walk (404 when none)
//   POST /audit                             { locations, area? } start a walk
//   POST /audit/scan                        { equipmentId }
//   POST /audit/finish                      unfound items become missing
//   GET  /discharge/:mrn                    patient and their loaned items
//   POST /discharge/:mrn/override           { reason } (staff: request; manager: approve)
//   GET  /discharge/overrides               pending approvals (managers)
//   POST /discharge/overrides/:id/approve   { reason } (managers), and /reject
//   GET  /insights                          monthly overview (managers)
//
//   POST /auth/login                        { username, password } -> { token, expiresAt, user }
//   GET  /auth/me                           signed-in user
//   POST /auth/change-password              { currentPassword, newPassword }
//   GET  /users, POST /users, PUT /users/:id, POST /users/:id/reset-password   (managers only)
//
// Item status is one of: available, issued, inspect, missing.
// In sample-data mode any password works for the users below.
import { ApiError } from "./api";

const TYPES = [
  { name: "Wheelchair", category: "Wheelchairs", icon: "wheelchair", labelPrefix: "WC", defaultLoanDays: 14 },
  { name: "Pressure cushion", category: "Cushions", icon: "layers", labelPrefix: "CU", defaultLoanDays: 14 },
  { name: "Sliding board", category: "Sliding boards", icon: "layers", labelPrefix: "SB", defaultLoanDays: 14 },
];

const POOLS = [
  { code: "MAIN", name: "Main OT stock", description: "General OT equipment store" },
  { code: "BURKITT", name: "Burkitt / ARHC", description: "Held for Burkitt and ARHC" },
  { code: "WSU_ASU", name: "WSU / ASU own funding", description: "Bought from WSU / ASU budgets" },
  { code: "WINTER", name: "Winter Initiative", description: "Bought through the Winter Initiative" },
];

const DETAIL_FIELDS = ["model", "serialNumber", "assetNumber", "assetFlag", "size", "maxUserWeight", "hc21Number", "dmlNumber",
  "therapist", "comments", "purchaseDate", "lastServiceDate"];
const details = {}; // extra registration details by item ID

const LOCATIONS = [
  { name: "OT store", kind: "store" }, { name: "Workshop", kind: "workshop" },
  ...["Ward 1B", "Ward 2A", "Ward 2B", "Ward 2C", "Ward 3A", "Ward 3B", "Ward 4A", "Ward 4C", "Ward 5A", "Ward 5B", "Ward 6A"]
    .map(name => ({ name, kind: "ward" })),
];

const equipment = {
  "CU-024": { id: "CU-024", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "issued", location: "Ward 2B", note: "Loaned 28 Sep · Due today" },
  "CU-009": { id: "CU-009", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "available", location: "OT store", note: "Checked today" },
  "CU-031": { id: "CU-031", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "missing", location: "Ward 3B", note: "Last seen 29 Sep" },
  "WC-018": { id: "WC-018", name: "Transit wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "missing", location: "Ward 4C", note: "Last seen 30 Sep" },
  "WC-026": { id: "WC-026", name: "Wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "issued", location: "Ward 5A", note: "Loaned today · Due today" },
  "WC-031": { id: "WC-031", name: "Wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "available", location: "OT store", note: "Checked 6 Oct" },
  "SB-011": { id: "SB-011", name: "Sliding board", type: "Sliding board", category: "Sliding boards", status: "inspect", location: "OT store", note: "Returned today · Needs inspection" },
  "SB-006": { id: "SB-006", name: "Sliding board", type: "Sliding board", category: "Sliding boards", status: "missing", location: "OT store", note: "Last seen 1 Oct" },
};

let waitlist = [
  { id: 1, ward: "Ward 3A", type: "Pressure cushion", days: 6, priority: "routine", mrn: null, note: null },
  { id: 2, ward: "Ward 5B", type: "Wheelchair", days: 5, priority: "soon", mrn: null, note: null },
  { id: 3, ward: "Ward 2C", type: "Sliding board", days: 4, priority: "urgent", mrn: null, note: null },
  { id: 4, ward: "Ward 4A", type: "Pressure cushion", days: 3, priority: "soon", mrn: null, note: null },
  { id: 5, ward: "Ward 1B", type: "Wheelchair", days: 2, priority: "routine", mrn: null, note: null },
  { id: 6, ward: "Ward 6A", type: "Pressure cushion", days: 1, priority: "routine", mrn: null, note: null },
  { id: 7, ward: "Ward 2A", type: "Wheelchair", days: 1, priority: "routine", mrn: null, note: null },
];
const PRIORITY_ORDER = { urgent: 0, soon: 1, routine: 2 };
const queue = () => [...waitlist].sort((a, b) => PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority] || b.days - a.days || a.id - b.id);
let nextRequestId = 8;

const activity = [
  { icon: "in", title: "Sliding board returned", detail: "SB-011 · OT store", time: "09:32", by: "alex.morgan", equipmentId: "SB-011" },
  { icon: "out", title: "Wheelchair issued", detail: "WC-026 · Ward 5A", time: "09:18", by: "alex.morgan", equipmentId: "WC-026" },
  { icon: "clipboard", title: "Cushion inspected", detail: "CU-009 · Available", time: "08:56", by: "alex.morgan", equipmentId: "CU-009" },
];

const patients = {
  "4821": { mrn: "4821", ward: "Ward 4C", bed: "06", items: ["WC-018"], override: null },
};
let nextOverrideId = 1;

// The audit walk in progress, or null
let walk = {
  area: "Ward 3 · OT store", started: "09:10", by: "Alex Morgan",
  expected: ["CU-009", "CU-031", "WC-031", "SB-011", "SB-006"], scanned: ["CU-009", "WC-031", "SB-011"],
};

const users = [
  { id: 1, username: "alex.morgan", name: "Alex Morgan", team: "OT equipment team", role: "MANAGER", active: true, locked: false, lastLoginAt: null },
  { id: 2, username: "jordan.lee", name: "Jordan Lee", team: "OT equipment team", role: "MANAGER", active: true, locked: false, lastLoginAt: null },
  { id: 3, username: "sam.patel", name: "Sam Patel", team: "OT equipment team", role: "OT_STAFF", active: true, locked: false, lastLoginAt: null },
  { id: 4, username: "riley.chen", name: "Riley Chen", team: "OT equipment team", role: "OT_STAFF", active: true, locked: false, lastLoginAt: null },
];
const TOKEN_HOURS = 8;
const summary = u => ({ name: u.name, team: u.team, role: u.role });
const findUser = username => users.find(u => u.username.toLowerCase() === username?.trim().toLowerCase());

function managerOnly(me) {
  if (me.role !== "MANAGER") throw new ApiError(403, "You don't have permission to do that");
}

function checkPassword(password) {
  if (!password || password.length < 8 || password.length > 72) throw new ApiError(400, "Passwords must be 8 to 72 characters");
}

const now = () => new Date().toTimeString().slice(0, 5);
const log = (me, icon, title, detail, equipmentId = null) =>
  activity.unshift({ icon, title, detail, time: now(), by: me.username, equipmentId });

function item(id) {
  const found = equipment[id?.trim().toUpperCase()];
  if (!found) throw new ApiError(404, `No equipment with ID ${id}`);
  return found;
}

function openRequest(id) {
  const request = waitlist.find(r => r.id === Number(id));
  if (!request) throw new ApiError(404, "Request not found");
  return request;
}

// In queue order (most urgent, then longest waiting), requests take the available items of their type
function withAvailability() {
  const left = {};
  return queue().map(request => {
    left[request.type] ??= Object.values(equipment).filter(e => e.type === request.type && e.status === "available").length;
    const availableNow = left[request.type] > 0;
    if (availableNow) left[request.type]--;
    return { ...request, availableNow };
  });
}

function returnItem(me, e, clean) {
  if (e.status !== "issued") throw new ApiError(409, `${e.id} is not issued`);
  Object.assign(e, { status: clean ? "available" : "inspect", location: "OT store", note: clean ? "Checked today" : "Returned today · Needs inspection" });
  log(me, "in", `${e.name} returned`, `${e.id} · OT store`, e.id);
}

function allocate(me, request, e) {
  if (e.status !== "available" || e.type !== request.type) throw new ApiError(409, `${e.id} can't be allocated to this request`);
  if (request.mrn && patients[request.mrn]) patients[request.mrn].items.push(e.id);
  Object.assign(e, { status: "issued", location: request.ward, note: "Loaned today · Due in 14 days" });
  waitlist = waitlist.filter(r => r !== request);
  log(me, "out", `${e.name} issued`, `${e.id} · ${request.ward}`, e.id);
  return e;
}

function walkResponse() {
  if (!walk) throw new ApiError(404, "No audit walk is in progress");
  return {
    area: walk.area, started: walk.started, by: walk.by, total: walk.expected.length,
    found: walk.expected.filter(id => walk.scanned.includes(id)).length,
    missing: walk.expected.filter(id => !walk.scanned.includes(id)).map(item),
  };
}

function patientFor(mrn) {
  const patient = patients[mrn.replace(/\D/g, "").slice(-4)];
  if (!patient) throw new ApiError(404, "No patient found with that MRN");
  return patient;
}

const patientResponse = patient => ({ ...patient, items: patient.items.map(item) });

function detailsOf(e) {
  const extra = details[e.id] ?? {};
  const pool = POOLS.find(p => p.code === (extra.pool ?? "MAIN"));
  return { ...e, pool: pool.code, poolName: pool.name, labelPrintedAt: extra.labelPrintedAt ?? null,
    ...Object.fromEntries(DETAIL_FIELDS.map(f => [f, extra[f] ?? null])) };
}

function checkDetails(d) {
  if (!d?.name?.trim()) throw new ApiError(400, "Enter a name for the item");
  if (d.assetFlag && !["AT+", "AT-"].includes(d.assetFlag)) throw new ApiError(400, "Asset flag must be AT+ or AT-");
}

function poolCode(code) {
  const pool = POOLS.find(p => p.code.toLowerCase() === code?.toLowerCase());
  if (!pool) throw new ApiError(400, `Unknown funding pool ${code}`);
  return pool.code;
}

const routes = {
  "POST /auth/login": (_, { username, password }) => {
    if (!username?.trim()) throw new ApiError(400, "Enter your username");
    if (!password) throw new ApiError(400, "Enter your password");
    const user = findUser(username);
    if (!user) throw new ApiError(401, "Incorrect username or password");
    if (!user.active) throw new ApiError(401, "This account has been deactivated. Ask a manager to reactivate it.");
    user.lastLoginAt = new Date().toISOString();
    const expiresAt = new Date(Date.now() + TOKEN_HOURS * 3600 * 1000).toISOString();
    return { token: `mock:${user.username}`, expiresAt, user: summary(user) };
  },

  "GET /auth/me": (_, __, me) => summary(me),

  "POST /auth/change-password": (_, { currentPassword, newPassword }) => {
    if (!currentPassword) throw new ApiError(400, "Enter your current password");
    if (!newPassword || newPassword.length < 8 || newPassword.length > 72) throw new ApiError(400, "The new password must be 8 to 72 characters");
    if (newPassword === currentPassword) throw new ApiError(400, "Choose a password different from your current one");
    return null;
  },

  "GET /users": (_, __, me) => {
    managerOnly(me);
    return [...users].sort((a, b) => a.name.localeCompare(b.name));
  },

  "POST /users": (_, { username, name, team, role, password }, me) => {
    managerOnly(me);
    if (!/^[A-Za-z0-9._-]{3,60}$/.test(username?.trim() ?? "")) throw new ApiError(400, "Usernames are 3 to 60 letters, numbers, dots, dashes or underscores");
    if (!name?.trim()) throw new ApiError(400, "Enter a name");
    if (!team?.trim()) throw new ApiError(400, "Enter a team");
    checkPassword(password);
    if (findUser(username)) throw new ApiError(409, `The username ${username.trim()} is already taken`);
    const user = { id: Math.max(...users.map(u => u.id)) + 1, username: username.trim(), name: name.trim(), team: team.trim(), role, active: true, locked: false, lastLoginAt: null };
    users.push(user);
    return user;
  },

  "PUT /users/:id": ({ id }, { name, team, role, active }, me) => {
    managerOnly(me);
    const user = users.find(u => u.id === Number(id));
    if (!user) throw new ApiError(404, "User not found");
    if (!name?.trim()) throw new ApiError(400, "Enter a name");
    if (!team?.trim()) throw new ApiError(400, "Enter a team");
    if (user === me && (!active || role !== "MANAGER")) throw new ApiError(409, "You can't deactivate your own account or remove your own manager role");
    return Object.assign(user, { name: name.trim(), team: team.trim(), role, active });
  },

  "POST /users/:id/reset-password": ({ id }, { password }, me) => {
    managerOnly(me);
    const user = users.find(u => u.id === Number(id));
    if (!user) throw new ApiError(404, "User not found");
    checkPassword(password);
    user.locked = false;
    return null;
  },

  "GET /home": (_, __, me) => ({
    user: me.name.split(" ")[0],
    team: me.team,
    tasks: [
      { icon: "layers", title: "Allocate pressure cushion", detail: "CU-009 · Ward 3A is next", to: "/waitlist?allocate=CU-009" },
      { icon: "clock", title: "Loan due", detail: "WC-026 · Ward 5A · Due today", to: "/item/WC-026" },
      { icon: "eyeoff", title: "Unseen items", detail: "3 items · Last seen over 7 days ago", to: "/audit" },
      { icon: "clipboard", title: "Discharge check", detail: "Ward 4C · MRN ••••4821", to: "/discharge?mrn=4821" },
      ...(me.role === "MANAGER" ? Object.values(patients).filter(p => p.override?.status === "requested").map(p => (
        { icon: "shield", title: "Approve discharge", detail: `${p.ward} · MRN ••••${p.mrn} · asked by ${p.override.requestedBy}`, to: `/discharge?mrn=${p.mrn}` }
      )) : []),
    ],
    activity: activity.slice(0, 5).map(({ icon, title, detail, time }) => ({ icon, title, detail, time })),
  }),

  "POST /equipment": (_, { type, pool, location, details: d }, me) => {
    managerOnly(me);
    const t = TYPES.find(x => x.name === type);
    if (!t) throw new ApiError(400, `Unknown equipment type ${type}`);
    const code = poolCode(pool);
    const where = location || "OT store";
    if (!LOCATIONS.some(l => l.name === where)) throw new ApiError(400, `Unknown location ${where}`);
    checkDetails(d);
    const highest = Math.max(0, ...Object.keys(equipment).filter(id => id.startsWith(`${t.labelPrefix}-`)).map(id => Number(id.split("-")[1]) || 0));
    const id = `${t.labelPrefix}-${String(highest + 1).padStart(3, "0")}`;
    equipment[id] = { id, name: d.name.trim(), type: t.name, category: t.category, status: "available", location: where, note: "Checked today" };
    details[id] = { ...d, pool: code };
    log(me, "in", `${d.name.trim()} registered`, `${id} · ${where}`, id);
    return detailsOf(equipment[id]);
  },

  "GET /equipment": (_, __, ___, query) => {
    const page = Number(query.page ?? 0), size = Number(query.size ?? 20);
    const search = query.search?.trim().toLowerCase();
    const items = Object.values(equipment)
      .filter(e => !query.status || e.status === query.status)
      .filter(e => !query.type || e.type.toLowerCase() === query.type.toLowerCase())
      .filter(e => !query.category || e.category.toLowerCase() === query.category.toLowerCase())
      .filter(e => !search || e.id.toLowerCase().includes(search) || e.name.toLowerCase().includes(search))
      .sort((a, b) => a.id.localeCompare(b.id));
    return { items: items.slice(page * size, (page + 1) * size), page, size, total: items.length };
  },

  // Must come before /equipment/:id
  "GET /equipment/recent": (_, __, me) => {
    const ids = [...new Set(activity.filter(a => a.by === me.username && a.equipmentId).map(a => a.equipmentId))];
    return ids.slice(0, 5).map(item);
  },

  "GET /equipment/:id": ({ id }) => item(id),

  "GET /equipment/:id/details": ({ id }) => detailsOf(item(id)),

  "PUT /equipment/:id": ({ id }, { pool, details: d }, me) => {
    managerOnly(me);
    const e = item(id);
    checkDetails(d);
    details[e.id] = { ...details[e.id], ...d, pool: poolCode(pool) };
    e.name = d.name.trim();
    log(me, "clipboard", `${e.name} details updated`, e.id, e.id);
    return detailsOf(e);
  },

  "POST /equipment/:id/label-printed": ({ id }, _, me) => {
    managerOnly(me);
    const e = item(id);
    const reprint = Boolean(details[e.id]?.labelPrintedAt);
    details[e.id] = { ...details[e.id], labelPrintedAt: new Date().toISOString() };
    log(me, "clipboard", reprint ? "Label reprinted" : "Label printed", e.id, e.id);
    return detailsOf(e);
  },

  "POST /equipment/:id/return": ({ id }, { clean }, me) => {
    if (typeof clean !== "boolean") throw new ApiError(400, "Say whether the item is clean and undamaged");
    const e = item(id);
    returnItem(me, e, clean);
    return e;
  },

  "POST /equipment/:id/inspect": ({ id }, _, me) => {
    const e = item(id);
    if (e.status !== "inspect") throw new ApiError(409, `${e.id} doesn't need inspection`);
    Object.assign(e, { status: "available", note: "Checked today" });
    log(me, "clipboard", `${e.name} inspected`, `${e.id} · Available`, e.id);
    return e;
  },

  "POST /equipment/:id/return-and-allocate": ({ id }, { requestId }, me) => {
    if (requestId == null) throw new ApiError(400, "Choose the request to allocate to");
    const request = openRequest(requestId);
    const e = item(id);
    if (e.status !== "issued") throw new ApiError(409, `${e.id} is not issued`);
    if (e.type !== request.type) throw new ApiError(409, `${e.id} can't be allocated to this request`);
    returnItem(me, e, true);
    return allocate(me, request, e);
  },

  "GET /equipment-types": () => TYPES.map(({ name, category, labelPrefix, defaultLoanDays }) => ({ name, category, labelPrefix, defaultLoanDays })),

  "POST /equipment-types": (_, { name, category, labelPrefix, defaultLoanDays }, me) => {
    managerOnly(me);
    if (!name?.trim()) throw new ApiError(400, "Enter a name for the type");
    if (!category?.trim()) throw new ApiError(400, "Choose or enter a category");
    const prefix = labelPrefix?.trim().toUpperCase() ?? "";
    if (!/^[A-Z0-9]{1,10}$/.test(prefix)) throw new ApiError(400, "Label prefixes are 1 to 10 letters or numbers, e.g. WC");
    if (TYPES.some(t => t.name === name.trim())) throw new ApiError(409, `There is already a type called ${name.trim()}`);
    if (TYPES.some(t => t.labelPrefix === prefix)) throw new ApiError(409, `The prefix ${prefix} is already used by another type`);
    const type = { name: name.trim(), category: category.trim(), icon: "layers", labelPrefix: prefix, defaultLoanDays: Number(defaultLoanDays) || 14 };
    TYPES.push(type);
    log(me, "clipboard", "Equipment type added", `${type.name} · ${prefix}`);
    return { name: type.name, category: type.category, labelPrefix: prefix, defaultLoanDays: type.defaultLoanDays };
  },

  "GET /pools": () => POOLS,

  "GET /categories": () => [...new Set(TYPES.map(t => t.category))].map(name => ({
    name, icon: TYPES.find(t => t.category === name).icon, types: TYPES.filter(t => t.category === name).map(t => t.name),
  })),

  "GET /locations": (_, __, ___, query) => LOCATIONS.filter(l => !query.kind || l.kind === query.kind),

  "GET /waitlist": () => withAvailability(),

  "POST /waitlist": (_, { ward, type, mrn, priority, note }, me) => {
    if (!type) throw new ApiError(400, "Choose the item needed");
    if (!ward) throw new ApiError(400, "Choose a ward");
    if (!mrn?.trim()) throw new ApiError(400, "Enter the patient's MRN");
    if (!priority) throw new ApiError(400, "Choose a priority");
    if (!(priority in PRIORITY_ORDER)) throw new ApiError(400, "Priority must be urgent, soon or routine");
    if (!LOCATIONS.some(l => l.kind === "ward" && l.name === ward)) throw new ApiError(400, `Unknown ward ${ward}`);
    if (!TYPES.some(t => t.name === type)) throw new ApiError(400, `Unknown equipment type ${type}`);
    const digits = mrn.replace(/\D/g, "");
    if (digits.length < 4) throw new ApiError(400, "Enter at least the last 4 digits of the MRN");
    const suffix = digits.slice(-4);
    if (digits.length === 4 && !patients[suffix]) throw new ApiError(400, "Enter the full MRN to add a new patient");
    patients[suffix] ??= { mrn: suffix, ward, bed: null, items: [], override: null };
    const request = { id: nextRequestId++, ward, type, days: 0, priority, mrn: suffix, note: note?.trim() || null };
    waitlist.push(request);
    log(me, "clipboard", `${type} requested`, `${ward} · ${priority[0].toUpperCase()}${priority.slice(1)}`);
    return withAvailability().find(r => r.id === request.id);
  },

  "DELETE /waitlist/:id": ({ id }, _, me) => {
    const request = openRequest(id);
    waitlist = waitlist.filter(r => r !== request);
    log(me, "clipboard", "Request cancelled", `${request.type} · ${request.ward}`);
    return null;
  },

  "POST /waitlist/:id/allocate": ({ id }, { equipmentId }, me) => {
    if (!equipmentId) throw new ApiError(400, "Choose the item to allocate");
    const request = openRequest(id);
    return allocate(me, request, item(equipmentId));
  },

  "GET /audit": () => walkResponse(),

  "POST /audit": (_, { locations, area }, me) => {
    if (walk) throw new ApiError(409, "An audit walk is already in progress. Finish it before starting another.");
    if (!locations?.length) throw new ApiError(400, "Choose at least one location");
    walk = {
      area: area?.trim() || locations.join(" · "), started: now(), by: me.name,
      expected: Object.values(equipment).filter(e => locations.includes(e.location)).map(e => e.id), scanned: [],
    };
    log(me, "clipboard", "Audit walk started", walk.area);
    return walkResponse();
  },

  "POST /audit/scan": (_, { equipmentId }, me) => {
    if (!walk) throw new ApiError(409, "No audit walk is in progress. Start one first.");
    const e = item(equipmentId);
    if (!walk.scanned.includes(e.id)) {
      walk.scanned.push(e.id);
      if (e.status === "missing") {
        Object.assign(e, { status: "inspect", note: "Returned today · Needs inspection" });
        log(me, "clipboard", `${e.name} found`, `${e.id} · Needs inspection`, e.id);
      } else {
        log(me, "clipboard", `${e.name} checked`, `${e.id} · ${walk.area}`, e.id);
      }
    }
    return walkResponse();
  },

  "POST /audit/finish": (_, __, me) => {
    if (!walk) throw new ApiError(409, "No audit walk is in progress");
    const result = walkResponse();
    result.missing.forEach(({ id }) => Object.assign(equipment[id], { status: "missing", note: "Last seen before today" }));
    log(me, "clipboard", "Audit walk finished", `${walk.area} · ${result.found} of ${result.total} found`);
    walk = null;
    return { area: result.area, total: result.total, found: result.found, markedMissing: result.missing.length };
  },

  // Must come before /discharge/:mrn
  "GET /discharge/overrides": (_, __, me) => {
    managerOnly(me);
    return Object.values(patients).filter(p => p.override?.status === "requested").map(p => ({
      id: p.override.id, mrn: p.mrn, ward: p.ward, bed: p.bed, reason: p.override.reason, requestedBy: p.override.requestedBy,
      requestedAt: p.override.requestedAt, itemsOut: p.items.map(item).filter(e => e.status !== "available").length,
    }));
  },

  "POST /discharge/overrides/:id/approve": ({ id }, { reason }, me) => decideOverride(me, Number(id), true, reason),

  "POST /discharge/overrides/:id/reject": ({ id }, { reason }, me) => decideOverride(me, Number(id), false, reason),

  "GET /discharge/:mrn": ({ mrn }) => patientResponse(patientFor(mrn)),

  // Staff ask for an override; a manager doing the check approves it straight away
  "POST /discharge/:mrn/override": ({ mrn }, { reason }, me) => {
    const patient = patientFor(mrn);
    if (!reason?.trim()) throw new ApiError(400, "A reason is required");
    if (patient.override?.status === "requested") throw new ApiError(409, "An override for this patient is already waiting for a manager");
    const manager = me.role === "MANAGER";
    patient.override = {
      id: nextOverrideId++, reason: reason.trim(), status: manager ? "approved" : "requested", requestedBy: me.name, requestedAt: now(),
      decidedBy: manager ? me.name : null, decisionReason: manager ? reason.trim() : null,
    };
    log(me, "clipboard", manager ? "Discharge approved" : "Discharge override requested", `${patient.ward} · MRN ••••${patient.mrn}`);
    return patientResponse(patient);
  },

  "GET /insights": (_, __, me) => {
    managerOnly(me);
    const items = Object.values(equipment);
    const count = (category, ...statuses) => items.filter(e => e.category === category && statuses.includes(e.status)).length;
    return {
      month: new Date().toLocaleDateString("en-IE", { month: "long" }),
      tracked: items.length,
      unaccounted: items.filter(e => e.status === "missing").length,
      waitlists: waitlist.length,
      readyToAllocate: withAvailability().filter(r => r.availableNow).length,
      onTime: 86,
      categories: [...new Set(TYPES.map(t => t.category))].map(name => {
        const available = count(name, "available");
        const waiting = waitlist.filter(r => TYPES.find(t => t.name === r.type).category === name).length;
        return {
          name, available, issued: count(name, "issued"), check: count(name, "inspect", "missing"), waiting,
          advice: waiting > available ? "Consider buying" : waiting > 0 ? "Redistribute" : null,
        };
      }),
    };
  },
};

function decideOverride(me, id, approve, reason) {
  managerOnly(me);
  if (!reason?.trim()) throw new ApiError(400, "A reason is required");
  const patient = Object.values(patients).find(p => p.override?.id === id);
  if (!patient) throw new ApiError(404, "Override request not found");
  if (patient.override.status !== "requested") throw new ApiError(409, `This request has already been ${patient.override.status}`);
  Object.assign(patient.override, { status: approve ? "approved" : "rejected", decidedBy: me.name, decisionReason: reason.trim() });
  log(me, "clipboard", approve ? "Discharge approved" : "Discharge override rejected", `${patient.ward} · MRN ••••${patient.mrn}`);
  return patientResponse(patient);
}

// Match "/equipment/CU-024/return" against "/equipment/:id/return"
function match(pattern, path) {
  const a = pattern.split("/"), b = path.split("/");
  if (a.length !== b.length) return null;
  const params = {};
  for (let i = 0; i < a.length; i++) {
    if (a[i].startsWith(":")) params[a[i].slice(1)] = decodeURIComponent(b[i]);
    else if (a[i] !== b[i]) return null;
  }
  return params;
}

export async function mockRequest(method, path, body = {}, token) {
  await new Promise(resolve => setTimeout(resolve, 250)); // feel like a network call

  // Tokens look like "mock:alex.morgan"; deactivated users are rejected like the real API
  const me = token && findUser(token.replace(/^mock:/, ""));
  if (path !== "/auth/login") {
    if (!me) throw new ApiError(401, "Your session has expired. Please sign in again.");
    if (!me.active) throw new ApiError(401, "This account has been deactivated. Ask a manager to reactivate it.");
  }

  const [pathname, queryString] = path.split("?");
  const query = Object.fromEntries(new URLSearchParams(queryString));
  for (const [route, handler] of Object.entries(routes)) {
    const [routeMethod, pattern] = route.split(" ");
    const params = routeMethod === method && match(pattern, pathname);
    if (params) return structuredClone(handler(params, body ?? {}, me, query));
  }
  throw new ApiError(404, `No route for ${method} ${pathname}`);
}
