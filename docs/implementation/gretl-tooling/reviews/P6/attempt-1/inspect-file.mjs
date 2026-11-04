import {readFile} from 'node:fs/promises';
const p='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/gui/current/cases/graph-first-open.png';
const b=await readFile(p);
console.log({length:b.length,head:[...b.slice(0,32)],ascii:b.toString('ascii',0,16)});
