import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';
import { validateReviewArtifacts } from '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product/tools/gretl-tooling/review.mjs';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const request = JSON.parse(readFileSync(resolve(evidence, 'request.json'), 'utf8'));
const verdict = JSON.parse(readFileSync(resolve(evidence, 'verdict.json'), 'utf8'));
const report = readFileSync(resolve(evidence, 'review.md'), 'utf8');

const completion = {
  exitCode: 0,
  timedOut: false,
  finished: true,
  childrenFinished: true,
  unchanged: true,
  fresh: true
};

assert.deepEqual(verdict.verifiedCurrentEvidence.flatMap(row => row.checkIds).sort(), ['root-services', 'root-standard']);
assert.deepEqual(verdict.executedChecks.map(row => row.id).filter(id => ['root-standard', 'root-services'].includes(id)), []);
assert.equal(verdict.criteria.length, 81);
assert.equal(verdict.guiEvidence.flatMap(row => row.caseIds).length, 30);
assert.equal(new Set(verdict.guiEvidence.flatMap(row => row.caseIds)).size, 30);
assert.equal(verdict.counterprobes.length, 2);
for (const row of [...verdict.criteria, ...verdict.counterprobes, ...verdict.executedChecks, ...verdict.verifiedCurrentEvidence, ...verdict.guiEvidence]) {
  for (const path of row.evidence ?? []) {
    assert.ok(isAbsolute(path), path);
    assert.ok(!path.includes('#'), `Fragment not allowed: ${path}`);
    assert.ok(!/:\d+$/.test(path), `Line suffix not allowed: ${path}`);
  }
}

const result = validateReviewArtifacts(request, verdict, report, completion);
assert.equal(result, 'PASS');
console.log('validateReviewArtifacts PASS');
console.log('criteria=' + verdict.criteria.length,
  'gui=' + verdict.guiEvidence.flatMap(row => row.caseIds).length,
  'counterprobes=' + verdict.counterprobes.length,
  'executedChecks=' + verdict.executedChecks.length,
  'verified=' + JSON.stringify(verdict.verifiedCurrentEvidence.flatMap(row => row.checkIds)));
