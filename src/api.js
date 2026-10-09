// Every screen gets its data through these two functions.
// With VITE_API_URL unset they use the sample data in mockData.js;
// set it (see .env.example) and the same calls go to the real backend.
import { mockRequest } from "./mockData";

const API_URL = import.meta.env.VITE_API_URL;

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  if (!API_URL) return mockRequest(method, path, body);

  const response = await fetch(API_URL + path, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw new ApiError(response.status, (await response.text()) || response.statusText);
  return response.json();
}

export const get = path => request("GET", path);
export const post = (path, body) => request("POST", path, body);
