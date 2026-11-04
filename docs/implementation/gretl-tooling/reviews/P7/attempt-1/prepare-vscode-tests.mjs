import {spawnSync} from 'node:child_process';
import {writeFile} from 'node:fs/promises';
const cwd='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product/vscode/gretl-vscode';
const startedAt=new Date().toISOString();
const result=spawnSync('npm',['ci','--ignore-scripts'],{cwd,encoding:'utf8',timeout:300000,env:{...process.env}});
const report={startedAt,endedAt:new Date().toISOString(),cwd,status:result.status,signal:result.signal,stdout:result.stdout,stderr:result.stderr,passed:result.status===0};
await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/vscode-npm-ci-result.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
if(result.status!==0)process.exitCode=1;
