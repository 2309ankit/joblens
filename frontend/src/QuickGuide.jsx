import { useEffect, useRef, useState } from 'react';
import { ArrowRight, Bookmark, FileText, HelpCircle, SlidersHorizontal, Sparkles, X } from 'lucide-react';

const SEEN_KEY = 'joblens.quick-guide.v1';

export function QuickGuide() {
  const dialog = useRef(null);
  const trigger = useRef(null);
  const closeTimer = useRef(null);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    let seen = false;
    try { seen = window.localStorage.getItem(SEEN_KEY) === 'seen'; }
    catch { /* Storage may be unavailable in private or restricted browsers. */ }
    if (!seen) dialog.current.showModal();
    return () => window.clearTimeout(closeTimer.current);
  }, []);

  function dismiss() {
    if (closeTimer.current) return;
    try { window.localStorage.setItem(SEEN_KEY, 'seen'); }
    catch { /* The guide remains dismissible even when storage is blocked. */ }
    setClosing(true);
    const delay = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180;
    closeTimer.current = window.setTimeout(() => {
      dialog.current.close();
      setClosing(false);
      closeTimer.current = null;
      trigger.current.focus();
    }, delay);
  }

  return <>
    <button ref={trigger} className="quick-guide-trigger" onClick={() => dialog.current.showModal()}><HelpCircle size={16} aria-hidden="true" /> Quick guide</button>
    <dialog ref={dialog} className={`quick-guide${closing ? ' is-closing' : ''}`} aria-labelledby="quick-guide-title" aria-describedby="quick-guide-description" onCancel={event => { event.preventDefault(); dismiss(); }}>
      <button className="quick-guide-close" aria-label="Close quick guide" onClick={dismiss}><X size={20} aria-hidden="true" /></button>
      <span className="quick-guide-mark"><Sparkles size={24} aria-hidden="true" /></span>
      <p className="eyebrow">Welcome to JobLens</p>
      <h2 id="quick-guide-title">Your next role starts here.</h2>
      <p id="quick-guide-description">Three simple steps. Your search, your pace.</p>
      <ol className="quick-guide-steps">
        <li><FileText size={20} aria-hidden="true" /><div><h3>1. Add your resume</h3><p>Upload it to build your profile.</p></div></li>
        <li><SlidersHorizontal size={20} aria-hidden="true" /><div><h3>2. Make it yours</h3><p>Review your skills, choose roles and locations, then activate.</p></div></li>
        <li><Bookmark size={20} aria-hidden="true" /><div><h3>3. Explore & save</h3><p>Browse your matches and track favourites in My list.</p></div></li>
      </ol>
      <button className="button primary quick-guide-done" autoFocus onClick={dismiss}>Got it, let’s go <ArrowRight size={16} aria-hidden="true" /></button>
      <p className="quick-guide-hint">Need a reminder? Open Quick guide anytime.</p>
    </dialog>
  </>;
}
