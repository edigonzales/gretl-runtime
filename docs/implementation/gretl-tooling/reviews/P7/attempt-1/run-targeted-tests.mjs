import {spawn} from 'node:child_process';
import {appendFile, mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';

const product='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product';
const evidence='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const logDir=path.join(evidence,'targeted-tests');
await mkdir(logDir,{recursive:true});

function run(label,cmd,args,cwd,timeoutMs=600000){
  return new Promise(resolve=>{
    const startedAt=new Date().toISOString();
    const logPath=path.join(logDir,label+'.log');
    const child=spawn(cmd,args,{cwd,env:{...process.env},stdio:['ignore','pipe','pipe']});
    let stdout='',stderr='',timedOut=false;
    const timer=setTimeout(()=>{timedOut=true;child.kill('SIGTERM');},timeoutMs);
    child.stdout.on('data',chunk=>{stdout+=chunk;appendFile(logPath,chunk).catch(()=>{});});
    child.stderr.on('data',chunk=>{stderr+=chunk;appendFile(logPath,chunk).catch(()=>{});});
    child.on('error',error=>{stderr+=String(error);});
    child.on('close',(code,signal)=>{
      clearTimeout(timer);
      const endedAt=new Date().toISOString();
      resolve({label,cmd,args,cwd,startedAt,endedAt,code,signal,timedOut,logPath,passed:code===0&&!timedOut,tail:(stdout+'\n'+stderr).slice(-4000)});
    });
  });
}

const checks=[];
checks.push(await run(
  'core-affected',
  './mvnw',
  ['-B','-pl','gretl-tooling-core','-Dtest=GraphFormProjectorTest,SemanticEditPlannerTest,DependencySourceEditsTest,DefaultsSourceEditsTest,ReferenceSourceEditsTest,EditVerifierTest,SnapshotNavigationTest','test'],
  product,
  600000
));
checks.push(await run(
  'node-modeler-affected',
  process.execPath,
  ['--test','test/modeler-edit-plan.test.mjs','test/edit-guard.test.mjs','test/modeler-controller.test.mjs','test/modeler-intent.test.mjs','test/modeler-connection.test.mjs','test/modeler-view-history.test.mjs','test/modeler-state.test.mjs'],
  path.join(product,'vscode','gretl-vscode'),
  300000
));
checks.push(await run(
  'lsp-affected',
  './mvnw',
  ['-B','-pl','gretl-lsp','-Dtest=HostSchedulingTest,HostBoundaryTest,DiagnosticPublicationStateTest','-Dit.test=ServerProcessIT','verify'],
  product,
  600000
));
const report={generatedAt:new Date().toISOString(),candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',passed:checks.every(c=>c.passed),checks};
await writeFile(path.join(evidence,'targeted-tests-result.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({passed:report.passed,checks:checks.map(c=>({label:c.label,passed:c.passed,code:c.code,signal:c.signal,timedOut:c.timedOut,logPath:c.logPath,tail:c.tail.slice(-1000)}))},null,2));
if(!report.passed)process.exitCode=1;
