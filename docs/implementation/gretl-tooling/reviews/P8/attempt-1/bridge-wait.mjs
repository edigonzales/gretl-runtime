import { existsSync } from 'node:fs';
const file = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence/gui-bridge/responses/012.json';
const deadline = Date.now() + 180000;
while (Date.now() < deadline) {
  if (existsSync(file)) { console.log('READY'); process.exit(0); }
  await new Promise(resolve => setTimeout(resolve, 1000));
}
console.log('NO_RESPONSE');
