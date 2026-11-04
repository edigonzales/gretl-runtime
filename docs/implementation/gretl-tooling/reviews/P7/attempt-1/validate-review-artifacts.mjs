import { readFileSync } from 'node:fs';
import { validateReviewArtifacts } from '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product/tools/gretl-tooling/review.mjs';

const base = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const request = JSON.parse(readFileSync(`${base}/request.json`, 'utf8'));
const verdict = JSON.parse(readFileSync(`${base}/verdict.json`, 'utf8'));
const report = readFileSync(`${base}/review.md`, 'utf8');
const completion = {
  exitCode: 0,
  timedOut: false,
  finished: true,
  childrenFinished: true,
  unchanged: true,
  fresh: true
};
console.log('validation result:', validateReviewArtifacts(request, verdict, report, completion));
console.log('criteria:', verdict.criteria.length, 'guiEvidence:', verdict.guiEvidence.length, 'counterprobes:', verdict.counterprobes.length);
