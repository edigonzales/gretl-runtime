import {spawnSync} from 'node:child_process';
for (const exe of ['java']) {
  const r=spawnSync(exe,['-version'],{encoding:'utf8'});
  console.log(JSON.stringify({exe,status:r.status,stdout:r.stdout,stderr:r.stderr}));
}
console.log('JAVA_HOME='+process.env.JAVA_HOME);
