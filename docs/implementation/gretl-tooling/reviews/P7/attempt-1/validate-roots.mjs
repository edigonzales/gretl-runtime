import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, statSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const CANDIDATE = '53f7f3daaf92d002fd75978c25471321fd99a310';
const gates = [
  { id: 'root-standard', dir: 'root-standard', expected: 918 },
  { id: 'root-services', dir: 'root-services', expected: 974 },
];
const results = [];

function sha256(buf) {
  return createHash('sha256').update(buf).digest('hex');
}

for (const gate of gates) {
  const dir = resolve(ROOT, gate.dir);
  const manifestPath = resolve(dir, 'manifest.json');
  const commandPath = resolve(dir, 'command.log');
  const manifestBytes = readFileSync(manifestPath);
  const manifest = JSON.parse(manifestBytes.toString('utf8'));
  const problems = [];
  if (manifest.sha !== CANDIDATE) problems.push(`sha ${manifest.sha}`);
  if (manifest.dirty !== '') problems.push(`dirty ${JSON.stringify(manifest.dirty)}`);
  if (manifest.exit?.code !== 0 || manifest.exit?.signal !== null) problems.push(`exit ${JSON.stringify(manifest.exit)}`);
  for (const [key, expected] of Object.entries({ tests: gate.expected, failures: 0, errors: 0, skipped: 0 })) {
    if (manifest.counts?.[key] !== expected) problems.push(`counts.${key}=${manifest.counts?.[key]} expected ${expected}`);
  }
  const started = Date.parse(manifest.startedAt);
  const ended = Date.parse(manifest.endedAt);
  if (!Number.isFinite(started) || !Number.isFinite(ended) || ended <= started) problems.push('invalid run window');
  if (!Array.isArray(manifest.captured) || manifest.captured.length === 0) problems.push('no captured entries');
  let capturedBad = 0;
  let capturedChecked = 0;
  let recordedTimeBad = 0;
  let copyMtimeMismatches = 0;
  let copyMtimes = [];
  const xmlFiles = [];
  const seen = new Set();
  for (const item of manifest.captured ?? []) {
    if (!item?.path) { capturedBad++; problems.push('captured entry without path'); continue; }
    if (!item.path.startsWith('/Users/stefan/.cache/gretl-tooling-review/')) problems.push(`captured path outside review cache: ${item.path}`);
    if (seen.has(item.path)) problems.push(`duplicate captured path: ${item.path}`);
    seen.add(item.path);
    if (!existsSync(item.path)) { capturedBad++; problems.push(`missing captured: ${item.path}`); continue; }
    const st = statSync(item.path);
    if (!st.isFile()) { capturedBad++; problems.push(`not regular file: ${item.path}`); continue; }
    const bytes = readFileSync(item.path);
    const digest = sha256(bytes);
    if (digest !== item.sha256) { capturedBad++; problems.push(`sha mismatch: ${item.path}`); }
    const sourceTime = Date.parse(item.sourceModifiedAt);
    if (!Number.isFinite(sourceTime)) { recordedTimeBad++; problems.push(`unparseable sourceModifiedAt: ${item.path}`); }
    else if (sourceTime < started - 1000 || sourceTime > ended + 1000) { recordedTimeBad++; problems.push(`recorded source time outside run window: ${item.path} at ${item.sourceModifiedAt}`); }
    if (Number.isFinite(sourceTime) && Math.abs(st.mtimeMs - sourceTime) > 1500) {
      copyMtimeMismatches++;
      if (copyMtimes.length < 5) copyMtimes.push({ path: item.path, stMtime: st.mtimeMs, sourceModifiedAt: item.sourceModifiedAt });
    }
    if (/\/TEST-.*\.xml$/.test(item.path)) xmlFiles.push(item.path);
    capturedChecked++;
  }
  let xmlTests = 0, xmlFailures = 0, xmlErrors = 0, xmlSkipped = 0, xmlBad = 0;
  let xmlSuites = 0;
  for (const path of xmlFiles) {
    const text = readFileSync(path, 'utf8');
    const m = text.match(/<testsuite\b[^>]*>/);
    if (!m) { xmlBad++; problems.push(`no testsuite header: ${path}`); continue; }
    xmlSuites++;
    const attr = (name) => {
      const mm = m[0].match(new RegExp(`\\b${name}="(\\d+)"`));
      return mm ? Number(mm[1]) : null;
    };
    const tests = attr('tests'), failures = attr('failures'), errors = attr('errors'), skipped = attr('skipped');
    if ([tests, failures, errors, skipped].some((v) => v === null)) { xmlBad++; problems.push(`missing count attr: ${path}`); continue; }
    if (failures !== 0 || errors !== 0 || skipped !== 0) { xmlBad++; problems.push(`nonzero xml counts: ${path}`); }
    xmlTests += tests; xmlFailures += failures; xmlErrors += errors; xmlSkipped += skipped;
  }
  if (xmlTests !== manifest.counts?.tests) problems.push(`XML tests ${xmlTests} != manifest ${manifest.counts?.tests}`);
  if (xmlFailures !== 0 || xmlErrors !== 0 || xmlSkipped !== 0) problems.push(`XML nonzero totals ${xmlFailures}/${xmlErrors}/${xmlSkipped}`);
  let log = '';
  if (!existsSync(commandPath)) problems.push('missing command.log');
  else log = readFileSync(commandPath, 'utf8');
  const buildSuccesses = (log.match(/BUILD SUCCESS/g) ?? []).length;
  const buildFailures = (log.match(/BUILD FAILURE/g) ?? []).length;
  const errorLines = log.split(/\r?\n/).filter((l) => /\[ERROR\]|FAILURE!|Tests in error:/.test(l));
  if (buildSuccesses < 1) problems.push('no BUILD SUCCESS in command.log');
  if (buildFailures !== 0) problems.push('BUILD FAILURE in command.log');
  if (errorLines.length) problems.push(`error lines in command.log: ${errorLines.slice(0, 3).join(' | ')}`);
  const expectedArgv = gate.id === 'root-standard'
    ? ['./mvnw', '-B', 'clean', 'verify']
    : ['./mvnw', '-B', '-Pgeotools-probe,backing-services', 'clean', 'verify'];
  if (JSON.stringify(manifest.argv) !== JSON.stringify(expectedArgv)) problems.push(`argv ${JSON.stringify(manifest.argv)}`);
  results.push({
    id: gate.id,
    passed: problems.length === 0,
    manifest: manifestPath,
    manifestSha256: sha256(manifestBytes),
    commandLog: commandPath,
    commandLogSha256: existsSync(commandPath) ? sha256(readFileSync(commandPath)) : null,
    runWindow: [manifest.startedAt, manifest.endedAt],
    durationSeconds: Math.round((ended - started) / 1000),
    counts: manifest.counts,
    capturedEntries: manifest.captured?.length ?? 0,
    capturedChecked,
    capturedBad,
    recordedTimeBad,
    copyMtimeMismatches,
    copyMtimeExamples: copyMtimes,
    xmlSuites,
    xmlMeasuredCounts: { tests: xmlTests, failures: xmlFailures, errors: xmlErrors, skipped: xmlSkipped },
    buildSuccesses,
    buildFailures,
    errorLines: errorLines.slice(0, 10),
    problemCount: problems.length,
    problemsSample: problems.slice(0, 40),
    note: problems.length === 0
      ? 'All captured content hashes and recorded source times validate; captured copy mtimes were reset by staging, so recorded sourceModifiedAt values were validated against the run window.'
      : undefined,
  });
  console.log(`${gate.id}: ${problems.length === 0 ? 'PASS' : 'FAIL'} captured=${capturedChecked}/${manifest.captured?.length} badHashes=${capturedBad} recordedTimeBad=${recordedTimeBad} copyMtimeMismatches=${copyMtimeMismatches} xmlSuites=${xmlSuites} xmlTests=${xmlTests} window=${manifest.startedAt}..${manifest.endedAt} problems=${problems.length}`);
  if (problems.length) console.log(problems.slice(0, 10).join('\n'));
}
const outPath = resolve(ROOT, 'validate-roots-result.json');
writeFileSync(outPath, JSON.stringify({ candidate: CANDIDATE, results }, null, 2));
console.log(`wrote ${outPath}`);
if (results.some((r) => !r.passed)) process.exitCode = 1;
