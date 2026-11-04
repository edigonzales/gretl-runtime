import {readFile,writeFile,stat} from 'node:fs/promises';
import {isAbsolute,resolve,dirname} from 'node:path';
const evidence='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const product='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/product';
const verdict=JSON.parse(await readFile(`${evidence}/verdict.json`,'utf8'));
const review=await readFile(`${evidence}/review.md`,'utf8');
const map=p=>{
  if(isAbsolute(p))return p;
  const base=p.replace(/#.*$/,'');
  if(base.startsWith('evidence/'))return resolve(evidence,base.slice('evidence/'.length));
  if(base.startsWith('docs/')||base.startsWith('gretl-')||base.startsWith('vscode/')||base.startsWith('tools/'))return resolve(product,base);
  throw new Error(`Unmapped evidence reference: ${p}`);
};
const missing=[];
const used=new Set();
const check=async p=>{try{const s=await stat(p);if(!s.isFile())missing.push(`not a regular file: ${p}`);used.add(p)}catch{missing.push(`missing: ${p}`)}};
for(const c of verdict.criteria)for(const p of c.evidence)await check(map(p));
for(const c of verdict.counterprobes)for(const p of c.evidence)await check(map(p));
for(const c of verdict.executedChecks)for(const p of c.evidence)await check(map(p));
for(const c of verdict.verifiedCurrentEvidence)for(const p of c.evidence)await check(map(p));
for(const g of verdict.guiEvidence)for(const p of g.evidence)await check(map(p));
console.log(JSON.stringify({unique:used.size,missing},null,2));
if(missing.length)process.exit(1);
const out=structuredClone(verdict);
for(const c of out.criteria)c.evidence=c.evidence.map(map);
for(const c of out.counterprobes)c.evidence=c.evidence.map(map);
for(const c of out.executedChecks)c.evidence=c.evidence.map(map);
for(const c of out.verifiedCurrentEvidence)c.evidence=c.evidence.map(map);
for(const g of out.guiEvidence)g.evidence=g.evidence.map(map);
await writeFile(`${evidence}/resume-1/verdict.json`,JSON.stringify(out,null,2)+'\n');
const note=`
## Metadata correction after outer validation

The first submission of this complete review was rejected by the outer validator with
\`Evidence must be an absolute local path: docs/implementation/gretl-tooling/implementation/P6.md\`.
The review's technical work, evidence, PASS judgment and all identities remain unchanged.
Only the machine-readable \`evidence\` arrays in \`verdict.json\` were corrected: every entry
is now an absolute path to an existing regular local file. Repository-relative references
and \`#anchor\` qualifiers that remain in this report are explanatory prose; anchors were
removed from the machine-readable evidence entries. No test, root gate, GUI case or npm
task was re-executed for this correction, and no supplied run was relabelled as new.

This file is the complete original \`review.md\` plus this metadata note. The original
\`review.md\`, \`verdict.json\`, \`process.json\` and \`validation.json\` remain untouched in the
parent evidence directory.
`;
await writeFile(`${evidence}/resume-1/review.md`,review+note);
console.log('wrote resume-1/review.md and resume-1/verdict.json');
