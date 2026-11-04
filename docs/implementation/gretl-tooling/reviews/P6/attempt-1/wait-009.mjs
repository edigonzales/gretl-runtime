import {access} from 'node:fs/promises';
const target='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/gui-bridge/responses/009.json';
const deadline=Date.now()+10*60*1000;
while(Date.now()<deadline){
  try{await access(target);console.log('response ready');process.exit(0)}catch{}
  await new Promise(r=>setTimeout(r,1000));
}
console.error('timeout');process.exit(1);
