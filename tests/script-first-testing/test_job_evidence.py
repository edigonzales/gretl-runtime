#!/usr/bin/env python3
"""Negative probes against an actual completed proof copy, never the candidate outputs."""
import argparse
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--evidence-root', type=Path, required=True)
parser.add_argument('--mode', choices=['SCRIPT','JAR','IMAGE'], required=True)
parser.add_argument('--scope', choices=['local','full'], required=True)
options = parser.parse_args()
repository = Path(__file__).resolve().parents[2]

class AuditNegatives(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = options.evidence_root.resolve()
        cls.summary = json.loads((cls.source/'gretl-integration-tests/target/qualification/summary.json').read_text())
        cls.command = ['python3', str(repository/'scripts/audit-job-evidence.py'), '--mode',options.mode,'--scope',options.scope,
                       '--run-id',cls.summary['suiteRunId']]
        if not cls.summary['sourceDirty']: cls.command += ['--source',cls.summary['sourceRevision']]
        baseline = subprocess.run(cls.command+['--evidence-root',str(cls.source)],capture_output=True,text=True)
        if baseline.returncode: raise RuntimeError('Unchanged baseline must pass before any mutation: '+baseline.stderr[-3000:])
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix='gretl-audit-negative-')
        self.root = Path(self.scratch.name)/'proof'
        def copy(source, target):
            # Only unmodified large binary artifacts share bytes; mutated JSON/XML is always copied.
            return os.link(source,target) if Path(source).suffix in {'.jar','.zip','.gz'} else shutil.copy2(source,target)
        shutil.copytree(self.source,self.root,copy_function=copy)
        self.executions = sorted(p for name in ('job-evidence','project-script-evidence') for p in (self.root/'gretl-integration-tests/target'/name).rglob('execution.json'))
    def tearDown(self): self.scratch.cleanup()
    def audit(self, success=False):
        result = subprocess.run(self.command+['--evidence-root',str(self.root)],capture_output=True,text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout[-1500:]+result.stderr[-3000:])
        return result
    def row(self, predicate=lambda row: True):
        for path in self.executions:
            row=json.loads(path.read_text())
            if predicate(row):return path,row
        self.fail('Required real test fixture not found')
    def save(self,path,row):path.write_text(json.dumps(row))
    def test_unchanged_real_proof_passes(self): self.audit(True)
    def test_E01_missing_scenario(self):
        path,_=self.row();path.unlink();self.audit()
    def test_E02_relabelled_jar(self):
        path,row=self.row(lambda r:r['mode']=='JAR' and r['process']['state']=='EXITED')
        row['mode']='SCRIPT';self.save(path,row);self.audit()
    def test_E03_stale_revision(self):
        path,row=self.row(lambda r:'sourceRevision' in r);row['sourceRevision']='0'*40;self.save(path,row);self.audit()
    def test_E04_correct_labels_wrong_actual_image_bytes(self):
        actual=self.root/'gretl-dist/target/image/extracted/bin/gretl'
        actual.write_bytes(actual.read_bytes()+b'\n# wrong actual image bytes\n')
        self.audit()
    def test_E08_changed_input_identity(self):
        path,row=self.row(lambda r:r['mode']=='IMAGE' and 'input' in r)
        inventory=path.parent/'input-manifest.json';data=json.loads(inventory.read_text())
        self.assertTrue(data['files']);data['files'][0]['sha256']='0'*64
        self.save(inventory,data);self.audit()
    def test_E11_recording_engine_is_not_real_container(self):
        path,row=self.row(lambda r:r['mode']=='IMAGE' and 'image' in r)
        proof=path.parent/'image-proof.json';data=json.loads(proof.read_text())
        data.pop('container');self.save(proof,data);self.audit()
    def test_E05_missing_required_report(self):
        path,row=self.row(lambda r:'reportIdentity' in r)
        report=path.parent/('explicit-reports' if Path(row['report']).parent.name=='explicit-reports' else 'reports')/'report.json'
        report.unlink();self.audit()
    def test_E05_foreign_report(self):
        rows=[(p,json.loads(p.read_text())) for p in self.executions if 'reportIdentity' in json.loads(p.read_text())]
        first,second=rows[:2]
        def report(pair):
            p,r=pair;return p.parent/('explicit-reports' if Path(r['report']).parent.name=='explicit-reports' else 'reports')/'report.json'
        shutil.copyfile(report(second),report(first));self.audit()
    def test_E06_skipped_junit(self):
        path=next((self.root/'gretl-integration-tests/target/failsafe-reports').glob('TEST-*.xml'))
        tree=ET.parse(path);ET.SubElement(tree.getroot().find('testcase'),'skipped');tree.write(path);self.audit()
    def test_E06_foreign_junit_run(self):
        path=next((self.root/'gretl-integration-tests/target/failsafe-reports').glob('TEST-*.xml'))
        tree=ET.parse(path)
        for p in tree.getroot().findall('properties/property'):
            if p.get('name')=='gretl.test.runId':p.set('value','foreign-suite')
        tree.write(path);self.audit()
    def test_E06_unmatched_junit_method(self):
        path=next((self.root/'gretl-integration-tests/target/failsafe-reports').glob('TEST-*.xml'))
        tree=ET.parse(path);tree.getroot().find('testcase').set('name','foreignMethod');tree.write(path);self.audit()
    def test_E07_other_modes_cannot_fill_operations(self):
        self.assertEqual('full', options.scope)
        expected = {o['id'] for o in json.loads((repository/'docs/history/contracts/operation-baseline.json').read_text())}
        other_mode_filled = False
        for path in self.executions:
            row=json.loads(path.read_text())
            if 'reportIdentity' not in row:continue
            report=path.parent/('explicit-reports' if Path(row['report']).parent.name=='explicit-reports' else 'reports')/'report.json'
            data=json.loads(report.read_text())
            if row['mode']==options.mode: data['execution']['invocations']=[]
            elif not other_mode_filled:
                # The union is complete, but the requested mode has no positives.
                data['execution']['invocations']=[{'operationId':i,'status':'SUCCEEDED'} for i in sorted(expected)]
                other_mode_filled=True
            report.write_text(json.dumps(data))
            row['reportIdentity']['sha256']=hashlib.sha256(report.read_bytes()).hexdigest();self.save(path,row)
        self.assertTrue(other_mode_filled)
        result=self.audit()
        self.assertIn('missingPositiveOperations',result.stderr)
    def test_E09_started_only(self):
        path,row=self.row();row['process']['state']='STARTED';self.save(path,row);self.audit()
    def test_E10_maven_failure_is_not_hidden(self):
        fake=Path(self.scratch.name)/'fake';(fake/'scripts').mkdir(parents=True)
        shutil.copyfile(repository/'scripts/verify-job-matrix.sh',fake/'scripts/verify-job-matrix.sh')
        shutil.copyfile(repository/'scripts/preserve-job-evidence.py',fake/'scripts/preserve-job-evidence.py')
        shutil.copyfile(repository/'scripts/run-maven-gate.py',fake/'scripts/run-maven-gate.py')
        (fake/'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules/></project>')
        (fake/'mvnw').write_text('#!/bin/sh\necho deliberate-maven-failure\nexit 37\n');(fake/'mvnw').chmod(0o700)
        for command in (['git','init','-q'],['git','add','.'],['git','-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','fixture']):
            subprocess.run(command,cwd=fake,check=True,capture_output=True)
        result=subprocess.run(['bash','scripts/verify-job-matrix.sh','--output',str(Path(self.scratch.name)/'matrix')],cwd=fake,capture_output=True,text=True)
        self.assertEqual(37,result.returncode,result.stdout+result.stderr)
        self.assertFalse(list((Path(self.scratch.name)/'matrix').glob('*/complete.json')))

names = unittest.defaultTestLoader.getTestCaseNames(AuditNegatives)
if options.mode != 'IMAGE':
    for name in ('test_E04_correct_labels_wrong_actual_image_bytes','test_E08_changed_input_identity','test_E11_recording_engine_is_not_real_container'):
        names.remove(name)
if options.scope == 'local':
    names.remove('test_E07_other_modes_cannot_fill_operations')
    print('LOCAL selection: E07 operation coverage is selected only in FULL scope.', flush=True)
result = unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(AuditNegatives(name) for name in names))
raise SystemExit(0 if result.wasSuccessful() else 1)
