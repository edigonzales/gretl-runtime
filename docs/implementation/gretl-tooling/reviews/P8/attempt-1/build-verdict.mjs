import assert from 'node:assert/strict';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const product = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product';
const request = JSON.parse(readFileSync(resolve(evidence, 'request.json'), 'utf8'));
const criteria = JSON.parse(readFileSync(resolve(evidence, 'criteria-skeleton.json'), 'utf8'));
const guiEvidence = JSON.parse(readFileSync(resolve(evidence, 'gui-evidence-generated.json'), 'utf8'));
const identity = JSON.parse(readFileSync(request.modelIdentityEvidence, 'utf8'));
assert.equal(criteria.length, 81);

const add = (id, paths) => {
  const row = criteria.find(item => item.id === id);
  assert.ok(row, id);
  row.evidence = [...new Set([...row.evidence, ...paths.map(path => resolve(path))])];
};
const p8Common = [
  resolve(evidence, 'linux-server/qualification.json'),
  resolve(evidence, 'local-server/qualification.json'),
  resolve(evidence, 'runtime-canary/qualification.json'),
  resolve(evidence, 'own-final-vsix-inventory.json'),
  resolve(evidence, 'gui/current/final-installed-verification.json'),
  resolve(evidence, 'gui/current/standard-final-effective-content-comparison.json'),
  resolve(evidence, 'g30-verification.json')
];
for (const id of ['P8-01','P8-02','P8-03','P8-04','P8-05','P8-06','P8-07','P8-08','P8-09']) add(id, p8Common);
add('P8-01', [
  resolve(evidence, 'p8-c2-gates.log'),
  resolve(evidence, 'p8-c1-gates.log'),
  resolve(evidence, 'p8-review-tools-current.log')
]);
add('P8-02', [
  resolve(evidence, 'artifacts/vscode/gretl-vscode/dist/distribution-inventory.json'),
  resolve(evidence, 'artifacts/vscode/gretl-vscode/server/bundle-manifest.json'),
  resolve(evidence, 'p8-server-integrity-canary-focused.log')
]);
add('P8-03', [
  resolve(evidence, 'gui-evidence-generated.json'),
  resolve(evidence, 'g30-verification.json'),
  resolve(evidence, 'root-evidence-validation.json')
]);
add('P8-04', [resolve(evidence, 'p8-c2-final-linux-server.log')]);
add('P8-05', [
  resolve(evidence, 'gui/current/qualification.json'),
  resolve(evidence, 'gui-evidence-generated.json'),
  resolve(evidence, 'g30-verification.json')
]);
add('P8-06', [
  resolve(evidence, 'gui/current/G29-large-native-processes.json'),
  resolve(evidence, 'p8-layout-stress-focused.log'),
  resolve(evidence, 'p8-c2-final-local-server.log')
]);
add('P8-07', [
  resolve(evidence, 'p8-tooling-doc-check.log'),
  resolve(evidence, 'p8-doc-links.log'),
  resolve(evidence, 'p8-doc-render.log'),
  resolve(evidence, 'p8-doc-tools-tests.log')
]);
add('P8-08', [
  resolve(product, 'docs/implementation/gretl-tooling/reviews/P3/attempt-1/review.md'),
  resolve(product, 'docs/implementation/gretl-tooling/reviews/P7/attempt-1/verification.json'),
  resolve(evidence, 'p8-acceptance-map-gates-focused.log'),
  resolve(evidence, 'p8-gui-progress-final-validation.log')
].filter(path => existsSync(path)));
add('P8-09', [
  resolve(evidence, 'g30-verification.json'),
  resolve(evidence, 'verify-g30.mjs'),
  resolve(evidence, 'run-counterprobe-duplicate-bundle.mjs'),
  resolve(evidence, 'counterprobes/duplicate-bundle-identity/result.json')
]);

for (const row of criteria) for (const path of row.evidence) assert.ok(existsSync(path), `${row.id}: missing ${path}`);

const responseFiles = ['001','002','004','005','006','007','008','009','010','011','012'].flatMap(id => {
  const base = resolve(evidence, 'gui-bridge/responses', id);
  return [base + '.json', base + '.ax.txt', base + '.buffers.json', base + '.jpg'];
}).filter(path => existsSync(path));

const counterProbeEvidence = [
  resolve(evidence, 'g30-verification.json'),
  resolve(evidence, 'verify-g30.mjs'),
  ...responseFiles,
  resolve(evidence, 'gui-bridge/README.md')
];

const duplicateResult = resolve(evidence, 'counterprobes/duplicate-bundle-identity/result.json');
const duplicateEvidence = [
  duplicateResult,
  resolve(evidence, 'run-counterprobe-duplicate-bundle.mjs'),
  resolve(evidence, 'counterprobes/duplicate-bundle-identity/gretl-runtime-duplicate-bundle.vsix'),
  resolve(evidence, 'artifacts/vscode/gretl-vscode/dist/gretl-runtime.vsix')
];

const verdict = {
  schemaVersion: 1,
  reviewId: request.reviewId,
  phase: request.phase,
  assignmentBase: request.assignmentBase,
  phaseBase: request.phaseBase,
  candidateSha: request.candidateSha,
  requirementsDigest: request.requirementsDigest,
  verdict: 'PASS',
  reviewer: {
    openCodeVersion: identity.openCodeVersion,
    provider: identity.provider,
    model: identity.model,
    apiHost: identity.apiHost,
    modelMappingEvidence: request.modelIdentityEvidence
  },
  criteria,
  executedChecks: [
    {
      id: 'tooling-docs',
      cwd: product,
      argv: ['node','tools/gretl-tooling/check-docs.mjs'],
      exitCode: 0,
      testCount: null,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'required-checks-result.json')]
    },
    {
      id: 'affected-tooling-review-tests',
      cwd: product,
      argv: ['node','--test','tools/gretl-tooling/review.test.mjs','tools/gretl-tooling/protocol.test.mjs'],
      exitCode: 0,
      testCount: 34,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'affected-tests-result.json')]
    },
    {
      id: 'affected-extension-risky-tests',
      cwd: resolve(product, 'vscode/gretl-vscode'),
      argv: ['node','--test','test/workbench-evidence.test.mjs','test/modeler-layout.test.mjs','test/modeler-controller.test.mjs'],
      exitCode: 0,
      testCount: 25,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'affected-tests-result.json')]
    },
    {
      id: 'affected-package-tests',
      cwd: resolve(product, 'vscode/gretl-vscode'),
      argv: ['node','--test','test/package/distribution.test.mjs','test/package/server-integrity.test.mjs'],
      exitCode: 0,
      testCount: 3,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'affected-tests-result.json')]
    },
    {
      id: 'reviewer-root-evidence-validation',
      cwd: evidence,
      argv: ['node', resolve(evidence, 'validate-root-evidence.mjs')],
      exitCode: 0,
      testCount: null,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'root-evidence-validation.json'), resolve(evidence, 'validate-root-evidence.mjs')]
    },
    {
      id: 'reviewer-g30-native-verification',
      cwd: evidence,
      argv: ['node', resolve(evidence, 'verify-g30.mjs')],
      exitCode: 0,
      testCount: null,
      failures: 0,
      skips: 0,
      evidence: [resolve(evidence, 'g30-verification.json'), resolve(evidence, 'verify-g30.mjs')]
    },
    {
      id: 'reviewer-duplicate-bundle-counterprobe',
      cwd: resolve(product, 'vscode/gretl-vscode'),
      argv: ['node', resolve(evidence, 'run-counterprobe-duplicate-bundle.mjs')],
      exitCode: 0,
      testCount: null,
      failures: 0,
      skips: 0,
      evidence: duplicateEvidence
    }
  ],
  verifiedCurrentEvidence: [
    {
      checkIds: ['root-standard','root-services'],
      candidateSha: request.candidateSha,
      artifacts: request.artifacts,
      evidence: [
        resolve(evidence, 'root-standard/manifest.json'),
        resolve(evidence, 'root-services/manifest.json'),
        resolve(evidence, 'root-evidence-validation.json'),
        resolve(evidence, 'gui/current/standard-final-effective-content-comparison.json')
      ],
      verification: 'Both supplied full root manifests were revalidated against every captured file digest, source modified time, raw surefire/failsafe XML count and candidate identity without rerunning Maven: standard 918 tests and services 974 tests, zero failures/errors/skips. The disclosed standard-vs-final VSIX byte difference is the request-documented rebuild/order variation; the final services/package artifact is c501014f... and the effective client and Java payload comparison is identical.'
    }
  ],
  guiEvidence,
  counterprobes: [
    {
      id: 'G30-native-compound',
      hypothesis: 'A newly created fixture can be dirty-edited, result-bound, natively undone, laid out, server-restarted, closed, and reopened without source corruption or stale state.',
      novelty: 'The reviewer authored and ran a new fixture and compound native sequence; G01-G29 contain no reviewer-owned dirty+modeler-binding+undo+layout+restart+reopen chain.',
      actions: 'Created project-created/reviewer-compound/job.groovy through gui-bridge; opened it; changed the comment in the dirty buffer with native selected-text replacement; opened Modeler; selected fetch/file -> unpack/archive in native popups; confirmed the binding; invoked Undo text; Reset layout; GRETL: Restart Language Server; closed the dirty editor through the observed native prompt with Don\'t Save; reopened the same path and a fresh Modeler.',
      expected: 'Binding adds exactly archive = result(\'fetch\').file in the unsaved buffer; native undo restores the pre-binding dirty bytes; layout leaves source unchanged; restart preserves the dirty version but replaces the server instance; reopen restores the saved baseline without the discarded dirty text.',
      observed: 'Exact read-only buffers: version 2 dirty hash 62c5c147...; binding version 3 hash 2d6b2288... and diagnostic cleared; Undo text version 4 hash 62c5c147... identical to the dirty pre-binding text; Reset layout and restart keep that hash; restart changes server instance e2344a11... to 2693b140...; the native save prompt was shown; Don\'t Save and reopen load baseline hash 373346d7... with the dirty marker and binding absent. Images were personally read.',
      evidence: counterProbeEvidence
    },
    {
      id: 'duplicate-bundle-identity',
      hypothesis: 'A package archive carrying two conflicting server/bundle-manifest.json members must be rejected before either identity can be trusted.',
      novelty: 'Existing package canaries mutate a payload or server JAR. This new probe creates an archive-level duplicate manifest name with a conflicting sourceCommit/version, exercising archive enumeration and bundle identity together.',
      actions: 'Copied the final c501014f... VSIX to a private scratch directory, appended a second extension/server/bundle-manifest.json with sourceCommit 000... and version 99.99.99, then invoked the shipped scripts/inventory.mjs with a must-not-exist output path.',
      expected: 'Inventory verification fails on duplicate VSIX entries and emits no success inventory.',
      observed: 'Exit 1 with AssertionError "Duplicate VSIX entries" (20 !== 21); the output file remained absent. The original package hash was unchanged at c501014f...; the scratch archive hash was d83dfc6e...',
      evidence: duplicateEvidence
    }
  ],
  openMandatoryFindings: [],
  missingRequiredChecks: [],
  report: 'review.md',
  summary: 'PASS. All 81 criteria are mapped to current code/tests, supplied raw evidence was independently validated at candidate 54bb123070fc7bd81aea58b4e1191beecf568cbf (standard 918, services 974, zero failures/errors/skips), the docs gate and affected risky tests are green, G01-G29 hashes were checked and G30 was newly run end-to-end in the real installed client. Two new counterprobes are documented: the G30 native compound case and a duplicate-bundle package identity attack. No open mandatory findings; the standard-vs-final VSIX byte difference and the G13 pre-correction image identity are explicitly disclosed and covered by effective-content evidence.'
};

for (const row of verdict.counterprobes) for (const path of row.evidence) assert.ok(existsSync(path), `counterprobe ${row.id}: ${path}`);
for (const row of verdict.executedChecks) for (const path of row.evidence) assert.ok(existsSync(path), `check ${row.id}: ${path}`);
for (const row of verdict.verifiedCurrentEvidence) for (const path of row.evidence) assert.ok(existsSync(path), `verified ${path}`);

writeFileSync(resolve(evidence, 'verdict.json'), JSON.stringify(verdict, null, 2) + '\n');
console.log(`Wrote verdict.json: ${verdict.criteria.length} criteria, ${verdict.guiEvidence.flatMap(row => row.caseIds).length} GUI IDs, ${verdict.counterprobes.length} counterprobes.`);
