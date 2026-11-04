import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { verifyCurrentGateManifest } from '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product/tools/gretl-tooling/review.mjs';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const request = JSON.parse(readFileSync(resolve(evidence, 'request.json'), 'utf8'));
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const gates = [
  {
    id: 'root-standard',
    manifestPath: resolve(evidence, 'root-standard/manifest.json'),
    argv: ['./mvnw', '-B', 'clean', 'verify'],
    expectedTests: 918,
    expectedEditorVsix: 'cdec076b970c1d890a9d1b45e6166ee9da721509d3e31af804c38b095197d6c8'
  },
  {
    id: 'root-services',
    manifestPath: resolve(evidence, 'root-services/manifest.json'),
    argv: ['./mvnw', '-B', '-Pgeotools-probe,backing-services', 'clean', 'verify'],
    expectedTests: 974,
    expectedEditorVsix: 'c501014f5b1e7bfd26596e6d8cb5df49ff8d89560dc04dfcbbcc11995719fb6e'
  }
];

const result = { candidateSha: request.candidateSha, gates: [], artifacts: [] };
for (const gate of gates) {
  const manifest = JSON.parse(readFileSync(gate.manifestPath, 'utf8'));
  verifyCurrentGateManifest(manifest, request.candidateSha, gate.argv);
  if (manifest.counts.tests !== gate.expectedTests) throw new Error(`${gate.id}: expected ${gate.expectedTests} tests`);
  const editorPath = resolve(evidence, gate.id, 'editor-artifacts/dist/gretl-runtime.vsix');
  const editor = [{ path: editorPath, sha256: sha(readFileSync(editorPath)) }];
  if (editor[0].sha256 !== gate.expectedEditorVsix) throw new Error(`${gate.id}: expected disclosed editor artifact ${gate.expectedEditorVsix}, got ${editor[0].sha256}`);
  result.gates.push({
    id: gate.id,
    manifest: gate.manifestPath,
    manifestSha256: sha(readFileSync(gate.manifestPath)),
    argv: manifest.argv,
    cwd: manifest.cwd,
    startedAt: manifest.startedAt,
    endedAt: manifest.endedAt,
    exit: manifest.exit,
    counts: manifest.counts,
    capturedFiles: manifest.captured.length,
    rawTestXmlFiles: manifest.captured.filter(row => /\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(row.path)).length,
    editorArtifacts: editor,
    expectedEditorVsixMatched: gate.expectedEditorVsix
  });
}

for (const artifact of request.artifacts) {
  const actual = sha(readFileSync(artifact.path));
  if (actual !== artifact.sha256) throw new Error(`artifact mismatch: ${artifact.path}`);
  result.artifacts.push({ path: artifact.path, sha256: actual, kind: artifact.kind });
}
result.standardVsFinalDisclosure = {
  standardEditorVsix: gates[0].expectedEditorVsix,
  finalEditorVsix: gates[1].expectedEditorVsix,
  difference: 'Both gates ran on candidate 54bb123070fc7bd81aea58b4e1191beecf568cbf. The standard VSIX was produced by that gate before the final package rebuild; the final request artifact is c501014f... and is byte-identical to the services-gate editor artifact. The disclosed effective-content comparison covers identical client payloads and Java classes/resources with only catalogue-manifest member ordering differing inside the JAR.'
};
writeFileSync(resolve(evidence, 'root-evidence-validation.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
