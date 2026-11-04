import {spawn} from 'node:child_process';
import {appendFile, writeFile} from 'node:fs/promises';
const cwd='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product/vscode/gretl-vscode';
const logPath='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/targeted-tests/node-modeler-affected.log';
const args=['--test','test/modeler-edit-plan.test.mjs','test/edit-guard.test.mjs','test/modeler-controller.test.mjs','test/modeler-intent.test.mjs','test/modeler-connection.test.mjs','test/modeler-view-history.test.mjs','test/modeler-state.test.mjs'];
const startedAt=new Date().toISOString();
await writeFile(logPath,'');
const child=spawn(process.execPath,args,{cwd,stdio:['ignore','pipe','pipe'],env:{...process.env}});
let stdout='',stderr='';
child.stdout.on('data',chunk=>{stdout+=chunk;appendFile(logPath,chunk).catch(()=>{});});
child.stderr.on('data',chunk=>{stderr+=chunk;appendFile(logPath,chunk).catch(()=>{});});
child.on('close',async(code,signal)=>{
  const report={label:'node-modeler-affected',startedAt,endedAt:new Date().toISOString(),cwd,args,code,signal,passed:code===0,logPath,stdout,stderr};
  await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/node-modeler-tests-result.json',JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({passed:report.passed,code,signal,logPath,tail:(stdout+'\n'+stderr).slice(-3000)},null,2));
  if(code!==0)process.exitCode=1;
});
