import {spawn} from 'node:child_process';
import {writeFile} from 'node:fs/promises';

const evidence='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const product='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/product';
const run=(argv,cwd,timeout=600000)=>new Promise(resolve=>{
  const started=Date.now();
  const child=spawn(argv[0],argv.slice(1),{cwd,env:process.env});
  let out='',err='';
  child.stdout.on('data',d=>{out+=d.toString();});
  child.stderr.on('data',d=>{err+=d.toString();});
  const timer=setTimeout(()=>{child.kill('SIGKILL');},timeout);
  child.on('close',code=>{clearTimeout(timer);resolve({argv,cwd,exitCode:code,durationMs:Date.now()-started,stdout:out,stderr:err});});
});
const result={};
result.targeted=await run(['./mvnw','-B','-pl','gretl-language-contract,gretl-tooling-core,gretl-lsp','verify'],product);
const tail=result.targeted.stdout;
const summary=[...tail.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g)];
const totals=summary.reduce((acc,m)=>({tests:acc.tests+Number(m[1]),failures:acc.failures+Number(m[2]),errors:acc.errors+Number(m[3]),skipped:acc.skipped+Number(m[4])}),{tests:0,failures:0,errors:0,skipped:0});
result.targeted.summaryMatches=summary.length;
result.targeted.finalSummary=summary.length?summary[summary.length-1].slice(1):null;
result.targeted.aggregateSummary=totals;
result.targeted.buildSuccess=/BUILD SUCCESS/.test(tail);
result.targeted.stdoutTail=tail.slice(-4000);
result.targeted.stderrTail=result.targeted.stderr.slice(-4000);
delete result.targeted.stdout;
delete result.targeted.stderr;

result.docs=await run(['node','tools/gretl-tooling/check-docs.mjs'],product);
result.docs.stdoutTail=result.docs.stdout.slice(-4000);
result.docs.stderrTail=result.docs.stderr.slice(-2000);
delete result.docs.stdout;
delete result.docs.stderr;
result.candidateSha=(await (await import('node:child_process')).execFileSync('git',['rev-parse','HEAD'],{cwd:product})).toString().trim();
await writeFile(`${evidence}/required-checks-evidence.json`,JSON.stringify(result,null,2));
console.log(JSON.stringify({targetedExit:result.targeted.exitCode,targetedFinal:result.targeted.finalSummary,targetedAggregate:result.targeted.aggregateSummary,buildSuccess:result.targeted.buildSuccess,docsExit:result.docs.exitCode,candidateSha:result.candidateSha},null,2));
