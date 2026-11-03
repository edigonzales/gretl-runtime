import {readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const vsix=path.resolve(process.argv[2]??path.join(root,'dist/gretl-runtime.vsix'));
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const names=execFileSync('unzip',['-Z1',vsix],{encoding:'utf8'}).trim().split('\n').filter(name=>!name.endsWith('/'));
assert.equal(new Set(names).size,names.length,'Duplicate VSIX entries');
function read(name){const pattern=name.replaceAll('\\','\\\\').replaceAll('*','\\*').replaceAll('?','\\?').replaceAll('[','\\[').replaceAll(']','\\]');return execFileSync('unzip',['-p',vsix,pattern],{maxBuffer:32<<20,stdio:['ignore','pipe','pipe']});}
const manifest=JSON.parse(read('extension/server/bundle-manifest.json').toString());
const pkg=JSON.parse(read('extension/package.json').toString());
assert.equal(manifest.extensionId,pkg.publisher+'.'+pkg.name);assert.match(manifest.sourceCommit,/^[a-f0-9]{40}$/);
assert.equal(manifest.extensionVersion,pkg.version);assert.equal(typeof manifest.sourceDirty,'boolean');
const identity=read('extension.vsixmanifest').toString().match(/<Identity\b[^>]*>/)?.[0];assert.ok(identity,'Missing VSIX identity');
for(const [attribute,expected]of [['Id',pkg.name],['Publisher',pkg.publisher],['Version',pkg.version]])assert.equal(identity.match(new RegExp('\\b'+attribute+'="([^"]+)"'))?.[1],expected,'VSIX '+attribute);
assert.deepEqual(manifest.inventoryExcludes,['server/bundle-manifest.json']);
assert.equal(manifest.javaRelease,25);assert.equal(manifest.wireSchemaVersion,1);assert.ok(manifest.mavenVersion&&manifest.groovyVersion&&manifest.mavenArtifactVersion);
const expected=['[Content_Types].xml','extension.vsixmanifest','extension/server/bundle-manifest.json',...Object.keys(manifest.payloadSha256).map(name=>'extension/'+name)].sort();
assert.deepEqual(names.toSorted(),expected,'Missing or unexpected delivered payload');
const entries=[];
for(const name of names){assert.ok(!name.startsWith('/')&&!name.split('/').includes('..'),'Unsafe archive entry');const bytes=read(name),digest=hash(bytes);if(name.startsWith('extension/')&&name!=='extension/server/bundle-manifest.json')assert.equal(digest,manifest.payloadSha256[name.slice(10)],'Payload digest mismatch: '+name);entries.push({path:name,size:bytes.length,sha256:digest});}
const inventory={schemaVersion:1,candidateSha:manifest.sourceCommit,extensionId:manifest.extensionId,vsix,vsixSha256:hash(await readFile(vsix)),manifestSha256:hash(read('extension/server/bundle-manifest.json')),entries};
const output=process.argv[3]??path.join(root,'dist/distribution-inventory.json');await writeFile(output,JSON.stringify(inventory,null,2)+'\n');console.log('Verified '+entries.length+' delivered VSIX entries and complete payload hashes.');
