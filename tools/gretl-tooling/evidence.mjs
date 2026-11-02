import {spawn,spawnSync} from 'node:child_process';
import {mkdirSync,existsSync,readdirSync,readFileSync,writeFileSync,copyFileSync,statSync} from 'node:fs';
import {resolve,relative,dirname} from 'node:path';
import {hash} from './review.mjs';
import assert from 'node:assert/strict';
const repo=resolve(import.meta.dirname,'../..');
function* files(dir) {if(!existsSync(dir))return;for(const e of readdirSync(dir,{withFileTypes:true})){const p=resolve(dir,e.name);if(e.isDirectory())yield*files(p);else if(e.isFile())yield p;}}
export function snapshotReports(destination) {
  mkdirSync(destination,{recursive:true,mode:0o700});
  const modules=[...readFileSync(resolve(repo,'pom.xml'),'utf8').matchAll(/<module>([^<]+)<\/module>/g)].map(m=>m[1]);
  const counts={tests:0,failures:0,errors:0,skipped:0};const captured=[];
  for(const module of modules)for(const p of files(resolve(repo,module,'target'))){
    const rel=relative(repo,p);
    if(!(/\/(?:surefire|failsafe)-reports\//.test(rel)||/\/job-evidence\/.*\/(?:execution|package-inventory|report)\.json$/.test(rel)||/\/qualification\//.test(rel)||/\/(?:inventory|package-catalog)\.json$/.test(rel)||/\/catalog\//.test(rel)))continue;
    const dst=resolve(destination,rel);mkdirSync(dirname(dst),{recursive:true});copyFileSync(p,dst);captured.push({path:dst,sha256:hash(readFileSync(dst))});
    if(/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(rel)){
      const header=readFileSync(p,'utf8').match(/<testsuite\b[^>]*>/)?.[0];
      assert(header,`Missing test suite ${rel}`);
      for(const key of Object.keys(counts))counts[key]+=Number(header.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1]??0);
    }
  }
  const result={counts,captured};writeFileSync(resolve(destination,'reports.json'),JSON.stringify(result,null,2)+'\n');return result;
}
export async function runGate(destination,argv){
  assert(!existsSync(destination),'Fresh gate directory required');mkdirSync(destination,{recursive:true,mode:0o700});
  const sha=spawnSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).stdout.trim();
  const dirty=spawnSync('git',['status','--porcelain'],{cwd:repo,encoding:'utf8'}).stdout.trim();
  const startedAt=new Date().toISOString();
  const log=await import('node:fs').then(fs=>fs.openSync(resolve(destination,'command.log'),'wx',0o600));
  const exit=await new Promise((done,reject)=>{const child=spawn(argv[0],argv.slice(1),{cwd:repo,stdio:['ignore',log,log]});child.on('error',reject);child.on('close',(code,signal)=>done({code,signal}));});
  (await import('node:fs')).closeSync(log);
  const reports=snapshotReports(resolve(destination,'evidence'));
  const manifest={sha,dirty,argv,cwd:repo,startedAt,endedAt:new Date().toISOString(),exit,...reports};
  writeFileSync(resolve(destination,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  assert.equal(exit.code,0,'Gate command failed');assert.equal(exit.signal,null);
  assert(reports.counts.tests>0,'No tests');for(const k of ['failures','errors','skipped'])assert.equal(reports.counts[k],0,`Gate ${k}`);
  return manifest;
}
if(process.argv[1]===import.meta.filename){const[action,destination,...argv]=process.argv.slice(2);if(action==='snapshot')snapshotReports(resolve(destination));else if(action==='run')await runGate(resolve(destination),argv);else throw new Error('evidence.mjs snapshot <dir> | run <dir> <argv...>');}
