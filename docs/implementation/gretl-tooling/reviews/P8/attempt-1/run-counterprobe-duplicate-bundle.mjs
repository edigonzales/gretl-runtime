import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { accessSync, constants, copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const original = resolve(evidence, 'artifacts/vscode/gretl-vscode/dist/gretl-runtime.vsix');
const extension = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product/vscode/gretl-vscode';
const scratch = resolve(evidence, 'counterprobes/duplicate-bundle-identity');
const tampered = resolve(scratch, 'gretl-runtime-duplicate-bundle.vsix');
const output = resolve(scratch, 'must-not-exist.json');
const resultFile = resolve(scratch, 'result.json');
const sha = bytes => createHash('sha256').update(bytes).digest('hex');

assert.equal(sha(readFileSync(original)), 'c501014f5b1e7bfd26596e6d8cb5df49ff8d89560dc04dfcbbcc11995719fb6e');
rmSync(scratch, { recursive: true, force: true });
mkdirSync(scratch, { recursive: true });
copyFileSync(original, tampered);

const creator = spawnSync('python3', ['-c', String.raw`
import json, sys, warnings, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as incoming:
    bundle = json.loads(incoming.read('extension/server/bundle-manifest.json'))
bundle['sourceCommit'] = '0' * 40
bundle['extensionVersion'] = '99.99.99'
with warnings.catch_warnings():
    warnings.simplefilter('ignore')
    with zipfile.ZipFile(dst, 'a', compression=zipfile.ZIP_DEFLATED) as outgoing:
        outgoing.writestr('extension/server/bundle-manifest.json', json.dumps(bundle, separators=(',', ':')) + '\n')
`, original, tampered], { encoding: 'utf8' });
assert.equal(creator.status, 0, creator.stderr);

const inspection = spawnSync('python3', ['-c', String.raw`
import collections, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    names = z.namelist()
counts = collections.Counter(names)
print(len(names))
print(json_names := ','.join(sorted(n for n, c in counts.items() if c > 1)))
`, tampered], { encoding: 'utf8' });
assert.equal(inspection.status, 0, inspection.stderr);
const [entryCountText, duplicates] = inspection.stdout.trim().split('\n');
assert.equal(duplicates, 'extension/server/bundle-manifest.json');

const run = spawnSync(process.execPath, ['scripts/inventory.mjs', tampered, output], {
  cwd: extension,
  encoding: 'utf8'
});
const combined = run.stdout + run.stderr;
assert.notEqual(run.status, 0, 'duplicate bundle identity was accepted');
assert.match(combined, /Duplicate VSIX entries/);
assert.equal(existsSync(output), false, 'inventory output was emitted for a rejected archive');
let outputStatus = 'absent';
try { accessSync(output, constants.F_OK); outputStatus = 'present'; } catch {}

const result = {
  id: 'duplicate-bundle-identity',
  candidateSha: '54bb123070fc7bd81aea58b4e1191beecf568cbf',
  originalVsix: original,
  originalSha256: sha(readFileSync(original)),
  tamperedVsix: tampered,
  tamperedSha256: sha(readFileSync(tampered)),
  archiveEntryCount: Number(entryCountText),
  duplicateNames: duplicates.split(','),
  accepted: run.status === 0,
  exitCode: run.status,
  outputStatus,
  stderr: combined.trim(),
  assertion: 'Rejected before trusting either conflicting server/bundle-manifest.json member; no success inventory emitted.'
};
writeFileSync(resultFile, JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
