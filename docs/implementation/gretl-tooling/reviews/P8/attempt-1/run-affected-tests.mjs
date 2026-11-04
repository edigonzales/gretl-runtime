import {spawnSync} from 'node:child_process';
import {writeFileSync, cpSync, rmSync, existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';

const product = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product';
const ev = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const h = b => createHash('sha256').update(b).digest('hex');
const rows = [];
function run(id, argv, cwd, timeout=900000) {
  const started = new Date().toISOString();
  const r = spawnSync(argv[0], argv.slice(1), {cwd, encoding:'utf8', timeout, maxBuffer:64<<20, env:{...process.env, PWD:cwd}});
  const out = r.stdout || '', err = r.stderr || '';
  const tests = [...out.matchAll(/^# tests (\d+)$/gm)].map(m=>Number(m[1])).reduce((a,b)=>a+b,0);
  const fail = [...out.matchAll(/^# fail (\d+)$/gm)].map(m=>Number(m[1])).reduce((a,b)=>a+b,0);
  const skip = [...out.matchAll(/^# skipped (\d+)$/gm)].map(m=>Number(m[1])).reduce((a,b)=>a+b,0);
  rows.push({id, cwd, argv, started, ended:new Date().toISOString(), exitCode:r.status, signal:r.signal, tests, failures:fail, skips:skip, stdout:out.slice(-12000), stderr:err.slice(-8000)});
  console.log(`\n### ${id}: exit=${r.status} tests=n/a\n${out.slice(-2500)}${err.slice(-1500)}`);
  return r;
}

// Give the package tests the final candidate's shipped dist artifacts (ignored build path).
const dist = resolve(product,'vscode/gretl-vscode/dist');
rmSync(dist,{recursive:true,force:true});
cpSync(resolve(ev,'artifacts/vscode/gretl-vscode/dist'), dist, {recursive:true});

run('tooling-review-tests', ['node','--test','tools/gretl-tooling/review.test.mjs','tools/gretl-tooling/protocol.test.mjs'], product);
run('extension-risky-tests', ['node','--test','test/workbench-evidence.test.mjs','test/modeler-layout.test.mjs','test/modeler-controller.test.mjs'], resolve(product,'vscode/gretl-vscode'));
run('extension-package-tests', ['node','--test','test/package/distribution.test.mjs','test/package/server-integrity.test.mjs'], resolve(product,'vscode/gretl-vscode'));

// Independent inventory verification of the exact supplied VSIX.
const invOut = resolve(ev,'own-final-vsix-inventory.json');
const inv = run('own-inventory-final-vsix', ['node','scripts/inventory.mjs', resolve(ev,'artifacts/vscode/gretl-vscode/dist/gretl-runtime.vsix'), invOut], resolve(product,'vscode/gretl-vscode'));
if (existsSync(invOut)) rows.push({id:'own-inventory-artifact', path:invOut, sha256:h(await (await import('node:fs/promises')).readFile(invOut))});

writeFileSync(resolve(ev,'affected-tests-result.json'), JSON.stringify({candidate:'54bb123070fc7bd81aea58b4e1191beecf568cbf', rows}, null, 2)+'\n');
console.log('\nwrote affected-tests-result.json');
process.exitCode = rows.some(r=>r.exitCode!==undefined && r.exitCode!==0) ? 1 : 0;
