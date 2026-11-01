#!/usr/bin/env python3
"""Build one generic local runtime; independently compare its actual files with the input distribution."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

parser=argparse.ArgumentParser()
parser.add_argument('--distribution',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--source',help='Require this source revision')
parser.add_argument('--image',help='Verify an explicitly supplied existing candidate image instead of building')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1];dist=args.distribution.resolve();out=args.output.resolve()
if out == dist or out.is_relative_to(dist):parser.error('Image output must be outside the input distribution')
out.mkdir(parents=True,exist_ok=True)
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):h.update(block)
 return h.hexdigest()
def verify(directory):
 if any(p.is_symlink() for p in directory.rglob('*')):raise ValueError('Distribution symlink')
 entries={}
 for line in (directory/'SHA256SUMS').read_text().splitlines():
  expected,relative=line.split(maxsplit=1);relative=relative.lstrip('*')
  if Path(relative).is_absolute() or '..' in Path(relative).parts or relative in entries:raise ValueError('Invalid manifest path')
  entries[relative]=expected
  if sha(directory/relative)!=expected:raise ValueError('Distribution hash mismatch: '+relative)
 actual=set()
 for path in directory.rglob('*'):
  if path.is_symlink():raise ValueError('Distribution symlink')
  if path.is_file() and path.relative_to(directory).as_posix()!='SHA256SUMS':actual.add(path.relative_to(directory).as_posix())
 if actual!=set(entries):raise ValueError('Distribution inventory mismatch')
 return entries
entries=verify(dist);catalog=json.loads((dist/'package-catalog.json').read_text())
if args.source and catalog['sourceRevision']!=args.source:raise ValueError('Wrong source revision')
manifest_sha=sha(dist/'SHA256SUMS');source=catalog['sourceRevision']
def command(argv,limit=180):
 # Redirect first, then wait: subprocess output cannot fill a pipe while the parent waits.
 log=out/('docker-'+uuid.uuid4().hex+'.log')
 with log.open('w+b') as stream:
  process=subprocess.Popen(argv,stdout=stream,stderr=subprocess.STDOUT)
  try:code=process.wait(timeout=limit)
  except subprocess.TimeoutExpired:
   process.kill();process.wait(timeout=10);raise RuntimeError('Docker command timed out: '+str(argv[:3]))
  stream.seek(0);data=stream.read(4*1024*1024+1)
 if len(data)>4*1024*1024:raise ValueError('Unexpectedly large Docker metadata output')
 if code:raise RuntimeError('Docker command failed: '+str(argv[:3])+': '+data.decode(errors='replace')[-4000:])
 return data.decode().strip()
dockerfile=root/'gretl-dist/src/main/docker/Dockerfile'
base=next(line.split()[1] for line in dockerfile.read_text().splitlines() if line.startswith('FROM '))
if '@sha256:' not in base:raise ValueError('Unpinned Java runtime base')
if args.image:
 image=args.image;build=[]
else:
 context=out/'context'
 if context.exists():shutil.rmtree(context)
 context.mkdir();shutil.copytree(dist,context/'dist');shutil.copyfile(dockerfile,context/'Dockerfile')
 image='gretl-runtime:'+source[:12]+'-'+manifest_sha[:12]
 build=['docker','build','--progress=plain','--build-arg','SOURCE_REVISION='+source,'--build-arg','DISTRIBUTION_MANIFEST_SHA256='+manifest_sha,'--iidfile',str(out/'image-id.txt'),'-t',image,str(context)]
 command(build,900)
template='{"Id":{{json .Id}},"Os":{{json .Os}},"Architecture":{{json .Architecture}},"Config":{"Entrypoint":{{json .Config.Entrypoint}},"User":{{json .Config.User}},"Labels":{"org.opencontainers.image.revision":{{json (index .Config.Labels "org.opencontainers.image.revision")}},"ch.so.agi.gretl.distribution-manifest-sha256":{{json (index .Config.Labels "ch.so.agi.gretl.distribution-manifest-sha256")}}}}}'
metadata=json.loads(command(['docker','image','inspect','--format',template,image]))
image_id=metadata['Id'];config=metadata['Config']
if config['Entrypoint']!=['/opt/gretl/bin/gretl'] or config['User']!='10001:0':raise ValueError('Wrong generic runtime entrypoint/user')
labels=config.get('Labels',{})
if labels.get('org.opencontainers.image.revision')!=source or labels.get('ch.so.agi.gretl.distribution-manifest-sha256')!=manifest_sha:raise ValueError('Wrong image provenance labels')
name='gretl-image-proof-'+uuid.uuid4().hex
container=command(['docker','create','--pull=never','--label','gretl.verification.run='+os.environ.get('GRETL_VERIFICATION_RUN_ID','image-build-'+uuid.uuid4().hex),'--name',name,image_id,'--version'])
(out/'proof-container-id.txt').write_text(container+'\n')
extract=out/'extracted'
if extract.exists():shutil.rmtree(extract)
extract.mkdir()
try:command(['docker','cp',container+':/opt/gretl/.',str(extract)])
finally:command(['docker','rm','--force',container])
if sha(extract/'SHA256SUMS')!=manifest_sha or verify(extract)!=entries:raise ValueError('Actual image distribution bytes differ')
proof=dict(schemaVersion=1,id=image_id,sourceRevision=source,sourceDirty=catalog['sourceDirty'],distributionManifestSha256=manifest_sha,
 os=metadata['Os'],architecture=metadata['Architecture'],baseReference=base,entrypoint=config['Entrypoint'],user=config['User'],buildCommand=build,
 files=[dict(path=k,sha256=v) for k,v in sorted(entries.items())])
(out/'candidate.json').write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps({k:v for k,v in proof.items() if k not in {'files','buildCommand'}},indent=2))
