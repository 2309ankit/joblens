import { useEffect, useState } from 'react';
import { Check, FileUp, MapPin, Plus, Sparkles, X } from 'lucide-react';
import { ApiError, request } from './api';
import { displayMessage } from './messages';
import { useWorkspaceNavigation } from './navigation';

const defaults = { targetDomains: '', primaryLocation: '', keywords: '', maxPages: 3, employmentPreference: 'PERMANENT', workPreference: 'REMOTE,HYBRID,ONSITE' };
const FALLBACK_MARKET = { countryCode: 'SG', location: 'Singapore', source: 'JobLens default' };
const TIME_ZONE_COUNTRIES = new Map([
  ['Asia/Singapore', 'SG'], ['Asia/Kolkata', 'IN'], ['Asia/Calcutta', 'IN'],
  ['Pacific/Auckland', 'NZ'], ['Pacific/Chatham', 'NZ'], ['Europe/London', 'GB'],
  ['Europe/Vienna', 'AT'], ['Europe/Brussels', 'BE'], ['Europe/Zurich', 'CH'],
  ['Europe/Berlin', 'DE'], ['Europe/Madrid', 'ES'], ['Europe/Paris', 'FR'],
  ['Europe/Rome', 'IT'], ['Europe/Amsterdam', 'NL'], ['Europe/Warsaw', 'PL'],
  ['Europe/Moscow', 'RU'], ['Africa/Johannesburg', 'ZA'], ['America/Sao_Paulo', 'BR'],
  ['America/Mexico_City', 'MX'], ['America/Toronto', 'CA'], ['America/Vancouver', 'CA'],
]);

let marketSequence = 0;

function marketRow(countryCode = '', location = '') {
  marketSequence += 1;
  return { id: `market-${marketSequence}`, countryCode, location };
}

function localeCountry(languages = []) {
  for (const language of languages) {
    try {
      const region = new Intl.Locale(language).region;
      if (region) return region.toUpperCase();
    } catch {
      const match = String(language).match(/[-_]([A-Za-z]{2})\b/);
      if (match) return match[1].toUpperCase();
    }
  }
  return '';
}

function timeZoneCountry(timeZone = '') {
  if (TIME_ZONE_COUNTRIES.has(timeZone)) return TIME_ZONE_COUNTRIES.get(timeZone);
  if (timeZone.startsWith('Australia/')) return 'AU';
  if (['America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles', 'America/Phoenix', 'Pacific/Honolulu'].includes(timeZone)) return 'US';
  return '';
}

function timeZoneCity(timeZone = '') {
  const city = timeZone.split('/').at(-1)?.replaceAll('_', ' ') || '';
  return city === 'Calcutta' ? 'Kolkata' : city;
}

export function browserLocationHints() {
  let timeZone = '';
  try { timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch { /* use locale or fallback */ }
  return { languages: globalThis.navigator?.languages || [globalThis.navigator?.language].filter(Boolean), timeZone };
}

export function detectBrowserMarket(countries, hints = {}) {
  const supportedCodes = new Set(countries.map(country => country.code));
  const zoneCode = timeZoneCountry(hints.timeZone);
  const languageCode = localeCountry(hints.languages);
  const countryCode = supportedCodes.has(zoneCode) ? zoneCode : supportedCodes.has(languageCode) ? languageCode : FALLBACK_MARKET.countryCode;
  const country = countries.find(item => item.code === countryCode);
  const fromTimeZone = supportedCodes.has(zoneCode) && zoneCode === countryCode;
  return {
    countryCode,
    location: fromTimeZone ? timeZoneCity(hints.timeZone) || country?.name || FALLBACK_MARKET.location : country?.name || FALLBACK_MARKET.location,
    source: fromTimeZone ? 'browser time zone' : supportedCodes.has(languageCode) ? 'browser language' : FALLBACK_MARKET.source,
  };
}

export function normalizeSearchMarkets(markets) {
  return markets.map(({ countryCode, location }) => ({ countryCode: countryCode.trim().toUpperCase(), location: location.trim().replace(/\s+/g, ' ') }));
}

export function validateSearchMarkets(markets, countries) {
  if (!markets.length) return 'Add at least one search market.';
  if (markets.length > 10) return 'Choose no more than 10 search markets.';
  const supportedCodes = new Set(countries.map(country => country.code));
  const seen = new Set();
  for (const market of normalizeSearchMarkets(markets)) {
    if (!market.countryCode || !market.location) return 'Choose a country and enter a city or region for every search market.';
    if (!supportedCodes.has(market.countryCode)) return 'Choose a country supported by an integrated JobLens source.';
    const key = `${market.countryCode}|${market.location.toLowerCase()}`;
    if (seen.has(key)) return 'Remove the duplicate search market before activating your profile.';
    seen.add(key);
  }
  return '';
}

export function Setup() {
  const { navigate } = useWorkspaceNavigation();
  const [profile, setProfile] = useState(null);
  const [countries, setCountries] = useState([]);
  const [intelligence, setIntelligence] = useState({ skillSuggestions: [], roleSuggestions: [] });
  const [readiness, setReadiness] = useState(null);
  const [form, setForm] = useState(defaults);
  const [skills, setSkills] = useState([]);
  const [roles, setRoles] = useState([]);
  const [markets, setMarkets] = useState(() => [marketRow(FALLBACK_MARKET.countryCode, FALLBACK_MARKET.location)]);
  const [locationSource, setLocationSource] = useState(FALLBACK_MARKET.source);
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
      const detected = detectBrowserMarket(countryList, browserLocationHints());
      setProfile(current);
      setIntelligence(insight);
      setReadiness(assessment);
      setSkills(current.skills || insight.skillSuggestions.map(item => item.name));
      setRoles(current.targetRoles?.length ? current.targetRoles : insight.roleSuggestions.slice(0, 1).map(item => item.name));
      if (preference) {
        setForm({ ...defaults, ...preference });
        setMarkets(preference.searchMarkets.map(target => marketRow(target.countryCode, target.location)));
        setLocationSource('saved profile');
      } else {
        setForm({ ...defaults, primaryLocation: detected.location });
        setMarkets([marketRow(detected.countryCode, detected.location)]);
        setLocationSource(detected.source);
      }
    } catch (failure) {
      if (!(failure instanceof ApiError) || failure.code !== 'PROFILE_NOT_READY') setError(displayMessage(failure));
    }
  };

  useEffect(() => { load().catch(failure => setError(displayMessage(failure))); }, []);
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
    event.preventDefault();
    const marketError = validateSearchMarkets(markets, countries);
    if (marketError) { setError(marketError); return; }
    setWorking(true); setError('');
    try {
      await request('/api/candidate-profile/activate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...form, primaryLocation: form.primaryLocation.trim(), skills, targetRoles: roles, searchMarkets: normalizeSearchMarkets(markets), acknowledgeReadiness }) });
      navigate('/dashboard');
    } catch (failure) { setError(displayMessage(failure)); } finally { setWorking(false); }
  };
  const estimateCurrentLocation = () => {
    const detected = detectBrowserMarket(countries, browserLocationHints());
    setForm(current => ({ ...current, primaryLocation: detected.location }));
    setLocationSource(detected.source);
  };
  return <main className="app-shell setup-shell"><header className="topbar"><a className="brand" href="/dashboard"><span className="brand-mark"><Sparkles size={17} /></span><span>JobLens<small>career intelligence</small></span></a><nav aria-label="Workspace navigation"><a href="/dashboard">Browse</a><a className="active" href="/setup">Profile</a><a href="/applications">My list</a></nav></header>
    {error && <p className="inline-error" role="alert">{error}</p>}{message && <p className="inline-success" role="status">{message}</p>}
    <section className="setup-hero"><p className="eyebrow">Profile setup</p><h1>Tell us where you want to go. We’ll handle the busywork.</h1><p>Upload a resume, review visible suggestions, choose your roles and search markets, then activate your profile.</p></section>
    {!profile ? <ResumeUpload working={working} onSubmit={upload} /> : <><ResumeUpload compact working={working} onSubmit={upload} /><form className="setup-flow" onSubmit={activate}>
      <section className="surface setup-card"><p className="eyebrow">Step 2 · direction</p><h2>Use your suggested profile</h2><p className="muted">Suggestions are based on resume evidence. You can change or remove every suggestion before activation.</p><ChipEditor title="Skills" values={skills} setValues={setSkills} input={skillInput} setInput={setSkillInput} add={() => add(skillInput, skills, setSkills)} suggestions={intelligence.skillSuggestions.map(item => item.name)} /><ChipEditor title="Target roles" values={roles} setValues={setRoles} input={roleInput} setInput={setRoleInput} add={() => add(roleInput, roles, setRoles, 3)} suggestions={intelligence.roleSuggestions.map(item => item.name)} limit={3} /><label className="field-label">Preferred sectors <span>(optional)</span><input value={form.targetDomains || ''} onChange={event => setForm({ ...form, targetDomains: event.target.value })} placeholder="For example: healthcare, retail, education" /></label><p className="field-help">Leave this blank to search across sectors.</p>
      </section>
      <section className="surface setup-card"><p className="eyebrow">Step 3 · markets</p><h2>Where should we look?</h2><div className="current-location"><div><label className="field-label">Where you live now<input value={form.primaryLocation} onChange={event => { setForm({ ...form, primaryLocation: event.target.value }); setLocationSource('entered by you'); }} required /></label><p className="field-help">Used as profile context for match explanations. This does not add a search market.</p><p className="location-source">Current estimate: <strong>{locationSource}</strong>. Always check it before activation.</p></div><button className="button secondary location-button" type="button" onClick={estimateCurrentLocation}><MapPin size={14}/> Use browser estimate</button></div>
        <div className="market-heading"><div><h3>Places you want to search</h3><p className="field-help">Add up to 10 country and city/region pairs. Each row runs as an independent market search.</p></div><span>{markets.length}/10</span></div>
        <SearchMarketsEditor countries={countries} markets={markets} setMarkets={setMarkets} />
      </section>
      {readiness && <section className="surface setup-card readiness-card"><p className="eyebrow">Resume readability · {readiness.score}/100</p><h2>{readiness.status === 'REVIEW_REQUIRED' ? 'Review your resume before activation' : 'Your resume is ready for review'}</h2>{readiness.findings.filter(item => item.severity !== 'PASS').map(item => <p className="muted" key={item.code}><strong>{item.message}</strong> {item.remediation}</p>)}{readiness.acknowledgementRequired && <label className="acknowledgement"><input type="checkbox" checked={acknowledgeReadiness} onChange={event => setAcknowledgeReadiness(event.target.checked)} />I reviewed these machine-readability warnings and want to continue.</label>}</section>}
      <details className="surface setup-card advanced-preferences"><summary><span>Advanced search preferences</span><small>Optional · sensible defaults are already selected</small></summary><p className="muted">Change these only when you want tighter provider searches. Your roles and markets above remain the main search direction.</p><label className="field-label">Provider query override <span>(optional)</span><input value={form.keywords || ''} onChange={event => setForm({ ...form, keywords: event.target.value })} placeholder="Leave blank to use your roles and skills" /></label><p className="field-help">Overrides provider query text; it does not replace your selected target roles.</p><div className="setup-grid"><label>Pages per source<input type="number" min="1" max="20" value={form.maxPages} onChange={event => setForm({ ...form, maxPages: Number(event.target.value) })} required /></label><label>Employment type<select value={form.employmentPreference} onChange={event => setForm({ ...form, employmentPreference: event.target.value })}><option value="ANY">Any employment type</option><option value="PERMANENT">Permanent</option><option value="CONTRACT">Contract</option></select></label><label>Work arrangement<select value={form.workPreference} onChange={event => setForm({ ...form, workPreference: event.target.value })}><option value="REMOTE,HYBRID,ONSITE">Any arrangement</option><option value="REMOTE">Remote</option><option value="HYBRID">Hybrid</option><option value="ONSITE">On-site</option></select></label></div></details>
      <button className="button primary activate" disabled={working || !skills.length || !roles.length || (readiness?.acknowledgementRequired && !acknowledgeReadiness)}>{working ? 'Activating…' : <><Check size={16}/> Activate profile</>}</button>
    </form></>}</main>;
}

export function SearchMarketsEditor({ countries, markets, setMarkets }) {
  const update = (id, change) => setMarkets(markets.map(market => market.id === id ? { ...market, ...change } : market));
  const changeCountry = (market, countryCode) => {
    const country = countries.find(item => item.code === countryCode);
    update(market.id, { countryCode, location: country?.name || '' });
  };
  return <div className="market-editor">{markets.map((market, index) => {
    const selectedCountry = countries.find(item => item.code === market.countryCode);
    return <div className="market-row" key={market.id}><span className="market-number" aria-hidden="true">{index + 1}</span><label>Country<select aria-label={`Search country ${index + 1}`} value={market.countryCode} onChange={event => changeCountry(market, event.target.value)} required><option value="">Choose a country</option>{countries.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select></label><label>City or region<input aria-label={`City or region ${index + 1}`} value={market.location} onChange={event => update(market.id, { location: event.target.value })} required /></label><button className="remove-market" type="button" aria-label={`Remove search market ${index + 1}`} disabled={markets.length === 1} onClick={() => setMarkets(markets.filter(item => item.id !== market.id))}><X size={16}/></button>{selectedCountry && <p className="market-capability">{selectedCountry.capabilityExplanation}</p>}</div>;
  })}<button className="button secondary add-market" type="button" disabled={markets.length >= 10} onClick={() => setMarkets([...markets, marketRow()])}><Plus size={14}/> Add another market</button></div>;
}

function ResumeUpload({ compact = false, working, onSubmit }) {
  return <section className="surface setup-card"><p className="eyebrow">{compact ? 'Resume source' : 'Step 1 · resume'}</p><h2>{compact ? 'Update your resume' : 'Start with your resume'}</h2><p className="muted">PDF, DOC, or DOCX up to 5 MB. JobLens reads it for review; the original file is not retained.</p><form onSubmit={onSubmit}><label className="file-picker"><FileUp size={18}/><span><strong>{compact ? 'Choose a newer resume' : 'Choose resume'}</strong><small>PDF, DOC, or DOCX</small></span><input name="file" type="file" accept=".pdf,.doc,.docx" required /></label><button className="button secondary" disabled={working}>{working ? 'Reading resume…' : compact ? 'Replace suggestions' : 'Read my resume'}</button></form></section>;
}

function ChipEditor({ title, values, setValues, input, setInput, add, suggestions, limit }) {
  return <div className="chip-editor"><div className="editor-heading"><h3>{title}</h3>{limit && <span>{values.length}/{limit}</span>}</div><div className="chips">{values.map(value => <span className="chip" key={value}>{value}<button type="button" aria-label={`Remove ${value}`} onClick={() => setValues(values.filter(item => item !== value))}><X size={13}/></button></span>)}</div><div className="quick-add"><input value={input} onChange={event => setInput(event.target.value)} placeholder={`Add a ${title.toLowerCase().slice(0, -1)}`} /><button className="button secondary" type="button" onClick={add}><Plus size={14}/> Add</button></div>{suggestions.length > 0 && <div className="suggestion-chips">{suggestions.filter(value => !values.includes(value)).slice(0, 6).map(value => <button key={value} type="button" onClick={() => { if (!limit || values.length < limit) setValues([...values, value]); }}>{value}</button>)}</div>}</div>;
}
