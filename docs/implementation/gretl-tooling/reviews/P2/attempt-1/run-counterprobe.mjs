#!/usr/bin/env node
// Compile and run the reviewer-owned P2 counterprobe against the freshly built
// candidate classes. No product file is written; output stays in evidence/.
import { spawnSync } from 'node:child_process';
import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'node:fs';

const ROOT = '/Users/stefan/.cache/gretl-tooling-review/P2-review-1';
const EVIDENCE = `${ROOT}/evidence`;
const PRODUCT = `${ROOT}/product`;
const out = `${EVIDENCE}/counterprobe-out`;
const cp = [
  `${PRODUCT}/gretl-tooling-core/target/classes`,
  `${PRODUCT}/gretl-language-contract/target/classes`,
  '/Users/stefan/.m2/repository/org/apache/groovy/groovy/6.0.0/groovy-6.0.0.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar'
];
for (const p of cp) {
  if (!existsSync(p)) { console.error('Missing classpath entry ' + p); process.exit(2); }
}
mkdirSync(out, { recursive: true });
const compile = spawnSync('javac', ['-encoding', 'UTF-8', '-d', out, '-cp', cp.join(':'), `${EVIDENCE}/P2ReviewCounterprobe.java`], { encoding: 'utf8' });
process.stdout.write(compile.stdout || '');
process.stderr.write(compile.stderr || '');
if (compile.status !== 0) { console.error('javac failed with ' + compile.status); process.exit(2); }
const run = spawnSync('java', ['-cp', `${out}:${cp.join(':')}`, 'P2ReviewCounterprobe'], { encoding: 'utf8' });
process.stdout.write(run.stdout || '');
process.stderr.write(run.stderr || '');
writeFileSync(`${EVIDENCE}/counterprobe-run.log`, (run.stdout || '') + (run.stderr ? '\nSTDERR:\n' + run.stderr : ''));
process.exit(run.status ?? 1);
