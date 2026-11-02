import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {validateReviewArtifacts,phaseCriteria,hash,verifyModelIdentity,captureRequirements,scopedProfile,waitForReviewer} from './review.mjs';
import {checkRegistry} from './check-docs.mjs';
import {verifyTree} from './verify-dependencies.mjs';
const scratch=mkdtempSync(resolve(tmpdir(),'gretl-review-unit-'));
const proof=resolve(scratch,'proof.txt');writeFileSync(proof,'Synthetic gate unit-test fixture, never a product review.');
const identity={openCodeVersion:'2.2.5',provider:'deepseek',model:'deepseek-flash',apiHost:'api.deepseek.com',modelVersion:'DeepSeek-V4.1-Flash-1017',mappingUrl:'https://api-docs.deepseek.com/quick_start/pricing/',mappingText:'deepseek-flash DeepSeek-V4.1-Flash-1017',checkedAt:new Date().toISOString(),actualProvider:'deepseek',actualModel:'deepseek-flash',effectiveBaseUrl:'https://api.deepseek.com'};
const identityPath=resolve(scratch,'identity.json');writeFileSync(identityPath,JSON.stringify(identity));
function fixture(phase='P0'){
  const r={schemaVersion:1,reviewId:'synthetic-unit-fixture',phase,assignmentBase:'a'.repeat(40),phaseBase:'b'.repeat(40),candidateSha:'c'.repeat(40),requirementsDigest:'d'.repeat(64),criteria:phaseCriteria(phase),modelIdentityEvidence:identityPath,requiredChecks:[{id:'risk-check'}],requiredGuiCases:[]};
  const v={schemaVersion:1,reviewId:r.reviewId,phase,assignmentBase:r.assignmentBase,phaseBase:r.phaseBase,candidateSha:r.candidateSha,requirementsDigest:r.requirementsDigest,verdict:'PASS',reviewer:{openCodeVersion:'2.2.5',provider:'deepseek',model:'deepseek-flash',apiHost:'api.deepseek.com',modelMappingEvidence:identityPath},criteria:r.criteria.map(id=>({id,status:'fulfilled',evidence:[proof],findingIds:[]})),executedChecks:[{id:'risk-check',cwd:scratch,argv:['node','--test'],exitCode:0,testCount:1,failures:0,skips:0,evidence:[proof]}],verifiedCurrentEvidence:[],guiEvidence:[],counterprobes:[{id:'new',hypothesis:'test',novelty:'new case',actions:'run',expected:'rejected',observed:'rejected',evidence:[proof]}],openMandatoryFindings:[],missingRequiredChecks:[],report:'review.md',summary:'synthetic fixture'};
  return {r,v,report:`${r.candidateSha} PASS ${'synthetic unit fixture '.repeat(20)}`,c:{exitCode:0,timedOut:false,finished:true,childrenFinished:true,unchanged:true,fresh:true}};
}
test('synthetic structural fixture is accepted only after all external conditions',()=>{const f=fixture();assert.equal(validateReviewArtifacts(f.r,f.v,f.report,f.c),'PASS');});
const mutations={
  sha:f=>f.v.candidateSha='f'.repeat(40),phase:f=>f.v.phase='P1',criteria:f=>f.v.criteria.pop(),duplicate:f=>f.v.criteria[1]=f.v.criteria[0],digest:f=>f.v.requirementsDigest='f'.repeat(64),running:f=>f.c.finished=false,children:f=>f.c.childrenFinished=false,modified:f=>f.c.unchanged=false,prefilled:f=>f.c.fresh=false,exit:f=>f.c.exitCode=1,timeout:f=>f.c.timedOut=true,provider:f=>f.v.reviewer.provider='openrouter',counterprobe:f=>f.v.counterprobes=[],missing:f=>f.v.executedChecks=[],evidence:f=>f.v.criteria[0].evidence=[resolve(scratch,'absent')],skipped:f=>f.v.executedChecks[0].skips=1,zeroTests:f=>f.v.executedChecks[0].testCount=0,report:f=>f.report='PASS',unverified:f=>f.v.criteria[0].status='unverified',finding:f=>f.v.criteria[0].findingIds=['open'],extra:f=>f.v.extra='PASS'
};
for(const [name,mutate] of Object.entries(mutations))test(`gate rejects ${name}`,()=>{const f=fixture();mutate(f);assert.throws(()=>validateReviewArtifacts(f.r,f.v,f.report,f.c));});
test('later phase cannot omit actual GUI or cumulative counterprobes',()=>{for(const phase of ['P5','P8']){const f=fixture(phase);assert.throws(()=>validateReviewArtifacts(f.r,f.v,f.report,f.c));}});
test('model identity forbids fallback, stale mapping and endpoint changes',()=>{verifyModelIdentity(identity);for(const patch of [{provider:'openrouter'},{actualModel:'deepseek-v4-pro'},{checkedAt:'2020-01-01T00:00:00Z'},{mappingText:'unknown'},{effectiveBaseUrl:'https://proxy.invalid'}])assert.throws(()=>verifyModelIdentity({...identity,...patch}));});
test('resolved dependency guard detects production leaks while permitting test parity',()=>{
  for(const artifactId of ['gretl-api','gretl-runner','gretl-tooling-catalog','gretl-next'])assert.throws(()=>verifyTree({children:[{groupId:'ch.so.agi.gretl',artifactId,scope:'compile'}]},'core'));
  assert.throws(()=>verifyTree({children:[{groupId:'com.fasterxml.jackson.core',artifactId:'jackson-databind',scope:'compile'}]},'contract'));
  verifyTree({children:[{groupId:'ch.so.agi.gretl',artifactId:'gretl-runner',scope:'test'}]},'core');
});
test('registry compares all 81 exact requirements and captures stable digest',()=>{assert.equal(checkRegistry(),81);assert.deepEqual(captureRequirements(),captureRequirements());});
test('review profile is scoped and does not grant general shell or product edits',()=>{const c=scopedProfile('/product','/evidence');const p=c.agents['gretl-independent-review'].permissions;assert(p.some(r=>r.action==='*'&&r.effect==='deny'));assert(!p.some(r=>r.action==='shell'&&r.resource==='*'&&r.effect==='allow'));assert(!p.some(r=>r.action==='edit'&&r.resource==='/product/*'&&r.effect==='allow'));});
test('wait drains both streams and detects actual timeout',async()=>{
  const d=resolve(scratch,'process');(await import('node:fs')).mkdirSync(d);
  const result=await waitForReviewer(process.execPath,['-e','process.stdout.write("done");process.stderr.write("error-stream")'],scratch,d,5000);
  assert.equal(result.exitCode,0);assert.equal(readFileSync(resolve(d,'opencode.jsonl'),'utf8'),'done');assert.equal(readFileSync(resolve(d,'opencode.stderr.log'),'utf8'),'error-stream');
  const d2=resolve(scratch,'timeout');(await import('node:fs')).mkdirSync(d2);
  const timeout=await waitForReviewer(process.execPath,['-e','setInterval(()=>{},1000)'],scratch,d2,100);
  assert.equal(timeout.timedOut,true);assert.notEqual(timeout.signal,null);
});
test.after(()=>rmSync(scratch,{recursive:true}));
