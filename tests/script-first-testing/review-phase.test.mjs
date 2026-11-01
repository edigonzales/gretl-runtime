import { test } from 'node:test';
import assert from 'node:assert/strict';
import { criteria, validateRequest, validateVerdict } from '../../scripts/review-phase.mjs';

// Synthetic orchestration fixtures; these are explicitly not actual model reviews.
function fixture() {
  const sha = 'a'.repeat(40);
  const request = { schemaVersion: 1, assignment: 'gretl-script-first-testing', phase: 'TST1', attempt: 1,
    baseSha: sha, assignmentBaseSha: sha, candidateSha: sha,
    reviewWorktree: '/scratch/worktree', reviewDirectory: '/scratch/review',
    criteria: criteria('TST1'), requiredChecks: [{ id: 'standard' }],
    reviewer: { provider: 'deepseek', model: 'deepseek-flash', apiHost: 'api.deepseek.com', requestedVersion: 'DeepSeek-V4.1-Flash-1017' } };
  const verdict = { ...structuredClone(request), verdict: 'PASS',
    reviewer: { ...request.reviewer, openCodeVersion: '2.2.5', modelMappingEvidence: 'synthetic mapping evidence for validator tests' },
    criteria: criteria('TST1').map(id => ({ id, result: 'fulfilled', evidence: ['synthetic-test-evidence'] })),
    checks: [{ id: 'standard', result: 'passed', evidence: ['synthetic-test-evidence'] }],
    counterprobes: [{ description: 'synthetic validator test', evidence: ['synthetic-test-evidence'] }],
    openMandatoryFindings: [], missingRequiredChecks: [], report: 'review.md' };
  return { request, verdict, report: `Synthetic validator fixture for ${sha}. `.repeat(4), completion: { exit: 0, finished: true, unchanged: true } };
}
test('all 38 cumulative criteria are required', () => { assert.equal(criteria('TST4').length, 38); });
test('complete synthetic fixture is accepted by the technical validator', () => {
  const f = fixture(); assert.equal(validateVerdict(f.request, f.verdict, f.report, f.completion), 'PASS');
});
for (const [name, mutate] of Object.entries({
  'missing report': f => { f.report = ''; },
  'wrong SHA': f => { f.verdict.candidateSha = 'b'.repeat(40); },
  'wrong phase': f => { f.verdict.phase = 'TST2'; },
  'missing criterion': f => { f.verdict.criteria.pop(); },
  'duplicate criterion': f => { f.verdict.criteria.push(f.verdict.criteria[0]); },
  'unknown verdict': f => { f.verdict.verdict = 'MOSTLY_PASS'; },
  'open finding': f => { f.verdict.openMandatoryFindings = ['must fix']; },
  'missing gate': f => { f.verdict.checks = []; },
  'failed gate': f => { f.verdict.checks[0].result = 'failed'; },
  'wrong provider': f => { f.verdict.reviewer.provider = 'openrouter'; },
  'wrong host': f => { f.verdict.reviewer.apiHost = 'proxy.invalid'; },
  'V1 CLI': f => { f.verdict.reviewer.openCodeVersion = '1.18.22'; },
  'unfinished session': f => { f.completion.finished = false; },
  'changed worktree': f => { f.completion.unchanged = false; },
  'exit error despite PASS': f => { f.completion.exit = 1; },
  'no counterprobe': f => { f.verdict.counterprobes = []; },
  'unverified criterion': f => { f.verdict.criteria[0].result = 'unverified'; },
})) test(`rejects ${name}`, () => {
  const f = fixture(); mutate(f); assert.throws(() => validateVerdict(f.request, f.verdict, f.report, f.completion));
});
test('invalid JSON cannot become a verdict', () => { assert.throws(() => JSON.parse('{')); });
test('request cannot shrink the assigned criteria', () => { const f = fixture(); f.request.criteria.pop(); assert.throws(() => validateRequest(f.request)); });
