"""Synthetic receipts exercise the validator; they are never review evidence."""
import copy
import os
from pathlib import Path
import tempfile
import time
import unittest

import review


class ReviewValidationTest(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        root = Path(scratch.name).resolve()
        self.worktree = root / 'candidate'
        self.evidence = root / 'evidence'
        self.worktree.mkdir()
        self.evidence.mkdir()
        self.xml = self.worktree/'target/surefire-reports/TEST-Contract.xml'
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase/></testsuite>')
        self.probe = self.evidence/'probe.py'
        self.probe.write_text('assert 1 + 1 == 2\n')
        self.result = self.evidence/'counterprobe.log'
        self.result.write_text('controlled assertion\n')
        (self.evidence/'review.md').write_text('Synthetic unit fixture. ' * 10)
        now = time.time()
        self.process = dict(cwd=str(self.worktree), exit_code=0, started=now-5, ended=now+5)
        self.session = dict(id='synthetic', time={'created': int(now*1000)-4000, 'idle': int(now*1000)+1000},
                            location={'directory':str(self.worktree)}, outcome='succeeded', agent=review.AGENT,
                            model={'providerID':'deepseek','id':'deepseek-flash'})
        self.request = dict(assignment_id='synthetic', spec_version='1', phase='verify',
                            assignment_base_sha='a'*40, phase_base_sha='a'*40, candidate_sha='b'*40,
                            requirements_digest='c'*64, review_worktree=str(self.worktree),
                            opencode_version='opencode v2.2.5', provider_model=review.MODEL, criteria=['A','B'],
                            required_checks=[dict(id='contract', argv=['./mvnw','test'], expected_exit=0,
                                                  reports=['target/surefire-reports/TEST-Contract.xml'])])
        self.verdict = {key:self.request[key] for key in review.IDENTITY}
        self.verdict.update(session_id='synthetic', verdict='ABGENOMMEN', required_not_executed=[], findings=[],
                            integrity='unchanged', criteria=[dict(id=k,status='fulfilled',evidence='fixture')
                                                            for k in self.request['criteria']],
                            executed_checks=[dict(command='./mvnw test',exit_code=0,assertion='synthetic',
                                                  evidence=[str(self.xml)])],
                            counterprobes=[dict(command='python3 '+str(self.probe),exit_code=0,assertion='synthetic',
                                                evidence=[str(self.result)],hypothesis='hypothesis',input='input',
                                                expectation='expected',authored_by_reviewer=True,executed=True)])
        def event(tool, input, exit_code=0):
            return dict(type='tool_use', sessionID='synthetic', part=dict(tool=tool,
                        state=dict(status='completed', input=input, output='', time={'start':int((now-2)*1000),
                        'end':int((now+2)*1000)}, metadata={'metadata':{'exit':exit_code}})))
        write = event('write', {'path':str(self.probe)})
        write['part']['state']['time']['end'] = int((now-3)*1000)
        self.events = [event('shell', {'command':'./mvnw test'}),write,
                       event('shell', {'command':'python3 '+str(self.probe)}),
                       dict(type='step_finish', sessionID='synthetic', part={'reason':'stop'})]

    def validate(self, verdict=None, events=None):
        return review.validate_verdict(self.request, verdict or self.verdict, self.process, events or self.events,
                                       self.session, self.evidence, self.worktree)

    def test_accepts_only_the_bound_fresh_report(self):
        result = self.validate()
        self.assertEqual({'target/surefire-reports/TEST-Contract.xml':1},result['tests']['contract'])

    def test_rejects_unsupported_acceptance_claims(self):
        for key, value in [('candidate_sha','d'*40), ('requirements_digest','e'*64),
                           ('criteria',self.verdict['criteria'][:1]), ('executed_checks',[]),
                           ('required_not_executed',['contract'])]:
            with self.subTest(key=key):
                verdict = dict(self.verdict, **{key:value})
                with self.assertRaises(ValueError):
                    self.validate(verdict=verdict)

    def test_rejects_old_empty_or_skipped_reports(self):
        self.verdict['executed_checks'][0]['evidence'] = [str(self.result)]
        old = self.process['started'] - 20
        os.utime(self.xml, (old,old))
        with self.assertRaisesRegex(ValueError, 'current report'):
            self.validate()
        self.xml.write_text('<testsuite tests="0" failures="0" errors="0" skipped="0"/>')
        with self.assertRaisesRegex(ValueError, 'Empty, failing or skipped'):
            self.validate()
        self.xml.write_text('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase><skipped/></testcase></testsuite>')
        with self.assertRaisesRegex(ValueError, 'Empty, failing or skipped'):
            self.validate()

    def test_rejects_required_shell_check_from_a_different_worktree(self):
        events = copy.deepcopy(self.events)
        events[0]['part']['state']['input']['workdir'] = str(self.worktree.parent / 'different-candidate')
        with self.assertRaisesRegex(ValueError, 'No matching real check'):
            self.validate(events=events)

    def test_rejects_forged_or_failed_probe(self):
        with self.assertRaisesRegex(ValueError, 'reviewer-authored'):
            self.validate(events=[self.events[i] for i in (0,2,3)])
        events = copy.deepcopy(self.events)
        events[2]['part']['state']['metadata']['metadata']['exit'] = 1
        with self.assertRaisesRegex(ValueError, 'No matching real check'):
            self.validate(events=events)

    def test_verifies_external_requirements_against_digest(self):
        req = self.evidence/'requirement.md'
        req.write_text('original')
        manifest = f'{review.sha(b"original")}  requirement.md\n'.encode()
        request = dict(requirements_directory=str(self.evidence), requirement_files=[req.name],
                       requirements_digest=review.sha(manifest))
        self.assertEqual(manifest,review.capture_requirements(request))
        req.write_text('mutated')
        with self.assertRaisesRegex(ValueError,'Requirement digest'):
            review.capture_requirements(request)


if __name__ == '__main__':
    unittest.main()
