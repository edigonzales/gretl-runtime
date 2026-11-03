#!/usr/bin/env node
// Compile and run the reviewer-owned control-flow counterprobe against the
// freshly built candidate classes. Writes only under evidence/.
import { spawnSync } from 'node:child_process';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';

const P = '/Users/stefan/.cache/gretl-tooling-review/P2-review-2/product';
const E = '/Users/stefan/.cache/gretl-tooling-review/P2-review-2/evidence';
const out = `${E}/control-flow-probe-out`;
const cp = [
  `${P}/gretl-groovy/target/classes`,
  `${P}/gretl-groovy/target/test-classes`,
  `${P}/gretl-runner/target/classes`,
  `${P}/gretl-api/target/classes`,
  `${P}/gretl-i18n/target/classes`,
  `${P}/gretl-language-contract/target/classes`,
  `${P}/gretl-tooling-core/target/classes`,
  '/Users/stefan/.m2/repository/org/apache/groovy/groovy/6.0.0/groovy-6.0.0.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar'
];
for (const p of cp) if (!existsSync(p)) { console.error('missing ' + p); process.exit(2); }
mkdirSync(out, { recursive: true });
const compile = spawnSync('javac', ['-encoding', 'UTF-8', '-d', out, '-cp', cp.join(':'), `${E}/P2ControlFlowProbe.java`], { encoding: 'utf8' });
process.stdout.write(compile.stdout || '');
process.stderr.write(compile.stderr || '');
if (compile.status !== 0) { console.error('javac failed ' + compile.status); process.exit(2); }
const run = spawnSync('java', ['-cp', `${out}:${cp.join(':')}`, 'ch.so.agi.gretl.runtime.groovy.P2ControlFlowProbe'], { encoding: 'utf8' });
process.stdout.write(run.stdout || '');
process.stderr.write(run.stderr || '');
writeFileSync(`${E}/control-flow-probe.log`, (run.stdout || '') + (run.stderr ? '\nSTDERR:\n' + run.stderr : ''));
process.exit(run.status ?? 1);
