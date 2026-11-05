"""Negative evidence canaries. Never modify real review worktrees or evidence."""
import datetime as dt
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('finalizers_review',Path(__file__).with_name('review.py'))
review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)

class ReviewGuards(unittest.TestCase):
    def test_optimized_execution_is_refused(self):
        p=subprocess.run([sys.executable,'-O',str(Path(review.__file__)),'--help'],capture_output=True,text=True)
        self.assertNotEqual(0,p.returncode);self.assertIn('refusing execution',p.stderr)

    def test_reports_require_positive_fresh_selected_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);p=root/'module/target/surefire-reports/TEST-Probe.xml';p.parent.mkdir(parents=True)
            start=(dt.datetime.now(dt.timezone.utc)-dt.timedelta(seconds=1)).isoformat()
            def report(tests):p.write_text(f'<testsuite name="example.ProbeTest" tests="{tests}" errors="0" failures="0" skipped="0"/>')
            report(1);review.validate_reports(root,'./mvnw -Dtest=ProbeTest test',start)
            report(0)
            with self.assertRaises(AssertionError):review.validate_reports(root,'./mvnw -Dtest=ProbeTest test',start)
            report(1)
            with self.assertRaisesRegex(AssertionError,'Missing selected test'):review.validate_reports(root,'./mvnw -Dtest=AbsentTest test',start)
            future=(dt.datetime.now(dt.timezone.utc)+dt.timedelta(days=1)).isoformat()
            with self.assertRaisesRegex(AssertionError,'Stale report'):review.validate_reports(root,'./mvnw -Dtest=ProbeTest test',future)

    def test_process_local_infrastructure_exclusion_cannot_hide_product_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);worktree=root/'product';worktree.mkdir();evidence=root/'evidence';evidence.mkdir()
            subprocess.run(['git','init','--quiet',str(worktree)],check=True)
            product=worktree/'product.txt';product.write_text('original')
            subprocess.run(['git','add','product.txt'],cwd=worktree,check=True)
            product.write_text('changed')
            config=worktree/'.opencode/opencode.json';config.parent.mkdir();config.write_text('{}')
            normal=subprocess.check_output(['git','status','--porcelain'],cwd=worktree,text=True)
            self.assertIn('.opencode/',normal)
            environment=review.review_environment(evidence,worktree)
            scoped=subprocess.check_output(['git','status','--porcelain'],cwd=worktree,text=True,env=environment)
            self.assertEqual('AM product.txt\n',scoped)
            self.assertEqual('/.opencode/\n',(evidence/'review-infrastructure.ignore').read_text())
            self.assertEqual(normal,subprocess.check_output(['git','status','--porcelain'],cwd=worktree,text=True))

    def test_wrong_identity_fails_before_export(self):
        # The candidate checkout can be the current repository: wrong identity fails before model export.
        root=review.ROOT;sha=review.output(['git','rev-parse','HEAD'])
        original_output=review.output
        try:
            # This test's in-progress tracked changes are unrelated to the identity assertion.
            review.output=lambda argv,cwd=root: '' if argv[:2]==['git','status'] else original_output(argv,cwd)
            with tempfile.TemporaryDirectory() as directory:
                ev=Path(directory)
                handoff=dict(assignment_id='task-finalizers',spec_version='1.0',phase='P0',assignment_base_sha=sha,
                             phase_base_sha=sha,head_sha=sha,reviewed_sha=sha,review_worktree=str(root),requirements_digest='correct',
                             opencode_version='2.2.5',provider_model='deepseek/deepseek-flash')
                review.write(ev/'handoff.json',handoff)
                review.write(ev/'process.json',dict(exit_code=0,ended_at='finished',tracked_status=''))
                for key in ('assignment_id','spec_version','head_sha','reviewed_sha','requirements_digest'):
                    with self.subTest(key=key):
                        review.write(ev/'verdict.json',{**handoff,key:'wrong'})
                        with self.assertRaisesRegex(AssertionError,key):
                            review.validate(ev,Path('/unavailable-opencode'))
        finally:
            review.output=original_output

if __name__=='__main__':unittest.main()
