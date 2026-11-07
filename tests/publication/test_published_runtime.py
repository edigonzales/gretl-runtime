"""HTTP/download control tests; tiny executable fixtures are not product qualification."""
from functools import partial
import gzip
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
<extension>zip</extension><value>{VALUE}</value></snapshotVersion><snapshotVersion><classifier>bin-tar-gz</classifier>
<extension>tar.gz</extension><value>{VALUE}</value></snapshotVersion></snapshotVersions></versioning></metadata>''')
        self.archive = self.folder / ('gretl-dist-' + VALUE + '-bin.zip')
        self.write_archive()
        self.tar_archive = self.folder / ('gretl-dist-' + VALUE + '-bin-tar-gz.tar.gz')
        self.tar_archive.write_bytes(gzip.compress(b'qualified TAR fixture'))
        self.checksums = self.root / 'distribution.sha256'
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

    def qualify(self):
        self.checksums.write_text(''.join(
            f'{hashlib.sha256(path.read_bytes()).hexdigest()}  gretl-runtime-{VERSION}-bin.{extension}\n'
            for path, extension in ((self.archive, 'zip'), (self.tar_archive, 'tar.gz'))
        ))

    def verify(self):
        if not self.checksums.exists():
            self.qualify()
        return published.verify(self.repository, VERSION, SOURCE, self.root / 'smoke proof', self.checksums)

    def test_timestamped_download_and_external_process(self):
        proof = self.verify()
        self.assertEqual('PASS', proof['status'])
        self.assertTrue(proof['url'].endswith('gretl-dist-' + VALUE + '-bin.zip'))
        self.assertEqual(b'snapshot-smoke-ok\n', (self.root / 'smoke proof/result.txt').read_bytes())
        self.assertIn('external job.groovy', proof['command'][2])

    def test_missing_zip_fails(self):
        self.qualify()
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

    def test_missing_tar_classifier_fails(self):
        self.metadata.write_text(self.metadata.read_text().replace('<classifier>bin-tar-gz</classifier>', '<classifier>other</classifier>'))
        with self.assertRaisesRegex(ValueError, 'timestamped bin-tar-gz'):
            self.verify()

    def test_mixed_timestamp_builds_fail(self):
        original = self.metadata.read_text()
        old = '<extension>tar.gz</extension><value>' + VALUE
        self.metadata.write_text(original.replace(old, old[:-1] + '2'))
        with self.assertRaisesRegex(ValueError, 'different builds'):
            self.verify()

    def test_wrong_zip_sha_fails_before_unpacking(self):
        self.qualify()
        with zipfile.ZipFile(self.archive, 'a') as archive:
            archive.comment = b'Different ZIP bytes with the same internal files'
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch for runtime.zip'):
            self.verify()
        self.assertFalse((self.root / 'smoke proof/distribution').exists())

    def test_wrong_tar_sha_fails(self):
        self.qualify()
        self.tar_archive.write_bytes(gzip.compress(b'different TAR bytes'))
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch for runtime.tar.gz'):
            self.verify()

    def test_missing_qualified_tar_digest_fails(self):
        self.qualify()
        self.checksums.write_text(self.checksums.read_text().splitlines()[0] + '\n')
        with self.assertRaisesRegex(ValueError, 'both distribution archives'):
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

    def test_workflow_checks_both_archives_before_deploy(self):
        workflow = (ROOT / '.github/workflows/publish-snapshots.yml').read_text()
        match = re.search(r'- name: Install the qualified archives into the deploy reactor\n'
                          r'        run: \|\n(.*?)      - name:', workflow, re.S)
        self.assertIsNotNone(match)
        script = '\n'.join(line[10:] for line in match.group(1).splitlines())
        self.assertLess(workflow.index('Install the qualified archives'), workflow.index('- name: Publish snapshot artefacts'))
        with tempfile.TemporaryDirectory(prefix='qualified-handoff-') as directory:
            root = Path(directory)
            handoff = root / 'gretl-verified-runtime'
            handoff.mkdir()
            archive_names = [f'gretl-runtime-{VERSION}-bin.{extension}' for extension in ('zip', 'tar.gz')]
            checksums = handoff / 'distribution.sha256'
            lines = [f'{hashlib.sha256(name.encode()).hexdigest()}  {name}\n' for name in archive_names]
            env = dict(os.environ, RUNNER_TEMP=directory)
            for files, manifest, expected in [
                (archive_names, lines, 0),
                (archive_names[:1], lines, 1),
                (archive_names, lines[:1], 1),
                (archive_names, [lines[0], '0' * 64 + lines[1][64:]], 1),
            ]:
                with self.subTest(files=files, manifest=manifest):
                    for name in archive_names:
                        (handoff / name).unlink(missing_ok=True)
                    for name in files:
                        (handoff / name).write_text(name)
                    checksums.write_text(''.join(manifest))
                    result = subprocess.run(['bash', '-euo', 'pipefail', '-c', script], cwd=root,
                                            env=env, capture_output=True, timeout=10)
                    self.assertEqual(expected, result.returncode, result.stderr)

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
