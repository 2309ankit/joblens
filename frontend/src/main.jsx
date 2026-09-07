import { useCallback, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowRight, BriefcaseBusiness, ExternalLink, Play, RefreshCw, Sparkles } from 'lucide-react';
import { Toaster, toast } from 'sonner';
import { ApiError, dashboard, findJobs, restartFindJobs, saveApplication } from './api';
import { displayMessage } from './messages';
import './styles.css';

function Dashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);
  const refresh = useCallback(async () => {
    setError('');
    try { setData(await dashboard()); }
    catch (failure) {
      if (failure instanceof ApiError && failure.code === 'WORKSPACE_NOT_READY') window.location.assign('/setup');
      else setError(displayMessage(failure));
    }
  }, []);
  useEffect(() => { refresh(); }, [refresh]);
  async function run(action, success) {
    setWorking(true); setError('');
    try { await action(); toast.success(success); await refresh(); }
    catch (failure) { toast.error(displayMessage(failure)); }
    finally { setWorking(false); }
  }
  if (!data && !error) return <main className="app-shell loading"><Brand /><div className="loading-orbit" /><p role="status">Mapping your opportunity landscape…</p></main>;
  if (!data) return <main className="app-shell"><Brand /><section className="surface error-state"><p className="eyebrow">Dashboard unavailable</p><h1>We couldn’t load your workspace.</h1><p role="alert">{error}</p><button className="button primary" onClick={refresh}>Try again</button></section></main>;
  const runDetail = data.latestSearchRun;
  const runRecord = runDetail?.run;
  const jobCount = data.jobs.length;
  const featuredJob = data.jobs[0];
  const topMatches = data.jobs.slice(0, 8);
  const moreMatches = data.jobs.slice(8);
  return <main className="app-shell">
    <Toaster theme="dark" richColors closeButton position="top-right" />
    <header className="topbar"><Brand compact /><nav aria-label="Workspace navigation"><a className="active" href="/dashboard">Browse</a><a href="/setup">Profile</a><a href="/applications">My list</a></nav></header>
    {error && <p className="inline-error" role="alert">{error}</p>}
    {featuredJob ? <FeaturedJob job={featuredJob} working={working} onSave={() => run(() => saveApplication(featuredJob.id), 'Job saved to your application tracker.')} /> : <section className="empty-feature"><p className="eyebrow"><Sparkles size={13} aria-hidden="true" /> Your job feed</p><h1>Ready when you are.</h1><p>Run a search to build a personalised selection of explainable job matches.</p><button className="button primary" disabled={working} onClick={() => run(findJobs, 'Your Find Jobs run has started.')}>{working ? 'Searching…' : 'Find new roles'} <Play size={15} fill="currentColor" aria-hidden="true" /></button></section>}
    <section className="watch-stats" aria-label="Workspace summary"><Metric value={jobCount} label="For you" accent="violet" /><Metric value={data.applicationCount} label="My list" accent="cyan" /><Metric value={data.openFollowUpCount} label="To follow up" accent="amber" /></section>
    {runRecord && <section className="surface run-panel"><SectionTitle eyebrow="Search intelligence" title="Latest market pulse" action={runRecord.batchStatus === 'FAILED' && <button className="button secondary" disabled={working} onClick={() => run(() => restartFindJobs(runRecord.jobExecutionId), 'The failed run has been restarted.')}>Restart run</button>} /><div className="run-overview"><div><span className={`status-dot ${runRecord.outcome?.toLowerCase()}`} /><strong>{runRecord.outcome}</strong><p>{runRecord.outcome === 'PARTIAL' ? 'Some sources completed before another failed. Their results are still here.' : 'Source activity and ranking progress for your latest search.'}</p></div><div className="run-badge"><span>Batch status</span><strong>{runRecord.batchStatus}</strong></div></div><SourceRuns sources={runDetail.sources} /></section>}
    <section id="ranked-jobs" className="rail-section"><SectionTitle eyebrow="Picked for you" title="Top matches" action={<button className="button primary compact-action" disabled={working} onClick={() => run(findJobs, 'Your Find Jobs run has started.')}>{working ? <RefreshCw className="spin" size={14} aria-hidden="true" /> : <Play size={14} fill="currentColor" aria-hidden="true" />}{working ? 'Searching…' : 'Refresh matches'}</button>} />{jobCount === 0 ? <EmptyJobs /> : <div className="job-rail">{topMatches.map((job, index) => <JobCard key={job.id} job={job} rank={index + 1} working={working} onSave={() => run(() => saveApplication(job.id), 'Job saved to your application tracker.')} />)}</div>}</section>
    {moreMatches.length > 0 && <section className="rail-section"><SectionTitle eyebrow="Keep exploring" title="More roles for you" action={<span className="count-pill">{moreMatches.length} more</span>} /><div className="job-rail">{moreMatches.map((job, index) => <JobCard key={job.id} job={job} rank={index + 9} working={working} onSave={() => run(() => saveApplication(job.id), 'Job saved to your application tracker.')} />)}</div></section>}
    {data.portalSearchLinks.length > 0 && <section className="surface portal-panel"><SectionTitle eyebrow="Expand your search" title="Official portal shortcuts" /><p className="muted">Focused queries built from your profile. External listings are never copied into or scored by JobLens.</p><div className="portal-grid">{data.portalSearchLinks.map((link) => <a className="portal-card" key={`${link.portal}-${link.region}-${link.query}`} href={link.url} target="_blank" rel="noreferrer"><span className="portal-mark">↗</span><span className="portal-name">{link.portal}</span><span className="portal-region">{link.region}</span><strong>{link.intent}</strong><code>{link.query}</code><span className="portal-open">Open search →</span></a>)}</div></section>}
    {data.insights.length > 0 && <section className="surface insight-panel"><SectionTitle eyebrow="Market signal" title="Weekly intelligence" /><div className="insight-grid">{data.insights.map((item) => <article className="insight-card" key={`${item.week_start}-${item.source}`}><span>{item.source}</span><strong>{item.job_count}</strong><p>open roles · {item.remote_job_count} remote</p><small>Week of {item.week_start}</small></article>)}</div></section>}
  </main>;
}
function Brand({ compact = false }) { return <a className={`brand ${compact ? 'compact' : ''}`} href="/dashboard" aria-label="JobLens dashboard"><span className="brand-mark"><BriefcaseBusiness size={17} aria-hidden="true" /></span><span>JobLens<small>career intelligence</small></span></a>; }
function Metric({ value, label, accent }) { return <article className={`metric ${accent}`}><strong>{value}</strong><span>{label}</span><i aria-hidden="true" /></article>; }
function SectionTitle({ eyebrow, title, action }) { return <div className="section-title"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div>{action}</div>; }
function EmptyJobs() { return <div className="empty-jobs"><span><Sparkles size={22} aria-hidden="true" /></span><h3>Your first ranked opportunities will land here.</h3><p>Complete your profile, then run a search to map the market around your goals.</p><a className="button secondary" href="/setup">Refine profile</a></div>; }
function SourceRuns({ sources }) { return <div className="source-grid">{sources.map((source) => <article className="source-card" key={source.searchProfileId}><div><span className="source-label">{source.source}</span><strong>{source.location || source.countryCode || 'Configured market'}</strong></div><span className={`source-status ${source.status?.toLowerCase()}`}>{source.status}</span><dl><div><dt>Received</dt><dd>{source.recordsReceived}</dd></div><div><dt>Scored</dt><dd>{source.scoredRecords}</dd></div><div><dt>Pages</dt><dd>{source.pagesFetched}/{source.pagesAttempted}</dd></div></dl>{source.failureReason && <p className="source-error">{source.failureReason}</p>}</article>)}</div>; }
function FeaturedJob({ job, working, onSave }) { return <section className="featured"><div className="featured-art" aria-hidden="true"><span>{job.source}</span><strong>{job.score}</strong><small>match score</small></div><div className="featured-copy"><p className="eyebrow"><Sparkles size={13} aria-hidden="true" /> Featured for you</p><h1>{job.title}</h1><p className="featured-company">{job.company || 'Company not disclosed'} <span>·</span> {job.location || 'Location flexible'}</p><div className="job-meta"><span>{job.best_target_role_name || 'Universal match'}</span>{job.calibration_pack_code && <span>{job.calibration_pack_code}</span>}</div><p className="featured-note">A high-ranking role selected from your latest search. Open it to review the complete listing and score evidence.</p><div className="hero-actions"><a className="button primary" href={`/api/job-views/${job.id}/open`} target="_blank" rel="noreferrer"><Play size={15} fill="currentColor" aria-hidden="true" /> Open role</a>{job.application_id ? <a className="button ghost" href="/applications">In your list <ArrowRight size={15} aria-hidden="true" /></a> : <button className="button ghost" disabled={working} onClick={onSave}>+ My list</button>}</div></div></section>; }
function JobCard({ job, rank, working, onSave }) { return <article className="job-card"><div className="job-art"><span>{String(rank).padStart(2, '0')}</span><strong>{job.source}</strong><i>{job.score}</i></div><div className="job-main"><span className="source-label">{job.best_target_role_name || 'Recommended role'}</span><h3>{job.title}</h3><p>{job.company || 'Company not disclosed'} <span>·</span> {job.location || 'Location flexible'}</p><div className="job-actions"><a href={`/api/job-views/${job.id}/open`} target="_blank" rel="noreferrer">View role <ExternalLink size={13} aria-hidden="true" /></a>{job.application_id ? <a href="/applications">My list <ArrowRight size={13} aria-hidden="true" /></a> : <button className="text-button" disabled={working} onClick={onSave}>+ Save</button>}</div></div></article>; }
createRoot(document.getElementById('root')).render(<Dashboard />);
