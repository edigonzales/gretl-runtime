import assert from 'node:assert/strict';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const product = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product';
const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const mapPath = resolve(product, 'docs/implementation/gretl-tooling/implementation/P8-acceptance-map.json');
const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const request = JSON.parse(readFileSync(resolve(evidence, 'request.json'), 'utf8'));
const prior = {
  P0: ['reviews/P0/attempt-1/review.md','reviews/P0/attempt-1/verdict.json'],
  P1: ['reviews/P1/attempt-2/review.md','reviews/P1/attempt-2/verdict.json','reviews/P1/attempt-1/review.md'],
  P2: ['reviews/P2/attempt-4-resumed-pass/review.md','reviews/P2/attempt-4-resumed-pass/verdict.json'],
  P3: ['reviews/P3/attempt-2/review.md','reviews/P3/attempt-2/verdict.json','reviews/P3/attempt-1/review.md'],
  P4: ['reviews/P4/attempt-2/review.md','reviews/P4/attempt-2/verdict.json'],
  P5: ['reviews/P5/attempt-1/review.md','reviews/P5/attempt-1/resume-1/review.md','reviews/P5/attempt-1/resume-1/validation.json'],
  P6: ['reviews/P6/attempt-1/review.md','reviews/P6/attempt-1/resume-1/review.md','reviews/P6/attempt-1/resume-1/validation.json'],
  P7: ['reviews/P7/attempt-1/review.md','reviews/P7/attempt-1/verdict.json'],
  P8: []
};
for (const paths of Object.values(prior)) for (const p of paths) assert.ok(existsSync(resolve(product, 'docs/implementation/gretl-tooling', p)), p);

const rows = map.entries.map(entry => {
  assert.equal(entry.id, entry.id.toUpperCase());
  const phase = entry.id.slice(0, 2);
  const evidencePaths = [
    resolve(product, entry.primaryCode),
    ...entry.tests.map(path => resolve(product, path)),
    ...prior[phase].map(path => resolve(product, 'docs/implementation/gretl-tooling', path)),
    resolve(evidence, 'root-standard/manifest.json'),
    resolve(evidence, 'root-services/manifest.json'),
    resolve(evidence, 'root-evidence-validation.json')
  ];
  if ((entry.currentGuiCases ?? []).length) {
    evidencePaths.push(
      resolve(evidence, 'gui/current/qualification.json'),
      resolve(evidence, 'gui-evidence-generated.json')
    );
  }
  if (entry.additionalCurrentEvidence?.includes('model-readiness')) evidencePaths.push(request.modelIdentityEvidence);
  if (phase === 'P8') {
    evidencePaths.push(
      resolve(product, 'docs/implementation/gretl-tooling/implementation/P8.md'),
      resolve(product, 'docs/implementation/gretl-tooling/STATUS.md'),
      resolve(evidence, 'affected-tests-result.json'),
      resolve(evidence, 'required-checks-result.json')
    );
  }
  const unique = [...new Set(evidencePaths)];
  for (const p of unique) assert.ok(existsSync(p), `${entry.id}: missing ${p}`);
  return { id: entry.id, status: 'fulfilled', evidence: unique, findingIds: [] };
});
assert.equal(rows.length, 81);
writeFileSync(resolve(evidence, 'criteria-skeleton.json'), JSON.stringify(rows, null, 2) + '\n');
console.log(`Generated ${rows.length} criteria with existing evidence paths.`);
