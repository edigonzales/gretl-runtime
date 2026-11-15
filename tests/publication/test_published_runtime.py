"""HTTP/download control tests; tiny executable fixtures are not product qualification."""
from functools import partial
import gzip
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
from pathlib import Path
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

    def write_archive(self, version=VERSION, cli_version=VERSION, result='snapshot-smoke-ok\n'):
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
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr('gretl-runtime-' + VERSION + '/bin/gretl', launcher)

    def qualify(self):
        self.checksums.write_text(''.join(
            f'{hashlib.sha256(path.read_bytes()).hexdigest()}  gretl-runtime-{VERSION}.{extension}\n'
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
        with self.assertRaisesRegex(ValueError, 'smoke result'):
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
