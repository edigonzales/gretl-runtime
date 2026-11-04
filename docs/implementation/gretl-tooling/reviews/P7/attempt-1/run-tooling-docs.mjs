import { spawnSync } from 'node:child_process';
import { writeFileSync, readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

const PRODUCT = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product';
const OUT = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/tooling-docs-result.json';
const TOOL = `${PRODUCT}/tools/gretl-tooling`;

function run(label, cmd, args, cwd, timeout = 600000) {
  const startedAt = new Date().toISOString();
  const r = spawnSync(cmd, args, { cwd, encoding: 'utf8', timeout, env: { ...process.env, CI: '1' } });
  const endedAt = new Date().toISOString();
  return {
    label, cmd, args, cwd, startedAt, endedAt,
    status: r.status, signal: r.signal, error: r.error ? String(r.error) : null,
    stdout: r.stdout ?? '', stderr: r.stderr ?? '',
    exit: r.status === 0 && !r.signal && !r.error,
  };
}

const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const steps = [];
steps.push(run('npm-ci', npm, ['ci', '--ignore-scripts'], TOOL));
steps.push(run('check-docs', process.execPath, ['tools/gretl-tooling/check-docs.mjs'], PRODUCT, 120000));

const result = {
  checkedAt: new Date().toISOString(),
  product: PRODUCT,
  steps: steps.map((s) => ({
    ...s,
    stdout: s.stdout.slice(-12000),
    stderr: s.stderr.slice(-12000),
  })),
  passed: steps.every((s) => s.exit),
  npmVersion: (() => {
    const r = spawnSync(npm, ['--version'], { encoding: 'utf8' });
    return r.status === 0 ? r.stdout.trim() : null;
  })(),
  nodeVersion: process.version,
};
writeFileSync(OUT, JSON.stringify(result, null, 2));
for (const s of steps) {
  console.log(`${s.label}: exit=${s.status} signal=${s.signal ?? '-'} error=${s.error ?? '-'}`);
  if (s.stdout.trim()) console.log(`stdout: ${s.stdout.trim().slice(-2000)}`);
  if (s.stderr.trim()) console.log(`stderr: ${s.stderr.trim().slice(-2000)}`);
}
console.log(`passed=${result.passed} wrote=${OUT}`);
process.exitCode = result.passed ? 0 : 1;
