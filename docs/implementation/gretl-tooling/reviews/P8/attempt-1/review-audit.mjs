import {readFileSync, existsSync, statSync, readdirSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';

const product = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product';
const ev = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const h = b => createHash('sha256').update(b).digest('hex');
const readJson = p => JSON.parse(readFileSync(p, 'utf8'));
const spec = 'docs/implementation/gretl-tooling';
const request = readJson(resolve(ev, 'request.json'));
const out = {candidate: request.candidateSha, checks: [], failures: []};
const check = (name, cond, detail='') => {
  out.checks.push({name, ok: !!cond, detail});
  if (!cond) out.failures.push({name, detail});
};

// --- requirements + rules digests exactly as tools/gretl-tooling/review.mjs
const reqFiles = ['EXECUTION_PLAN.md','ARCHITECTURE.md','TEST_MATRIX.md','OPENCODE_REVIEW_PROMPT.md','ACCEPTANCE_CRITERIA.json','schemas/review-request.schema.json','schemas/review-verdict.schema.json'];
const manifest = reqFiles.sort().map(p => ({path:`${spec}/${p}`, sha256:h(readFileSync(resolve(product, spec, p)))}));
const requirementsDigest = h(JSON.stringify(manifest));
const rulePaths = ['AGENTS.md', `${spec}/AGENTS.md`].sort();
const rules = rulePaths.map(path => ({path, sha256:h(readFileSync(resolve(product,path)))}));
const repositoryRulesDigest = h(JSON.stringify(rules));
check('requirementsDigest', requirementsDigest === request.requirementsDigest, `${requirementsDigest} vs ${request.requirementsDigest}`);
check('repositoryRulesDigest', repositoryRulesDigest === request.repositoryRulesDigest, `${repositoryRulesDigest} vs ${request.repositoryRulesDigest}`);
out.digests = {requirementsDigest, repositoryRulesDigest, manifest, rules};

// --- identity
check('head==candidate', (await import('node:child_process')).execFileSync('git',['rev-parse','HEAD'],{cwd:product,encoding:'utf8'}).trim() === request.candidateSha);

// --- artifacts
out.artifacts = [];
for (const a of request.artifacts) {
  const exists = existsSync(a.path) && statSync(a.path).isFile();
  const actual = exists ? h(readFileSync(a.path)) : null;
  check(`artifact ${a.kind} ${a.path.split('/').slice(-1)[0]}`, exists && actual === a.sha256, `${actual}`);
  out.artifacts.push({path:a.path, expected:a.sha256, actual, exists});
}

// --- model identity
const identity = readJson(request.modelIdentityEvidence);
out.modelIdentity = identity;
check('identity provider', identity.provider === 'deepseek');
check('identity model', identity.model === 'deepseek-flash');
check('identity host', identity.apiHost === 'api.deepseek.com');
check('identity model version', identity.modelVersion === 'DeepSeek-V4.1-Flash-1017');
check('identity actual provider/model', identity.actualProvider === 'deepseek' && identity.actualModel === 'deepseek-flash');
check('identity base url', identity.effectiveBaseUrl === 'https://api.deepseek.com');
check('identity freshness <24h', Date.now() - Date.parse(identity.checkedAt) < 24*3600*1000, identity.checkedAt);

// --- verify one root gate manifest raw
function verifyGate(name, path, argv) {
  const m = readJson(path);
  check(`${name} sha`, m.sha === request.candidateSha, m.sha);
  check(`${name} clean`, m.dirty === '', JSON.stringify(m.dirty));
  check(`${name} argv`, JSON.stringify(m.argv) === JSON.stringify(argv), JSON.stringify(m.argv));
  check(`${name} exit`, m.exit?.code === 0 && m.exit?.signal === null, JSON.stringify(m.exit));
  const start = Date.parse(m.startedAt), end = Date.parse(m.endedAt);
  check(`${name} times`, Number.isFinite(start) && Number.isFinite(end) && end >= start, `${m.startedAt}..${m.endedAt}`);
  const counts = {tests:0, failures:0, errors:0, skipped:0};
  let missing = 0, changed = 0, outside = 0, xmlFiles = 0, capturedFiles = 0;
  const seen = new Set();
  for (const a of m.captured) {
    if (seen.has(a.path)) { check(`${name} duplicate captured`, false, a.path); }
    seen.add(a.path);
    if (!existsSync(a.path) || !statSync(a.path).isFile()) { missing++; continue; }
    capturedFiles++;
    if (h(readFileSync(a.path)) !== a.sha256) changed++;
    const mt = Date.parse(a.sourceModifiedAt);
    if (!(Number.isFinite(mt) && mt >= start && mt <= end)) outside++;
    if (/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(a.path)) {
      xmlFiles++;
      const header = readFileSync(a.path,'utf8').match(/<testsuite\b[^>]*>/)?.[0];
      if (!header) { check(`${name} xml header`, false, a.path); continue; }
      for (const k of Object.keys(counts)) counts[k] += Number(header.match(new RegExp(`\\b${k}="(\\d+)"`))?.[1] ?? 0);
    }
  }
  check(`${name} captured files present`, missing === 0, `${missing} missing of ${m.captured.length}`);
  check(`${name} captured hashes`, changed === 0, `${changed} changed`);
  check(`${name} captured within run`, outside === 0, `${outside} outside`);
  check(`${name} xml files`, xmlFiles > 0, String(xmlFiles));
  check(`${name} counts match raw XML`, JSON.stringify(counts) === JSON.stringify(m.counts), `${JSON.stringify(counts)} vs ${JSON.stringify(m.counts)}`);
  check(`${name} zero failures/errors/skips`, counts.failures === 0 && counts.errors === 0 && counts.skipped === 0);
  out[name] = {counts, capturedFiles, xmlFiles, missing, changed, outside, extraKeys:Object.keys(m).filter(k=>!['sha','dirty','argv','cwd','startedAt','endedAt','exit','counts','captured'].includes(k))};
}
verifyGate('rootStandard', resolve(ev,'root-standard/manifest.json'), ['./mvnw','-B','clean','verify']);
verifyGate('rootServices', resolve(ev,'root-services/manifest.json'), ['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify']);

// --- editor artifacts in gate dirs
out.editorArtifacts = {};
for (const gate of ['root-standard','root-services']) {
  const dir = resolve(ev, gate, 'editor-artifacts');
  const rows = [];
  const walk = d => { for (const e of readdirSync(d,{withFileTypes:true})) { const p = resolve(d,e.name); if (e.isDirectory()) walk(p); else rows.push({path:p, sha256:h(readFileSync(p)), size:statSync(p).size}); } };
  walk(dir);
  out.editorArtifacts[gate] = rows;
  const vsix = rows.find(r=>r.path.endsWith('.vsix'));
  check(`${gate} editor vsix`, vsix && vsix.sha256 === 'c501014f5b1e7bfd26596e6d8cb5df49ff8d89560dc04dfcbbcc11995719fb6e', vsix?.sha256);
}

// --- GUI cases hashes
const casesDir = resolve(ev,'gui/current/cases');
let caseOk = 0, caseBad = [];
for (const f of readdirSync(casesDir).filter(n=>n.endsWith('.json'))) {
  try {
    const m = readJson(resolve(casesDir,f));
    if (m.image) {
      const img = resolve(casesDir, m.image);
      if (!existsSync(img) || h(readFileSync(img)) !== m.imageSha256) caseBad.push(f+':image');
      const ax = resolve(casesDir, f.replace(/\.json$/,'.ax.txt'));
      if (!existsSync(ax) || h(readFileSync(ax)) !== m.axSha256) caseBad.push(f+':ax');
      caseOk++;
    }
  } catch(e) { caseBad.push(f+':parse '+e.message); }
}
check('gui case image/ax hashes', caseBad.length === 0, `${caseOk} checked, bad=${caseBad.join(',')}`);

// --- files.json manifest
try {
  const files = readJson(resolve(ev,'gui/files.json'));
  let fm=0, fb=[];
  for (const f of files) { if (!existsSync(f.path)) { fb.push('missing '+f.path); continue; } const b=readFileSync(f.path); if (b.length!==f.size || h(b)!==f.sha256) fb.push('mismatch '+f.path); else fm++; }
  check('gui files.json', fb.length===0, `${fm}/${files.length} ${fb.slice(0,5).join('; ')}`);
} catch(e) { check('gui files.json', false, e.message); }

console.log(JSON.stringify(out, null, 2));
if (out.failures.length) process.exitCode = 1;
