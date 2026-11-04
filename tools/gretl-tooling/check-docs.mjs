import assert from 'node:assert/strict';
import {readFileSync,existsSync,readdirSync,statSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {hash,captureRequirements} from './review.mjs';
const repo=resolve(import.meta.dirname,'../..'),spec=resolve(repo,'docs/implementation/gretl-tooling');
export function checkRegistry(directory=spec){
  const plan=readFileSync(resolve(directory,'EXECUTION_PLAN.md'),'utf8'),registry=JSON.parse(readFileSync(resolve(directory,'ACCEPTANCE_CRITERIA.json')));
  assert.equal(registry.derivedFrom.sha256,hash(plan),'Stale criteria registry');
  const rows=[...plan.matchAll(/^\| (P[0-8]-0[1-9]) \| (.*?) \|$/gm)].map(m=>({id:m[1],description:m[2]}));
  const entries=registry.phases.flatMap(p=>p.criteria.map(c=>({id:c.id,description:c.description})));
  assert.equal(rows.length,81);assert.equal(registry.criterionCount,81);assert.deepEqual(entries,rows);
  return rows.length;
}
export function checkLinks(directory=spec){
  for(const name of readdirSync(directory).filter(n=>n.endsWith('.md'))){
    const path=resolve(directory,name),text=readFileSync(path,'utf8');
    for(const match of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){
      const link=match[1];if(/^[a-z]+:|^#|^</i.test(link))continue;
      const target=decodeURIComponent(link.split('#')[0]);if(!target)continue;
      assert(existsSync(resolve(dirname(path),target)),`Broken link ${path}: ${link}`);
    }
  }
}
export function checkAcceptanceMap(file=resolve(spec,'implementation/P8-acceptance-map.json')){
  const map=JSON.parse(readFileSync(file)),registry=JSON.parse(readFileSync(resolve(spec,'ACCEPTANCE_CRITERIA.json')));
  const expected=registry.phases.flatMap(p=>p.criteria.map(c=>({id:c.id,requirement:c.description})));
  assert.equal(map.criteriaCount,81,'Acceptance map requires 81 exact criteria');
  assert.deepEqual(map.entries.map(e=>({id:e.id,requirement:e.requirement})),expected,'Acceptance map differs from exact requirements');
  for(const entry of map.entries){
    assert(entry.tests.length&&entry.rootEvidenceIds.includes('root-standard')&&entry.rootEvidenceIds.includes('root-services'),'Missing current evidence mapping');
    for(const source of[entry.primaryCode,...entry.tests,...entry.sharedCurrentTests]){const target=resolve(repo,source);assert(target.startsWith(repo+'/')&&statSync(target).isFile(),'Missing current code/test: '+source);}
  }
  return map.entries.length;
}
if(process.argv[1]===import.meta.filename){console.log(`Validated ${checkRegistry()} exact criteria`);checkLinks();checkAcceptanceMap();captureRequirements();console.log('Tooling documentation links, acceptance mapping and requirement inputs valid');}
