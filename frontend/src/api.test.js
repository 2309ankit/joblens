import { describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './api';
import { displayMessage } from './messages';

describe('request', () => {
  it('keeps the server error code and safe message for the UI', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false, status: 409,
      json: () => Promise.resolve({ error: 'WORKSPACE_NOT_READY', message: 'Set up your profile first.' }),
    }));
    await expect(request('/api/dashboard')).rejects.toEqual(new ApiError('Set up your profile first.', 409, 'WORKSPACE_NOT_READY'));
  });

  it('does not render a framework failure returned by a command endpoint', () => {
    expect(displayMessage(new ApiError('JobExecution 42 is already running', 409, 'JOB_LAUNCH_CONFLICT')))
      .toBe('A Find Jobs run is already in progress. Review the latest source run before trying again.');
  });
});
