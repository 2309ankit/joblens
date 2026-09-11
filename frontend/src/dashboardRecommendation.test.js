import { describe, expect, it } from 'vitest';
import { partitionRecommendedJobs } from './dashboardRecommendation';

describe('partitionRecommendedJobs', () => {
  it('splits qualifying jobs from non-qualifying jobs', () => {
    const jobs = [
      { id: 1, qualifies_recommended: true },
      { id: 2, qualifies_recommended: false },
      { id: 3, qualifies_recommended: true },
    ];

    const { recommended, exploreMore } = partitionRecommendedJobs(jobs);

    expect(recommended.map(job => job.id)).toEqual([1, 3]);
    expect(exploreMore.map(job => job.id)).toEqual([2]);
  });

  it('handles an empty job list', () => {
    expect(partitionRecommendedJobs([])).toEqual({ recommended: [], exploreMore: [] });
  });

  it('puts every job in recommended when all qualify', () => {
    const jobs = [{ id: 1, qualifies_recommended: true }, { id: 2, qualifies_recommended: true }];

    expect(partitionRecommendedJobs(jobs)).toEqual({ recommended: jobs, exploreMore: [] });
  });

  it('puts every job in exploreMore when none qualify', () => {
    const jobs = [{ id: 1, qualifies_recommended: false }, { id: 2, qualifies_recommended: false }];

    expect(partitionRecommendedJobs(jobs)).toEqual({ recommended: [], exploreMore: jobs });
  });
});
