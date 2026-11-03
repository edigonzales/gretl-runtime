import assert from 'node:assert/strict';
import Ajv from 'ajv/dist/2020.js';
import {hash} from './review.mjs';
import {readFileSync,existsSync,readdirSync} from 'node:fs';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
const repo=resolve(import.meta.dirname,'../..');
export function verifyTree(node,kind){
  const internal=kind==='contract'?new Set():new Set(['gretl-tooling-core','gretl-language-contract',...(kind==='server'?['gretl-tooling-catalog']:[])]);
  const visit=(n,root=false)=>{
    if(!root&&n.scope!=='test'){
      if(kind==='contract')assert.fail(`Contract has production dependency ${n.groupId}:${n.artifactId}`);
      if(n.groupId==='ch.so.agi.gretl')assert(internal.has(n.artifactId),`Forbidden runtime module ${n.artifactId}`);
      assert(!/gradle|geotools/.test(n.groupId)&&!(kind!=='server'&&/lsp4j/.test(n.groupId)),'Forbidden production library');
      if(n.artifactId==='gretl-tooling-catalog')assert(n.classifier==='catalog'&&!(n.children?.length),'Only isolated data classifier allowed in server');
      assert(!/gretl-next|gretl-doclet/.test(n.artifactId),'Foreign GRETL dependency');
    }
    if(n.scope==='test')return;for(const c of n.children??[])visit(c);
  };visit(node,true);
}
export function verifyArchives(){
  const catalogDirectory=resolve(repo,'gretl-tooling-catalog/target/catalog');
  const catalogBytes=readFileSync(resolve(catalogDirectory,'operations.json'));
  const schemaBytes=readFileSync(resolve(catalogDirectory,'operations.schema.json'));
  const catalog=JSON.parse(catalogBytes),manifest=JSON.parse(readFileSync(resolve(catalogDirectory,'catalog-manifest.json')));
  assert.equal(manifest.catalogId,catalog.catalogId);assert.equal(manifest.operationsSha256,hash(catalogBytes));assert.equal(manifest.schemaSha256,hash(schemaBytes));
  const validate=new Ajv({strict:false}).compile(JSON.parse(schemaBytes));assert(validate(catalog),'Generated catalog violates its bundled JSON schema');
  assert(!validate({...catalog,schemaVersion:2}),'Schema canary did not fail');
  assert(!validate({...catalog,operations:[]}),'Empty catalog schema canary did not fail');
  for(const mod of ['gretl-language-contract','gretl-tooling-core','gretl-tooling-catalog']){
    const dir=resolve(repo,mod,'target');assert(existsSync(dir));
    const jars=readdirSync(dir).filter(n=>n.endsWith('.jar'));assert(jars.length>0);
    for(const jar of jars){const r=spawnSync('jar',['tf',resolve(dir,jar)],{encoding:'utf8'});assert.equal(r.status,0);const lines=r.stdout.trim().split('\n');
      if(jar.endsWith('-catalog.jar')){assert(lines.includes('operations.json'));assert(lines.every(p=>!p.endsWith('.class')&&!p.endsWith('.jar')),'Data artifact contains executable code');}
      else assert(lines.every(p=>!/^ch\/so\/agi\/gretl\/runtime\/|^org\/gradle\//.test(p)),'Runtime leaked into tooling archive');
    }
  }
}
export function verifyServer(){
  verifyTree(JSON.parse(readFileSync(resolve(repo,'gretl-lsp/target/tooling-dependencies.json'))),'server');
  const file=resolve(repo,'gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar');
  const listing=spawnSync('jar',['tf',file],{encoding:'utf8'});assert.equal(listing.status,0);
  const entries=listing.stdout.trim().split('\n');
  for(const required of ['ch/so/agi/gretl/lsp/GretlServerLauncher.class','operations.json','operations.schema.json','catalog-manifest.json','gretl-protocol-v1.schema.json'])assert(entries.includes(required),`Server bundle missing ${required}`);
  assert(entries.every(p=>!/^ch\/so\/agi\/gretl\/runtime\/|^org\/gradle\/|^org\/geotools\//.test(p)&&!p.endsWith('.jar')),'Forbidden server archive content');
  assert(!entries.includes('META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider'),'Runtime provider discovery leaked into server');
  console.log('Independent server production graph and executable data bundle verified');
}
if(process.argv[1]===import.meta.filename){
  if(process.argv[2]==='--mode'&&process.argv[3]==='server'&&process.argv.length===4)verifyServer();
  else {assert.deepEqual(process.argv.slice(2),['--mode','all']);for(const [mod,kind] of [['gretl-language-contract','contract'],['gretl-tooling-core','core']])verifyTree(JSON.parse(readFileSync(resolve(repo,mod,'target/tooling-dependencies.json'))),kind);verifyArchives();console.log('Resolved tooling production graphs and actual archives verified');}
}
