import { useEffect, useMemo, useState } from 'react';
import { Check, FileUp, Plus, Sparkles, X } from 'lucide-react';
import { ApiError, request } from './api';
import { displayMessage } from './messages';
import { useWorkspaceNavigation } from './navigation';

const defaults = { targetDomains: '', primaryLocation: 'Singapore', keywords: '', maxPages: 3, employmentPreference: 'PERMANENT', workPreference: 'REMOTE,HYBRID,ONSITE' };

export function Setup() {
  const { navigate } = useWorkspaceNavigation();
  const [profile, setProfile] = useState(null);
  const [countries, setCountries] = useState([]);
  const [intelligence, setIntelligence] = useState({ skillSuggestions: [], roleSuggestions: [] });
  const [readiness, setReadiness] = useState(null);
  const [form, setForm] = useState(defaults);
  const [skills, setSkills] = useState([]);
  const [roles, setRoles] = useState([]);
  const [country, setCountry] = useState('SG');
  const [location, setLocation] = useState('Singapore');
  const [skillInput, setSkillInput] = useState('');
  const [roleInput, setRoleInput] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);
  const [acknowledgeReadiness, setAcknowledgeReadiness] = useState(false);

  const load = async () => {
    setError('');
    const countryList = await request('/api/candidate-profile/countries');
    setCountries(countryList);
    try {
      const current = await request('/api/candidate-profile');
      const [insight, assessment, preference] = await Promise.all([
        request('/api/candidate-profile/intelligence'),
        request('/api/candidate-profile/readiness').catch(() => null),
        request('/api/candidate-profile/preferences').catch(() => null),
      ]);
      setProfile(current); setIntelligence(insight); setReadiness(assessment);
      setSkills(current.skills || insight.skillSuggestions.map(item => item.name));
      setRoles(current.targetRoles?.length ? current.targetRoles : insight.roleSuggestions.slice(0, 1).map(item => item.name));
      if (preference) {
        setForm(preference);
        const target = preference.searchMarkets?.[0];
        if (target) { setCountry(target.countryCode); setLocation(target.location); }
      }
    } catch (failure) {
      if (!(failure instanceof ApiError) || failure.code !== 'PROFILE_NOT_READY') setError(displayMessage(failure));
    }
  };
  useEffect(() => { load().catch(failure => setError(displayMessage(failure))); }, []);
  const selectedCountry = useMemo(() => countries.find(item => item.code === country), [countries, country]);
  const add = (value, values, setValues, limit = Infinity) => {
    const clean = value.trim().replace(/\s+/g, ' ');
    if (clean && values.length < limit && !values.some(item => item.toLowerCase() === clean.toLowerCase())) setValues([...values, clean]);
  };
  const upload = async event => {
    event.preventDefault(); const file = event.currentTarget.file.files[0]; if (!file) return;
    setWorking(true); setError('');
    try { const data = new FormData(); data.append('file', file); await request('/api/candidate-profile/resume', { method: 'POST', body: data }); setMessage('Resume read. Review the suggested profile below.'); await load(); }
    catch (failure) { setError(displayMessage(failure)); } finally { setWorking(false); }
  };
  const activate = async event => {
    event.preventDefault(); setWorking(true); setError('');
    try {
      await request('/api/candidate-profile/activate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...form, skills, targetRoles: roles, searchMarkets: [{ countryCode: country, location }], acknowledgeReadiness }) });
      navigate('/dashboard');
    } catch (failure) { setError(displayMessage(failure)); } finally { setWorking(false); }
  };
  const changeCountry = code => { setCountry(code); const next = countries.find(item => item.code === code); if (next) { setLocation(next.name); setForm({ ...form, primaryLocation: next.name }); } };
  return <main className="app-shell setup-shell"><header className="topbar"><a className="brand" href="/dashboard"><span className="brand-mark"><Sparkles size={17} /></span><span>JobLens<small>career intelligence</small></span></a><nav aria-label="Workspace navigation"><a href="/dashboard">Browse</a><a className="active" href="/setup">Profile</a><a href="/applications">My list</a></nav></header>
    {error && <p className="inline-error" role="alert">{error}</p>}{message && <p className="inline-success" role="status">{message}</p>}
    <section className="setup-hero"><p className="eyebrow">Profile setup</p><h1>Tell us where you want to go. We’ll handle the busywork.</h1><p>Upload a resume, review visible suggestions, choose a role and market, then activate your search profile.</p></section>
    {!profile ? <ResumeUpload working={working} onSubmit={upload} /> : <><ResumeUpload compact working={working} onSubmit={upload} /><form className="setup-flow" onSubmit={activate}>
      <section className="surface setup-card"><p className="eyebrow">Step 2 · direction</p><h2>Use your suggested profile</h2><p className="muted">Suggestions are based on resume evidence. You can change any of them before activation.</p><ChipEditor title="Skills" values={skills} setValues={setSkills} input={skillInput} setInput={setSkillInput} add={() => add(skillInput, skills, setSkills)} suggestions={intelligence.skillSuggestions.map(item => item.name)} /><ChipEditor title="Target roles" values={roles} setValues={setRoles} input={roleInput} setInput={setRoleInput} add={() => add(roleInput, roles, setRoles, 3)} suggestions={intelligence.roleSuggestions.map(item => item.name)} limit={3} />
      </section>
      <section className="surface setup-card"><p className="eyebrow">Step 3 · market</p><h2>Where should we look?</h2><div className="setup-grid"><label>Search country<select value={country} onChange={event => changeCountry(event.target.value)}>{countries.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select></label><label>City or region<input value={location} onChange={event => setLocation(event.target.value)} required /></label></div><p className="muted">{selectedCountry?.capabilityExplanation || 'Choose a supported integrated market.'}</p></section>
      {readiness && <section className="surface setup-card readiness-card"><p className="eyebrow">Resume readability · {readiness.score}/100</p><h2>{readiness.status === 'REVIEW_REQUIRED' ? 'Review your resume before activation' : 'Your resume is ready for review'}</h2>{readiness.findings.filter(item => item.severity !== 'PASS').map(item => <p className="muted" key={item.code}><strong>{item.message}</strong> {item.remediation}</p>)}{readiness.acknowledgementRequired && <label className="acknowledgement"><input type="checkbox" checked={acknowledgeReadiness} onChange={event => setAcknowledgeReadiness(event.target.checked)} />I reviewed these machine-readability warnings and want to continue.</label>}</section>}
      <details className="surface setup-card"><summary>Advanced preferences</summary><div className="setup-grid"><label>Current location<input value={form.primaryLocation} onChange={event => setForm({ ...form, primaryLocation: event.target.value })} required /></label><label>Preferred sectors (optional)<input value={form.targetDomains || ''} onChange={event => setForm({ ...form, targetDomains: event.target.value })} /></label></div></details>
      <button className="button primary activate" disabled={working || !skills.length || !roles.length || (readiness?.acknowledgementRequired && !acknowledgeReadiness)}>{working ? 'Activating…' : <><Check size={16}/> Use suggested profile</>}</button>
    </form></>}</main>;
}

function ResumeUpload({ compact = false, working, onSubmit }) {
  return <section className="surface setup-card"><p className="eyebrow">{compact ? 'Resume source' : 'Step 1 · resume'}</p><h2>{compact ? 'Update your resume' : 'Start with your resume'}</h2><p className="muted">PDF, DOC, or DOCX up to 5 MB. JobLens reads it for review; the original file is not retained.</p><form onSubmit={onSubmit}><label className="file-picker"><FileUp size={18}/><span><strong>{compact ? 'Choose a newer resume' : 'Choose resume'}</strong><small>PDF, DOC, or DOCX</small></span><input name="file" type="file" accept=".pdf,.doc,.docx" required /></label><button className="button secondary" disabled={working}>{working ? 'Reading resume…' : compact ? 'Replace suggestions' : 'Read my resume'}</button></form></section>;
}

function ChipEditor({ title, values, setValues, input, setInput, add, suggestions, limit }) {
  return <div className="chip-editor"><div className="editor-heading"><h3>{title}</h3>{limit && <span>{values.length}/{limit}</span>}</div><div className="chips">{values.map(value => <span className="chip" key={value}>{value}<button type="button" aria-label={`Remove ${value}`} onClick={() => setValues(values.filter(item => item !== value))}><X size={13}/></button></span>)}</div><div className="quick-add"><input value={input} onChange={event => setInput(event.target.value)} placeholder={`Add a ${title.toLowerCase().slice(0, -1)}`} /><button className="button secondary" type="button" onClick={add}><Plus size={14}/> Add</button></div>{suggestions.length > 0 && <div className="suggestion-chips">{suggestions.filter(value => !values.includes(value)).slice(0, 6).map(value => <button key={value} type="button" onClick={() => { if (!limit || values.length < limit) setValues([...values, value]); }}>{value}</button>)}</div>}</div>;
}
