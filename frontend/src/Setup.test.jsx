import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { addDistinctValue, catalogChoices, catalogPopupOpen, CatalogChipEditor, detectBrowserMarket, normalizeSearchMarkets, ResumeInsightSummary, SearchMarketsEditor, Setup, validateSearchMarkets } from './Setup';

const countries = [
  { code: 'AU', name: 'Australia', capabilityExplanation: 'Integrated search available through ADZUNA.' },
  { code: 'SG', name: 'Singapore', capabilityExplanation: 'Integrated search available through ADZUNA.' },
];

describe('assisted multi-market setup', () => {
  it('keeps the opening state resume-first', () => {
    const html = renderToStaticMarkup(<Setup />);

    expect(html).toContain('Start with your resume');
    expect(html).not.toContain('Places you want to search');
    expect(html).not.toContain('Advanced search preferences');
  });

  it('renders every saved market as an editable row', () => {
    const html = renderToStaticMarkup(<SearchMarketsEditor countries={countries} markets={[
      { id: 'one', countryCode: 'SG', location: 'Singapore' },
      { id: 'two', countryCode: 'AU', location: 'Sydney' },
    ]} setMarkets={() => {}} />);

    expect(html).toContain('aria-label="Search country 1"');
    expect(html).toContain('aria-label="Search country 2"');
    expect(html).toContain('value="Singapore"');
    expect(html).toContain('value="Sydney"');
    expect(html).toContain('Add another market');
  });

  it('submits normalized distinct rows and explains duplicate input', () => {
    const markets = [
      { countryCode: 'sg', location: ' Singapore ' },
      { countryCode: 'AU', location: 'New   South Wales' },
    ];

    expect(validateSearchMarkets(markets, countries)).toBe('');
    expect(normalizeSearchMarkets(markets)).toEqual([
      { countryCode: 'SG', location: 'Singapore' },
      { countryCode: 'AU', location: 'New South Wales' },
    ]);
    expect(validateSearchMarkets([...markets, { countryCode: 'SG', location: 'singapore' }], countries))
      .toBe('Remove the duplicate search market before activating your profile.');
  });

  it('accepts a country-wide market while still rejecting duplicate country-wide rows', () => {
    const countryWide = [{ countryCode: 'AU', location: '   ' }];

    expect(validateSearchMarkets(countryWide, countries)).toBe('');
    expect(normalizeSearchMarkets(countryWide)).toEqual([{ countryCode: 'AU', location: '' }]);
    expect(validateSearchMarkets([...countryWide, { countryCode: 'au', location: '' }], countries))
      .toBe('Remove the duplicate search market before activating your profile.');
  });

  it('prefers the browser time zone, then its language, then the visible Singapore fallback', () => {
    expect(detectBrowserMarket(countries, { timeZone: 'Australia/Sydney', languages: ['en-US'] }))
      .toEqual({ countryCode: 'AU', location: 'Sydney', source: 'browser time zone' });
    expect(detectBrowserMarket(countries, { timeZone: 'Asia/Tokyo', languages: ['en-AU'] }))
      .toEqual({ countryCode: 'AU', location: 'Australia', source: 'browser language' });
    expect(detectBrowserMarket(countries, { timeZone: 'Asia/Tokyo', languages: ['ja-JP'] }))
      .toEqual({ countryCode: 'SG', location: 'Singapore', source: 'JobLens default' });
  });

  it('deduplicates reviewed values and exposes an accessible catalogue search', () => {
    expect(addDistinctValue(['AI Engineer'], ' ai   engineer ', 3)).toEqual(['AI Engineer']);
    expect(addDistinctValue(['AI Engineer'], 'Frontend Engineer', 3))
      .toEqual(['AI Engineer', 'Frontend Engineer']);

    const html = renderToStaticMarkup(<CatalogChipEditor
      title="Target roles"
      itemName="role"
      values={[]}
      setValues={() => {}}
      endpoint="/api/candidate-profile/roles/catalog"
      limit={3}
      suggestions={[{ name: 'AI Engineer', category: 'DATA', confidence: 0.95, evidence: 'Senior AI Engineer', evidenceSource: 'RESUME_HEADLINE', matchType: 'EXACT_CANONICAL' }]}
    />);

    expect(html).toContain('role="combobox"');
    expect(html).toContain('aria-label="Search target roles"');
    expect(html).toContain('From your resume — review before adding');
    expect(html).toContain('Use this role');
  });

  it('keeps private additions in keyboard choices and closes the popup on Escape state', () => {
    expect(catalogChoices(
      [{ name: 'AI Engineer', category: 'DATA', custom: false }],
      [],
      'Applied Scientist',
    )).toEqual([
      { name: 'AI Engineer', category: 'DATA', custom: false },
      { name: 'Applied Scientist', custom: true, addition: true },
    ]);
    expect(catalogPopupOpen('Applied Scientist', 'ready')).toBe(true);
    expect(catalogPopupOpen('Applied Scientist', 'idle')).toBe(false);
  });

  it('keeps detailed resume evidence progressively disclosed', () => {
    const html = renderToStaticMarkup(<ResumeInsightSummary intelligence={{
      skillSuggestions: [{ name: 'Python', confidence: 0.99, evidence: 'Skills: Python', evidenceSection: 'SKILLS' }],
      roleSuggestions: [{ name: 'AI Engineer', confidence: 0.95, evidence: 'Senior AI Engineer', evidenceSource: 'RESUME_HEADLINE' }],
      termSuggestions: [{ term: 'A.I.', reviewState: 'AMBIGUOUS', evidenceStrength: 0.4, evidence: 'Worked on A.I.', evidenceSection: 'SUMMARY' }],
    }} />);

    expect(html).toContain('<details');
    expect(html).toContain('Review what JobLens found');
    expect(html).toContain('Omitted or ambiguous text');
    expect(html).toContain('deterministic matches, not confirmed facts');
  });
});
