// Every screen gets its data through these functions.
// With VITE_API_URL unset they use the sample data in mockData.js;
// set it (see .env.example) and the same calls go to the real backend.
import { mockRequest } from "./mockData";

const API_URL = import.meta.env.VITE_API_URL;
export const usingMock = !API_URL;

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// The session token lives in sessionStorage: one per tab, gone when the tab
// closes, never in localStorage. Storage can be unavailable, so never throw.
const SESSION_KEY = "equiptrack.session";

export function getSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(SESSION_KEY));
    return session && Date.parse(session.expiresAt) > Date.now() ? session : null;
  } catch {
    return null;
  }
}

export function saveSession({ token, expiresAt }) {
  try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token, expiresAt })); } catch { /* sign-in lasts for this page only */ }
}

export function clearSession() {
  try { sessionStorage.removeItem(SESSION_KEY); } catch { /* nothing stored */ }
}

// Set by the auth context: called when the server says the session is over
let unauthorized = () => {};
export function onUnauthorized(handler) {
  unauthorized = handler;
}

const EXPIRED = "Your session has expired. Please sign in again.";

async function request(method, path, body) {
  const signingIn = path === "/auth/login";
  const session = getSession();
  if (!session && !signingIn) {
    clearSession();
    unauthorized(EXPIRED);
    throw new ApiError(401, EXPIRED);
  }

  try {
    return API_URL ? await send(method, path, body, session?.token) : await mockRequest(method, path, body, session?.token);
  } catch (error) {
    if (error.status === 401 && !signingIn) {
      clearSession();
      unauthorized(error.message);
    }
    throw error;
  }
}

async function send(method, path, body, token) {
  const headers = {};
  if (body) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(API_URL + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  if (!response.ok) throw new ApiError(response.status, text || response.statusText);
  return text ? JSON.parse(text) : null; // 204 has no body
}

export const get = path => request("GET", path);
export const post = (path, body) => request("POST", path, body);
export const put = (path, body) => request("PUT", path, body);
export const del = path => request("DELETE", path);
