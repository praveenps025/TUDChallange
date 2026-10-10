// Sample data and a fake backend, used until VITE_API_URL is set.
// It follows the real API (docs/API.md) closely enough for every screen:
//
//   GET  /home                              greeting, to-do tasks, recent activity
//   GET  /equipment?status&type&category&search&page&size   search, paged
//   GET  /equipment/available?category&type&search&weight   "Find equipment": what can be taken now
//   GET  /equipment/recent                  items the signed-in user last worked on
//   GET  /equipment/:id                     one item
//   POST /equipment/:id/return              { clean: true|false }
//   POST /equipment/:id/inspect
//   POST /equipment/:id/return-and-allocate { requestId }
//   POST /equipment                         register (managers) { type, pool, location, details }
//   GET  /equipment/:id/details             everything about one item
//   PUT  /equipment/:id                     edit details (managers) { pool, details }
//   POST /equipment/:id/label-printed       (managers)
//   GET  /equipment-types, /categories, /locations?kind=ward, /pools, /weight-categories
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
//   POST /home-loans                        { mrn, items: [{ equipmentId, dueOn? }], note? } (staff: request; manager: lend)
//   GET  /home-loans/pending                (managers), POST /home-loans/:id/approve | /reject { reason }
//   GET  /insights?period=this-month|last-month|this-year   overview (managers)
//
//   POST /auth/login                        { username, password } -> { token, expiresAt, user }
//   GET  /auth/me                           signed-in user
//   POST /auth/change-password              { currentPassword, newPassword }
//   GET  /users, POST /users, PUT /users/:id, POST /users/:id/reset-password   (managers only)
//
// Item status is one of: available, issued, inspect, missing.
// In sample-data mode any password works for the users below.
import { ApiError } from "./api";

// ---------------------------------------------------------------- dates
// Sample dates are relative to today, so the demo never looks out of date

const isoDay = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const addDays = n => { const d = new Date(); d.setDate(d.getDate() + n); return isoDay(d); };
const todayIso = () => addDays(0);
function dayLabel(iso) {
  if (iso === todayIso()) return "today";
  if (iso === addDays(-1)) return "yesterday";
  if (iso === addDays(1)) return "tomorrow";
  return new Date(`${iso}T12:00:00`).toLocaleDateString("en-IE", { day: "numeric", month: "short" });
}
const dueText = loan => loan.dueOn < todayIso() ? `Overdue since ${dayLabel(loan.dueOn)}` : `Due ${dayLabel(loan.dueOn)}`;

// ---------------------------------------------------------- reference data

const TYPES = [
  { name: "Wheelchair", category: "Wheelchairs", icon: "wheelchair", labelPrefix: "WC", defaultLoanDays: 14 },
  { name: "Pressure cushion", category: "Cushions", icon: "layers", labelPrefix: "CU", defaultLoanDays: 14 },
  { name: "Sliding board", category: "Sliding boards", icon: "layers", labelPrefix: "SB", defaultLoanDays: 14 },
  { name: "Hoist", category: "Hoists", icon: "layers", labelPrefix: "HO", defaultLoanDays: 28 },
];

const POOLS = [
  { code: "MAIN", name: "Main OT stock", description: "General OT equipment store" },
  { code: "BURKITT", name: "Burkitt / ARHC", description: "Held for Burkitt and ARHC" },
  { code: "WSU_ASU", name: "WSU / ASU own funding", description: "Bought from WSU / ASU budgets" },
  { code: "WINTER", name: "Winter Initiative", description: "Bought through the Winter Initiative" },
];

// Patient weight bands; an item rated for a band suits lighter patients too
const WEIGHTS = [
  { name: "Underweight", maxKg: 60 }, { name: "Standard", maxKg: 125 },
  { name: "Overweight", maxKg: 170 }, { name: "Bariatric", maxKg: 250 },
];
const weightOf = name => WEIGHTS.find(w => w.name.toLowerCase() === name?.toLowerCase());

const LOCATIONS = [
  { name: "OT store", kind: "store" }, { name: "Workshop", kind: "workshop" },
  ...["Ward 1B", "Ward 2A", "Ward 2B", "Ward 2C", "Ward 3A", "Ward 3B", "Ward 4A", "Ward 4C", "Ward 5A", "Ward 5B", "Ward 6A"]
    .map(name => ({ name, kind: "ward" })),
];

// ------------------------------------------------------------- equipment
// Every patient, MRN, serial number and name below is fictional.

const item_ = (id, name, type, status, location, note = "") => {
  const t = TYPES.find(x => x.name === type);
  return [id, { id, name, type, category: t.category, status, location, note, homeLoan: false }];
};

const equipment = Object.fromEntries([
  // Wheelchairs
  item_("WC-018", "Transit wheelchair", "Wheelchair", "missing", "Ward 4C", "Last seen 10 days ago"),
  item_("WC-026", "Wheelchair", "Wheelchair", "issued", "Ward 5A"),
  item_("WC-031", "Wheelchair", "Wheelchair", "available", "OT store", "Checked 4 days ago"),
  item_("WC-033", "Self-propelled wheelchair", "Wheelchair", "available", "Workshop", "Checked yesterday"),
  item_("WC-035", "Wheelchair", "Wheelchair", "issued", "Ward 3A"),
  item_("WC-040", "Bariatric wheelchair", "Wheelchair", "available", "OT store", "Checked today"),
  item_("WC-041", "Transit wheelchair", "Wheelchair", "inspect", "OT store", "Returned today · Needs inspection"),
  // Cushions
  item_("CU-009", "Pressure cushion", "Pressure cushion", "available", "OT store", "Checked today"),
  item_("CU-024", "Pressure cushion", "Pressure cushion", "issued", "Ward 2B"),
  item_("CU-031", "Pressure cushion", "Pressure cushion", "missing", "Ward 3B", "Last seen 11 days ago"),
  item_("CU-044", "Air cushion", "Pressure cushion", "issued", "Ward 6A"),
  item_("CU-050", "Pressure cushion", "Pressure cushion", "available", "OT store", "Checked 2 days ago"),
  // Sliding boards
  item_("SB-006", "Sliding board", "Sliding board", "missing", "OT store", "Last seen 9 days ago"),
  item_("SB-011", "Sliding board", "Sliding board", "inspect", "OT store", "Returned today · Needs inspection"),
  item_("SB-014", "Sliding board", "Sliding board", "issued", "Ward 4C"),
  item_("SB-020", "Curved sliding board", "Sliding board", "available", "Workshop", "Checked 3 days ago"),
  // Hoists
  item_("HO-001", "Mobile hoist", "Hoist", "available", "OT store", "Checked yesterday"),
  item_("HO-002", "Standing hoist", "Hoist", "issued", "Ward 5B"),
]);

const DETAIL_FIELDS = ["model", "serialNumber", "assetNumber", "assetFlag", "size", "maxUserWeight", "hc21Number", "dmlNumber",
  "therapist", "comments", "purchaseDate", "lastServiceDate"];
const details = { // registration details by item ID
  "WC-018": { weightCategory: "Standard", model: "Lightweight transit", serialNumber: "SN-TR-20418", assetFlag: "AT+", purchaseDate: "2024-03-12" },
  "WC-026": { weightCategory: "Standard" },
  "WC-031": { weightCategory: "Overweight", model: "Heavy-duty", serialNumber: "SN-HD-31007", assetNumber: "A-10031", size: "51 x 46 cm", maxUserWeight: "160 kg" },
  "WC-033": { weightCategory: "Standard", pool: "WINTER", model: "Self-propel", serialNumber: "SN-SP-33090" },
  "WC-035": { weightCategory: "Standard" },
  "WC-040": { weightCategory: "Bariatric", pool: "WSU_ASU", model: "Bariatric 66", serialNumber: "SN-BA-40221", maxUserWeight: "250 kg", purchaseDate: "2025-11-02" },
  "WC-041": { weightCategory: "Standard" },
  "CU-009": { weightCategory: "Standard" },
  "CU-024": { weightCategory: "Standard" },
  "CU-031": { weightCategory: "Standard" },
  "CU-044": { weightCategory: "Overweight", model: "Alternating air", serialNumber: "SN-AIR-4410" },
  "CU-050": { weightCategory: "Standard", pool: "BURKITT" },
  "SB-006": { weightCategory: "Standard" },
  "SB-011": { weightCategory: "Bariatric" },
  "SB-014": { weightCategory: "Standard" },
  "SB-020": { weightCategory: "Overweight" },
  "HO-001": { weightCategory: "Bariatric", model: "Mobile 200", serialNumber: "SN-HO-00170", lastServiceDate: addDays(-30) },
  "HO-002": { weightCategory: "Overweight", model: "Sit-to-stand", serialNumber: "SN-HO-00288" },
};

// Open loans of issued items: when they went out and when they are due back
const loans = {
  "WC-026": { issuedOn: addDays(0), dueOn: addDays(0) },
  "WC-035": { issuedOn: addDays(-20), dueOn: addDays(-3) },          // overdue
  "CU-024": { issuedOn: addDays(-12), dueOn: addDays(0) },
  "CU-044": { issuedOn: addDays(-6), dueOn: addDays(8), mrn: "7731" },
  "SB-014": { issuedOn: addDays(-3), dueOn: addDays(4), mrn: "4821" },
  "HO-002": { issuedOn: addDays(-9), dueOn: addDays(5), mrn: "5502" },
};

// ---------------------------------------------------------------- patients
// plannedToday: due to be discharged today (shows on Home as a discharge check)

const patients = {
  "4821": { mrn: "4821", ward: "Ward 4C", bed: "06", items: ["WC-018", "SB-014"], override: null, plannedToday: true },
  "7731": { mrn: "7731", ward: "Ward 6A", bed: "12", items: ["CU-044"], override: null, plannedToday: true },
  "5502": {
    mrn: "5502", ward: "Ward 5B", bed: "03", items: ["HO-002"], plannedToday: true,
    override: { id: 1, reason: "Moving to a nursing home; they will return the hoist this week", status: "requested",
      requestedBy: "Riley Chen", requestedAt: "09:05", decidedBy: null, decisionReason: null },
  },
  "6190": { mrn: "6190", ward: "Ward 2A", bed: "09", items: [], override: null, plannedToday: true },
  "3317": { mrn: "3317", ward: "Ward 3A", bed: "02", items: [], override: null },
  "2264": { mrn: "2264", ward: "Ward 2C", bed: "15", items: [], override: null },
  "5120": { mrn: "5120", ward: "Ward 5B", bed: "07", items: [], override: null },
  "4410": { mrn: "4410", ward: "Ward 4A", bed: "11", items: [], override: null },
  "1185": { mrn: "1185", ward: "Ward 1B", bed: "04", items: [], override: null },
  "6044": { mrn: "6044", ward: "Ward 6A", bed: "05", items: [], override: null },
};
let nextOverrideId = 2;

// A home loan already waiting for a manager (patient 7731's air cushion)
let homeLoans = [
  { id: 1, status: "requested", mrn: "7731", ward: "Ward 6A", bed: "12", note: "Daughter collecting on Friday",
    requestedBy: "Sam Patel", requestedAt: "08:40", decidedBy: null, decisionReason: null,
    items: [{ id: "CU-044", name: "Air cushion", dueOn: addDays(21) }] },
];
let nextHomeLoanId = 2;

// ---------------------------------------------------------------- waitlist

let waitlist = [
  { id: 1, ward: "Ward 3A", type: "Pressure cushion", days: 6, priority: "routine", mrn: "3317", note: null },
  { id: 2, ward: "Ward 5B", type: "Wheelchair", days: 5, priority: "soon", mrn: "5120", note: "Needs a narrow chair" },
  { id: 3, ward: "Ward 2C", type: "Sliding board", days: 4, priority: "urgent", mrn: "2264", note: "Discharge blocked" },
  { id: 4, ward: "Ward 4A", type: "Pressure cushion", days: 3, priority: "soon", mrn: "4410", note: null },
  { id: 5, ward: "Ward 1B", type: "Wheelchair", days: 2, priority: "routine", mrn: "1185", note: null },
  { id: 6, ward: "Ward 6A", type: "Hoist", days: 1, priority: "urgent", mrn: "6044", note: "Pressure-injury risk" },
  { id: 7, ward: "Ward 2A", type: "Wheelchair", days: 1, priority: "routine", mrn: null, note: null },
];
const PRIORITY_ORDER = { urgent: 0, soon: 1, routine: 2 };
const queue = () => [...waitlist].sort((a, b) => PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority] || b.days - a.days || a.id - b.id);
let nextRequestId = 8;

// ---------------------------------------------------------------- activity

const activity = [
  { icon: "clipboard", title: "Discharge override requested", detail: "Ward 5B · MRN ••••5502", time: "09:05", by: "riley.chen", equipmentId: null },
  { icon: "in", title: "Transit wheelchair returned", detail: "WC-041 · OT store", time: "08:58", by: "riley.chen", equipmentId: "WC-041" },
  { icon: "clipboard", title: "Home loan requested", detail: "1 item · MRN ••••7731", time: "08:40", by: "sam.patel", equipmentId: null },
  { icon: "in", title: "Sliding board returned", detail: "SB-011 · OT store", time: "08:32", by: "sam.patel", equipmentId: "SB-011" },
  { icon: "out", title: "Wheelchair issued", detail: "WC-026 · Ward 5A", time: "08:18", by: "sam.patel", equipmentId: "WC-026" },
  { icon: "clipboard", title: "Pressure cushion inspected", detail: "CU-009 · Available", time: "07:56", by: "alex.morgan", equipmentId: "CU-009" },
  { icon: "in", title: "Bariatric wheelchair registered", detail: "WC-040 · OT store", time: "07:40", by: "alex.morgan", equipmentId: "WC-040" },
];

// The audit walk in progress, or null
let walk = {
  area: "Ward 3 · OT store", started: "09:10", by: "Alex Morgan",
  expected: ["CU-009", "CU-031", "CU-050", "HO-001", "SB-006", "SB-011", "WC-031", "WC-040", "WC-041"],
  scanned: ["CU-009", "HO-001", "SB-011", "WC-031", "WC-040"],
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
  const loan = loans[found.id];
  if (found.status === "issued" && loan) {
    found.note = `${found.homeLoan ? "Home loan since" : "Loaned"} ${dayLabel(loan.issuedOn)} · ${dueText(loan)}`;
  }
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
  Object.assign(e, { status: clean ? "available" : "inspect", location: "OT store", homeLoan: false, note: clean ? "Checked today" : "Returned today · Needs inspection" });
  Object.values(patients).forEach(p => { p.items = p.items.filter(id => id !== e.id); });
  delete loans[e.id];
  log(me, "in", `${e.name} returned`, `${e.id} · OT store`, e.id);
}

function allocate(me, request, e) {
  if (e.status !== "available" || e.type !== request.type) throw new ApiError(409, `${e.id} can't be allocated to this request`);
  if (request.mrn && patients[request.mrn]) patients[request.mrn].items.push(e.id);
  Object.assign(e, { status: "issued", location: request.ward });
  loans[e.id] = { issuedOn: todayIso(), dueOn: addDays(TYPES.find(t => t.name === e.type)?.defaultLoanDays ?? 14), mrn: request.mrn ?? undefined };
  item(e.id);
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

const patientResponse = patient => ({
  ...patient, items: patient.items.map(item),
  homeLoan: [...homeLoans].reverse().find(r => r.mrn === patient.mrn) ?? null,
});

function lendHome(me, request) {
  const patient = patients[request.mrn];
  request.items.forEach(({ id, dueOn }) => {
    const e = item(id);
    const onThisPatient = e.status === "issued" && patient.items.includes(e.id) && !e.homeLoan;
    if (e.status !== "available" && !onThisPatient) throw new ApiError(409, `${e.id} can't be lent: it is ${e.status} and not on loan to this patient`);
  });
  request.items.forEach(({ id, dueOn }) => {
    const e = item(id);
    Object.assign(e, { status: "issued", homeLoan: true, location: `Home loan · MRN ••••${patient.mrn}` });
    loans[e.id] = { issuedOn: todayIso(), dueOn, mrn: patient.mrn, home: true };
    item(e.id);
    if (!patient.items.includes(e.id)) patient.items.push(e.id);
    log(me, "out", `${e.name} sent home on loan`, `${e.id} · MRN ••••${patient.mrn}`, e.id);
  });
}

function detailsOf(e) {
  const extra = details[e.id] ?? {};
  const pool = POOLS.find(p => p.code === (extra.pool ?? "MAIN"));
  const weight = weightOf(extra.weightCategory);
  return { ...e, pool: pool.code, poolName: pool.name, labelPrintedAt: extra.labelPrintedAt ?? null,
    weightCategory: weight?.name ?? null, weightMaxKg: weight?.maxKg ?? null,
    ...Object.fromEntries(DETAIL_FIELDS.map(f => [f, extra[f] ?? null])) };
}

function checkDetails(d) {
  if (!d?.name?.trim()) throw new ApiError(400, "Enter a name for the item");
  if (d.assetFlag && !["AT+", "AT-"].includes(d.assetFlag)) throw new ApiError(400, "Asset flag must be AT+ or AT-");
  if (d.weightCategory && !weightOf(d.weightCategory)) throw new ApiError(400, `Unknown weight category ${d.weightCategory}`);
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

  // Tasks are worked out from the sample data, like the real backend does
  "GET /home": (_, __, me) => {
    const tasks = [];
    const types = new Set();
    for (const request of queue()) {
      if (types.has(request.type)) continue;
      types.add(request.type);
      const free = Object.values(equipment).find(e => e.type === request.type && e.status === "available");
      if (free) tasks.push({ icon: "layers", title: `Allocate ${request.type.toLowerCase()}`, detail: `${free.id} · ${request.ward} is next`, to: `/waitlist?allocate=${free.id}` });
    }
    Object.entries(loans)
      .filter(([id, loan]) => equipment[id]?.status === "issued" && loan.dueOn <= todayIso())
      .sort(([, a], [, b]) => a.dueOn.localeCompare(b.dueOn))
      .slice(0, 5)
      .forEach(([id, loan]) => {
        const where = loan.home ? `Home · MRN ••••${loan.mrn}` : equipment[id].location;
        const kind = loan.home ? "Home loan" : "Loan";
        tasks.push({ icon: "clock", title: `${kind} ${loan.dueOn < todayIso() ? "overdue" : "due"}`, detail: `${id} · ${where} · ${dueText(loan)}`, to: `/item/${id}` });
      });
    const missing = Object.values(equipment).filter(e => e.status === "missing").length;
    if (missing) tasks.push({ icon: "eyeoff", title: "Missing items", detail: `${missing} items reported missing · Check on an audit walk`, to: "/audit" });
    Object.values(patients)
      .filter(p => p.plannedToday && p.items.some(id => equipment[id].status !== "available" && !equipment[id].homeLoan))
      .forEach(p => tasks.push({ icon: "clipboard", title: "Discharge check", detail: `${p.ward} · MRN ••••${p.mrn}`, to: `/discharge?mrn=${p.mrn}` }));
    if (me.role === "MANAGER") {
      Object.values(patients).filter(p => p.override?.status === "requested").forEach(p => tasks.push(
        { icon: "shield", title: "Approve discharge", detail: `${p.ward} · MRN ••••${p.mrn} · asked by ${p.override.requestedBy}`, to: `/discharge?mrn=${p.mrn}` }));
      homeLoans.filter(r => r.status === "requested").forEach(r => tasks.push(
        { icon: "shield", title: "Approve home loan", detail: `${r.items.length} item${r.items.length === 1 ? "" : "s"} · MRN ••••${r.mrn} · asked by ${r.requestedBy}`, to: `/discharge?mrn=${r.mrn}` }));
    }
    return {
      user: me.name.split(" ")[0],
      team: me.team,
      tasks,
      activity: activity.slice(0, 5).map(({ icon, title, detail, time }) => ({ icon, title, detail, time })),
    };
  },

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
      .sort((a, b) => a.id.localeCompare(b.id))
      .map(e => item(e.id));
    return { items: items.slice(page * size, (page + 1) * size), page, size, total: items.length };
  },

  // Must come before /equipment/:id
  "GET /equipment/available": (_, __, ___, { category, type, search, weight }) => {
    const band = weight && weightOf(weight);
    if (weight && !band) throw new ApiError(400, `Unknown weight category ${weight}`);
    const text = search?.trim().toLowerCase();
    const matches = Object.values(equipment)
      .filter(e => !category || e.category.toLowerCase() === category.toLowerCase())
      .filter(e => !type || e.type.toLowerCase() === type.toLowerCase())
      .filter(e => !text || [e.type, e.category, e.name, e.id].some(v => v.toLowerCase().includes(text)))
      .filter(e => !band || (weightOf(details[e.id]?.weightCategory)?.maxKg ?? 0) >= band.maxKg);
    const items = matches.filter(e => e.status === "available")
      .sort((a, b) => a.location.localeCompare(b.location) || a.id.localeCompare(b.id))
      .map(e => {
        const w = weightOf(details[e.id]?.weightCategory);
        return { id: e.id, name: e.name, type: e.type, category: e.category, location: e.location, weightCategory: w?.name ?? null, maxKg: w?.maxKg ?? null };
      });
    const count = status => matches.filter(e => e.status === status).length;
    return { items, unavailable: { issued: count("issued"), inspect: count("inspect"), missing: count("missing") } };
  },

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

  "GET /weight-categories": () => WEIGHTS,

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

  // period: this-month, last-month or this-year (sample loan figures for each)
  "POST /home-loans": (_, { mrn, items, note }, me) => {
    if (!mrn?.trim()) throw new ApiError(400, "Enter the patient's MRN");
    if (!items?.length) throw new ApiError(400, "Choose at least one item to lend");
    const patient = patientFor(mrn);
    if (homeLoans.some(r => r.mrn === patient.mrn && r.status === "requested")) throw new ApiError(409, "A home loan for this patient is already waiting for a manager");
    const today = new Date().toISOString().slice(0, 10);
    const request = {
      id: nextHomeLoanId++, status: "requested", mrn: patient.mrn, ward: patient.ward, bed: patient.bed, note: note?.trim() || null,
      requestedBy: me.name, requestedAt: now(), decidedBy: null, decisionReason: null,
      items: items.map(({ equipmentId, dueOn }) => {
        const e = item(equipmentId);
        const onThisPatient = e.status === "issued" && patient.items.includes(e.id) && !e.homeLoan;
        if (e.status !== "available" && !onThisPatient) throw new ApiError(409, `${e.id} can't be lent: it is ${e.status} and not on loan to this patient`);
        if (dueOn && dueOn < today) throw new ApiError(400, "The return date can't be in the past");
        const due = dueOn || new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
        return { id: e.id, name: e.name, dueOn: due };
      }),
    };
    log(me, "clipboard", "Home loan requested", `${request.items.length} item${request.items.length === 1 ? "" : "s"} · MRN ••••${patient.mrn}`);
    if (me.role === "MANAGER") {
      lendHome(me, request);
      Object.assign(request, { status: "approved", decidedBy: me.name, decisionReason: request.note ?? "Approved by the manager who requested it" });
    }
    homeLoans.push(request);
    return request;
  },

  "GET /home-loans/pending": (_, __, me) => {
    managerOnly(me);
    return homeLoans.filter(r => r.status === "requested");
  },

  "POST /home-loans/:id/approve": ({ id }, { reason }, me) => decideHomeLoan(me, Number(id), true, reason),

  "POST /home-loans/:id/reject": ({ id }, { reason }, me) => decideHomeLoan(me, Number(id), false, reason),

  "GET /insights": (_, __, me, { period = "this-month" }) => {
    managerOnly(me);
    const loans = { "this-month": [12, 9, 86], "last-month": [31, 28, 79], "this-year": [240, 221, 83] }[period];
    if (!loans) throw new ApiError(400, "Period must be this-month, last-month or this-year");
    const now = new Date();
    const month = offset => new Date(now.getFullYear(), now.getMonth() + offset, 1).toLocaleDateString("en-IE", { month: "long" });
    const items = Object.values(equipment);
    const count = (category, ...statuses) => items.filter(e => e.category === category && statuses.includes(e.status)).length;
    return {
      period,
      month: period === "this-year" ? String(now.getFullYear()) : month(period === "last-month" ? -1 : 0),
      tracked: items.length,
      unaccounted: items.filter(e => e.status === "missing").length,
      waitlists: waitlist.length,
      readyToAllocate: withAvailability().filter(r => r.availableNow).length,
      issued: loans[0],
      returned: loans[1],
      onTime: loans[2],
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

function decideHomeLoan(me, id, approve, reason) {
  managerOnly(me);
  if (!reason?.trim()) throw new ApiError(400, "A reason is required");
  const request = homeLoans.find(r => r.id === id);
  if (!request) throw new ApiError(404, "Home loan request not found");
  if (request.status !== "requested") throw new ApiError(409, `This request has already been ${request.status}`);
  if (approve) lendHome(me, request);
  Object.assign(request, { status: approve ? "approved" : "rejected", decidedBy: me.name, decisionReason: reason.trim() });
  log(me, "clipboard", approve ? "Home loan approved" : "Home loan rejected", `MRN ••••${request.mrn}`);
  return request;
}

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

// ---------------------------------------------------------- saved demo state
// Changes made in the demo are kept in this browser, so a refresh doesn't undo
// them. Each browser has its own copy. Account → "Reset demo data" starts over.

const STORAGE_KEY = "caretrack.demo.v1";

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      equipment, details, loans, patients, activity, users, TYPES,
      homeLoans, nextHomeLoanId, waitlist, nextRequestId, nextOverrideId, walk,
    }));
  } catch { /* storage unavailable: the demo still works until the page is reloaded */ }
}

function refill(target, source) {
  if (Array.isArray(target)) target.splice(0, target.length, ...source);
  else { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, source); }
}

function loadState() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (!saved) return;
    [[equipment, saved.equipment], [details, saved.details], [loans, saved.loans], [patients, saved.patients],
      [activity, saved.activity], [users, saved.users], [TYPES, saved.TYPES]].forEach(([target, source]) => refill(target, source));
    ({ homeLoans, nextHomeLoanId, waitlist, nextRequestId, nextOverrideId, walk } = saved);
  } catch { /* unreadable: start from the sample data */ }
}

export function resetDemoData() {
  try { localStorage.removeItem(STORAGE_KEY); } catch { /* nothing saved */ }
}

loadState();

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
    if (params) {
      const result = structuredClone(handler(params, body ?? {}, me, query));
      if (method !== "GET") saveState();
      return result;
    }
  }
  throw new ApiError(404, `No route for ${method} ${pathname}`);
}
