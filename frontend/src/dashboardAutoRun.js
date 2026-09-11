export function shouldAutoRunFindJobs(data, alreadyAttempted) {
  return Boolean(data) && !data.latestSearchRun && data.jobs.length === 0 && !alreadyAttempted;
}
