import {readFile} from 'node:fs/promises';
const file='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/gui-bridge/responses/004.json';
const deadline=Date.now()+10*60_000;
while(Date.now()<deadline){
  try{console.log(await readFile(file,'utf8'));process.exit(0);}catch(error){if(error.code!=='ENOENT')throw error;}
  await new Promise(resolve=>setTimeout(resolve,2000));
}
console.error('timeout waiting for '+file);process.exit(2);
