import {readFile,access} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {dirname,resolve} from 'node:path';
const base='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const verdict=JSON.parse(await readFile(`${base}/verdict.json`,'utf8'));
const problems=[];
const check=(ok,message)=>{if(!ok)problems.push(message);};
const sha=async path=>createHash('sha256').update(await readFile(path)).digest('hex');
const required=['schemaVersion','reviewId','phase','assignmentBase','phaseBase','candidateSha','requirementsDigest','verdict','reviewer','criteria','executedChecks','verifiedCurrentEvidence','guiEvidence','counterprobes','openMandatoryFindings','missingRequiredChecks','report','summary'];
for(const key of required)check(key in verdict,`missing top-level ${key}`);
for(const key of Object.keys(verdict))check(required.includes(key),`unexpected top-level ${key}`);
const exact=(value,keys,label)=>{const actual=Object.keys(value).sort(),expected=[...keys].sort();check(JSON.stringify(actual)===JSON.stringify(expected),`${label} keys ${actual}`)};
exact(verdict.reviewer,['openCodeVersion','provider','model','apiHost','modelMappingEvidence'],'reviewer');
check(verdict.schemaVersion===1,'schemaVersion');
check(verdict.phase==='P6','phase');
check(verdict.verdict==='PASS','verdict');
check(/^[0-9a-f]{40}$/.test(verdict.assignmentBase),'assignmentBase');
check(/^[0-9a-f]{40}$/.test(verdict.phaseBase),'phaseBase');
check(/^[0-9a-f]{40}$/.test(verdict.candidateSha),'candidateSha');
check(/^[0-9a-f]{64}$/.test(verdict.requirementsDigest),'requirementsDigest');
check(verdict.reviewer?.openCodeVersion==='2.2.5','reviewer openCodeVersion');
check(verdict.reviewer?.provider==='deepseek','reviewer provider');
check(verdict.reviewer?.model==='deepseek-flash','reviewer model');
check(verdict.reviewer?.apiHost==='api.deepseek.com','reviewer apiHost');
check(typeof verdict.reviewer?.modelMappingEvidence==='string'&&verdict.reviewer.modelMappingEvidence.length>0,'reviewer mapping evidence');
check(Array.isArray(verdict.criteria)&&verdict.criteria.length===9,'criteria count');
const ids=verdict.criteria.map(c=>c.id);
check(JSON.stringify(ids)===JSON.stringify(['P6-01','P6-02','P6-03','P6-04','P6-05','P6-06','P6-07','P6-08','P6-09']),'criteria ids/order');
for(const c of verdict.criteria){
  exact(c,['id','status','evidence','findingIds'],`criteria ${c.id}`);
  check(c.status==='fulfilled',`${c.id} status`);
  check(Array.isArray(c.evidence)&&c.evidence.length>0,`${c.id} evidence`);
  check(Array.isArray(c.findingIds)&&c.findingIds.length===0,`${c.id} findingIds`);
}
check(Array.isArray(verdict.executedChecks)&&verdict.executedChecks.length>0,'executedChecks empty');
for(const e of verdict.executedChecks){
  exact(e,['id','cwd','argv','exitCode','testCount','failures','skips','evidence'],`executedCheck ${e.id}`);
  check(e.exitCode===0,`executedCheck ${e.id} exit`);
  check(e.failures===0,`executedCheck ${e.id} failures`);
  check(Array.isArray(e.evidence)&&e.evidence.length>0,`executedCheck ${e.id} evidence`);
}
check(Array.isArray(verdict.verifiedCurrentEvidence)&&verdict.verifiedCurrentEvidence.length===1,'verifiedCurrentEvidence length');
const v=verdict.verifiedCurrentEvidence[0];
exact(v,['checkIds','candidateSha','artifacts','evidence','verification'],'verifiedCurrentEvidence');
check(JSON.stringify(v.checkIds)===JSON.stringify(['root-standard','root-services']),'verifiedCurrentEvidence checkIds must be exactly the two supplied root gates');
check(v.candidateSha===verdict.candidateSha,'verified candidateSha');
check(Array.isArray(v.artifacts)&&v.artifacts.length===11,'verified artifacts count');
for(const a of v.artifacts){
  exact(a,['path','sha256','kind'],`artifact ${a.path}`);
  check(/^[0-9a-f]{64}$/.test(a.sha256),`artifact hash format ${a.path}`);
  try{await access(a.path);check(await sha(a.path)===a.sha256,`artifact hash mismatch ${a.path}`)}catch{problems.push(`artifact missing ${a.path}`)}
}
check(Array.isArray(v.evidence)&&v.evidence.length>0,'verified evidence');
check(typeof v.verification==='string'&&v.verification.length>0,'verified verification');
check(Array.isArray(verdict.guiEvidence)&&verdict.guiEvidence.length>0,'guiEvidence empty');
check(verdict.guiEvidence.some(g=>g.kind==='own-run'),'guiEvidence missing own-run');
for(const g of verdict.guiEvidence){
  exact(g,['caseIds','kind','actions','observations','screenshots','evidence'],`gui ${g.caseIds}`);
  check(['own-run','verified-current'].includes(g.kind),'gui kind');
  check(Array.isArray(g.caseIds)&&g.caseIds.length>0,'gui caseIds');
  for(const id of g.caseIds)check(/^G(?:0[1-9]|[12][0-9]|30)$/.test(id),`gui case id ${id}`);
  check(typeof g.actions==='string'&&g.actions.length>0,'gui actions');
  check(typeof g.observations==='string'&&g.observations.length>0,'gui observations');
  check(Array.isArray(g.screenshots)&&g.screenshots.length>0,'gui screenshots');
  for(const s of g.screenshots){
    exact(s,['path','sha256','kind'],`screenshot ${s.path}`);
    check(/^[0-9a-f]{64}$/.test(s.sha256),`screenshot hash format ${s.path}`);
    try{await access(s.path);check(await sha(s.path)===s.sha256,`screenshot hash mismatch ${s.path}`)}catch{problems.push(`screenshot missing ${s.path}`)}
  }
  check(Array.isArray(g.evidence)&&g.evidence.length>0,'gui evidence');
}
check(Array.isArray(verdict.counterprobes)&&verdict.counterprobes.length>0,'counterprobes empty');
for(const c of verdict.counterprobes){
  exact(c,['id','hypothesis','novelty','actions','expected','observed','evidence'],`counterprobe ${c.id}`);
  for(const key of ['id','hypothesis','novelty','actions','expected','observed'])check(typeof c[key]==='string'&&c[key].length>0,`counterprobe ${c.id} ${key}`);
  check(Array.isArray(c.evidence)&&c.evidence.length>0,`counterprobe ${c.id} evidence`);
}
check(Array.isArray(verdict.openMandatoryFindings)&&verdict.openMandatoryFindings.length===0,'openMandatoryFindings not empty');
check(Array.isArray(verdict.missingRequiredChecks)&&verdict.missingRequiredChecks.length===0,'missingRequiredChecks not empty');
check(verdict.report==='review.md','report');
try{await access(resolve(base,verdict.report))}catch{problems.push('review.md missing')}
check(typeof verdict.summary==='string'&&verdict.summary.length>0,'summary');
if(problems.length){console.error(JSON.stringify(problems,null,2));process.exit(1)}
console.log('verdict structure and all referenced artifact/screenshot hashes valid');
