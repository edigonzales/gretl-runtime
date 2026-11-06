import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtempSync,writeFileSync,readFileSync,rmSync,mkdirSync,utimesSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {snapshotReports,verifyCurrentGateManifest} from './evidence.mjs';
import {verifyTree} from './verify-dependencies.mjs';

const scratch=mkdtempSync(resolve(tmpdir(),'gretl-evidence-unit-'));
const hash=data=>createHash('sha256').update(data).digest('hex');

test('resolved dependency guard detects production leaks while permitting test parity',()=>{
  for(const artifactId of ['gretl-api','gretl-runner','gretl-tooling-catalog','gretl-next'])assert.throws(()=>verifyTree({children:[{groupId:'ch.so.agi.gretl',artifactId,scope:'compile'}]},'core'));
  assert.throws(()=>verifyTree({children:[{groupId:'com.fasterxml.jackson.core',artifactId:'jackson-databind',scope:'compile'}]},'contract'));
  verifyTree({children:[{groupId:'ch.so.agi.gretl',artifactId:'gretl-runner',scope:'test'}]},'core');
});
function reportFixture(){
  const root=mkdtempSync(resolve(scratch,'reports-')),start=Date.now()-10000;
  writeFileSync(resolve(root,'pom.xml'),'<project><modules><module>active</module></modules><profiles><profile><modules><module>inactive</module></modules></profile></profiles></project>');
  for(const [module,count,modified] of [['active',3,start+1000],['inactive',4,start-1000]]){
    const dir=resolve(root,module,'target/surefire-reports');mkdirSync(dir,{recursive:true});
    for(const [name,text] of [['TEST-Case.xml',`<testsuite tests="${count}" failures="0" errors="0" skipped="0"></testsuite>`],['Case.txt','Synthetic report fixture']]){
      const file=resolve(dir,name);writeFileSync(file,text);utimesSync(file,modified/1000,modified/1000);
    }
  }
  return {root,start:new Date(start).toISOString(),end:new Date().toISOString()};
}
test('inactive profile reports are preserved separately and cannot inflate fresh counts',()=>{
  const f=reportFixture(),snapshot=snapshotReports(resolve(f.root,'saved'),{sourceRoot:f.root,notBefore:f.start});
  assert.deepEqual(snapshot.counts,{tests:3,failures:0,errors:0,skipped:0});assert.equal(snapshot.captured.length,2);assert.equal(snapshot.priorArtifacts.length,2);
  for(const artifact of snapshot.priorArtifacts){assert.equal(artifact.reason,'predatesGateStart');assert.equal(hash(readFileSync(artifact.path)),artifact.sha256);assert(artifact.path.includes('/prior-artifacts/inactive/'));}
  assert(readFileSync(resolve(f.root,'inactive/target/surefire-reports/TEST-Case.xml'),'utf8').includes('tests="4"'));
  verifyCurrentGateManifest({sha:'c'.repeat(40),dirty:'',exit:{code:0,signal:null},argv:['synthetic'],startedAt:f.start,endedAt:f.end,...snapshot},'c'.repeat(40),['synthetic']);
});
test('pre-clean archival snapshots deliberately preserve every existing report',()=>{
  const f=reportFixture(),snapshot=snapshotReports(resolve(f.root,'saved'),{sourceRoot:f.root});
  assert.equal(snapshot.counts.tests,7);assert.equal(snapshot.captured.length,4);assert.equal(snapshot.priorArtifacts.length,0);
});
test('gate validation rejects inflated counts, stale metadata and changed raw XML',()=>{
  const f=reportFixture(),snapshot=snapshotReports(resolve(f.root,'saved'),{sourceRoot:f.root,notBefore:f.start});
  const manifest={sha:'c'.repeat(40),dirty:'',exit:{code:0,signal:null},argv:['synthetic'],startedAt:f.start,endedAt:f.end,...snapshot};
  const inflated=structuredClone(manifest);inflated.counts.tests+=4;assert.throws(()=>verifyCurrentGateManifest(inflated,manifest.sha,manifest.argv),/counts differ/);
  const stale=structuredClone(manifest);stale.captured.push(snapshot.priorArtifacts[0]);assert.throws(()=>verifyCurrentGateManifest(stale,manifest.sha,manifest.argv),/predates/);
  const changed=manifest.captured.find(a=>a.path.endsWith('.xml'));writeFileSync(changed.path,'<testsuite tests="99" failures="0" errors="0" skipped="0"/>');
  assert.throws(()=>verifyCurrentGateManifest(manifest,manifest.sha,manifest.argv),/Artifact changed/);
});
test('only old reports cannot qualify a new successful gate',()=>{
  const f=reportFixture();rmSync(resolve(f.root,'active'),{recursive:true});
  const snapshot=snapshotReports(resolve(f.root,'saved'),{sourceRoot:f.root,notBefore:f.start});assert.equal(snapshot.counts.tests,0);assert.equal(snapshot.captured.length,0);
  assert.throws(()=>verifyCurrentGateManifest({sha:'c'.repeat(40),dirty:'',exit:{code:0,signal:null},argv:['synthetic'],startedAt:f.start,endedAt:f.end,...snapshot},'c'.repeat(40),['synthetic']),/No current/);
});
test.after(()=>rmSync(scratch,{recursive:true}));
