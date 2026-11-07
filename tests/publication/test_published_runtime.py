"""HTTP/download control tests; tiny executable fixtures are not product qualification."""
from functools import partial
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('published', ROOT / 'scripts/verify-published-runtime.py')
published = importlib.util.module_from_spec(spec)
spec.loader.exec_module(published)
VERSION = '6.0.0-SNAPSHOT'
SOURCE = 'a' * 40
VALUE = '6.0.0-20261102.123456-1'


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


class DownloadVerification(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='published-runtime-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / 'ch/so/agi/gretl/gretl-dist' / VERSION
        self.folder.mkdir(parents=True)
        self.metadata = self.folder / 'maven-metadata.xml'
        self.metadata.write_text(f'''<metadata><groupId>ch.so.agi.gretl</groupId><artifactId>gretl-dist</artifactId>
<version>{VERSION}</version><versioning><snapshotVersions><snapshotVersion><classifier>bin</classifier>
<extension>zip</extension><value>{VALUE}</value></snapshotVersion></snapshotVersions></versioning></metadata>''')
        self.archive = self.folder / ('gretl-dist-' + VALUE + '-bin.zip')
        self.write_archive()
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(self.root)))
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(self.stop_server, thread)
        self.repository = 'http://127.0.0.1:' + str(self.server.server_port)

    def stop_server(self, thread):
        self.server.shutdown()
        thread.join(timeout=5)
        self.server.server_close()

    def write_archive(self, version=VERSION, source=SOURCE, corrupt=False, cli_version=VERSION, result='snapshot-smoke-ok\n'):
        # An executable protocol fixture lets these tests exercise subprocess and output validation.
        launcher = '''#!/usr/bin/env python3
import sys,json
from pathlib import Path
assert 'MAVEN_PASSWORD' not in __import__('os').environ
if sys.argv[1]=='--version':print(VERSION)
else:
 args=sys.argv;out=Path(args[args.index('--param')+1].split('=',1)[1]);out.write_text(RESULT)
 Path(args[args.index('--report')+1]).write_text(json.dumps(dict(execution=dict(status='SUCCEEDED',exitCode=0),identity=dict(runtimeVersion=RUNTIME))))
'''.replace('print(VERSION)', 'print(' + repr('gretl-runtime ' + cli_version + '\npackage: gretl-runtime-script\nJava: 25') + ')').replace('write_text(RESULT)', 'write_text(' + repr(result) + ')').replace('runtimeVersion=RUNTIME', 'runtimeVersion=' + repr(version))
        files = {'bin/gretl': launcher.encode(), 'package-catalog.json': json.dumps(dict(
            runtimeVersion=version, sourceRevision=source, sourceDirty=False)).encode()}
        manifest = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n' for name, data in files.items())
        if corrupt:
            files['bin/gretl'] += b'\n# changed after checksum\n'
        with zipfile.ZipFile(self.archive, 'w') as archive:
            for name, data in {**files, 'SHA256SUMS': manifest.encode()}.items():
                archive.writestr('gretl-runtime-' + VERSION + '/' + name, data)

    def verify(self):
        return published.verify(self.repository, VERSION, SOURCE, self.root / 'smoke proof')

    def test_timestamped_download_and_external_process(self):
        proof = self.verify()
        self.assertEqual('PASS', proof['status'])
        self.assertTrue(proof['url'].endswith('gretl-dist-' + VALUE + '-bin.zip'))
        self.assertEqual(b'snapshot-smoke-ok\n', (self.root / 'smoke proof/result.txt').read_bytes())
        self.assertIn('external job.groovy', proof['command'][2])

    def test_missing_zip_fails(self):
        self.archive.unlink()
        with self.assertRaisesRegex(Exception, '404'):
            self.verify()

    def test_wrong_metadata_version_fails(self):
        self.metadata.write_text(self.metadata.read_text().replace('<version>' + VERSION, '<version>7.0.0-SNAPSHOT'))
        with self.assertRaisesRegex(ValueError, 'coordinates'):
            self.verify()

    def test_missing_bin_classifier_fails(self):
        self.metadata.write_text(self.metadata.read_text().replace('<classifier>bin</classifier>', '<classifier>other</classifier>'))
        with self.assertRaisesRegex(ValueError, 'timestamped bin ZIP'):
            self.verify()

    def test_wrong_runtime_version_fails(self):
        self.write_archive(version='7.0.0-SNAPSHOT')
        with self.assertRaisesRegex(ValueError, 'runtime version'):
            self.verify()

    def test_wrong_source_fails(self):
        self.write_archive(source='b' * 40)
        with self.assertRaisesRegex(ValueError, 'runtime source'):
            self.verify()

    def test_corrupt_checksum_fails(self):
        self.write_archive(corrupt=True)
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            self.verify()

    def test_wrong_cli_version_fails(self):
        self.write_archive(cli_version='7.0.0-SNAPSHOT')
        with self.assertRaisesRegex(ValueError, 'CLI version'):
            self.verify()

    def test_wrong_external_job_result_fails(self):
        self.write_archive(result='wrong\n')
        with self.assertRaisesRegex(ValueError, 'smoke result'):
            self.verify()

    def test_archive_cannot_escape_destination(self):
        with zipfile.ZipFile(self.archive, 'a') as archive:
            archive.writestr('../escape.txt', 'no')
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            self.verify()
        self.assertFalse((self.root / 'escape.txt').exists())


class WorkflowCredentials(unittest.TestCase):
    def test_real_workflow_credential_preflight(self):
        workflow = (ROOT / '.github/workflows/publish-snapshots.yml').read_text()
        match = re.search(r'- name: Require publishing credentials\n.*?        run: \|\n(.*?)      - name:', workflow, re.S)
        self.assertIsNotNone(match)
        script = '\n'.join(line[10:] for line in match.group(1).splitlines())
        for user, password in [('', ''), ('synthetic-user', ''), ('', 'synthetic-password'), ('synthetic-user', 'synthetic-password')]:
            env = os.environ.copy()
            env.update(MAVEN_USERNAME=user, MAVEN_PASSWORD=password)
            result = subprocess.run(['bash', '-euc', script], env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(0 if user and password else 2, result.returncode)
            self.assertNotIn('synthetic-password', result.stdout + result.stderr)
        self.assertLess(workflow.index('Require publishing credentials'), workflow.index('- name: Publish snapshot artefacts'))

    def test_workflow_publishes_only_after_success_on_main(self):
        workflow = (ROOT / '.github/workflows/publish-snapshots.yml').read_text()
        verify, publish = workflow.split('\n  publish:\n', maxsplit=1)
        self.assertIn('\n  push:\n    branches: [main]\n', verify)
        self.assertIn('\n  pull_request:\n    branches: [main]\n', verify)
        self.assertIn('\n  workflow_dispatch:\n', verify)
        self.assertIn('run: scripts/verify-runtime.sh', verify)
        self.assertNotIn('secrets.', verify)
        self.assertNotIn(' deploy', verify)
        self.assertIn('needs: verify', publish)
        condition = re.search(r'^    if: (.+)$', publish, re.M)
        self.assertIsNotNone(condition)
        clauses = [re.fullmatch(r"(needs.verify.result|github.ref|github.event_name) (==|!=) '([^']+)'", clause)
                   for clause in condition.group(1).split(' && ')]
        self.assertTrue(all(clauses))
        self.assertEqual(3, len(clauses))
        for event, ref, result, expected in [
            ('pull_request', 'refs/heads/main', 'success', False),
            ('workflow_dispatch', 'refs/heads/feature', 'success', False),
            ('push', 'refs/heads/main', 'failure', False),
            ('push', 'refs/heads/main', 'cancelled', False),
            ('push', 'refs/heads/main', 'success', True),
            ('workflow_dispatch', 'refs/heads/main', 'success', True),
        ]:
            with self.subTest(event=event, ref=ref, result=result):
                values = {'needs.verify.result': result, 'github.ref': ref, 'github.event_name': event}
                allowed = all((values[key] == value) == (operator == '==')
                              for key, operator, value in (match.groups() for match in clauses))
                self.assertEqual(expected, allowed)


if __name__ == '__main__':
    unittest.main()
