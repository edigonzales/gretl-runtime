import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdirSync,writeFileSync,readFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {discoverOpenCode,waitForReviewer,scopedProfile,verifyModelIdentity,writeScopedProfile} from './review.mjs';

/** Real access probe, separately from any acceptance verdict. Existing saved authentication is used. */
export async function preflightReviewAccess(binary,directory){
  const cli=discoverOpenCode(binary);mkdirSync(directory,{recursive:true,mode:0o700});
  const evidence=resolve(directory,'evidence');mkdirSync(evidence,{mode:0o700});
  const git=spawnSync('git',['init','-q',directory],{encoding:'utf8'});assert.equal(git.status,0); // A distinct project identity is required by OpenCode location resolution.
  writeFileSync(resolve(directory,'probe.txt'),'GRETL independent access probe\n',{flag:'wx'});
  writeFileSync(resolve(directory,'probe.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';test('independent process',()=>assert.equal(6*7,42));\n",{flag:'wx'});
  const profile=writeScopedProfile(directory,evidence,[{argv:['node','--test','probe.test.mjs']}]);
  const mappingUrl='https://api-docs.deepseek.com/quick_start/pricing/';
  const response=await fetch(mappingUrl,{signal:AbortSignal.timeout(30000)});assert(response.ok);
  const html=await response.text();assert(html.includes('DeepSeek-V4.1-Flash-1017')&&html.includes('deepseek-flash'));
  const mappingText=html.replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
  writeFileSync(resolve(directory,'official-model-mapping.txt'),mappingText);
  const argv=['run','--standalone','--agent','gretl-independent-review','--model','deepseek/deepseek-flash','--format','json',
    `Read probe.txt. Run exactly node --test probe.test.mjs with no appended commands. Write ${evidence}/readiness.md with actual marker and test result. This is an access probe, not acceptance. Do not change other files.`];
  const completion=await waitForReviewer(cli.path,argv,directory,evidence,120000);
  assert.equal(completion.exitCode,0);assert.equal(completion.timedOut,false);
  const events=readFileSync(resolve(evidence,'opencode.jsonl'),'utf8').trim().split('\n').map(s=>JSON.parse(s));
  assert.equal(events.at(-1)?.part?.reason,'stop');assert(!events.some(e=>e.type==='error'));
  const tools=events.filter(e=>e.type==='tool_use').map(e=>e.part);
  assert(tools.some(p=>p.tool==='read'&&p.state.status==='completed'));
  assert(tools.some(p=>p.tool==='shell'&&p.state.status==='completed'&&p.state.input.command==='node --test probe.test.mjs'&&/tests 1/.test(p.state.output)&&/pass 1/.test(p.state.output)));
  assert(readFileSync(resolve(evidence,'readiness.md'),'utf8').includes('GRETL independent access probe'));
  const session=events.find(e=>e.sessionID)?.sessionID;assert(session);
  const exported=spawnSync(cli.path,['session','export','--standalone','--sanitize',session],{cwd:directory,env:{...process.env,PWD:directory},encoding:'utf8',maxBuffer:16<<20,timeout:30000});assert.equal(exported.status,0);
  const sessionData=JSON.parse(exported.stdout),models=[];
  function visit(v){if(!v||typeof v!=='object')return;if(v.providerID&&v.id)models.push({provider:v.providerID,model:v.id});for(const c of Object.values(v))visit(c);}visit(sessionData);
  assert(models.length>0&&models.every(m=>m.provider==='deepseek'&&m.model==='deepseek-flash'));
  const identity={openCodeVersion:cli.version,provider:'deepseek',model:'deepseek-flash',apiHost:'api.deepseek.com',modelVersion:'DeepSeek-V4.1-Flash-1017',mappingUrl,mappingText,checkedAt:new Date().toISOString(),actualProvider:'deepseek',actualModel:'deepseek-flash',effectiveBaseUrl:profile.providers.deepseek.settings.baseURL,sessionId:session,models,
    limitation:'Effective endpoint explicitly pinned in private project profile; session metadata verifies provider/model. No cryptographic claim about server weights.'};
  verifyModelIdentity(identity);
  writeFileSync(resolve(directory,'identity.json'),JSON.stringify(identity,null,2));writeFileSync(resolve(directory,'process.json'),JSON.stringify(completion,null,2));
  return identity;
}
if(process.argv[1]===import.meta.filename){const [binary,directory]=process.argv.slice(2);await preflightReviewAccess(resolve(binary),resolve(directory));console.log('Independent direct-DeepSeek access probe completed');}
