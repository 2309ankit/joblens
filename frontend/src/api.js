export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function request(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...options.headers },
    ...options,
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(body?.message || 'JobLens could not complete that request.', response.status, body?.error);
  }
  return body;
}

export const dashboard = () => request('/api/dashboard');
export const findJobs = () => request('/api/batch/find-jobs/run', { method: 'POST' });
export const restartFindJobs = (executionId) => request(`/api/batch/find-jobs/runs/${executionId}/restart`, { method: 'POST' });
export const saveApplication = (normalizedJobId) => request('/api/applications', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ normalizedJobId }),
});
