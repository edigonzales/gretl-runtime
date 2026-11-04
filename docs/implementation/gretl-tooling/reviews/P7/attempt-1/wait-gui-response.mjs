import {readFile} from 'node:fs/promises';
const id=process.argv[2];
if(!id)throw new Error('usage: wait-gui-response.mjs ID');
const file=`/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/gui-bridge/responses/${id}.json`;
const deadline=Date.now()+10*60_000;
while(Date.now()<deadline){
  try{
    const text=await readFile(file,'utf8');
    console.log(text);
    process.exit(0);
  }catch(error){
    if(error.code!=='ENOENT')throw error;
  }
  await new Promise(resolve=>setTimeout(resolve,2000));
}
console.error('timeout waiting for '+file);
process.exit(2);
