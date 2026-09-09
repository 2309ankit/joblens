import { ApiError } from './api';

export function displayMessage(error) {
  if (error instanceof ApiError) {
    if (error.code === 'WORKSPACE_NOT_READY' || error.code === 'PROFILE_NOT_READY') {
      return 'Complete your profile before using the dashboard.';
    }
    if (error.code === 'JOB_ACTIVE') {
      return 'A Find Jobs run is already in progress. Review the latest source run before trying again.';
    }
    if (error.code === 'JOB_STALE') {
      return 'The previous search stopped updating. Restart it from the latest source run.';
    }
    if (error.code === 'JOB_ALREADY_COMPLETE') {
      return 'This search has already completed. Change your search settings before running it again.';
    }
    if (error.code === 'INVALID_PROFILE_REQUEST') {
      return error.message || 'Review the highlighted profile fields and try again.';
    }
  }
  return 'JobLens could not complete that request. Try again or review the latest source run.';
}
