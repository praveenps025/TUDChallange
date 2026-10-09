// Sample data and a fake backend, used until VITE_API_URL is set.
// The routes here are the API the screens expect, so the real backend can follow them:
//
//   GET  /home                        greeting, to-do tasks, recent activity
//   GET  /equipment/:id               one item
//   POST /equipment/:id/return        { clean: true|false }
//   POST /equipment/:id/inspect
//   GET  /waitlist                    requests, oldest first
//   POST /waitlist/:id/allocate       { equipmentId }
//   GET  /audit                       current audit walk
//   GET  /discharge/:mrn              patient and their loaned items
//   POST /discharge/:mrn/override     { reason }
//   GET  /insights                    monthly overview
//
//   POST /auth/login                  { username, password } -> { token, expiresAt, user }
//   GET  /auth/me                     signed-in user
//   POST /auth/change-password        { currentPassword, newPassword }
//   GET  /users, POST /users, PUT /users/:id, POST /users/:id/reset-password   (managers only)
//
// Item status is one of: available, issued, inspect, missing.
// In sample-data mode any password works for the users below.
import { ApiError } from "./api";

const equipment = {
  "CU-024": { id: "CU-024", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "issued", location: "Ward 2B", note: "Loaned 28 Sep · Due today" },
  "CU-009": { id: "CU-009", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "available", location: "OT store", note: "Inspected today" },
  "CU-031": { id: "CU-031", name: "Pressure cushion", type: "Pressure cushion", category: "Cushions", status: "missing", location: "Ward 3B", note: "Last seen 29 Sep" },
  "WC-018": { id: "WC-018", name: "Transit wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "missing", location: "Ward 4C", note: "Last seen 30 Sep" },
  "WC-026": { id: "WC-026", name: "Wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "issued", location: "Ward 5A", note: "Loaned today" },
  "WC-031": { id: "WC-031", name: "Wheelchair", type: "Wheelchair", category: "Wheelchairs", status: "available", location: "OT store", note: "Inspected 6 Oct" },
  "SB-011": { id: "SB-011", name: "Sliding board", type: "Sliding board", category: "Sliding boards", status: "inspect", location: "OT store", note: "Returned today · Not yet inspected" },
  "SB-006": { id: "SB-006", name: "Sliding board", type: "Sliding board", category: "Sliding boards", status: "missing", location: "OT store", note: "Last seen 1 Oct" },
};

let waitlist = [
  { id: 1, ward: "Ward 3A", type: "Pressure cushion", days: 6 },
  { id: 2, ward: "Ward 5B", type: "Wheelchair", days: 5 },
  { id: 3, ward: "Ward 2C", type: "Sliding board", days: 4 },
  { id: 4, ward: "Ward 4A", type: "Pressure cushion", days: 3 },
  { id: 5, ward: "Ward 1B", type: "Wheelchair", days: 2 },
  { id: 6, ward: "Ward 6A", type: "Pressure cushion", days: 1 },
  { id: 7, ward: "Ward 2A", type: "Wheelchair", days: 1 },
];

const activity = [
  { icon: "in", title: "Sliding board returned", detail: "SB-011 · OT store", time: "09:32" },
  { icon: "out", title: "Wheelchair issued", detail: "WC-026 · Ward 5A", time: "09:18" },
  { icon: "clipboard", title: "Cushion inspected", detail: "CU-009 · Available", time: "08:56" },
];

const patients = {
  "4821": { mrn: "4821", ward: "Ward 4C", bed: "06", items: ["WC-018"], override: null },
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
const log = (icon, title, detail) => activity.unshift({ icon, title, detail, time: now() });

function item(id) {
  const found = equipment[id.toUpperCase()];
  if (!found) throw new ApiError(404, `No equipment with ID ${id}`);
  return found;
}

// Oldest requests first get the available items of their type
function withAvailability() {
  const left = {};
  return waitlist.map(request => {
    left[request.type] ??= Object.values(equipment).filter(e => e.type === request.type && e.status === "available").length;
    const availableNow = left[request.type] > 0;
    if (availableNow) left[request.type]--;
    return { ...request, availableNow };
  });
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
    team: "OT equipment team",
    tasks: [
      { icon: "layers", title: "Allocate returned cushion", detail: "CU-024 · Ward 3A is next", to: "/return/CU-024" },
      { icon: "clock", title: "Loan due", detail: "WC-026 · Ward 5A · Due today", to: "/item/WC-026" },
      { icon: "eyeoff", title: "Unseen items", detail: "3 items · Last seen over 7 days ago", to: "/audit" },
      { icon: "clipboard", title: "Discharge check", detail: "Ward 4C · MRN ••••4821", to: "/discharge?mrn=4821" },
    ],
    activity: activity.slice(0, 5),
  }),

  "GET /equipment/:id": ({ id }) => item(id),

  "POST /equipment/:id/return": ({ id }, { clean }) => {
    const e = item(id);
    if (e.status !== "issued") throw new ApiError(409, `${e.id} is not issued`);
    Object.assign(e, { status: clean ? "available" : "inspect", location: "OT store", note: clean ? "Returned today · Checked" : "Returned today · Needs inspection" });
    log("in", `${e.name} returned`, `${e.id} · OT store`);
    return e;
  },

  "POST /equipment/:id/inspect": ({ id }) => {
    const e = item(id);
    if (e.status !== "inspect") throw new ApiError(409, `${e.id} doesn't need inspection`);
    Object.assign(e, { status: "available", note: "Inspected today" });
    log("clipboard", `${e.name} inspected`, `${e.id} · Available`);
    return e;
  },

  "GET /waitlist": () => withAvailability(),

  "POST /waitlist/:id/allocate": ({ id }, { equipmentId }) => {
    const request = waitlist.find(r => r.id === Number(id));
    const e = item(equipmentId);
    if (!request) throw new ApiError(404, "Request not found");
    if (e.status !== "available" || e.type !== request.type) throw new ApiError(409, `${e.id} can't be allocated to this request`);
    Object.assign(e, { status: "issued", location: request.ward, note: "Loaned today" });
    waitlist = waitlist.filter(r => r !== request);
    log("out", `${e.name} issued`, `${e.id} · ${request.ward}`);
    return e;
  },

  "GET /audit": () => ({
    area: "Ward 3 · OT store",
    started: "09:10",
    by: users[0].name,
    total: 41,
    found: 34,
    missing: Object.values(equipment).filter(e => e.status === "missing"),
  }),

  "GET /discharge/:mrn": ({ mrn }) => {
    const patient = patients[mrn.replace(/\D/g, "").slice(-4)];
    if (!patient) throw new ApiError(404, "No patient found with that MRN");
    return { ...patient, items: patient.items.map(item) };
  },

  "POST /discharge/:mrn/override": ({ mrn }, { reason }) => {
    const patient = patients[mrn];
    if (!patient) throw new ApiError(404, "No patient found with that MRN");
    if (!reason?.trim()) throw new ApiError(400, "A reason is required");
    patient.override = { reason: reason.trim(), status: "requested" };
    return { ...patient, items: patient.items.map(item) };
  },

  "GET /insights": () => ({
    month: "October",
    tracked: 155,
    unaccounted: Object.values(equipment).filter(e => e.status === "missing").length,
    waitlists: waitlist.length,
    readyToAllocate: withAvailability().filter(r => r.availableNow).length,
    onTime: 86,
    categories: [
      { name: "Wheelchairs", available: 18, issued: 40, check: 4, waiting: waitlist.filter(r => r.type === "Wheelchair").length, advice: "Redistribute" },
      { name: "Cushions", available: 5, issued: 50, check: 7, waiting: waitlist.filter(r => r.type === "Pressure cushion").length, advice: "Consider buying" },
      { name: "Sliding boards", available: 12, issued: 15, check: 2, waiting: waitlist.filter(r => r.type === "Sliding board").length, advice: "Redistribute" },
    ],
  }),
};

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

  for (const [route, handler] of Object.entries(routes)) {
    const [routeMethod, pattern] = route.split(" ");
    const params = routeMethod === method && match(pattern, path);
    if (params) return structuredClone(handler(params, body ?? {}, me));
  }
  throw new ApiError(404, `No route for ${method} ${path}`);
}
