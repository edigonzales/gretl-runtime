import { readFileSync, writeFileSync, existsSync, statSync, readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { join, relative } from 'node:path';

const PRODUCT = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product';
const OUT = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/identity-status-result.json';
const IDENTITY = '/Users/stefan/.cache/gretl-tooling-review/p2-resume-readiness/identity.json';
const WORKSPACE = '/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/workspace ä space';
const CANDIDATE = '53f7f3daaf92d002fd75978c25471321fd99a310';
const SECRET_KEY = /(key|token|secret|password|credential|authorization|cookie|bearer|api[-_]?key)/i;

function git(args) {
  const r = spawnSync('git', args, { cwd: PRODUCT, encoding: 'utf8' });
  return { status: r.status, signal: r.signal, stdout: r.stdout ?? '', stderr: r.stderr ?? '' };
}
function safeTree(value, path = '$', out = [], depth = 0) {
  if (out.length >= 200 || depth > 6) return out;
  if (value === null || typeof value !== 'object') {
    const key = path.split('.').at(-1).replace(/\[.*?\]/g, '');
    out.push({ path, type: typeof value, value: SECRET_KEY.test(key) ? '<redacted>' : value });
    return out;
  }
  if (Array.isArray(value)) {
    out.push({ path, type: 'array', length: value.length });
    value.slice(0, 20).forEach((v, i) => safeTree(v, `${path}[${i}]`, out, depth + 1));
    return out;
  }
  out.push({ path, type: 'object', keys: Object.keys(value) });
  for (const [k, v] of Object.entries(value)) safeTree(v, `${path}.${k}`, out, depth + 1);
  return out;
}
function fileInfo(path) {
  if (!existsSync(path)) return { path, exists: false };
  const st = statSync(path);
  if (!st.isFile()) return { path, exists: true, isFile: false, isDirectory: st.isDirectory() };
  const bytes = readFileSync(path);
  return { path, exists: true, isFile: true, size: st.size, mtime: st.mtime.toISOString(), sha256: createHash('sha256').update(bytes).digest('hex') };
}
function walk(root, depth = 0, out = []) {
  if (out.length >= 300 || depth > 4 || !existsSync(root)) return out;
  for (const name of readdirSync(root)) {
    const full = join(root, name);
    const st = statSync(full);
    const rel = relative(WORKSPACE, full);
    if (st.isDirectory()) { out.push({ path: rel, kind: 'dir' }); walk(full, depth + 1, out); }
    else if (st.isFile()) out.push({ ...fileInfo(full), path: rel, absolutePath: full });
  }
  return out;
}

const head = git(['rev-parse', 'HEAD']);
const status = git(['status', '--porcelain=v1', '--untracked-files=all']);
const branch = git(['rev-parse', '--abbrev-ref', 'HEAD']);
let identity = { path: IDENTITY, exists: existsSync(IDENTITY) };
if (identity.exists) {
  identity = { ...identity, ...fileInfo(IDENTITY) };
  try {
    identity.parsed = JSON.parse(readFileSync(IDENTITY, 'utf8'));
    identity.safeTree = safeTree(identity.parsed);
    delete identity.parsed;
  } catch (e) {
    identity.parseError = String(e);
  }
}
const result = {
  checkedAt: new Date().toISOString(),
  candidateExpected: CANDIDATE,
  git: {
    head: head.stdout.trim(),
    headMatchesCandidate: head.stdout.trim() === CANDIDATE,
    branch: branch.stdout.trim(),
    status: status.stdout,
    statusClean: status.stdout.trim() === '',
    statusExit: status.status,
  },
  identity,
  workspace: {
    path: WORKSPACE,
    exists: existsSync(WORKSPACE),
    files: walk(WORKSPACE),
  },
};
writeFileSync(OUT, JSON.stringify(result, null, 2));
console.log(JSON.stringify({
  git: result.git,
  identity: { path: identity.path, exists: identity.exists, size: identity.size, sha256: identity.sha256, safeTree: identity.safeTree },
  workspace: { path: WORKSPACE, exists: result.workspace.exists, files: result.workspace.files.map((f) => ({ path: f.path, kind: f.kind, size: f.size, sha256: f.sha256 })) },
}, null, 2));
