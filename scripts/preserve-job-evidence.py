#!/usr/bin/env python3
"""Retain the audited, non-secret proof set before Maven clean. No runtime configs or inputs."""
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

root, output = (Path(p).resolve() for p in sys.argv[1:3])
output.mkdir(parents=True, exist_ok=True, mode=0o700)

def copy(path):
    if path.is_symlink():
        raise ValueError('Symlink in retained proof: ' + str(path))
    if path.is_file():
        target = output / path.relative_to(root)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)

def tree(path):
    for file in sorted(path.rglob('*')):
        if file.is_file(): copy(file)

copy(root / 'pom.xml')
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [m.text for m in ET.parse(root/'pom.xml').getroot().findall('m:modules/m:module', ns)]
if len(sys.argv) > 3 and sys.argv[3] == 'full': modules.append('gretl-geotools-probe')
for module in modules:
    target = root / module / 'target'
    for kind in ('surefire-reports', 'failsafe-reports'):
        tree(target / kind)
    copy(target / 'site/jacoco/jacoco.xml')
    for file in target.glob('*-bin.*'): copy(file)
tree(root / 'gretl-dist/target/dist')
tree(root / 'gretl-integration-tests/target/qualification')
allowed = {'execution.json', 'command.json', 'input-manifest.json', 'package-inventory.json', 'isolation.json',
           'distribution-SHA256SUMS', 'job.jar', 'job.stdout.log', 'job.stderr.log', 'image-proof.json'}
executions = [p.parent for name in ('job-evidence', 'project-script-evidence') for p in (root/'gretl-integration-tests/target'/name).rglob('execution.json')]
for execution in sorted(executions):
    if not execution.is_dir(): continue
    for name in allowed: copy(execution / name)
    metadata = execution / 'execution.json'
    if metadata.exists():
        row = json.loads(metadata.read_text())
        artifact = row.get('jobJar', {}).get('path')
        if artifact:
            actual = execution / Path(artifact).name
            if actual.exists(): copy(actual)
        runtime = row.get('runtime', {}).get('root')
        if runtime and Path(runtime).is_relative_to(root/'gretl-integration-tests/target/job-evidence') and Path(runtime).exists(): tree(Path(runtime))
    tree(execution / 'reports')
    # Some permission contracts intentionally select a separate explicit report directory.
    tree(execution / 'explicit-reports')
    tree(execution / 'output')
    for file in execution.glob('controls/*/control-execution.json'): copy(file)
(output/'retention.json').write_text(json.dumps({'schemaVersion':1,'originalRoot':str(root),'inputs':'Verified input inventories retained; runtime verifies immutable source before and after start.', 'excluded':['runtime.json','image-runtime.json','secret files','source trees','workspaces','environment dumps']},indent=2)+'\n')
