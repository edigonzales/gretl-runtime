import {spawn,spawnSync} from 'node:child_process';
import {mkdirSync,existsSync,readdirSync,readFileSync,writeFileSync,copyFileSync,statSync} from 'node:fs';
import {resolve,relative,dirname,isAbsolute} from 'node:path';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const hash = data => createHash('sha256').update(data).digest('hex');

export function verifyArtifact(a) {
  assert(isAbsolute(a.path)); assert(statSync(a.path).isFile());
  assert.equal(hash(readFileSync(a.path)),a.sha256,`Artifact changed: ${a.path}`);
}
export function verifyCurrentGateManifest(manifest,candidateSha,argv){
  assert.equal(manifest.sha,candidateSha);assert.equal(manifest.dirty,'');assert.equal(manifest.exit.code,0);assert.equal(manifest.exit.signal,null);
  assert.deepEqual(manifest.argv,argv);
  const start=Date.parse(manifest.startedAt),end=Date.parse(manifest.endedAt);
  assert(Number.isFinite(start)&&Number.isFinite(end)&&end>=start,'Invalid gate times');
  const counts={tests:0,failures:0,errors:0,skipped:0};
  assert(Array.isArray(manifest.captured)&&manifest.captured.length>0,'No current captured evidence');
  const paths=new Set();
  for(const artifact of manifest.captured){
    assert(!paths.has(artifact.path),'Duplicate captured evidence');paths.add(artifact.path);verifyArtifact(artifact);
    const modified=Date.parse(artifact.sourceModifiedAt);
    assert(Number.isFinite(modified)&&modified>=start&&modified<=end,'Captured evidence predates or postdates gate');
    if(/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(artifact.path)){
      const header=readFileSync(artifact.path,'utf8').match(/<testsuite\b[^>]*>/)?.[0];assert(header,'Missing test suite');
      for(const key of Object.keys(counts))counts[key]+=Number(header.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1]??0);
    }
  }
  assert.deepEqual(manifest.counts,counts,'Manifest counts differ from current raw XML');
  assert(counts.tests>0,'No fresh tests');for(const key of ['failures','errors','skipped'])assert.equal(counts[key],0,`Gate ${key}`);
}

const repo=resolve(import.meta.dirname,'../..');
function* files(dir) {if(!existsSync(dir))return;for(const e of readdirSync(dir,{withFileTypes:true})){const p=resolve(dir,e.name);if(e.isDirectory())yield*files(p);else if(e.isFile())yield p;}}
export function snapshotReports(destination,{sourceRoot=repo,notBefore=null}={}) {
  mkdirSync(destination,{recursive:true,mode:0o700});
  const modules=[...readFileSync(resolve(sourceRoot,'pom.xml'),'utf8').matchAll(/<module>([^<]+)<\/module>/g)].map(m=>m[1]);
  const counts={tests:0,failures:0,errors:0,skipped:0};const captured=[],priorArtifacts=[];
  const cutoff=notBefore===null?null:Date.parse(notBefore);assert(cutoff===null||Number.isFinite(cutoff),'Invalid report cutoff');
  for(const module of modules)for(const p of files(resolve(sourceRoot,module,'target'))){
    const rel=relative(sourceRoot,p);
    if(!(/\/(?:surefire|failsafe)-reports\//.test(rel)||/\/job-evidence\/.*\/(?:execution|package-inventory|report)\.json$/.test(rel)||/\/qualification\//.test(rel)||/\/(?:inventory|package-catalog)\.json$/.test(rel)||/\/catalog\//.test(rel)))continue;
    const modified=statSync(p).mtimeMs,prior=cutoff!==null&&modified<cutoff;
    // Inactive Maven profiles retain their target directories across a standard clean.
    // Preserve these bytes separately; never count them as tests of the new candidate.
    const dst=resolve(destination,...(prior?['prior-artifacts',rel]:[rel]));mkdirSync(dirname(dst),{recursive:true});copyFileSync(p,dst);
    const artifact={path:dst,sha256:hash(readFileSync(dst)),sourceModifiedAt:new Date(modified).toISOString()};
    if(prior){priorArtifacts.push({...artifact,reason:'predatesGateStart'});continue;}
    captured.push(artifact);
    if(/\/(?:surefire|failsafe)-reports\/TEST-[^/]+\.xml$/.test(rel)){
      const header=readFileSync(p,'utf8').match(/<testsuite\b[^>]*>/)?.[0];
      assert(header,`Missing test suite ${rel}`);
      for(const key of Object.keys(counts))counts[key]+=Number(header.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1]??0);
    }
  }
  const result={counts,captured,priorArtifacts};writeFileSync(resolve(destination,'reports.json'),JSON.stringify(result,null,2)+'\n');return result;
}
export async function runGate(destination,argv){
  assert(!existsSync(destination),'Fresh gate directory required');mkdirSync(destination,{recursive:true,mode:0o700});
  const sha=spawnSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).stdout.trim();
  const dirty=spawnSync('git',['status','--porcelain'],{cwd:repo,encoding:'utf8'}).stdout.trim();
  const startedAt=new Date().toISOString();
  const log=await import('node:fs').then(fs=>fs.openSync(resolve(destination,'command.log'),'wx',0o600));
  const exit=await new Promise((done,reject)=>{const child=spawn(argv[0],argv.slice(1),{cwd:repo,stdio:['ignore',log,log]});child.on('error',reject);child.on('close',(code,signal)=>done({code,signal}));});
  (await import('node:fs')).closeSync(log);
  const reports=snapshotReports(resolve(destination,'evidence'),{notBefore:startedAt});
  const manifest={sha,dirty,argv,cwd:repo,startedAt,endedAt:new Date().toISOString(),exit,...reports};
  writeFileSync(resolve(destination,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  verifyCurrentGateManifest(manifest,sha,argv);
  return manifest;
}
if(process.argv[1]===import.meta.filename){const[action,destination,...argv]=process.argv.slice(2);if(action==='snapshot')snapshotReports(resolve(destination));else if(action==='run')await runGate(resolve(destination),argv);else throw new Error('evidence.mjs snapshot <dir> | run <dir> <argv...>');}
