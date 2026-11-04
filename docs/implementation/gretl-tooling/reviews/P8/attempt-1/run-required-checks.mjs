import {spawnSync} from 'node:child_process';
import {writeFileSync, mkdirSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';

const product = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/product';
const ev = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const h = b => createHash('sha256').update(b).digest('hex');
const result = {candidate: null, checks: []};
const git = spawnSync('git',['rev-parse','HEAD'],{cwd:product,encoding:'utf8'});
result.candidate = git.stdout.trim();

function run(id, argv, cwd, timeout=600000) {
  const r = spawnSync(argv[0], argv.slice(1), {cwd, encoding:'utf8', timeout, maxBuffer:64<<20, env:{...process.env, PWD:cwd}});
  const row = {id, cwd, argv, exitCode:r.status, signal:r.signal, stdout:(r.stdout||'').slice(-8000), stderr:(r.stderr||'').slice(-8000)};
  result.checks.push(row);
  console.log(`\n### ${id}: exit=${r.status}\n${(r.stdout||'').slice(-2500)}${(r.stderr||'').slice(-2000)}`);
  return row;
}

// Locked dependency installs (build/scratch only).
run('npm-ci-tools', ['npm','ci','--ignore-scripts','--no-audit','--no-fund'], resolve(product,'tools/gretl-tooling'));
run('npm-ci-vscode', ['npm','ci','--ignore-scripts','--no-audit','--no-fund'], resolve(product,'vscode/gretl-vscode'), 900000);

// Required check: exact immutable docs registry / 81 mapping / links.
run('tooling-docs', ['node','tools/gretl-tooling/check-docs.mjs'], product);

writeFileSync(resolve(ev,'required-checks-result.json'), JSON.stringify(result,null,2)+'\n');
console.log('\nwrote required-checks-result.json');
process.exitCode = result.checks.some(c=>c.exitCode!==0) ? 1 : 0;
