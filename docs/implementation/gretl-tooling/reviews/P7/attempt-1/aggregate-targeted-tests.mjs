import {readFile, writeFile} from 'node:fs/promises';
const base='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const prior=JSON.parse(await readFile(base+'/targeted-tests-result.json','utf8'));
const node=JSON.parse(await readFile(base+'/node-modeler-tests-result.json','utf8'));
const checks=[...prior.checks.filter(c=>c.label!=='node-modeler-affected'),node];
const report={generatedAt:new Date().toISOString(),candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',passed:checks.every(c=>c.passed),checks:checks.map(c=>({label:c.label,passed:c.passed,code:c.code,signal:c.signal,timedOut:c.timedOut??false,logPath:c.logPath,startedAt:c.startedAt,endedAt:c.endedAt,cwd:c.cwd,args:c.args}))};
await writeFile(base+'/targeted-tests-final-result.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
if(!report.passed)process.exitCode=1;
