import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P2-review-4-resume-1/evidence';
const product = '/Users/stefan/.cache/gretl-tooling-review/P2-review-4-resume-1/product';
const src = path.join(evidence, 'counterprobe', 'P2UriEnumIndependentCounterprobe.java');
const out = path.join(evidence, 'counterprobe', 'classes');
fs.mkdirSync(out, { recursive: true });

const cp = [
  path.join(product, 'gretl-language-contract/target/classes'),
  path.join(product, 'gretl-tooling-core/target/classes'),
  path.join(product, 'gretl-api/target/classes'),
  '/Users/stefan/.m2/repository/org/apache/groovy/groovy/6.0.0/groovy-6.0.0.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar',
].join(path.delimiter);

const startedAt = new Date().toISOString();
const compile = spawnSync('/usr/bin/javac', ['-cp', cp, '-d', out, src], { encoding: 'utf8', timeout: 120000 });
const compileLog = `exit=${compile.status} signal=${compile.signal}\nSTDOUT:\n${compile.stdout || ''}\nSTDERR:\n${compile.stderr || ''}\n`;
fs.writeFileSync(path.join(evidence, 'counterprobe', 'uri-compile.log'), compileLog);
if (compile.status !== 0) {
  console.error(compileLog);
  process.exit(compile.status ?? 1);
}

const runStartedAt = new Date().toISOString();
const run = spawnSync('/usr/bin/java', ['-cp', `${out}${path.delimiter}${cp}`, 'P2UriEnumIndependentCounterprobe'], { encoding: 'utf8', timeout: 120000 });
const runLog = `exit=${run.status} signal=${run.signal}\nSTDOUT:\n${run.stdout || ''}\nSTDERR:\n${run.stderr || ''}\n`;
fs.writeFileSync(path.join(evidence, 'counterprobe', 'uri-run.log'), runLog);
const endedAt = new Date().toISOString();

const hash = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const okCount = (run.stdout || '').split(/\r?\n/).filter(l => l.startsWith('OK ')).length;
const summary = {
  startedAt, runStartedAt, endedAt,
  compileExit: { code: compile.status, signal: compile.signal },
  runExit: { code: run.status, signal: run.signal },
  source: { path: src, sha256: hash(src) },
  classpath: `${out}${path.delimiter}${cp}`,
  logs: {
    compile: { path: path.join(evidence, 'counterprobe', 'uri-compile.log'), sha256: hash(path.join(evidence, 'counterprobe', 'uri-compile.log')) },
    run: { path: path.join(evidence, 'counterprobe', 'uri-run.log'), sha256: hash(path.join(evidence, 'counterprobe', 'uri-run.log')) },
  },
  okCount,
  pass: run.status === 0 && (run.stdout || '').includes('URI COUNTERPROBE PASS'),
};
fs.writeFileSync(path.join(evidence, 'counterprobe', 'uri-summary.json'), JSON.stringify(summary, null, 2) + '\n');
console.log(JSON.stringify(summary, null, 2));
if (!summary.pass) process.exit(1);
