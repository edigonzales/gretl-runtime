import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import Ajv from 'ajv/dist/2020.js';
const path=new URL('../../gretl-lsp/src/main/resources/gretl-protocol-v1.schema.json',import.meta.url);
const schema=JSON.parse(fs.readFileSync(path));
const ajv=new Ajv({strict:false,formats:{uuid:/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i}});const validate=ajv.compile(schema);
const id='11111111-1111-1111-1111-111111111111',symbol={namespace:'TASK',qualifiedName:':part:a'};
test('versioned custom requests accept typed data and exact 64-bit scalar strings',()=> {
 for(const request of [
  {protocolVersion:1,catalogSchemaVersion:1},
  {protocolVersion:1,documentId:'file:///work/job.groovy',requestedSources:['file:///work/query.sql']},
  {protocolVersion:1,documentId:'file:///work/job.groovy',expectedSnapshotId:id,requireOpenDocuments:true,intent:{kind:'SetField',target:symbol,field:'limit',value:{kind:'Long',scalar:'9223372036854775807'}}},
  {protocolVersion:1,planId:id,expectedSnapshotId:id},
  {protocolVersion:1,documentId:'file:///work/job.groovy',expectedSnapshotId:id,offsetUtf16:23}
 ])assert(validate(request),JSON.stringify(validate.errors));
});
test('schema rejects unknown raw-code values, missing fields, wrong revisions and broad resource requests',()=> {
 const base={protocolVersion:1,documentId:'file:///work/job.groovy',expectedSnapshotId:id,requireOpenDocuments:true};
 for(const request of [
  {...base,intent:{kind:'SetField',target:symbol,field:'x',value:{kind:'RawGroovy',scalar:'new File(...)'}}},
  {...base,intent:{kind:'BindResult',target:symbol,producer:symbol,field:'input'}},
  {...base,intent:{kind:'SetField',target:symbol,field:'x',value:{kind:'Long',scalar:9223372036854775807}}},
  {protocolVersion:2,catalogSchemaVersion:1},
  {protocolVersion:1,documentId:'https://remote.invalid/job.groovy'},
  {protocolVersion:1,documentId:'file:///work/job.groovy',requestedSources:Array.from({length:33},(_,i)=>'file:///work/'+i)},
  {protocolVersion:1,documentId:'file:///work/job.groovy',launchCommand:'unsafe'}
 ])assert(!validate(request),JSON.stringify(request));
});

test('include context requests are additive, negotiated and bounded',()=>{
 const context={protocolVersion:1,includeToolingVersion:1,documentId:'file:///work/parts/tasks.groovy'};
 for(const value of [context,{...context,registerAsEntry:true},{...context,contextToken:id,ownerGeneration:'42',incarnation:'7'},
  {...context,ownerGeneration:'42',syncToken:id,buffers:[{documentId:context.documentId,version:7,sha256:'a'.repeat(64)}]},
  {protocolVersion:1,documentId:context.documentId,contextToken:id}])assert(validate(value),JSON.stringify(validate.errors));
 for(const value of [{...context,includeToolingVersion:2},{...context,contextToken:'forged',ownerGeneration:'42'},
  {...context,contextToken:id,ownerGeneration:42},{...context,ownerGeneration:'42',syncToken:id,buffers:Array(513).fill({documentId:context.documentId})}])assert(!validate(value),JSON.stringify(value));
});
