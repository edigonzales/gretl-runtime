#!/usr/bin/env node
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, mkdirSync, openSync, closeSync, statSync, realpathSync } from 'node:fs';
import { resolve, isAbsolute, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash, randomUUID } from 'node:crypto';
import Ajv from 'ajv/dist/2020.js';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const spec = 'docs/implementation/gretl-tooling';
export const hash = data => createHash('sha256').update(data).digest('hex');
const json = p => JSON.parse(readFileSync(p, 'utf8'));
const exactSet = (actual, expected) => {
  assert.equal(new Set(actual).size, actual.length, 'Duplicate entries');
  assert.deepEqual([...actual].sort(), [...expected].sort(), 'Unexpected criteria/check set');
};
function command(argv, cwd = root) {
  const r = spawnSync(argv[0], argv.slice(1), {cwd, env:{...process.env,PWD:cwd}, encoding:'utf8', timeout:30000, maxBuffer:32<<20});
  assert.equal(r.status,0,`${argv[0]} failed (${r.status}): ${r.error?.message ?? r.stderr}`);
  return r.stdout.trim();
}
export function captureRequirements(repo = root) {
  const files = ['EXECUTION_PLAN.md','ARCHITECTURE.md','TEST_MATRIX.md','OPENCODE_REVIEW_PROMPT.md','ACCEPTANCE_CRITERIA.json','schemas/review-request.schema.json','schemas/review-verdict.schema.json'];
  const manifest = files.sort().map(p => ({path:`${spec}/${p}`,sha256:hash(readFileSync(resolve(repo,spec,p)))}));
  const rules=['AGENTS.md',`${spec}/AGENTS.md`].sort().map(path=>({path,sha256:hash(readFileSync(resolve(repo,path)))}));
  return {manifest,rules,requirementsDigest:hash(JSON.stringify(manifest)),repositoryRulesDigest:hash(JSON.stringify(rules))};
}
export function phaseCriteria(phase) {
  assert.match(phase,/^P[0-8]$/);
  const registry=json(resolve(root,spec,'ACCEPTANCE_CRITERIA.json'));
  return registry.phases.filter(p=>phase==='P8'||p.id===phase).flatMap(p=>p.criteria.map(c=>c.id));
}
const ajv = new Ajv({allErrors:true,strict:false});
const validators = Object.fromEntries(['request','verdict'].map(name=>[name,ajv.compile(json(resolve(root,spec,`schemas/review-${name}.schema.json`)))]));
function schema(name, value) { assert(validators[name](value),`${name} schema: ${ajv.errorsText(validators[name].errors)}`); }
export function validateRequest(request, repo = root) {
  schema('request',request);
  exactSet(request.criteria,phaseCriteria(request.phase));
  const digests=captureRequirements(repo);
  for (const key of ['requirementsDigest','repositoryRulesDigest']) assert.equal(request[key],digests[key],`Wrong ${key}`);
  for (const p of [request.worktree, request.evidenceDirectory,request.modelIdentityEvidence]) assert(isAbsolute(p),'Absolute paths required');
  assert(!request.evidenceDirectory.startsWith(request.worktree+'/'),'Evidence must survive clean');
  assert.notEqual(request.evidenceDirectory,request.worktree);
  for (const file of ['plan','architecture','testMatrix','implementationReport']) assert(existsSync(resolve(repo,request[file])),`Missing ${file}`);
  const ids=request.requiredChecks.map(c=>c.id); exactSet(ids,[...new Set(ids)]);
  for (const gate of [['root-standard',['./mvnw','-B','clean','verify']],['root-services',['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify']]]) {
    assert(request.requiredChecks.some(c=>c.id===gate[0]&&JSON.stringify(c.argv)===JSON.stringify(gate[1])),`Missing root gate ${gate[0]}`);
  }
  const phase=json(resolve(repo,spec,'ACCEPTANCE_CRITERIA.json')).phases.find(p=>p.id===request.phase);
  const gui=request.phase==='P8'?Array.from({length:30},(_,i)=>`G${String(i+1).padStart(2,'0')}`):phase.guiCases;
  exactSet(request.requiredGuiCases,gui);
  for (const a of request.artifacts) verifyArtifact(a);
  verifyModelIdentity(json(request.modelIdentityEvidence));
}
export function verifyArtifact(a) {
  assert(isAbsolute(a.path)); assert(statSync(a.path).isFile());
  assert.equal(hash(readFileSync(a.path)),a.sha256,`Artifact changed: ${a.path}`);
}
export function verifyCurrentGateManifest(manifest,candidateSha,argv){
  assert.equal(manifest.sha,candidateSha);assert.equal(manifest.dirty,'');assert.equal(manifest.exit.code,0);assert.equal(manifest.exit.signal,null);
  assert.deepEqual(manifest.argv,argv);
  const start=Date.parse(manifest.startedAt),end=Date.parse(manifest.endedAt);
  assert(Number.isFinite(start)&&Number.isFinite(end)&&end>=start,'Invalid gate times');
  const counts={tests:0,failures:0,errors:0,skipped:0};
  assert(Array.isArray(manifest.captured)&&manifest.captured.length>0,'No current captured evidence');
  const paths=new Set();
  for(const artifact of manifest.captured){
    assert(!paths.has(artifact.path),'Duplicate captured evidence');paths.add(artifact.path);verifyArtifact(artifact);
    const modified=Date.parse(artifact.sourceModifiedAt);
    assert(Number.isFinite(modified)&&modified>=start&&modified<=end,'Captured evidence predates or postdates gate');
    if(/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(artifact.path)){
      const header=readFileSync(artifact.path,'utf8').match(/<testsuite\b[^>]*>/)?.[0];assert(header,'Missing test suite');
      for(const key of Object.keys(counts))counts[key]+=Number(header.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1]??0);
    }
  }
  assert.deepEqual(manifest.counts,counts,'Manifest counts differ from current raw XML');
  assert(counts.tests>0,'No fresh tests');for(const key of ['failures','errors','skipped'])assert.equal(counts[key],0,`Gate ${key}`);
}
export function verifyModelIdentity(identity) {
  assert.match(identity.openCodeVersion,/^2\.\d+\.\d+/);
  assert.equal(identity.provider,'deepseek'); assert.equal(identity.model,'deepseek-flash');
  assert.equal(identity.apiHost,'api.deepseek.com'); assert.equal(identity.modelVersion,'DeepSeek-V4.1-Flash-1017');
  assert.equal(identity.mappingUrl,'https://api-docs.deepseek.com/quick_start/pricing/');
  assert(identity.mappingText.includes('DeepSeek-V4.1-Flash-1017')&&identity.mappingText.includes('deepseek-flash'));
  assert(Date.now()-Date.parse(identity.checkedAt)<24*60*60*1000,'Stale model identity');
  assert.equal(identity.actualProvider,'deepseek'); assert.equal(identity.actualModel,'deepseek-flash');
  assert.equal(identity.effectiveBaseUrl,'https://api.deepseek.com');
}
export function discoverOpenCode(binary) {
  const path=realpathSync(binary ?? command(['which','opencode']));
  const version=command([path,'--version']).replace(/^opencode v/,'').replace(/^v/,'');
  assert.match(version,/^2\.\d+\.\d+/,'OpenCode 2.x required');
  const help=command([path,'run','--help']);
  for (const flag of ['--standalone','--model','--format','--file','--agent']) assert(help.includes(flag),`Missing CLI flag ${flag}`);
  command([path,'auth','--help']); command([path,'models','--help']);
  const auth=JSON.parse(command([path,'auth','list','--format','json']));
  assert(auth.some(a=>a.id==='deepseek'&&a.connections?.length),'Existing DeepSeek authentication required');
  return {path,version,help};
}
export function trackedState(repo) {
  return {sha:command(['git','rev-parse','HEAD'],repo),status:command(['git','status','--porcelain=v1','--untracked-files=no'],repo),diff:hash(command(['git','diff','HEAD','--binary'],repo))};
}
export function buildRequest(phase, assignmentBase, phaseBase, candidateSha, worktree, evidenceDirectory, requiredChecks, artifacts, modelIdentityEvidence) {
  return {schemaVersion:1,reviewId:randomUUID(),phase,assignmentBase,phaseBase,candidateSha,worktree,evidenceDirectory,
    ...Object.fromEntries(Object.entries(captureRequirements()).filter(([k])=>k.endsWith('Digest'))),
    plan:`${spec}/EXECUTION_PLAN.md`,architecture:`${spec}/ARCHITECTURE.md`,testMatrix:`${spec}/TEST_MATRIX.md`,implementationReport:`${spec}/implementation/${phase}.md`,
    criteria:phaseCriteria(phase),requiredChecks,requiredGuiCases:phase==='P8'?Array.from({length:30},(_,i)=>`G${String(i+1).padStart(2,'0')}`):json(resolve(root,spec,'ACCEPTANCE_CRITERIA.json')).phases.find(p=>p.id===phase).guiCases,
    notRequiredAdditionalChecks:[
      {id:'repeat-root-standard',reason:'Benutzerpräzisierung: vorhandenen frischen Standardlauf dieses Kandidaten anhand aller Rohbelege validieren; keinen zusätzlichen vollständigen Lauf starten.'},
      {id:'repeat-root-services',reason:'Benutzerpräzisierung: vorhandenen frischen GeoTools-/Dienstlauf dieses Kandidaten anhand aller Rohbelege validieren; keinen zusätzlichen vollständigen Lauf starten.'}
    ],artifacts,modelIdentityEvidence,...(phase==='P8'?{cumulative:true}:{})};
}
export function createReviewWorktree(candidateSha, path, repo=root) {
  assert(!existsSync(path),'Refusing existing review worktree');
  command(['git','worktree','add','--detach',path,candidateSha],repo);
  assert.equal(command(['git','rev-parse','HEAD'],path),candidateSha);
  assert.equal(command(['git','status','--porcelain'],path),'');
}
export function scopedProfile(worktree,evidence,checks=[]) {
  const permissions=[{action:'*',resource:'*',effect:'deny'},
    {action:'read',resource:'*',effect:'allow'},{action:'glob',resource:'*',effect:'allow'},{action:'grep',resource:'*',effect:'allow'},
    {action:'external_directory',resource:evidence+'/*',effect:'allow'},
    {action:'edit',resource:evidence+'/*',effect:'allow'},
    ...['git status *','git diff *','git rev-parse *','git show *','rg *','ls *','cat *',...checks.filter(c=>!['root-standard','root-services'].includes(c.id)).map(c=>c.argv.join(' ')),`node ${evidence}/*.mjs`,`node --test ${evidence}/*.mjs`].map(resource=>({action:'shell',resource,effect:'allow'})),
    {action:'shell',resource:'git push *',effect:'deny'}, {action:'shell',resource:'git commit *',effect:'deny'},
    {action:'read',resource:'*.env',effect:'deny'}, {action:'read',resource:'*/auth.json',effect:'deny'}];
  return {model:'deepseek/deepseek-flash',providers:{deepseek:{settings:{baseURL:'https://api.deepseek.com'}}},
    agents:{'gretl-independent-review':{description:'Independent GRETL phase reviewer',mode:'primary',permissions,system:'Read-only product review. Only evidence files may be written. No product fixes, publication, credential access or external jobs. User directs focused testing: validate supplied current root gate evidence; never repeat the complete root standard or service builds, including through evidence scripts. Execute only affected risky tests and new counterprobes. Missing or contradictory root evidence is a concrete review finding, not permission for a blanket rebuild.'}}};
}
export function writeScopedProfile(worktree, evidence, checks=[]) {
  const profile=scopedProfile(worktree,evidence,checks);
  const dir=resolve(worktree,'.opencode/agents');mkdirSync(dir,{recursive:true,mode:0o700});
  const agent=profile.agents['gretl-independent-review'];
  writeFileSync(resolve(dir,'gretl-independent-review.md'),'---\ndescription: Independent GRETL reviewer\nmode: primary\npermissions: '+JSON.stringify(agent.permissions)+'\n---\n'+agent.system+'\n',{flag:'wx',mode:0o600});
  writeFileSync(resolve(worktree,'opencode.json'),JSON.stringify({model:profile.model,providers:profile.providers},null,2),{flag:'wx',mode:0o600});
  return profile;
}
export async function waitForReviewer(binary,argv,cwd,evidence,timeoutMs=2*60*60*1000) {
  const startedAt=new Date().toISOString();
  const out=openSync(resolve(evidence,'opencode.jsonl'),'wx',0o600),err=openSync(resolve(evidence,'opencode.stderr.log'),'wx',0o600);
  let timer,monitor,escalation,child,timedOut=false;
  const owned = new Map();
  try {
    const completion=await new Promise((done,reject)=>{
      child=spawn(binary,argv,{cwd,env:{...process.env,PWD:cwd},stdio:['ignore',out,err],detached:true});
      const captureChildren=()=>{
        const rows=command(['ps','-axo','pid=,ppid=,pgid=,command=']).split('\n').map(line=>{
          const m=line.trim().match(/^(\d+)\s+(\d+)\s+(\d+)\s+(.+)$/);return m?{pid:Number(m[1]),parent:Number(m[2]),group:Number(m[3]),command:m[4]}:null;
        }).filter(Boolean);
        const parents=new Set([child.pid,...owned.keys()]);let changed=true;
        while(changed){changed=false;for(const row of rows)if(!parents.has(row.pid)&&(parents.has(row.parent)||row.group===child.pid)){parents.add(row.pid);owned.set(row.pid,row.command);changed=true;}}
      };
      monitor=setInterval(captureChildren,500);
      timer=setTimeout(()=>{timedOut=true;try{process.kill(-child.pid,'SIGTERM');}catch{}
        escalation=setTimeout(()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}},5000);
      },timeoutMs);
      child.on('error',reject);child.on('close',(exitCode,signal)=>done({exitCode,signal}));
    });
    return {argv:[binary,...argv],cwd,pid:child.pid,ownedProcesses:[...owned].map(([pid,command])=>({pid,command})),startedAt,endedAt:new Date().toISOString(),timedOut,...completion};
  } finally {clearTimeout(timer);clearTimeout(escalation);clearInterval(monitor);closeSync(out);closeSync(err);}
}
export function validateReviewArtifacts(request,verdict,report,completion) {
  schema('verdict',verdict);
  assert.equal(completion.exitCode,0,'Review process failed');assert.equal(completion.timedOut,false);
  assert.equal(completion.finished,true,'Review still running or stream incomplete');assert.equal(completion.childrenFinished,true,'Review children still running');
  assert.equal(completion.unchanged,true,'Tracked files changed');assert.equal(completion.fresh,true,'Prefilled/reused review');
  for(const key of ['schemaVersion','reviewId','phase','assignmentBase','phaseBase','candidateSha','requirementsDigest']) assert.equal(verdict[key],request[key],`Wrong ${key}`);
  assert(report.length>200&&report.includes(request.candidateSha)&&report.includes(verdict.verdict),'Report identity/verdict mismatch');
  exactSet(verdict.criteria.map(c=>c.id),request.criteria);
  for(const key of ['provider','model','apiHost']) assert.equal(verdict.reviewer[key],json(request.modelIdentityEvidence)[key]);
  if(verdict.verdict!=='PASS')return verdict.verdict;
  assert(verdict.counterprobes.length>=(request.phase==='P8'?2:1));
  const checkIds=[];
  const reachable=p=>{assert(isAbsolute(p),`Evidence must be an absolute local path: ${p}`);assert(statSync(p).isFile(),`Missing evidence ${p}`);};
  for(const c of verdict.criteria) {assert.equal(c.status,'fulfilled');assert.equal(c.findingIds.length,0);c.evidence.forEach(reachable);}
  for(const c of verdict.counterprobes)c.evidence.forEach(reachable);
  for(const c of verdict.executedChecks){assert.equal(c.exitCode,0);assert.equal(c.failures,0);assert.equal(c.skips,0);assert(c.testCount===null||c.testCount>0);c.evidence.forEach(reachable);checkIds.push(c.id);}
  for(const c of verdict.verifiedCurrentEvidence){
    assert.equal(c.candidateSha,request.candidateSha);c.artifacts.forEach(verifyArtifact);c.evidence.forEach(reachable);
    const manifests=c.evidence.filter(p=>p.endsWith('/manifest.json')).map(json);
    for(const id of c.checkIds){
      const required=request.requiredChecks.find(g=>g.id===id);assert(required,'Unknown verified check');
      const matching=manifests.find(m=>m.sha===request.candidateSha&&JSON.stringify(m.argv)===JSON.stringify(required.argv));
      assert(matching,'Missing current clean-candidate gate manifest');verifyCurrentGateManifest(matching,request.candidateSha,required.argv);
    }
    checkIds.push(...c.checkIds);
  }
  for(const c of request.requiredChecks) assert(checkIds.includes(c.id),`Missing check ${c.id}`);
  if(request.requiredGuiCases.length){const ids=verdict.guiEvidence.flatMap(g=>g.caseIds);for(const id of request.requiredGuiCases)assert(ids.includes(id),`Missing GUI case ${id}`);}
  for(const g of verdict.guiEvidence){g.screenshots.forEach(verifyArtifact);g.evidence.forEach(reachable);}
  return 'PASS';
}
export async function startReviewer(requestFile,binary) {
  const request=json(requestFile);validateRequest(request);
  const cli=discoverOpenCode(binary);
  assert.equal(trackedState(root).sha,request.candidateSha);assert.equal(trackedState(root).status,'');
  assert.equal(trackedState(request.worktree).sha,request.candidateSha);assert.equal(trackedState(request.worktree).status,'');
  for(const f of ['review.md','verdict.json','opencode.jsonl'])assert(!existsSync(resolve(request.evidenceDirectory,f)),'Prefilled review refused');
  const before={product:trackedState(root),review:trackedState(request.worktree)};
  writeScopedProfile(request.worktree,request.evidenceDirectory,request.requiredChecks);
  const argv=['run','--standalone','--agent','gretl-independent-review','--model','deepseek/deepseek-flash','--format','json','--file',resolve(request.worktree,spec,'OPENCODE_REVIEW_PROMPT.md'),'--file',resolve(requestFile),
    `Prüfe ${request.phase} unabhängig und vollständig. Lies alle Anforderungen, Code, Tests und den gesamten Phasendiff. Führe eigene betroffene risikotragende Tests und neue Gegenproben durch. Schreibe Original review.md und verdict.json ausschliesslich in ${request.evidenceDirectory}. Keine Produktdateien ändern. Alle Evidenceverweise sind absolute lokale Dateipfade. Benutzerpräzisierung: Vorhandene vollständige Rootgates desselben Kandidaten anhand der frischen Rohbelege unabhängig validieren, nicht erneut ausführen (auch nicht über Hilfsskripte). Die Rootbefehle im Request bezeichnen zu validierende Gates, keine zusätzlichen Reviewläufe. Beende eigene Testprozesse und danach die Session vollständig.`];
  const completion=await waitForReviewer(cli.path,argv,request.worktree,request.evidenceDirectory);
  const events=readFileSync(resolve(request.evidenceDirectory,'opencode.jsonl'),'utf8').trim().split('\n').map(s=>JSON.parse(s));
  completion.finished=events.at(-1)?.type==='step_finish'&&events.at(-1)?.part?.reason==='stop'&&!events.some(e=>e.type==='error');
  const procs=command(['ps','-axo','pid=,ppid=,pgid=,command=']).split('\n').filter(l=>Number(l.trim().split(/\s+/)[2])===completion.pid);
  const live=command(['ps','-axo','pid=,command=']).split('\n');
  completion.childrenFinished=procs.length===0&&!completion.ownedProcesses.some(p=>live.some(line=>line.trim()===p.pid+' '+p.command));
  completion.unchanged=JSON.stringify(before)===JSON.stringify({product:trackedState(root),review:trackedState(request.worktree)});
  completion.fresh=['review.md','verdict.json'].every(f=>statSync(resolve(request.evidenceDirectory,f)).mtimeMs>=Date.parse(completion.startedAt));
  completion.reportDigests=Object.fromEntries(['review.md','verdict.json'].map(f=>[f,hash(readFileSync(resolve(request.evidenceDirectory,f)))]));
  writeFileSync(resolve(request.evidenceDirectory,'process.json'),JSON.stringify(completion,null,2)+'\n');
  const result=validateReviewArtifacts(request,json(resolve(request.evidenceDirectory,'verdict.json')),readFileSync(resolve(request.evidenceDirectory,'review.md'),'utf8'),completion);
  writeFileSync(resolve(request.evidenceDirectory,'validation.json'),JSON.stringify({reviewId:request.reviewId,candidateSha:request.candidateSha,result},null,2)+'\n');
  return result;
}
export function archiveReview(evidence,destination){assert(!existsSync(destination));mkdirSync(destination,{recursive:true});for(const f of ['review.md','verdict.json','process.json','validation.json'])writeFileSync(resolve(destination,f),readFileSync(resolve(evidence,f)));}
export function cleanupOwnedResources(worktree,repo=root){assert.equal(command(['git','symbolic-ref','-q','--short','HEAD'],repo).startsWith('codex/'),true);command(['git','worktree','remove',worktree],repo);}
async function main() {
  const [action,...args]=process.argv.slice(2);
  if(action==='preflight') {
    assert.deepEqual(args.slice(0,1),['--repo']);assert.equal(resolve(args[1]),root);
    const cli=discoverOpenCode(process.env.OPENCODE_BIN);
    const {mkdtempSync}=await import('node:fs');const {tmpdir}=await import('node:os');
    const directory=mkdtempSync(resolve(tmpdir(),'gretl-independent-preflight-'));
    const {preflightReviewAccess}=await import('./preflight.mjs');
    await preflightReviewAccess(cli.path,directory);console.log('Independent readiness probe: '+directory);
  }
  else if(action==='run') {const result=await startReviewer(resolve(args[0]),args[1]);console.log(result);if(result!=='PASS')process.exitCode=1;}
  else throw new Error('Usage: review.mjs preflight --repo <path> | run <request.json> <opencode-binary>');
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) main().catch(error=>{console.error(error);process.exitCode=1;});
