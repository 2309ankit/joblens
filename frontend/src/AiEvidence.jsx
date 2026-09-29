function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

export function ScoreEvidence({ job }) {
  const ai = job.scoring_source === 'NVIDIA_NEBIUS';
  const deterministic = job.scoring_source === 'DETERMINISTIC';
  const summary = ai ? text(job.score_summary) : '';
  return <section className="ai-evidence" aria-label="Score evidence">
    <span className={`evidence-label${ai ? ' evidence-label-ai' : ''}`}>
      {ai ? 'NVIDIA score · Nebius' : deterministic ? 'Rule-based score' : 'Score source unavailable'}
    </span>
    {ai && <details className="evidence-details">
      <summary>Why this score</summary>
      <p>{summary || 'An AI score is available, but its explanation was not provided.'}</p>
    </details>}
    {deterministic && <p className="evidence-note">Based on JobLens matching rules.</p>}
  </section>;
}

export function QueryPlanningEvidence({ source }) {
  const ai = source.queryPlanningSource === 'LLM_NEBIUS';
  const deterministic = source.queryPlanningSource === 'DETERMINISTIC';
  const rationale = ai ? text(source.queryPlanningRationale) : '';
  return <section className="ai-evidence" aria-label="Search planning evidence">
    <span className={`evidence-label${ai ? ' evidence-label-ai' : ''}`}>
      {ai ? 'AI-assisted query · Nebius' : deterministic ? 'Rule-based query' : 'Query source unavailable'}
    </span>
    {ai && <details className="evidence-details">
      <summary>Why this query</summary>
      <p>{rationale || 'AI planning was used, but its explanation was not provided.'}</p>
      <p className="evidence-note">The planner may keep a query unchanged when previous results support it.</p>
    </details>}
  </section>;
}
