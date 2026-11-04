#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const options={};for(let i=2;i<process.argv.length;i+=2)options[process.argv[i].slice(2)]=process.argv[i+1];
assert(options.evidence,'Usage: node tools/gretl-tooling/qualify-runtime-canary.mjs --evidence /fresh/directory [--candidate SHA]');
const root=path.resolve(options.evidence);assert(!fs.existsSync(root));fs.mkdirSync(root,{recursive:true,mode:0o700});
const hash=bytes=>createHash('sha256').update(bytes).digest('hex'),distribution=repo+'/gretl-dist/target/dist';
const catalog=JSON.parse(fs.readFileSync(distribution+'/package-catalog.json'));
const head=spawnSync('git',['-C',repo,'rev-parse','HEAD'],{encoding:'utf8'}).stdout.trim(),dirty=spawnSync('git',['-C',repo,'status','--porcelain'],{encoding:'utf8'}).stdout.trim()!=='';
if(options.candidate){assert.equal(head,options.candidate);assert.equal(dirty,false);}
if(options.candidate){assert.equal(catalog.sourceRevision,options.candidate);assert.equal(catalog.sourceDirty,false);}
const javaHome=process.env.JAVA_HOME;assert(javaHome,'An explicit JDK 25 JAVA_HOME is required');
function run(label,program,args,cwd=root){
 const result=spawnSync(program,args,{cwd,encoding:'utf8',timeout:90000,maxBuffer:4<<20});
 fs.writeFileSync(root+'/'+label+'.stdout.log',result.stdout??'');fs.writeFileSync(root+'/'+label+'.stderr.log',result.stderr??'');
 return {argv:[program,...args],cwd,exitCode:result.status,signal:result.signal,stdout:result.stdout??'',stderr:result.stderr??'',error:result.error?.message};
}
const version=run('java-version',javaHome+'/bin/java',['-version']);assert.equal(version.exitCode,0);assert.match(version.stderr,/version "25\./);
const xml=repo+'/gretl-integration-tests/target/failsafe-reports/TEST-ch.so.agi.gretl.it.GraphicalModelerScriptJobIT.xml';
const cpRun=run('read-current-test-classpath','python3',['-c','import sys,xml.etree.ElementTree as E; r=E.parse(sys.argv[1]).getroot(); print(next(p.get("value") for p in r.findall("properties/property") if p.get("name")=="java.class.path"))',xml]);assert.equal(cpRun.exitCode,0);const classpath=cpRun.stdout.trim();assert(classpath.includes('gretl-integration-tests/target/test-classes'));
const sourceFile=repo+'/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/GraphicalModelerScriptJobIT.java',original=fs.readFileSync(sourceFile,'utf8');
const oldName='GraphicalModelerScriptJobIT',method='actualGuiGeneratedSourceDownloadsAndExtractsALocalArchiveWithoutRewritingItsBytes',badWrite='zip.write("WRONG CONTROLLED RESULT".getBytes(StandardCharsets.UTF_8));';assert.equal(original.split('zip.write(payload);').length,2);
const sourceRoot=root+'/sources/ch/so/agi/gretl/it';fs.mkdirSync(sourceRoot,{recursive:true});
for(const [name,negative]of[['GraphicalModelerScriptControl',false],['GraphicalModelerScriptCanary',true]]){
 let text=original.replaceAll(oldName,name);if(negative)text=text.replace('zip.write(payload);',badWrite);
 assert.equal(text.replaceAll(name,oldName).replace(badWrite,'zip.write(payload);'),original,'The original oracle assertions must remain unchanged');
 fs.writeFileSync(sourceRoot+'/'+name+'.java',text);
}
fs.writeFileSync(sourceRoot+'/RuntimeCanaryMain.java',`package ch.so.agi.gretl.it;
public final class RuntimeCanaryMain {
 public static void main(String[] args)throws Throwable {
  var type=Class.forName("ch.so.agi.gretl.it."+args[0]);var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
  var method=type.getDeclaredMethod("${method}");method.setAccessible(true);
  try{method.invoke(constructor.newInstance());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
 }
}
`);
const classes=root+'/classes';fs.mkdirSync(classes);
const compile=run('compile-scratch-oracle',javaHome+'/bin/javac',['--release','25','-encoding','UTF-8','-cp',classpath,'-d',classes,...fs.readdirSync(sourceRoot).map(f=>sourceRoot+'/'+f)]);assert.equal(compile.exitCode,0,compile.stderr);
const results=[];
for(const [label,name,expected]of[['control','GraphicalModelerScriptControl',0],['wrong-result','GraphicalModelerScriptCanary',1]]){
 const cwd=root+'/'+label;fs.mkdirSync(cwd);
 const executed=run(label,javaHome+'/bin/java',['-Dfile.encoding=UTF-8','-Dgretl.test.reactorDistribution='+distribution,'-Dgretl.test.candidateSha='+catalog.sourceRevision,'-Dgretl.test.runId=controlled-oracle-canary','-cp',classes+path.delimiter+classpath,'ch.so.agi.gretl.it.RuntimeCanaryMain',name],cwd);
 assert.equal(executed.exitCode,expected,executed.stderr);
 if(expected===1){assert.match(executed.stderr,/org.opentest4j.AssertionFailedError: array lengths differ/);assert(executed.stderr.includes(name+'.'+method));}
 const jobs=cwd+'/target/job-evidence',directories=fs.readdirSync(jobs);assert.equal(directories.length,1);
 const job=jobs+'/'+directories[0],facts=JSON.parse(fs.readFileSync(job+'/execution.json'));assert.equal(facts.mode,'SCRIPT');assert.equal(facts.exitCode,0);assert.equal(facts.timedOut,false);assert.equal(facts.sourceRevision,catalog.sourceRevision);
 const staged=job+'/source/job.groovy',fixture=repo+'/gretl-integration-tests/src/test/resources/jobs/modeler-created/job.groovy';assert.equal(hash(fs.readFileSync(staged)),hash(fs.readFileSync(fixture)));
 results.push({label,oracleExitCode:executed.exitCode,runtimeExitCode:facts.exitCode,mode:facts.mode,execution:job+'/execution.json',inputSha256:hash(fs.readFileSync(staged)),stderr:root+'/'+label+'.stderr.log',expectedFailure:expected===1});
}
const result={kind:options.candidate?'FINAL_CANDIDATE_RUNTIME_ORACLE_CANARY':'DEVELOPMENT_RUNTIME_ORACLE_CANARY',passed:true,candidateSha:catalog.sourceRevision,sourceDirty:catalog.sourceDirty,probeRepositoryHead:head,probeRepositoryDirty:dirty,javaVersion:version.stderr,originalOracle:sourceFile,originalOracleSha256:hash(Buffer.from(original)),mutation:'Only the private copied local HTTP ZIP payload is wrong. Original JUnit assertions and exact GUI-generated Groovy input are unchanged.',results};
fs.writeFileSync(root+'/qualification.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
