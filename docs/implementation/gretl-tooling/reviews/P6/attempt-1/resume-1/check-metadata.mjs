import {readFile,stat} from 'node:fs/promises';
import {isAbsolute} from 'node:path';
import {createHash} from 'node:crypto';
const dir='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const read=async p=>readFile(p,'utf8');
const json=async p=>JSON.parse(await read(p));
const sha=p=>createHash('sha256').update(p).digest('hex');
const originalVerdict=await json(`${dir}/verdict.json`);
const newVerdict=await json(`${dir}/resume-1/verdict.json`);
const originalReview=await read(`${dir}/review.md`);
const newReview=await read(`${dir}/resume-1/review.md`);
const process=await json(`${dir}/process.json`);
const problems=[];
const check=(ok,msg)=>{if(!ok)problems.push(msg)};
const stripEvidence=v=>{
  const copy=structuredClone(v);
  delete copy.reviewer.modelMappingEvidence;
  for(const c of copy.criteria)delete c.evidence;
  for(const c of copy.counterprobes)delete c.evidence;
  for(const c of copy.executedChecks)delete c.evidence;
  for(const c of copy.verifiedCurrentEvidence)delete c.evidence;
  for(const g of copy.guiEvidence)delete g.evidence;
  return copy;
};
check(JSON.stringify(stripEvidence(originalVerdict))===JSON.stringify(stripEvidence(newVerdict)),'non-evidence verdict fields changed');
check(sha(originalReview)===process.reportDigests['review.md'],'original review.md changed');
check(sha(await read(`${dir}/verdict.json`))===process.reportDigests['verdict.json'],'original verdict.json changed');
check(newReview.startsWith(originalReview),'resume review does not preserve original review verbatim');
const paths=[];
for(const c of newVerdict.criteria)paths.push(...c.evidence);
for(const c of newVerdict.counterprobes)paths.push(...c.evidence);
for(const c of newVerdict.executedChecks)paths.push(...c.evidence);
for(const c of newVerdict.verifiedCurrentEvidence)paths.push(...c.evidence);
for(const g of newVerdict.guiEvidence)paths.push(...g.evidence);
for(const p of paths){
  check(isAbsolute(p),`not absolute: ${p}`);
  check(!p.includes('#'),`anchor remains: ${p}`);
  try{const s=await stat(p);check(s.isFile(),`not regular file: ${p}`)}catch{problems.push(`missing: ${p}`)}
}
check(paths.length>0,'no evidence paths');
check(JSON.stringify(originalVerdict.verifiedCurrentEvidence[0].artifacts)===JSON.stringify(newVerdict.verifiedCurrentEvidence[0].artifacts),'artifact list changed');
check(JSON.stringify(originalVerdict.guiEvidence.map(g=>g.screenshots))===JSON.stringify(newVerdict.guiEvidence.map(g=>g.screenshots)),'screenshot hashes changed');
for(const key of ['openCodeVersion','provider','model','apiHost'])check(originalVerdict.reviewer[key]===newVerdict.reviewer[key],`reviewer ${key} changed`);
check(newVerdict.reviewer.modelMappingEvidence.startsWith('/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/model-identity-check.json:'),'mapping evidence is not absolute');
check((await stat('/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/model-identity-check.json')).isFile(),'mapping evidence missing');
check(JSON.stringify(originalVerdict.criteria.map(c=>c.id))===JSON.stringify(newVerdict.criteria.map(c=>c.id)),'criteria ids changed');
console.log(JSON.stringify({evidenceEntries:paths.length,unique:new Set(paths).size,problems},null,2));
if(problems.length)process.exit(1);
