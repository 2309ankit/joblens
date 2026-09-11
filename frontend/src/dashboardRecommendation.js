export function partitionRecommendedJobs(jobs) {
  const recommended = jobs.filter(job => job.qualifies_recommended);
  const exploreMore = jobs.filter(job => !job.qualifies_recommended);
  return { recommended, exploreMore };
}
