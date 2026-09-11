import { describe, expect, it } from 'vitest';
import { shouldAutoRunFindJobs } from './dashboardAutoRun';

describe('shouldAutoRunFindJobs', () => {
  it('triggers for a freshly activated profile with no run yet and no jobs', () => {
    expect(shouldAutoRunFindJobs({ latestSearchRun: null, jobs: [] }, false)).toBe(true);
  });

  it('does not trigger before the dashboard has loaded', () => {
    expect(shouldAutoRunFindJobs(null, false)).toBe(false);
  });

  it('does not trigger once a run already exists, even if it has no jobs yet', () => {
    expect(shouldAutoRunFindJobs({ latestSearchRun: { status: 'FAILED' }, jobs: [] }, false)).toBe(false);
  });

  it('does not trigger when jobs are already present', () => {
    expect(shouldAutoRunFindJobs({ latestSearchRun: null, jobs: [{ id: 1 }] }, false)).toBe(false);
  });

  it('does not trigger twice in the same session', () => {
    expect(shouldAutoRunFindJobs({ latestSearchRun: null, jobs: [] }, true)).toBe(false);
  });
});
