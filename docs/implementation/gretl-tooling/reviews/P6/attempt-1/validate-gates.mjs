import {readFileSync, existsSync, statSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const evidence = dirname(fileURLToPath(import.meta.url));
const repo = '/Users/stefan/.cache/gretl-tooling-review/P6-review-1/product';
const sha = b => createHash('sha256').update(b).digest('hex');
const readJson = p => JSON.parse(readFileSync(p, 'utf8'));
const out = {checkedAt: new Date().toISOString(), gates: {}, artifacts: {}, requirements: {}, problems: []};
const bad = (m) => { out.problems.push(m); };

const REQ = readJson(resolve(evidence, 'request.json'));

// --- requirements digest, independently reconstructed from the named normative files ---
const spec = 'docs/implementation/gretl-tooling';
const files = ['EXECUTION_PLAN.md','ARCHITECTURE.md','TEST_MATRIX.md','OPENCODE_REVIEW_PROMPT.md','ACCEPTANCE_CRITERIA.json','schemas/review-request.schema.json','schemas/review-verdict.schema.json'].sort();
const manifest = files.map(p => ({path: `${spec}/${p}`, sha256: sha(readFileSync(resolve(repo, spec, p)))}));
const rules = ['AGENTS.md', `${spec}/AGENTS.md`].sort().map(path => ({path, sha256: sha(readFileSync(resolve(repo, path)))}));
out.requirements = {
  files: manifest,
  computedRequirementsDigest: sha(JSON.stringify(manifest)),
  requestRequirementsDigest: REQ.requirementsDigest,
  requirementsMatch: sha(JSON.stringify(manifest)) === REQ.requirementsDigest,
  computedRulesDigest: sha(JSON.stringify(rules)),
  requestRulesDigest: REQ.repositoryRulesDigest,
  rulesMatch: sha(JSON.stringify(rules)) === REQ.repositoryRulesDigest
};
if (!out.requirements.requirementsMatch) bad('requirementsDigest mismatch');
if (!out.requirements.rulesMatch) bad('repositoryRulesDigest mismatch');

// --- requirement/registry identity ---
const registry = readJson(resolve(repo, spec, 'ACCEPTANCE_CRITERIA.json'));
const p6 = registry.phases.find(p => p.id === 'P6');
out.registry = {criteria: p6.criteria.map(c => c.id), guiCases: p6.guiCases, testFocus: p6.testFocus};
if (JSON.stringify([...p6.criteria.map(c => c.id)].sort()) !== JSON.stringify([...REQ.criteria].sort())) bad('P6 criteria set differs from registry');
if (JSON.stringify([...p6.guiCases].sort()) !== JSON.stringify([...REQ.requiredGuiCases].sort())) bad('P6 GUI set differs from registry');

// --- gate manifests ---
for (const [name, argv] of [
  ['root-standard', ['./mvnw','-B','clean','verify']],
  ['root-services', ['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify']]
]) {
  const m = readJson(resolve(evidence, name, 'manifest.json'));
  const start = Date.parse(m.startedAt), end = Date.parse(m.endedAt);
  const counts = {tests:0, failures:0, errors:0, skipped:0};
  const seen = new Set();
  const missing = [], hashMismatch = [], timeMismatch = [], xmlMismatch = [];
  for (const c of m.captured) {
    if (seen.has(c.path)) bad(`${name}: duplicate captured ${c.path}`);
    seen.add(c.path);
    if (!existsSync(c.path)) { missing.push(c.path); continue; }
    const bytes = readFileSync(c.path);
    if (sha(bytes) !== c.sha256) hashMismatch.push(c.path);
    const mt = Date.parse(c.sourceModifiedAt);
    if (!(Number.isFinite(mt) && mt >= start && mt <= end)) timeMismatch.push({path: c.path, sourceModifiedAt: c.sourceModifiedAt});
    if (/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(c.path)) {
      const header = bytes.toString('utf8').match(/<testsuite\b[^>]*>/)?.[0];
      if (!header) { xmlMismatch.push(c.path); continue; }
      for (const k of Object.keys(counts)) counts[k] += Number(header.match(new RegExp(`\\b${k}="(\\d+)"`))?.[1] ?? 0);
    }
  }
  const log = readFileSync(resolve(evidence, name, 'command.log'), 'utf8');
  const logNoAnsi = log.replace(/\u001b\[[0-9;]*m/g, '');
  const buildSuccess = /BUILD SUCCESS/.test(logNoAnsi);
  const totals = [...logNoAnsi.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g)].reduce((a, x) => {
    a.tests += +x[1]; a.failures += +x[2]; a.errors += +x[3]; a.skipped += +x[4]; return a;
  }, {tests:0, failures:0, errors:0, skipped:0});
  out.gates[name] = {
    sha: m.sha, dirty: m.dirty, argv: m.argv, cwd: m.cwd,
    startedAt: m.startedAt, endedAt: m.endedAt, durationMs: end - start,
    exit: m.exit, declaredCounts: m.counts, xmlCounts: counts, logTotals: totals,
    captured: m.captured.length, missingCount: missing.length, hashMismatchCount: hashMismatch.length,
    timeMismatchCount: timeMismatch.length, xmlMismatchCount: xmlMismatch.length,
    buildSuccess, javaHome: (logNoAnsi.match(/Java home: (.+)/) || [])[1] || null,
    javaVersion: (logNoAnsi.match(/Java version: ([^,]+), vendor: ([^,]+)/) || []).slice(1)
  };
  if (m.sha !== REQ.candidateSha) bad(`${name}: candidate sha mismatch`);
  if (m.dirty !== '') bad(`${name}: dirty flag`);
  if (m.exit.code !== 0 || m.exit.signal !== null) bad(`${name}: non-zero exit/signal`);
  if (JSON.stringify(m.argv) !== JSON.stringify(argv)) bad(`${name}: argv mismatch`);
  if (!(end >= start)) bad(`${name}: bad times`);
  if (missing.length) bad(`${name}: ${missing.length} captured files missing`);
  if (hashMismatch.length) bad(`${name}: ${hashMismatch.length} captured hash mismatches`);
  if (timeMismatch.length) bad(`${name}: ${timeMismatch.length} captures outside gate interval`);
  if (xmlMismatch.length) bad(`${name}: ${xmlMismatch.length} missing test suite headers`);
  if (JSON.stringify(counts) !== JSON.stringify(m.counts)) bad(`${name}: XML counts differ from manifest`);
  if (totals.failures || totals.errors || totals.skipped) bad(`${name}: failures/errors/skips in log summary`);
  if (counts.failures || counts.errors || counts.skipped) bad(`${name}: failures/errors/skips in raw XML`);
  if (!buildSuccess) bad(`${name}: no BUILD SUCCESS in raw log`);

  // review-local copy of raw surefire/failsafe XML: same bytes as captured originals where present
  const localRoot = resolve(evidence, name, 'evidence');
  const localMissing = [];
  let localChecked = 0;
  for (const c of m.captured) {
    if (!/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(c.path)) continue;
    const rel = c.path.slice(c.path.indexOf('/evidence/') + '/evidence/'.length);
    const local = resolve(localRoot, rel);
    if (!existsSync(local)) { localMissing.push(local); continue; }
    localChecked++;
    if (sha(readFileSync(local)) !== sha(readFileSync(c.path))) bad(`${name}: local copy differs ${local}`);
  }
  out.gates[name].localRawXml = {checked: localChecked, missing: localMissing.length};
  if (localMissing.length) bad(`${name}: ${localMissing.length} raw XML copies missing from review evidence`);
}

// --- request artifacts ---
for (const a of REQ.artifacts) {
  const ok = existsSync(a.path) && statSync(a.path).isFile();
  const got = ok ? sha(readFileSync(a.path)) : null;
  out.artifacts[a.path] = {exists: ok, expected: a.sha256, actual: got, match: got === a.sha256, kind: a.kind};
  if (!ok || got !== a.sha256) bad(`artifact missing or hash mismatch: ${a.path}`);
}

// --- final VSIX server payload cross-boundary identity (request artifact vs installed evidence) ---
const qualification = readJson(resolve(evidence, 'gui/current/qualification.json'));
out.qualification = qualification;
if (qualification.candidateSha !== REQ.candidateSha) bad('qualification candidate mismatch');
if (qualification.finalVsixSha256 !== REQ.artifacts.find(a => a.path.endsWith('gretl-runtime.vsix'))?.sha256) bad('qualification VSIX sha mismatch');
const serverArtifact = REQ.artifacts.find(a => a.path.endsWith('gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar'));
if (qualification.finalServerSha256 !== serverArtifact?.sha256) bad('qualification server sha mismatch');
const vsixPath = REQ.artifacts.find(a => a.path.endsWith('gretl-runtime.vsix'))?.path;
const serverPath = serverArtifact?.path;
out.crossArtifacts = {vsixPath, serverPath, vsixExists: existsSync(vsixPath), serverExists: existsSync(serverPath)};

const id = readJson(resolve(evidence, 'gui/current/identity.json'));
out.guiIdentity = id;

writeFileSync(resolve(evidence, 'gate-validation.json'), JSON.stringify(out, null, 2) + '\n');
console.log(JSON.stringify({problems: out.problems, standard: out.gates['root-standard'], services: out.gates['root-services'], requirements: out.requirements.requirementsMatch, rules: out.requirements.rulesMatch}, null, 2));
