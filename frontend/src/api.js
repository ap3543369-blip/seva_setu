/* REST client for the Spring Boot backend (session cookie, JSON; FormData for photo upload). */
export class ApiError extends Error {
  constructor(status, message, errors) {
    super(message);
    this.status = status;
    this.errors = errors || {};
  }
}

/** api('/donations/mine') · api('/auth/login', { method: 'POST', body: {...} }) · api('/donations', { method: 'POST', form }) */
export async function api(path, opts = {}) {
  const init = { method: opts.method || 'GET', credentials: 'same-origin', headers: { Accept: 'application/json' } };
  if (opts.form) init.body = opts.form;
  else if (opts.body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(opts.body);
  }
  let res;
  try {
    res = await fetch('/api' + path, init);
  } catch (e) {
    throw new ApiError(0, 'Cannot reach the server. Please check that FoodShare is running.');
  }
  if (res.status === 204) return null;
  let data = null;
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (!res.ok) throw new ApiError(res.status, (data && data.message) || `Request failed (${res.status}).`, data && data.errors);
  return data;
}

export const post = (path, body) => api(path, { method: 'POST', body: body === undefined ? {} : body });
export const put = (path, body) => api(path, { method: 'PUT', body });
export const patch = (path, body) => api(path, { method: 'PATCH', body });
export const del = path => api(path, { method: 'DELETE' });
