#!/usr/bin/env node
// Run the reviewer-owned runtime probe for the when:{false} required-field question.
import { spawnSync } from 'node:child_process';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';

const ROOT = '/Users/stefan/.cache/gretl-tooling-review/P2-review-1';
const EVIDENCE = `${ROOT}/evidence`;
const PRODUCT = `${ROOT}/product`;
const out = `${EVIDENCE}/runtime-probe-out`;
const cp = [
  `${PRODUCT}/gretl-groovy/target/classes`,
  `${PRODUCT}/gretl-groovy/target/test-classes`,
  `${PRODUCT}/gretl-runner/target/classes`,
  `${PRODUCT}/gretl-api/target/classes`,
  `${PRODUCT}/gretl-i18n/target/classes`,
  `${PRODUCT}/gretl-language-contract/target/classes`,
  `${PRODUCT}/gretl-logging/target/classes`,
  '/Users/stefan/.m2/repository/org/apache/groovy/groovy/6.0.0/groovy-6.0.0.jar'
].filter(p => existsSync(p));
mkdirSync(out, { recursive: true });
const compile = spawnSync('javac', ['-encoding', 'UTF-8', '-d', out, '-cp', cp.join(':'), `${EVIDENCE}/P2RuntimeWhenProbe.java`], { encoding: 'utf8' });
if (compile.status !== 0) { console.error(compile.stdout + compile.stderr); process.exit(2); }
const run = spawnSync('java', ['-cp', `${out}:${cp.join(':')}`, 'ch.so.agi.gretl.runtime.groovy.P2RuntimeWhenProbe'], { encoding: 'utf8' });
process.stdout.write(run.stdout || '');
process.stderr.write(run.stderr || '');
writeFileSync(`${EVIDENCE}/runtime-when-probe.log`, (run.stdout || '') + (run.stderr ? '\nSTDERR:\n' + run.stderr : ''));
process.exit(run.status ?? 1);
