"""Adversarial tests are isolated from real OpenCode and user credentials."""
import copy
import dataclasses
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2]/'scripts'))
import cleanup_review as c
import verify_cleanup_docs as docs


class GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        subprocess.run(['git','init','-q'],cwd=self.root,check=True)
        subprocess.run(['git','config','user.email','gate@example.invalid'],cwd=self.root,check=True)
        subprocess.run(['git','config','user.name','Gate Test'],cwd=self.root,check=True)
        (self.root/'old.md').write_text('old source\n')
        subprocess.run(['git','add','.'],cwd=self.root,check=True)
        subprocess.run(['git','commit','-qm','baseline'],cwd=self.root,check=True)
        self.base=c.git(self.root,'rev-parse','HEAD')
        (self.root/'old.md').unlink()
        (self.root/'README.md').write_text('new source\n')
        subprocess.run(['git','add','-A'],cwd=self.root,check=True)
        subprocess.run(['git','commit','-qm','candidate'],cwd=self.root,check=True)
        self.sha=c.git(self.root,'rev-parse','HEAD')
        self.r=c.ReviewRequest('P0',self.base,self.sha,self.base,self.root,self.root/'README.md')
        self.gates=[c.CommandEvidence(('fake-test',),self.root,0,self.root/'out',self.root/'err','start','end') for _ in range(3)]
        self.review=dict(schemaVersion=1,phase='P0',phaseBaseSha=self.base,candidateSha=self.sha,verdict='PASS',scopeComplete=True,
            requirements=[dict(id=i,status='PASS',evidence=[dict(path='README.md',observation='README.md documents the executable behaviour and preserved contract.',commandOrReference='git diff '+self.base+'..'+self.sha)]) for i in c.REQUIRED_IDS['P0']],
            independentInspection=dict(diffReviewed=True,newAndDeletedFilesReviewed=True,criticalPaths=['old.md','README.md'],
                objectivesAssessment='README.md replaces old.md and the whole changed scope was checked against implementation.',
                regressionAssessment='README.md changes documentation; relevant references and old.md deletions were checked individually.'),
            findings=[],openQuestions=[],recommendation='Scope reviewed against source and actual independent reports.')

    def tearDown(self): self.tmp.cleanup()
    def evaluate(self,review=None): return c.evaluate_review(review or self.review,self.r,self.root,self.gates)
    def stream(self,events):
        p=self.root/'events.jsonl'; p.write_text('\n'.join(json.dumps(x) for x in events)+'\n'); return p
    def events(self):
        return [{'type':'text','sessionID':'ses_test','part':{'text':c.BEGIN+'\n'+json.dumps(self.review)+'\n'+c.END}},
                {'type':'step_finish','sessionID':'ses_test','part':{'reason':'stop'}}]

    def test_bound_pass(self): self.evaluate()
    def test_wrong_sha(self):
        self.review['candidateSha']='f'*40
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_wrong_phase(self):
        self.review['phase']='P1'
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_schema_version_boolean_is_not_integer(self):
        self.review['schemaVersion']=True
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_missing_or_invalid_required_report_fields(self):
        for field,bad in [('recommendation',None),('recommendation',''),('requirements',{}),('findings',{}),('scopeComplete','true')]:
            with self.subTest(field=field,bad=bad):
                value=copy.deepcopy(self.review);value[field]=bad
                with self.assertRaises(c.Blocked):self.evaluate(value)
    def test_mapping_is_bound_to_api_column(self):
        html='<table><tr><th>MODEL</th><th>deepseek-flash(1)</th><th>deepseek-v4-pro</th></tr><tr><td>MODEL VERSION</td><td>'+c.CHECKPOINT+'</td><td>other</td></tr></table>'
        c.verify_official_mapping(html)
        for broken in [html.replace(c.CHECKPOINT,'a-new-flash')+'<p>'+c.CHECKPOINT+'</p>',
                       html.replace('<td>'+c.CHECKPOINT+'</td><td>other</td>','<td>other</td><td>'+c.CHECKPOINT+'</td>'),
                       html+html,'<p>deepseek-flash '+c.CHECKPOINT+'</p>']:
            with self.subTest(html=broken),self.assertRaises(c.Blocked):c.verify_official_mapping(broken)
    def test_missing_unknown_duplicate_criterion(self):
        for ids in [self.review['requirements'][:-1], self.review['requirements']+[self.review['requirements'][0]], [dict(id='P0-99')]]:
            with self.subTest(ids=[x['id'] for x in ids]):
                review=copy.deepcopy(self.review); review['requirements']=ids
                with self.assertRaises(c.Blocked): self.evaluate(review)
    def test_no_deleted_path_inspection(self):
        self.review['independentInspection']['criticalPaths']=['README.md']
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_no_diff_inspection(self):
        self.review['independentInspection']['diffReviewed']=False
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_pass_with_blocker(self):
        self.review['findings']=[dict(severity='BLOCKER',description='Wrong code')]
        with self.assertRaises(c.Failed): self.evaluate()
    def test_empty_objectives(self):
        self.review['independentInspection']['objectivesAssessment']=''
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_invented_path(self):
        self.review['requirements'][0]['evidence'][0]['path']='imaginary.java'
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_traversal_path(self):
        self.review['requirements'][0]['evidence'][0]['path']='../secrets'
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_unresolved_questions(self):
        self.review['openQuestions']=['Required proof missing']
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_p3_cumulative_ids(self): self.assertEqual(24,len(c.REQUIRED_IDS['P3']))
    def test_valid_jsonl(self): self.assertEqual(self.review,c.parse_final_review(self.stream(self.events())))
    def test_empty_exit_zero(self):
        with self.assertRaises(c.Blocked): c.parse_final_review(self.stream([]))
    def test_error_events_401_429(self):
        for error in ['401 Unauthorized','429 Rate limit']:
            with self.subTest(error=error),self.assertRaises(c.Blocked):
                c.parse_final_review(self.stream(self.events()+[{'type':'error','error':error}]))
    def test_invalid_jsonl(self):
        p=self.stream(self.events()); p.write_text(p.read_text()+'{broken\n')
        with self.assertRaises(c.Blocked): c.parse_final_review(p)
    def test_invalid_utf8(self):
        p=self.root/'bad'; p.write_bytes(b'\xff')
        with self.assertRaises(c.Blocked): c.parse_final_review(p)
    def test_truncated_stream(self):
        with self.assertRaises(c.Blocked): c.parse_final_review(self.stream(self.events()[:-1]))
    def test_ambiguous_final(self):
        with self.assertRaises(c.Blocked): c.parse_final_review(self.stream(self.events()+self.events()))
    def test_contradictory_intermediate(self):
        with self.assertRaises(c.Blocked): c.parse_final_review(self.stream([{'type':'text','part':{'text':'FAIL'}}]+self.events()))
    def test_missing_failed_gate(self):
        self.gates.pop()
        with self.assertRaises(c.Blocked): self.evaluate()
    def test_missing_docker_build_exit(self):
        for code in [1,125]:
            fake=self.root/'fake-opencode'; fake.write_text('#!/bin/sh\nexit '+str(code)+'\n'); fake.chmod(0o700)
            with self.assertRaises(c.Blocked): c.run_command([str(fake)],self.root,self.root/('exit-'+str(code)),timeout=5)
    def test_fake_cli_success_process(self):
        fake=self.root/'fake-opencode'
        fake.write_text('#!'+sys.executable+'\nprint('+repr('\n'.join(json.dumps(e) for e in self.events()))+')\n'); fake.chmod(0o700)
        result=c.run_command([str(fake)],self.root,self.root/'fake-run',timeout=5)
        self.assertEqual(self.review,c.parse_final_review(result.stdout_path))
    def test_timeout_process_group_cleanup(self):
        with self.assertRaises(c.Blocked):
            c.run_command([sys.executable,'-c','import time; time.sleep(10)'],self.root,self.root/'timeout',timeout=.05)
        self.assertIn('TimeoutExpired',(self.root/'timeout/command.json').read_text())
    def test_process_interruption(self):
        original_wait=subprocess.Popen.wait
        calls=[]
        def interrupted(process, *args, **kwargs):
            if not calls:
                calls.append(True)
                raise KeyboardInterrupt()
            return original_wait(process,*args,**kwargs)
        with mock.patch.object(subprocess.Popen,'wait',interrupted):
            with self.assertRaises(c.Blocked): c.run_command([sys.executable,'-c','import time; time.sleep(10)'],self.root,self.root/'interrupt',timeout=1)
    def test_wrong_model_override(self):
        with mock.patch.dict(os.environ,{'GRETL_REVIEW_MODEL':'deepseek/deepseek-v4-pro'}):
            with self.assertRaises(c.Blocked): c.prepare_opencode_environment(self.r)
    def test_existing_profile_without_env_key(self):
        env={k:v for k,v in os.environ.items() if k not in ('DEEPSEEK_API_KEY','GRETL_REVIEW_MODEL')}
        env['OPENCODE_DB']='/profile/opencode.db'
        with mock.patch.dict(os.environ,env,clear=True):
            prepared=c.prepare_opencode_environment(self.r)
        self.assertEqual('/profile/opencode.db',prepared['OPENCODE_DB'])
        self.assertEqual(env.get('HOME'),prepared.get('HOME'))
        self.assertNotIn('DEEPSEEK_API_KEY',prepared)
    def test_reject_v1_and_accept_override_v2(self):
        fake=self.root/'fake-opencode'; fake.write_text('#!/bin/sh\nprintf "1.18.22\\n"\n'); fake.chmod(0o700)
        with mock.patch.dict(os.environ,{'OPENCODE_BIN':str(fake)}),mock.patch.object(c.shutil,'which',return_value=None),mock.patch.object(c.Path,'is_file',lambda p: str(p)==str(fake)):
            with self.assertRaises(c.Blocked): c.discover_opencode_v2(self.r)
        fake.write_text('#!/bin/sh\nprintf "opencode v2.2.5\\n"\n')
        with mock.patch.dict(os.environ,{'OPENCODE_BIN':str(fake)}): self.assertEqual(fake.resolve(),c.discover_opencode_v2(self.r))
    def test_no_secret_persistence(self):
        with mock.patch.dict(os.environ,{'DEEPSEEK_API_KEY':'test-key-must-never-leak'}):
            result=c.run_command([sys.executable,'-c','import os; print(os.environ["DEEPSEEK_API_KEY"])'],self.root,self.root/'secret',timeout=5)
        self.assertNotIn('test-key-must-never-leak',result.stdout_path.read_text())
        self.assertIn('[REDACTED]',result.stdout_path.read_text())
    def test_effective_config_denies_and_fixed_provider(self):
        config=c.review_config(self.r)
        self.assertEqual('https://api.deepseek.com',config['providers']['deepseek']['settings']['baseURL'])
        self.assertEqual('deepseek-flash',config['providers']['deepseek']['models']['deepseek-flash']['modelID'])
        rules=config['agents']['cleanup-review']['permissions']
        self.assertEqual({'action':'*','resource':'*','effect':'deny'},rules[0])
        self.assertFalse(any(x['action'] in ['permission','bash','task'] for x in rules))
        self.assertFalse(any(x['action']=='shell' and '*' in x['resource'] for x in rules))
    def test_worktree_mutation(self):
        r=dataclasses.replace(self.r,candidate_sha=self.sha)
        (self.root/'README.md').write_text('tampered')
        with self.assertRaises(c.Blocked): c.verify_worktree_unchanged(self.root,r)
    def test_worktree_untracked_mutation(self):
        (self.root/'intruder').write_text('tampered')
        with self.assertRaises(c.Blocked): c.verify_worktree_unchanged(self.root,self.r)
    def test_worktree_lifecycle(self):
        e=self.root.parent/('worktree-evidence-'+self.root.name); e.mkdir()
        try:
            w=c.create_detached_worktree(self.r,e)
            c.verify_worktree_unchanged(w,self.r)
            c.git(self.root,'worktree','remove',str(w))
            self.assertFalse(w.exists())
            self.assertNotIn(str(w),c.git(self.root,'worktree','list','--porcelain'))
        finally:
            import shutil
            shutil.rmtree(e)
    def test_invalid_sha_cannot_escape_evidence_directory(self):
        with self.assertRaises(c.Blocked): c.allocate_evidence_dir(dataclasses.replace(self.r,candidate_sha='../escape'))
    def test_evidence_attempts_unique(self):
        with mock.patch.object(c.tempfile,'gettempdir',return_value=str(self.root)):
            one=c.allocate_evidence_dir(self.r); (one/'keep').write_text('first')
            two=c.allocate_evidence_dir(self.r)
            self.assertNotEqual(one,two)
            self.assertEqual('first',(one/'keep').read_text())
    def test_wrong_live_provider_model_and_alias(self):
        run=c.CommandEvidence(('fake',),self.root,0,self.root/'out',self.root/'err',c.now(),c.now())
        run.stdout_path.write_text('\n'.join(json.dumps(e) for e in self.events()))
        for model in [{'id':'deepseek-v4-pro','providerID':'deepseek'}, {'id':'deepseek-flash','providerID':'openrouter'}, {'id':'deepseek-v4.1-flash-1017','providerID':'deepseek'}]:
            export=self.root/('export-'+model['providerID']+model['id']); export.write_text(json.dumps({'info':{'id':'ses_test','agent':'cleanup-review','outcome':'succeeded','model':model,'time':{'created':int(c.datetime.datetime.now(c.datetime.timezone.utc).timestamp()*1000)}},'messages':[{'type':'assistant','model':model}]}))
            fake=c.CommandEvidence(('fake',),self.root,0,export,self.root/'err','a','b')
            with mock.patch.object(c,'run_command',return_value=fake):
                with self.assertRaisesRegex(c.Blocked,'Wrong provider/API model'): c.validate_session(Path('/fake'),run,self.root,self.root/('provenance-'+model['providerID']+model['id']),{})
    def test_provider_metadata_uses_resolved_review_location(self):
        run=c.CommandEvidence(('fake',),self.root,0,self.root/'events',self.root/'err',c.now(),c.now())
        run.stdout_path.write_text('\n'.join(json.dumps(e) for e in self.events()))
        calls=[]
        model={'id':'deepseek-flash','providerID':'deepseek'}
        def fake_command(argv,cwd,destination,**kwargs):
            calls.append(argv)
            destination.mkdir()
            if 'export' in argv:
                value={'info':{'id':'ses_test','agent':'cleanup-review','outcome':'succeeded','model':model,
                               'time':{'created':int(c.datetime.datetime.now(c.datetime.timezone.utc).timestamp()*1000)}},
                       'messages':[{'type':'assistant','model':model}]}
            elif 'location.get' in argv:value={'directory':str(self.root)}
            else:value={'location':{'directory':str(self.root)},'data':{'id':'deepseek','integrationID':'deepseek',
                  'package':'@opencode/ai/providers/deepseek','settings':{'baseURL':'https://api.deepseek.com'}}}
            out=destination/'out';out.write_text(json.dumps(value))
            return c.CommandEvidence(tuple(argv),cwd,0,out,destination/'err',c.now(),c.now())
        with mock.patch.object(c,'run_command',side_effect=fake_command):
            self.assertEqual('ses_test',c.validate_session(Path('/fake'),run,self.root,self.root/'bound-session',{}))
        self.assertIn('location.get',calls[1]);self.assertIn('provider.get',calls[2])
        for argv in calls[1:]:self.assertIn('location[directory]='+str(self.root),argv)
    def test_implementer_evidence_binding_and_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            parent=Path(tmp);source=parent/'own';source.mkdir();worktree=parent/'review';worktree.mkdir();evidence=parent/'evidence';evidence.mkdir()
            c.save(source/'own-gates-result.json',{'status':'PASSED_OWN_GATES','candidateSha':self.sha})
            c.save(source/'source.json',{'head':self.sha,'status':''})
            counts={'tests':1,'failures':0,'errors':0,'skipped':0}
            for name,argv in [('standard',['./mvnw','-B','clean','verify']),('full',['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify'])]:
                gate=source/name;gate.mkdir();(gate/'qualification').mkdir()
                for stream in ['stdout','stderr']:(gate/(stream+'.log')).write_text('verified')
                c.save(gate/'command.json',{'argv':argv,'return_code':0,'failure':None,'stdoutSha256':c.digest(gate/'stdout.log'),'stderrSha256':c.digest(gate/'stderr.log')})
                c.save(gate/'reports.json',{'counts':counts})
                c.save(gate/'qualification/summary.json',{'sourceRevision':self.sha,'sourceDirty':False,'tests':counts})
            (worktree/'target/cleanup-review-evidence').mkdir(parents=True)
            c.attach_implementer_evidence(source,worktree,self.r,evidence)
            self.assertEqual(self.sha,json.loads((evidence/'implementer-own-gates.json').read_text())['candidateSha'])
            (source/'standard/stdout.log').write_text('tampered')
            with self.assertRaisesRegex(c.Blocked,'digest mismatch'):c.attach_implementer_evidence(source,worktree,self.r,evidence)
    def test_implementer_evidence_rejects_foreign_sha(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'own';source.mkdir()
            c.save(source/'own-gates-result.json',{'status':'PASSED_OWN_GATES','candidateSha':'f'*40})
            c.save(source/'source.json',{'head':'f'*40,'status':''})
            with self.assertRaisesRegex(c.Blocked,'clean candidate'):
                c.attach_implementer_evidence(source,Path(tmp)/'review',self.r,Path(tmp)/'evidence')
    def test_isolated_profile_without_gui_login(self):
        def fake_command(argv,cwd,destination,**kwargs):
            destination.mkdir()
            data='[]' if 'auth' in argv else ('opencode v2.2.5' if '--version' in argv else '--model --format --agent')
            out=destination/'out';out.write_text(data)
            return c.CommandEvidence(tuple(argv),cwd,0,out,destination/'err','a','b')
        with mock.patch.object(c,'run_command',side_effect=fake_command):
            with self.assertRaisesRegex(c.Blocked,'connection not accessible'):
                c.inspect_v2_auth_and_models(Path('/fake'),self.r,self.root,{})
    def test_new_documentation_is_discovered(self):
        (self.root/'website').mkdir();(self.root/'website/new.qmd').write_text('[broken](missing.qmd)')
        subprocess.run(['git','add','website'],cwd=self.root,check=True)
        rows=docs.audit(self.root)
        self.assertTrue(any(r['source']=='website/new.qmd' and not r['exists'] for r in rows))
    def test_missing_and_unknown_phase_scope(self):
        with self.assertRaises(c.Blocked): c.validate_request(dataclasses.replace(self.r,phase='P9'))
        with self.assertRaises(c.Blocked): c.validate_request(dataclasses.replace(self.r,candidate_sha='short'))
        with self.assertRaisesRegex(c.Blocked,'scope violation'): c.validate_request(self.r)

    def test_main_interruption_removes_worktree_and_preserves_evidence(self):
        inventory=self.root/'docs/maintenance/cleanup/inventory.json';inventory.parent.mkdir(parents=True)
        inventory.write_text(json.dumps({'initialBaselineSha':self.base}))
        evidence=self.root.parent/('main-evidence-'+self.root.name);evidence.mkdir()
        old=Path.cwd()
        try:
            os.chdir(self.root)
            with mock.patch.object(c,'validate_request'),mock.patch.object(c,'allocate_evidence_dir',return_value=evidence), \
                 mock.patch.object(c,'discover_opencode_v2',return_value=Path('/fake')), \
                 mock.patch.object(c,'inspect_v2_auth_and_models',return_value={}), \
                 mock.patch.object(c,'live_preflight',side_effect=KeyboardInterrupt()),mock.patch('builtins.print'):
                code=c.main(['--phase','P0','--base',self.base,'--candidate',self.sha,'--spec','README.md'])
            self.assertEqual(20,code)
            self.assertFalse((evidence/'worktree').exists())
            self.assertEqual('BLOCKED',json.loads((evidence/'gate-result.json').read_text())['verdict'])
            self.assertEqual(self.sha,c.git(self.root,'rev-parse','HEAD'))
            self.assertTrue(inventory.exists())
        finally:
            os.chdir(old)
            import shutil
            shutil.rmtree(evidence)

    def test_doc_link_negative(self):
        (self.root/'target.md').write_text('target')
        (self.root/'README.md').write_text('[link](target.md)')
        self.assertTrue(docs.audit(self.root,['README.md'])[0]['exists'])
        (self.root/'target.md').unlink()
        self.assertFalse(docs.audit(self.root,['README.md'])[0]['exists'])
    def test_asciidoc_include_negative(self):
        (self.root/'piece.adoc').write_text('included')
        (self.root/'page.adoc').write_text('include::piece.adoc[]')
        self.assertTrue(docs.audit(self.root,['page.adoc'])[0]['exists'])
        (self.root/'piece.adoc').unlink()
        self.assertFalse(docs.audit(self.root,['page.adoc'])[0]['exists'])
    def test_anchor_negative(self):
        (self.root/'README.md').write_text('# Real target\n[ok](README.md#real-target)\n[broken](README.md#absent)')
        rows=docs.audit(self.root,['README.md'])
        self.assertTrue(rows[0]['exists']);self.assertFalse(rows[1]['exists'])
    def test_inline_code_reference_negative(self):
        (self.root/'README.md').write_text('Run `examples/missing/job.groovy`.')
        self.assertFalse(docs.audit(self.root,['README.md'])[0]['exists'])
    def test_json_evidence_is_not_markdown(self):
        (self.root/'evidence.json').write_text(json.dumps({'contentEvidence':'[historic](missing.md)'}))
        self.assertEqual([],docs.audit(self.root,['evidence.json']))
    def test_quarto_target_negative(self):
        (self.root/'website').mkdir()
        (self.root/'website/_quarto.yml').write_text('href: start.qmd\n')
        (self.root/'website/start.qmd').write_text('# start')
        self.assertTrue(docs.audit(self.root,['website/_quarto.yml'])[0]['exists'])
        (self.root/'website/start.qmd').unlink()
        self.assertFalse(docs.audit(self.root,['website/_quarto.yml'])[0]['exists'])


if __name__=='__main__': unittest.main()
