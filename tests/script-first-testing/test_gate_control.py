"""Publication control flow with fake gates/deployer; never contacts a repository."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

REPOSITORY=Path(__file__).resolve().parents[2]

class PublicationGate(unittest.TestCase):
    def invoke(self,standard=0,full=0,deploy=0,credentials=True):
        self.temp=tempfile.TemporaryDirectory(prefix='gretl-publish-control-');self.addCleanup(self.temp.cleanup)
        root=Path(self.temp.name);(root/'scripts').mkdir()
        shutil.copyfile(REPOSITORY/'scripts/publish-snapshots.sh',root/'scripts/publish-snapshots.sh')
        gate=root/'scripts/verify-job-matrix.sh'
        gate.write_text('''#!/bin/sh
printf '%s\\n' "$*" >> "$CONTROL_LOG"
case "$*" in *'--mode ALL'*) exit "$FULL_EXIT";; *) exit "$STANDARD_EXIT";; esac
''');gate.chmod(0o700)
        maven=root/'mvnw';maven.write_text('#!/bin/sh\nprintf "deploy %s\\n" "$*" >> "$CONTROL_LOG"\nexit "$DEPLOY_EXIT"\n');maven.chmod(0o700)
        env=os.environ.copy();env.update(CONTROL_LOG=str(root/'calls.log'),STANDARD_EXIT=str(standard),FULL_EXIT=str(full),DEPLOY_EXIT=str(deploy))
        env.pop('MAVEN_USERNAME',None);env.pop('MAVEN_PASSWORD',None)
        if credentials:env.update(MAVEN_USERNAME='synthetic-control-user',MAVEN_PASSWORD='synthetic-control-password')
        result=subprocess.run(['bash','scripts/publish-snapshots.sh','-DskipTests'],cwd=root,env=env,capture_output=True,text=True,timeout=15)
        calls=(root/'calls.log').read_text().splitlines() if (root/'calls.log').exists() else []
        return result,calls
    def test_missing_credentials_starts_nothing(self):
        result,calls=self.invoke(credentials=False);self.assertEqual(2,result.returncode);self.assertEqual([],calls)
    def test_standard_failure_prevents_full_and_deploy(self):
        result,calls=self.invoke(standard=37);self.assertEqual(37,result.returncode);self.assertEqual(1,len(calls));self.assertIn('--mode SCRIPT',calls[0]);self.assertNotIn('skip',calls[0])
    def test_full_failure_prevents_deploy(self):
        result,calls=self.invoke(full=41);self.assertEqual(41,result.returncode);self.assertEqual(2,len(calls));self.assertIn('--mode ALL',calls[1]);self.assertFalse(any(c.startswith('deploy') for c in calls))
    def test_deploy_follows_both_gates_and_preserves_exit(self):
        result,calls=self.invoke(deploy=43);self.assertEqual(43,result.returncode);self.assertEqual(3,len(calls));self.assertIn('--mode SCRIPT',calls[0]);self.assertIn('--mode ALL',calls[1]);self.assertTrue(calls[2].startswith('deploy'));self.assertNotIn('skip',calls[0]+calls[1])

if __name__=='__main__':unittest.main()
