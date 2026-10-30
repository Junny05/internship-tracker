const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  constructor(message, status, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

async function request(path, { method = "GET", body, token } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return null;

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    throw new ApiError(data?.message || `Request failed (${res.status})`, res.status, data?.details);
  }

  return data;
}

export const api = {
  register: (email, password) =>
    request("/auth/register", { method: "POST", body: { email, password } }),

  login: (email, password) =>
    request("/auth/login", { method: "POST", body: { email, password } }),

  // GET /applications returns a Spring Data Page ({content, totalElements, ...}),
  // not a bare array - unwrap it here so every caller keeps getting a plain array.
  listApplications: (token, status) =>
    request(`/applications${status ? `?status=${status}` : ""}`, { token }).then(
      (page) => page.content,
    ),

  createApplication: (token, payload) =>
    request("/applications", { method: "POST", body: payload, token }),

  updateApplication: (token, id, payload) =>
    request(`/applications/${id}`, { method: "PUT", body: payload, token }),

  updateStatus: (token, id, status) =>
    request(`/applications/${id}/status`, { method: "PATCH", body: { status }, token }),

  deleteApplication: (token, id) =>
    request(`/applications/${id}`, { method: "DELETE", token }),

  listSavedSearches: (token) => request("/searches", { token }),

  createSavedSearch: (token, payload) =>
    request("/searches", { method: "POST", body: payload, token }),

  deleteSavedSearch: (token, id) =>
    request(`/searches/${id}`, { method: "DELETE", token }),
};
