#!/usr/bin/env python3
"""Download a Maven snapshot distribution and run a new external SCRIPT job."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def snapshot_url(repository, version):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*-SNAPSHOT', version):
        raise ValueError('Expected a Maven SNAPSHOT version')
    base = repository.rstrip('/') + '/ch/so/agi/gretl/gretl-dist/' + version
    with urllib.request.urlopen(base + '/maven-metadata.xml', timeout=30) as response:
        metadata = ET.fromstring(response.read(1 << 20))
    if (metadata.findtext('groupId'), metadata.findtext('artifactId'), metadata.findtext('version')) != (
            'ch.so.agi.gretl', 'gretl-dist', version):
        raise ValueError('Snapshot metadata coordinates differ')
    values = [row.findtext('value') for row in metadata.findall('./versioning/snapshotVersions/snapshotVersion')
              if row.findtext('classifier') == 'bin' and row.findtext('extension') == 'zip']
    if len(values) != 1 or not re.fullmatch(re.escape(version[:-9]) + r'-\d{8}\.\d{6}-\d+', values[0] or ''):
        raise ValueError('Missing or invalid timestamped bin ZIP in snapshot metadata')
    return base + '/gretl-dist-' + values[0] + '-bin.zip'


def unpack_verified(archive, output, version, source):
    base_name = 'gretl-runtime-' + version
    with zipfile.ZipFile(archive) as zip_file:
        names = set()
        for entry in zip_file.infolist():
            path = PurePosixPath(entry.filename)
            if (not path.parts or path.parts[0] != base_name or path.is_absolute()
                    or '..' in path.parts or '\\' in entry.filename
                    or stat.S_ISLNK(entry.external_attr >> 16) or entry.filename in names):
                raise ValueError('Unsafe or duplicate distribution ZIP entry: ' + entry.filename)
            names.add(entry.filename)
        zip_file.extractall(output)
    base = output / base_name
    manifest = base / 'SHA256SUMS'
    expected = {}
    for line in manifest.read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        name = name.lstrip('*')
        path = PurePosixPath(name)
        if (not re.fullmatch(r'[0-9a-f]{64}', digest) or path.is_absolute()
                or '..' in path.parts or '\\' in name or name in expected):
            raise ValueError('Invalid distribution checksum entry')
        expected[name] = digest
    actual = {p.relative_to(base).as_posix() for p in base.rglob('*') if p.is_file() and p != manifest}
    if actual != set(expected):
        raise ValueError('Distribution files differ from SHA256SUMS')
    for name, digest in expected.items():
        if hashlib.sha256((base / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Distribution checksum mismatch: ' + name)
    catalog = json.loads((base / 'package-catalog.json').read_text())
    if catalog.get('runtimeVersion') != version:
        raise ValueError('Downloaded runtime version differs')
    if catalog.get('sourceRevision') != source or catalog.get('sourceDirty') is not False:
        raise ValueError('Downloaded runtime source differs or is dirty')
    launcher = base / 'bin/gretl'
    launcher.chmod(0o755)
    return base


def run_cli(argv, cwd, output, name, environment):
    with (output / (name + '.stdout.log')).open('wb') as stdout, (output / (name + '.stderr.log')).open('wb') as stderr:
        process = subprocess.Popen(argv, cwd=cwd, env=environment, stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            code = process.wait(timeout=120)
        except BaseException:
            import signal
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=5)
            raise
    if code:
        raise ValueError(name + ' failed with exit ' + str(code) + '; see retained logs')


def verify(repository, version, source, output):
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Expected full source commit SHA')
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=False, mode=0o700)
    url = snapshot_url(repository, version)
    archive = output / 'runtime.zip'
    with urllib.request.urlopen(url, timeout=30) as response, archive.open('wb') as target:
        shutil.copyfileobj(response, target)
    base = unpack_verified(archive, output / 'distribution', version, source)
    cwd = output / 'foreign cwd'
    cwd.mkdir()
    # The job is created after download, outside the distribution, without a package descriptor.
    job = output / 'external job.groovy'
    job.write_text("""parameter('output', type: 'path', required: true, description: 'Smoke result')
action('write') {
    java.nio.file.Files.writeString(parameter('output'), 'snapshot-smoke-ok\\n')
}
defaults 'write'
""")
    config = output / 'runtime.json'
    config.write_text(json.dumps(dict(schemaVersion=1, runtime=dict(
        workRoot=str(output / 'work'), reportRoot=str(output / 'reports')), secrets={}, taskConfig={})))
    env = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'GRETL_JAVA_OPTS',
                 'MAVEN_USERNAME', 'MAVEN_PASSWORD'):
        env.pop(name, None)
    env['HOME'] = str(output / 'home')
    Path(env['HOME']).mkdir()
    launcher = str(base / 'bin/gretl')
    run_cli([launcher, '--version'], cwd, output, 'version', env)
    version_lines = (output / 'version.stdout.log').read_text().splitlines()
    if not version_lines or version_lines[0] != 'gretl-runtime ' + version:
        raise ValueError('CLI version differs')
    result = output / 'result.txt'
    report = output / 'report.json'
    argv = [launcher, 'run', str(job), '--config', str(config), '--report', str(report), '--param', 'output=' + str(result)]
    run_cli(argv, cwd, output, 'job', env)
    status = json.loads(report.read_text())
    if (status['execution']['status'] != 'SUCCEEDED' or status['execution']['exitCode'] != 0
            or status['identity']['runtimeVersion'] != version or result.read_bytes() != b'snapshot-smoke-ok\n'):
        raise ValueError('Published runtime SCRIPT smoke result differs')
    proof = dict(sourceRevision=source, runtimeVersion=version, url=url, status='PASS', command=argv,
                 archiveSha256=hashlib.sha256(archive.read_bytes()).hexdigest())
    (output / 'verification.json').write_text(json.dumps(proof, indent=2) + '\n')
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', required=True, type=Path, help='New directory for download, logs and proof')
    args = parser.parse_args()
    try:
        proof = verify(args.repository, args.version, args.source, args.output)
    except Exception as error:
        print('Published runtime verification FAILED: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps(proof, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
