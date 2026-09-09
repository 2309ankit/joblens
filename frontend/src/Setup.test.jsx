import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { detectBrowserMarket, normalizeSearchMarkets, SearchMarketsEditor, Setup, validateSearchMarkets } from './Setup';

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

  it('prefers the browser time zone, then its language, then the visible Singapore fallback', () => {
    expect(detectBrowserMarket(countries, { timeZone: 'Australia/Sydney', languages: ['en-US'] }))
      .toEqual({ countryCode: 'AU', location: 'Sydney', source: 'browser time zone' });
    expect(detectBrowserMarket(countries, { timeZone: 'Asia/Tokyo', languages: ['en-AU'] }))
      .toEqual({ countryCode: 'AU', location: 'Australia', source: 'browser language' });
    expect(detectBrowserMarket(countries, { timeZone: 'Asia/Tokyo', languages: ['ja-JP'] }))
      .toEqual({ countryCode: 'SG', location: 'Singapore', source: 'JobLens default' });
  });
});
