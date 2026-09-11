import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, FileText, FileUp, MapPin, Plus, RotateCcw, Search, Sparkles, X } from 'lucide-react';
import { ApiError, request } from './api';
import { displayMessage } from './messages';
import { useWorkspaceNavigation } from './navigation';

const defaults = { primaryLocation: '', keywords: '', maxPages: 3, employmentPreference: 'PERMANENT', workPreference: 'REMOTE,HYBRID,ONSITE' };
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

function normalizedKey(value = '') {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}

function savedDraft(profileId) {
  try {
    return JSON.parse(globalThis.sessionStorage?.getItem(`joblens.setup.draft.${profileId}`) || 'null');
  } catch {
    return null;
  }
}

export function addDistinctValue(values, value, limit = Infinity) {
  const clean = value.trim().replace(/\s+/g, ' ');
  if (!clean || values.length >= limit || values.some(item => normalizedKey(item) === normalizedKey(clean))) return values;
  return [...values, clean];
}

export function catalogChoices(options, values, query) {
  const available = options.filter(option => !values.some(value => normalizedKey(value) === normalizedKey(option.name)));
  const exact = options.some(option => normalizedKey(option.name) === normalizedKey(query));
  return exact || !query ? available : [...available, { name: query, custom: true, addition: true }];
}

export function catalogPopupOpen(query, state) {
  return query.trim().length >= 2 && state !== 'idle';
}

export function splitValues(value) {
  if (Array.isArray(value)) return value;
  return String(value || '').split(',').map(item => item.trim()).filter(Boolean);
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
    if (!market.countryCode) return 'Choose a country for every search market.';
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
  const [intelligence, setIntelligence] = useState({ skillSuggestions: [], roleSuggestions: [], termSuggestions: [] });
  const [readiness, setReadiness] = useState(null);
  const [form, setForm] = useState(defaults);
  const [skills, setSkills] = useState([]);
  const [roles, setRoles] = useState([]);
  const [sectors, setSectors] = useState([]);
  const [markets, setMarkets] = useState(() => [marketRow(FALLBACK_MARKET.countryCode, FALLBACK_MARKET.location)]);
  const [locationSource, setLocationSource] = useState(FALLBACK_MARKET.source);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);
  const [uploadStage, setUploadStage] = useState('');
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
      const draft = savedDraft(current.id);
      setProfile(current);
      setIntelligence(insight);
      setReadiness(assessment);
      setSkills(draft?.skills || current.skills || []);
      setRoles(draft?.roles || current.targetRoles || []);
      setSectors(draft?.sectors || current.targetDomains || []);
      setAcknowledgeReadiness(false);
      if (preference) {
        setForm(draft?.form || { ...defaults, ...preference });
        setSectors(draft?.sectors || splitValues(preference.targetDomains));
        setMarkets((draft?.markets || preference.searchMarkets).map(target => marketRow(target.countryCode, target.location)));
        setLocationSource('saved profile');
      } else {
        setForm(draft?.form || { ...defaults, primaryLocation: detected.location });
        setMarkets((draft?.markets || [detected]).map(target => marketRow(target.countryCode, target.location)));
        setLocationSource(draft ? 'saved in this browser session' : detected.source);
      }
    } catch (failure) {
      if (!(failure instanceof ApiError) || failure.code !== 'PROFILE_NOT_READY') setError(displayMessage(failure));
    }
  };

  useEffect(() => { load().catch(failure => setError(displayMessage(failure))); }, []);
  useEffect(() => {
    if (!profile) return;
    try {
      globalThis.sessionStorage?.setItem(
        `joblens.setup.draft.${profile.id}`,
        JSON.stringify({ skills, roles, sectors, form, markets: normalizeSearchMarkets(markets) }),
      );
    } catch { /* server draft remains the fallback */ }
  }, [profile, skills, roles, sectors, form, markets]);

  const upload = async file => {
    if (!file) return;
    setWorking(true); setError(''); setMessage(''); setUploadStage('Reading the file and matching profile evidence…');
    try {
      const data = new FormData(); data.append('file', file);
      await request('/api/candidate-profile/resume', { method: 'POST', body: data });
      setUploadStage('Preparing your review…');
      await load();
      setUploadStage('Resume ready for review.');
      setMessage('Resume read. Review and choose the evidence that should shape your searches.');
    } catch (failure) {
      setUploadStage('This file was not changed. Choose retry or select another file.');
      setError(displayMessage(failure));
    } finally { setWorking(false); }
  };

  const activate = async event => {
    event.preventDefault();
    const marketError = validateSearchMarkets(markets, countries);
    if (marketError) { setError(marketError); return; }
    setWorking(true); setError('');
    try {
      await request('/api/candidate-profile/activate', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, targetDomains: sectors.join(', '), primaryLocation: form.primaryLocation.trim(), skills, targetRoles: roles, searchMarkets: normalizeSearchMarkets(markets), acknowledgeReadiness }),
      });
      try { globalThis.sessionStorage?.removeItem(`joblens.setup.draft.${profile.id}`); } catch { /* activation already succeeded */ }
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
    <section className="setup-hero"><p className="eyebrow">Profile setup</p><h1>Tell us where you want to go. We’ll handle the busywork.</h1><p>Upload a resume, review visible evidence, choose your roles and search markets, then activate your profile.</p></section>
    {!profile ? <ResumeUpload working={working} stage={uploadStage} onUpload={upload} /> : <><ResumeUpload compact working={working} stage={uploadStage} onUpload={upload} /><ResumeInsightSummary intelligence={intelligence} /><form className="setup-flow" onSubmit={activate}>
      <section className="surface setup-card"><p className="eyebrow">Step 2 · direction</p><h2>Choose the profile JobLens should use</h2><p className="muted">Resume evidence stays separate from your selections. Nothing becomes search intent until you activate this form.</p>
        <CatalogChipEditor title="Skills" itemName="skill" values={skills} setValues={setSkills} endpoint="/api/candidate-profile/skills/catalog" suggestions={intelligence.skillSuggestions} />
        <CatalogChipEditor title="Target roles" itemName="role" values={roles} setValues={setRoles} endpoint="/api/candidate-profile/roles/catalog" suggestions={intelligence.roleSuggestions} limit={3} />
        <CatalogChipEditor title="Preferred sectors" itemName="sector" values={sectors} setValues={setSectors} endpoint="/api/candidate-profile/sectors/catalog" limit={10} optional />
      </section>
      <section className="surface setup-card"><p className="eyebrow">Step 3 · markets</p><h2>Where should we look?</h2><div className="current-location"><div><label className="field-label">Where you live now<input value={form.primaryLocation} onChange={event => { setForm({ ...form, primaryLocation: event.target.value }); setLocationSource('entered by you'); }} required /></label><p className="field-help">Used as profile context for match explanations. This does not add a search market.</p><p className="location-source">Current estimate: <strong>{locationSource}</strong>. Always check it before activation.</p></div><button className="button secondary location-button" type="button" onClick={estimateCurrentLocation}><MapPin size={14}/> Use browser estimate</button></div>
        <div className="market-heading"><div><h3>Places you want to search</h3><p className="field-help">Choose a country and optionally narrow it to a city or region. Each row runs as an independent market search.</p></div><span>{markets.length}/10</span></div>
        <SearchMarketsEditor countries={countries} markets={markets} setMarkets={setMarkets} />
      </section>
      {readiness && <section className="surface setup-card readiness-card"><p className="eyebrow">Resume readability · {readiness.score}/100</p><h2>{readiness.status === 'REVIEW_REQUIRED' ? 'Review your resume before activation' : 'Your resume is ready for review'}</h2>{readiness.findings.filter(item => item.severity !== 'PASS').map(item => <p className="muted" key={item.code}><strong>{item.message}</strong> {item.remediation}</p>)}{readiness.acknowledgementRequired && <label className="acknowledgement"><input type="checkbox" checked={acknowledgeReadiness} onChange={event => setAcknowledgeReadiness(event.target.checked)} />I reviewed these machine-readability warnings and want to continue.</label>}</section>}
      <details className="surface setup-card advanced-preferences"><summary><span>Advanced search preferences</span><small>Optional · sensible defaults are already selected</small></summary><p className="muted">Change these only when you want tighter provider searches. Your roles and markets above remain the main search direction.</p><label className="field-label">Provider query override <span>(optional)</span><input value={form.keywords || ''} onChange={event => setForm({ ...form, keywords: event.target.value })} placeholder="Leave blank to use your roles and skills" /></label><p className="field-help">Overrides provider query text; it does not replace your selected target roles.</p><div className="setup-grid"><label>Pages per source<input type="number" min="1" max="20" value={form.maxPages} onChange={event => setForm({ ...form, maxPages: Number(event.target.value) })} required /></label><label>Employment type<select value={form.employmentPreference} onChange={event => setForm({ ...form, employmentPreference: event.target.value })}><option value="ANY">Any employment type</option><option value="PERMANENT">Permanent</option><option value="CONTRACT">Contract</option></select></label><label>Work arrangement<select value={form.workPreference} onChange={event => setForm({ ...form, workPreference: event.target.value })}><option value="REMOTE,HYBRID,ONSITE">Any arrangement</option><option value="REMOTE">Remote</option><option value="HYBRID">Hybrid</option><option value="ONSITE">On-site</option></select></label></div></details>
      <button className="button primary activate" disabled={working || !skills.length || !roles.length || (readiness?.acknowledgementRequired && !acknowledgeReadiness)}>{working ? 'Activating…' : <><Check size={16}/> Activate profile</>}</button>
    </form></>}</main>;
}

export function SearchMarketsEditor({ countries, markets, setMarkets }) {
  const update = (id, change) => setMarkets(markets.map(market => market.id === id ? { ...market, ...change } : market));
  const changeCountry = (market, countryCode) => update(market.id, { countryCode, location: '' });
  return <div className="market-editor">{markets.map((market, index) => {
    const selectedCountry = countries.find(item => item.code === market.countryCode);
    return <MarketRow key={market.id} market={market} index={index} countries={countries} selectedCountry={selectedCountry} removable={markets.length > 1} update={update} changeCountry={changeCountry} onRemove={() => setMarkets(markets.filter(item => item.id !== market.id))} />;
  })}<button className="button secondary add-market" type="button" disabled={markets.length >= 10} onClick={() => setMarkets([...markets, marketRow()])}><Plus size={14}/> Add another market</button></div>;
}

function MarketRow({ market, index, countries, selectedCountry, removable, update, changeCountry, onRemove }) {
  const listId = useId();
  const [options, setOptions] = useState([]);
  const [state, setState] = useState('idle');
  const [focused, setFocused] = useState(false);
  const query = market.location.trim();
  const popupOpen = focused && Boolean(market.countryCode) && state !== 'idle';

  useEffect(() => {
    if (!focused || !market.countryCode) { setOptions([]); setState('idle'); return undefined; }
    const controller = new AbortController();
    setState('loading');
    const timer = setTimeout(() => request(`/api/candidate-profile/cities/catalog?countryCode=${encodeURIComponent(market.countryCode)}&query=${encodeURIComponent(query)}`, { signal: controller.signal })
      .then(result => { setOptions(result); setState(result.length ? 'ready' : 'empty'); })
      .catch(failure => { if (failure?.name !== 'AbortError') setState('error'); }), query.length === 0 ? 0 : 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [market.countryCode, query, focused]);

  const select = value => { update(market.id, { location: value }); setOptions([]); setState('idle'); };

  return <div className="market-row"><span className="market-number" aria-hidden="true">{index + 1}</span><label>Country<select aria-label={`Search country ${index + 1}`} value={market.countryCode} onChange={event => changeCountry(market, event.target.value)} required><option value="">Choose a country</option>{countries.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select></label><label>City or region <span>(optional)</span><div className="catalog-combobox"><Search size={16} aria-hidden="true"/><input aria-label={`City or region ${index + 1}, optional`} value={market.location} onChange={event => update(market.id, { location: event.target.value })} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} placeholder="Leave blank for country-wide" role="combobox" aria-expanded={popupOpen} aria-controls={popupOpen ? listId : undefined} aria-autocomplete="list" disabled={!market.countryCode} /></div>{popupOpen && <div className="catalog-results" id={listId} role="listbox" aria-label={`City suggestions for market ${index + 1}`} onMouseDown={event => event.preventDefault()}>{state === 'loading' && <p role="status">Searching cities…</p>}{state === 'error' && <p role="alert">Suggestions could not load.</p>}{state === 'empty' && <p>No matching city.</p>}{options.map(option => <button type="button" role="option" key={option.name} onClick={() => select(option.name)}><MapPin size={13} aria-hidden="true"/><span>{option.name}</span></button>)}</div>}{!market.countryCode && <p className="field-help">Choose a country to browse its cities.</p>}</label><button className="remove-market" type="button" aria-label={`Remove search market ${index + 1}`} disabled={!removable} onClick={onRemove}><X size={16}/></button>{selectedCountry && <p className="market-capability">{market.location.trim() ? `Narrowed to ${market.location.trim()}. ` : `Searching country-wide in ${selectedCountry.name}. `}{selectedCountry.capabilityExplanation}</p>}</div>;
}

export function ResumeUpload({ compact = false, working, stage, onUpload }) {
  const [file, setFile] = useState(null);
  const [inputKey, setInputKey] = useState(0);
  const submit = event => { event.preventDefault(); onUpload(file); };
  const clear = () => { setFile(null); setInputKey(key => key + 1); };
  return <section className={`surface setup-card resume-upload ${compact ? 'compact' : ''}`}><p className="eyebrow">{compact ? 'Resume source' : 'Step 1 · resume'}</p><h2>{compact ? 'Update your resume evidence' : 'Start with your resume'}</h2><p className="muted">PDF, DOC, or DOCX up to 5 MB. JobLens reads it in this service for review; the original file is not retained.</p><form onSubmit={submit}><label className="file-picker"><FileUp size={22}/><span><strong>{file ? 'File selected' : compact ? 'Choose a newer resume' : 'Choose resume'}</strong><small>{file ? `${file.name} · ${formatFileSize(file.size)} · ${file.type || 'document'}` : 'PDF, DOC, or DOCX'}</small></span><input key={inputKey} name="file" type="file" accept=".pdf,.doc,.docx" required onChange={event => setFile(event.target.files?.[0] || null)} disabled={working} /></label><div className="upload-actions"><button className="button secondary" disabled={working || !file}>{working ? 'Reading resume…' : file ? 'Read selected resume' : 'Select a file first'}</button>{file && !working && <button className="text-button" type="button" onClick={clear}><RotateCcw size={14}/> Clear selection</button>}</div>{stage && <p className="upload-stage" aria-live="polite">{working && <span className="status-pulse" aria-hidden="true"/>}{stage}</p>}</form></section>;
}

function formatFileSize(bytes = 0) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function CatalogChipEditor({ title, itemName, values, setValues, endpoint, suggestions = [], limit = Infinity, optional = false }) {
  const listId = useId();
  const listRef = useRef(null);
  const [input, setInput] = useState('');
  const [options, setOptions] = useState([]);
  const [state, setState] = useState('idle');
  const [activeIndex, setActiveIndex] = useState(0);
  const [lastRemoved, setLastRemoved] = useState('');
  const query = input.trim();
  const choices = useMemo(() => catalogChoices(options, values, query), [options, values, query]);
  const popupOpen = catalogPopupOpen(query, state);

  useEffect(() => {
    if (query.length < 2) { setOptions([]); setState('idle'); return undefined; }
    const controller = new AbortController();
    setState('loading');
    const timer = setTimeout(() => request(`${endpoint}?query=${encodeURIComponent(query)}`, { signal: controller.signal })
      .then(result => { setOptions(result); setState(result.length ? 'ready' : 'empty'); setActiveIndex(0); })
      .catch(failure => { if (failure?.name !== 'AbortError') setState('error'); }), 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [endpoint, query]);

  useEffect(() => {
    if (!popupOpen) return;
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex, choices.length, popupOpen]);

  const select = value => {
    setValues(addDistinctValue(values, value, limit));
    setLastRemoved('');
    setInput(''); setOptions([]); setState('idle');
  };
  const keyDown = event => {
    if (event.key === 'ArrowDown' && popupOpen && choices.length) { event.preventDefault(); setActiveIndex(index => Math.min(index + 1, choices.length - 1)); }
    if (event.key === 'ArrowUp' && popupOpen && choices.length) { event.preventDefault(); setActiveIndex(index => Math.max(index - 1, 0)); }
    if (event.key === 'Enter' && popupOpen && choices[activeIndex]) { event.preventDefault(); select(choices[activeIndex].name); }
    if (event.key === 'Escape') { setOptions([]); setState('idle'); }
  };
  const atLimit = values.length >= limit;
  const evidence = suggestions.filter((suggestion, index, all) => all.findIndex(item => normalizedKey(item.name) === normalizedKey(suggestion.name)) === index && !values.some(value => normalizedKey(value) === normalizedKey(suggestion.name)));

  const remove = value => { setValues(values.filter(item => normalizedKey(item) !== normalizedKey(value))); setLastRemoved(value); };
  const undo = () => { setValues(addDistinctValue(values, lastRemoved, limit)); setLastRemoved(''); };

  return <div className="chip-editor"><div className="editor-heading"><h3>{title} {optional && <span>(optional)</span>}</h3>{Number.isFinite(limit) && <span>{values.length}/{limit}</span>}</div><div className="chips">{values.map(value => <span className="chip" key={normalizedKey(value)}>{value}<button type="button" aria-label={`Remove ${value}`} onClick={() => remove(value)}><X size={13}/></button></span>)}</div>{lastRemoved && <p className="undo-removal" role="status">Removed {lastRemoved}. <button type="button" onClick={undo}>Undo</button></p>}
    <div className="catalog-combobox"><Search size={16} aria-hidden="true"/><input value={input} onChange={event => setInput(event.target.value)} onKeyDown={keyDown} placeholder={`Search or add a ${itemName}`} role="combobox" aria-label={`Search ${title.toLowerCase()}`} aria-expanded={popupOpen} aria-controls={popupOpen ? listId : undefined} aria-autocomplete="list" aria-activedescendant={popupOpen && choices[activeIndex] ? `${listId}-${activeIndex}` : undefined} disabled={atLimit} />{input && <button type="button" aria-label={`Clear ${itemName} search`} onClick={() => setInput('')}><X size={14}/></button>}</div>
    {popupOpen && <div className="catalog-results" id={listId} ref={listRef} role="listbox" aria-label={`${title} suggestions`}>{state === 'loading' && <p role="status">Searching the catalogue…</p>}{state === 'error' && <p role="alert">Suggestions could not load. You can retry or add a private value.</p>}{state === 'empty' && <p>No catalogue match.</p>}{choices.map((option, index) => <button type="button" role="option" aria-selected={index === activeIndex} className={`${option.addition ? 'custom-option ' : ''}${index === activeIndex ? 'active' : ''}`.trim()} id={`${listId}-${index}`} key={`${option.name}-${option.custom}-${option.addition || false}`} onMouseEnter={() => setActiveIndex(index)} onClick={() => select(option.name)}><span>{option.addition ? `Add “${option.name}”` : option.name}<small>{option.custom ? 'Private to this workspace' : formatCategory(option.category)}</small></span><Plus size={14}/></button>)}</div>}
    {evidence.length > 0 && <div className="resume-evidence"><p>From your resume — review before adding</p>{evidence.slice(0, 6).map(suggestion => <details key={normalizedKey(suggestion.name)}><summary><span>{suggestion.name}<small>{formatCategory(suggestion.category)} · {Math.round(Number(suggestion.confidence) * 100)}% evidence confidence</small></span><ChevronDown size={14}/></summary><p>{suggestion.evidence}</p><p className="evidence-source">{formatCategory(suggestion.evidenceSource || suggestion.evidenceSection)} · {suggestion.matchType?.replaceAll('_', ' ').toLowerCase()}</p><button className="button secondary" type="button" disabled={atLimit} onClick={() => select(suggestion.name)}><Plus size={14}/> Use this {itemName}</button></details>)}</div>}
    {optional && <p className="field-help">Leave this empty to search across all sectors. Selected canonical names also drive sector query and score explanations.</p>}
  </div>;
}

function formatCategory(value = '') {
  return value.replaceAll('_', ' ').toLocaleLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
}

export function ResumeInsightSummary({ intelligence }) {
  const skills = intelligence.skillSuggestions || [];
  const roles = intelligence.roleSuggestions || [];
  const terms = intelligence.termSuggestions || [];
  const suggestedTerms = terms.filter(term => term.reviewState === 'SUGGESTED');
  const reviewTerms = terms.filter(term => term.reviewState !== 'SUGGESTED');
  const termItems = items => items.map(term => ({ ...term, name: term.term, confidence: term.evidenceStrength }));
  return <details className="surface setup-card insight-summary"><summary><span><Sparkles size={16}/><strong>Review what JobLens found</strong><small>{skills.length} skills · {roles.length} role directions · {suggestedTerms.length} other terms · {reviewTerms.length} need attention</small></span><ChevronDown size={16}/></summary><p className="muted">These are deterministic matches, not confirmed facts. Open any item to inspect its bounded source evidence.</p><div className="insight-columns"><EvidenceList title="Skills and tools" items={skills} sourceKey="evidenceSection" /><EvidenceList title="Role directions" items={roles} sourceKey="evidenceSource" />{suggestedTerms.length > 0 && <EvidenceList title="Other detected terms" items={termItems(suggestedTerms)} sourceKey="evidenceSection" />}{reviewTerms.length > 0 && <EvidenceList title="Omitted or ambiguous text" items={termItems(reviewTerms)} sourceKey="evidenceSection" />}</div></details>;
}

function EvidenceList({ title, items, sourceKey }) {
  return <section><h3>{title}<span>{items.length}</span></h3>{items.length ? items.slice(0, 12).map((item, index) => <details key={`${item.name}-${index}`}><summary><span>{item.name}<small>{formatCategory(item[sourceKey])} · {Math.round(Number(item.confidence) * 100)}%</small></span><FileText size={13}/></summary><p>{item.evidence}</p></details>) : <p className="field-help">No supported evidence found in this group.</p>}</section>;
}
