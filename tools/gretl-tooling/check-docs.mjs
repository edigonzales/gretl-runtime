import assert from 'node:assert/strict';
import {readFileSync,existsSync,readdirSync} from 'node:fs';
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
if(process.argv[1]===import.meta.filename){console.log(`Validated ${checkRegistry()} exact criteria`);checkLinks();captureRequirements();console.log('Tooling documentation links and requirement inputs valid');}
