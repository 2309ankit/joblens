import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { QueryPlanningEvidence, ScoreEvidence } from './AiEvidence';

describe('AI evidence', () => {
  it('identifies NVIDIA scoring and renders its explanation as text, never HTML', () => {
    const html = renderToStaticMarkup(<ScoreEvidence job={{
      scoring_source: 'NVIDIA_NEBIUS', score_summary: '<script>alert(1)</script> Java fit',
    }} />);
    expect(html).toContain('NVIDIA score · Nebius');
    expect(html).toContain('<summary>Why this score</summary>');
    expect(html).toContain('&lt;script&gt;');
    expect(html).not.toContain('<script>');
  });

  it('never attributes stale AI explanation fields to deterministic scoring', () => {
    const html = renderToStaticMarkup(<ScoreEvidence job={{
      scoring_source: 'DETERMINISTIC', score_summary: 'Stale model explanation', ai_confidence: 0.9,
    }} />);
    expect(html).toContain('Rule-based score');
    expect(html).not.toContain('Stale model explanation');
    expect(html).not.toContain('NVIDIA');
    expect(html).not.toContain('90%');
  });

  it('does not invent a source or model explanation for absent fields', () => {
    expect(renderToStaticMarkup(<ScoreEvidence job={{}} />)).toContain('Score source unavailable');
    const html = renderToStaticMarkup(<ScoreEvidence job={{ scoring_source: 'NVIDIA_NEBIUS', score_summary: '  ' }} />);
    expect(html).toContain('explanation was not provided');
  });

  it('shows an AI planning rationale without claiming the query was changed', () => {
    const html = renderToStaticMarkup(<QueryPlanningEvidence source={{
      queryPlanningSource: 'LLM_NEBIUS', queryPlanningRationale: 'Keep the existing Java query.',
    }} />);
    expect(html).toContain('AI-assisted query · Nebius');
    expect(html).toContain('<summary>Why this query</summary>');
    expect(html).toContain('Keep the existing Java query.');
    expect(html).toContain('may keep a query unchanged');
  });

  it('keeps source-level deterministic planning distinct and ignores stale rationales', () => {
    const html = renderToStaticMarkup(<QueryPlanningEvidence source={{
      queryPlanningSource: 'DETERMINISTIC', queryPlanningRationale: 'Stale rationale',
    }} />);
    expect(html).toContain('Rule-based query');
    expect(html).not.toContain('Stale rationale');
    expect(html).not.toContain('AI-assisted');
  });

  it('handles missing and unrecognized query sources and missing AI rationales', () => {
    for (const queryPlanningSource of [undefined, 'FUTURE_PROVIDER']) {
      expect(renderToStaticMarkup(<QueryPlanningEvidence source={{ queryPlanningSource }} />))
        .toContain('Query source unavailable');
    }
    expect(renderToStaticMarkup(<QueryPlanningEvidence source={{ queryPlanningSource: 'LLM_NEBIUS' }} />))
      .toContain('explanation was not provided');
  });
});
