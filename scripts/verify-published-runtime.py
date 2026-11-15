#!/usr/bin/env python3
"""Download a Maven snapshot distribution and run a new external SCRIPT job."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def snapshot_urls(repository, version):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*-SNAPSHOT', version):
        raise ValueError('Expected a Maven SNAPSHOT version')
    base = repository.rstrip('/') + '/ch/so/agi/gretl/gretl-dist/' + version
    with urllib.request.urlopen(base + '/maven-metadata.xml', timeout=30) as response:
        metadata = ET.fromstring(response.read(1 << 20))
    if (metadata.findtext('groupId'), metadata.findtext('artifactId'), metadata.findtext('version')) != (
            'ch.so.agi.gretl', 'gretl-dist', version):
        raise ValueError('Snapshot metadata coordinates differ')
    urls = {}
    timestamps = set()
    for classifier, extension in (('bin', 'zip'), ('bin-tar-gz', 'tar.gz')):
        values = [row.findtext('value') for row in metadata.findall('./versioning/snapshotVersions/snapshotVersion')
                  if row.findtext('classifier') == classifier and row.findtext('extension') == extension]
        if len(values) != 1 or not re.fullmatch(re.escape(version[:-9]) + r'-\d{8}\.\d{6}-\d+', values[0] or ''):
            raise ValueError(f'Missing or invalid timestamped {classifier} {extension.upper()} in snapshot metadata')
        timestamps.add(values[0])
        urls[extension] = f'{base}/gretl-dist-{values[0]}-{classifier}.{extension}'
    if len(timestamps) != 1:
        raise ValueError('Snapshot distribution classifiers belong to different builds')
    return urls


def qualified_digests(path, version):
    expected = {}
    for line in path.read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ' + re.escape(f'gretl-runtime-{version}.') + r'(zip|tar\.gz)', line)
        if not match or match[2] in expected:
            raise ValueError('Invalid qualified distribution checksum list')
        expected[match[2]] = match[1]
    if set(expected) != {'zip', 'tar.gz'}:
        raise ValueError('Qualified checksums must cover both distribution archives')
    return expected


def download_verified(url, archive, expected):
    digest = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=30) as response, archive.open('wb') as target:
        while chunk := response.read(1 << 20):
            digest.update(chunk)
            target.write(chunk)
    actual = digest.hexdigest()
    if actual != expected:
        raise ValueError(f'Qualified archive SHA-256 mismatch for {archive.name}')
    return actual


def unpack_verified(archive, output, version):
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
        with (output / (name + '.stderr.log')).open('rb') as stderr:
            stderr.seek(max(0, stderr.seek(0, os.SEEK_END) - 4096))
            diagnostic = stderr.read().decode('utf-8', errors='replace').strip()
        raise ValueError(f'{name} failed with exit {code}\n{diagnostic}')


def verify(repository, version, source, output, checksums):
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Expected full source commit SHA')
    expected = qualified_digests(checksums, version)
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=False, mode=0o700)
    urls = snapshot_urls(repository, version)
    archive = output / 'runtime.zip'
    zip_digest = download_verified(urls['zip'], archive, expected['zip'])
    tar_digest = download_verified(urls['tar.gz'], output / 'runtime.tar.gz', expected['tar.gz'])
    base = unpack_verified(archive, output / 'distribution', version)
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
    proof = dict(sourceRevision=source, runtimeVersion=version, url=urls['zip'], tarUrl=urls['tar.gz'], status='PASS', command=argv,
                 archiveSha256=zip_digest, tarArchiveSha256=tar_digest)
    (output / 'verification.json').write_text(json.dumps(proof, indent=2) + '\n')
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--checksums', required=True, type=Path, help='Qualified ZIP and TAR.GZ SHA-256 list')
    parser.add_argument('--output', required=True, type=Path, help='New directory for download, logs and proof')
    args = parser.parse_args()
    try:
        proof = verify(args.repository, args.version, args.source, args.output, args.checksums)
    except Exception as error:
        print('Published runtime verification FAILED: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps(proof, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
