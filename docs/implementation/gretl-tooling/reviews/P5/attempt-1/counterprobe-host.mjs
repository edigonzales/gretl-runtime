import { createRequire } from 'node:module';
import { mkdtemp, mkdir, writeFile, symlink, rm, realpath } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

const extension = '/Users/stefan/.cache/gretl-tooling-review/P5-review-1/product/vscode/gretl-vscode';
const requireFromExtension = createRequire(path.join(extension, 'package.json'));
const { build } = requireFromExtension('esbuild');
const temp = await mkdtemp(path.join(await realpath(os.tmpdir()), 'gretl-review-counterprobe-'));

const guardOut = path.join(temp, 'guard.mjs');
await build({
  entryPoints: [path.join(extension, 'src/edit-guard.ts')],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: guardOut,
  absWorkingDir: extension,
});
const { diskSourceHash } = await import(pathToFileURL(guardOut));

const hostOut = path.join(temp, 'host.mjs');
await build({
  entryPoints: [path.join(extension, 'src/host.ts')],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: hostOut,
  absWorkingDir: extension,
});
const { sourceDigest } = await import(pathToFileURL(hostOut));

const results = [];
async function check(name, operation) {
  try {
    await operation();
    results.push({ name, passed: true });
  } catch (error) {
    results.push({ name, passed: false, error: String(error) });
  }
}

// 1. The exact 4 MiB boundary is accepted; 4 MiB + 1 byte is rejected.
const exact = path.join(temp, 'exact.groovy');
const exactText = 'a'.repeat(4 * 1024 * 1024);
await writeFile(exact, exactText);
await check('exactly 4 MiB regular source is hashable', async () => {
  const actual = await diskSourceHash(pathToFileURL(exact).toString());
  if (actual !== sourceDigest(exactText)) throw new Error('exact-boundary digest differs');
});
const over = path.join(temp, 'over.groovy');
await writeFile(over, Buffer.alloc(4 * 1024 * 1024 + 1, 0x61));
await check('4 MiB + 1 byte is rejected at the byte limit', async () => {
  try {
    await diskSourceHash(pathToFileURL(over).toString());
  } catch (error) {
    if (!/byte limit/.test(String(error))) throw error;
    return;
  }
  throw new Error('over-limit file was accepted');
});

// 2. Invalid UTF-8 closed source can never be treated as an editable current source.
const invalid = path.join(temp, 'invalid.groovy');
await writeFile(invalid, Buffer.from([0x61, 0xff, 0x62]));
await check('invalid UTF-8 closed source is rejected, not silently hashed', async () => {
  try {
    await diskSourceHash(pathToFileURL(invalid).toString());
  } catch {
    return;
  }
  throw new Error('invalid UTF-8 source was accepted');
});

// 3. A parent directory introduced as a symlink after capture is rejected.
const realDir = path.join(temp, 'real-dir');
await mkdir(realDir);
await writeFile(path.join(realDir, 'job.groovy'), "action('a') {}\n");
const linkedDir = path.join(temp, 'linked-dir');
await symlink(realDir, linkedDir, 'dir');
await check('parent-directory symlink is rejected by the component walk', async () => {
  try {
    await diskSourceHash(pathToFileURL(path.join(linkedDir, 'job.groovy')).toString());
  } catch (error) {
    if (!/symlink/.test(String(error))) throw error;
    return;
  }
  throw new Error('parent-directory symlink was followed');
});

// 4. UTF-16BE digest of a lone low surrogate is stable and distinct from replacement text.
await check('lone low surrogate has a stable UTF-16BE digest', async () => {
  const expected = createHash('sha256').update(Buffer.from([0xdc, 0x00])).digest('hex');
  const actual = sourceDigest('\uDC00');
  if (actual !== expected) throw new Error('lone low surrogate digest differs');
  if (actual === sourceDigest('\uFFFD')) throw new Error('lone surrogate collapsed to replacement character');
});

const report = {
  kind: 'NEW_REVIEW_HOST_COUNTERPROBE',
  candidate: '4520ee6960d8b0857fad828f972a44950c5aeb09',
  completedAt: new Date().toISOString(),
  scope: 'edit-guard disk/source precondition boundary; no GUI claim',
  results,
  passed: results.every((result) => result.passed),
};
fs.writeFileSync('/Users/stefan/.cache/gretl-tooling-review/P5-review-1/evidence/counterprobe-host.json', JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
await rm(temp, { recursive: true, force: true });
process.exit(report.passed ? 0 : 1);
