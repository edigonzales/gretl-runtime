#!/usr/bin/env python3
"""Audit files produced by the completed root verify; never builds or runs jobs."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--full', action='store_true', help='Require all 44 positive JAR operation proofs')
parser.add_argument('--source', help='Require clean distribution from this commit (CI)')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]

def read(path):
    return json.loads(path.read_text())

def sha(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()

dist = root / 'gretl-dist/target/dist'
catalog = read(dist / 'package-catalog.json')
if args.source:
    assert catalog['sourceRevision'] == args.source, catalog['sourceRevision']
    assert catalog['sourceDirty'] is False, 'Distribution was built from a dirty checkout'
for artifact in catalog['artifacts']:
    assert sha(dist / artifact['file']) == artifact['sha256'], artifact
for line in (dist / 'SHA256SUMS').read_text().splitlines():
    checksum, file = line.split(maxsplit=1)
    assert sha(dist / file.lstrip('*')) == checksum, file

executions = []
positive = set()
for file in sorted((root / 'gretl-integration-tests/target/job-evidence').glob('*/execution.json')):
    row = read(file)
    assert row['mode'] in {'JAR', 'SCRIPT'}, 'Mandatory CI must not execute GRETL IMAGE'
    if row['mode'] == 'JAR':
        jar = file.parent / 'job.jar'
        assert sha(jar) == row['sha256'], str(jar)
        assert (file.parent / 'package-inventory.json').is_file(), str(file.parent)
    reports = list((file.parent / 'reports').glob('report.json'))
    operations = []
    if reports:
        report = read(reports[0])
        row['reportExitCode'] = report['execution']['exitCode']
        if not row['aborted'] and not row['timedOut']:
            assert row['reportExitCode'] == row['exitCode'], str(file)
        operations = [{k: invocation[k] for k in ('operationId', 'status')}
                      for invocation in report['execution'].get('invocations', [])]
        if row['mode'] == 'JAR':
            positive.update(i['operationId'] for i in operations if i['status'] == 'SUCCEEDED')
    row['directory'] = str(file.parent.relative_to(root))
    row['operations'] = operations
    executions.append(row)
assert executions, 'No artifact job executions'
expected = {o['id'] for o in read(root / 'docs/implementation/improvement-plan/operation-inventory.json')}
if args.full:
    assert len(expected) == 44 and expected <= positive, sorted(expected - positive)

namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [m.text for m in ET.parse(root / 'pom.xml').getroot().findall('m:modules/m:module', namespace)]
if args.full:
    modules.append('gretl-geotools-probe')
tests = dict(tests=0, failures=0, errors=0, skipped=0)
for file in sorted(p for module in modules for p in (root / module / 'target').glob('*-reports/TEST-*.xml')):
    suite = ET.parse(file).getroot()
    for key in tests:
        tests[key] += int(suite.get(key, '0'))
assert tests['tests'] and not tests['failures'] and not tests['errors'] and not tests['skipped'], tests
coverage = []
for file in sorted(root / module / 'target/site/jacoco/jacoco.xml' for module in modules):
    if not file.is_file():
        continue
    report = ET.parse(file).getroot()
    coverage.append(dict(module=file.parts[-5], counters=[dict(c.attrib) for c in report.findall('counter')]))
assert coverage, 'No measured coverage'
output = root / 'gretl-integration-tests/target/qualification'
output.mkdir(exist_ok=True)
(output / 'jar-executions.json').write_text(json.dumps(executions, indent=2) + '\n')
summary = dict(sourceRevision=catalog['sourceRevision'], sourceDirty=catalog['sourceDirty'],
               tests=tests, successfulJarOperations=sorted(positive), full=args.full,
               distributionCatalogSha256=sha(dist / 'package-catalog.json'),
               archives=[dict(file=p.name, sha256=sha(p)) for p in sorted((root / 'gretl-dist/target').glob('*-bin.*'))],
               coverage=coverage, coverageLimit='Separate launcher and java -jar processes are not instrumented')
(output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({k: v for k, v in summary.items() if k != 'coverage'}, indent=2))
