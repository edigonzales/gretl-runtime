#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';
const ROOT = '/Users/stefan/.cache/gretl-tooling-review/P2-review-1';
const E = `${ROOT}/evidence`, P = `${ROOT}/product`, out = `${E}/static-probe-out`;
const cp = [
  `${P}/gretl-tooling-core/target/classes`,
  `${P}/gretl-language-contract/target/classes`,
  '/Users/stefan/.m2/repository/org/apache/groovy/groovy/6.0.0/groovy-6.0.0.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
  '/Users/stefan/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar'
];
for (const p of cp) if (!existsSync(p)) { console.error('missing ' + p); process.exit(2); }
mkdirSync(out, { recursive: true });
const c = spawnSync('javac', ['-encoding', 'UTF-8', '-d', out, '-cp', cp.join(':'), `${E}/P2StaticWhenProbe.java`], { encoding: 'utf8' });
if (c.status !== 0) { console.error(c.stdout + c.stderr); process.exit(2); }
const r = spawnSync('java', ['-cp', `${out}:${cp.join(':')}`, 'P2StaticWhenProbe'], { encoding: 'utf8' });
process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
writeFileSync(`${E}/static-when-probe.log`, (r.stdout || '') + (r.stderr ? '\nSTDERR:\n' + r.stderr : ''));
process.exit(r.status ?? 1);
