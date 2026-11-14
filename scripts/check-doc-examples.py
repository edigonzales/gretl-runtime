#!/usr/bin/env python3
"""Execute marked documentation examples and render both documentation sites."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import http.server
import threading
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def marked_examples(path):
    text=path.read_text()
    return re.findall(r'<!-- checked-example: ([a-z-]+) -->\s*```sh\n(.*?)```',text,re.S)


def run(root,evidence,thoth=None):
    root=root.resolve();evidence=evidence.resolve();evidence.mkdir(parents=True,exist_ok=False)
    results=[]
    def command(name,argv,cwd,env=None):
        out=evidence/(name+'.stdout');err=evidence/(name+'.stderr')
        with out.open('wb') as stdout,err.open('wb') as stderr:
            p=subprocess.run(argv,cwd=cwd,env=env,stdout=stdout,stderr=stderr,timeout=600)
        row=dict(name=name,argv=argv,cwd=str(cwd),exitCode=p.returncode,
                 stdoutSha256=hashlib.sha256(out.read_bytes()).hexdigest(),stderrSha256=hashlib.sha256(err.read_bytes()).hexdigest())
        results.append(row)
        if p.returncode:raise RuntimeError('Documentation check failed: '+name+'; see '+str(evidence))
    dist=root/'gretl-cli/build/install/gretl-runtime'
    if not (dist/'bin/gretl').is_file():raise RuntimeError('Build the standard distribution first')
    minimal=(dist/'examples/minimal-action/job.groovy').read_text().strip()
    if minimal not in (root/'README.md').read_text():raise RuntimeError('README Groovy example drifted from executed minimal action')
    for doc in ['README.md','website/getting-started.qmd']:
        examples = marked_examples(root/doc)
        if not examples:
            raise RuntimeError('No checked example in ' + doc)
        for label,script in examples:
            # Keep every synthetic result for independent inspection. mktemp is
            # overridden only to locate outputs; the documented commands run unchanged.
            sandbox=evidence/label;sandbox.mkdir()
            tools=sandbox/'tools';tools.mkdir()
            mktemp=tools/'mktemp';mktemp.write_text('#!/bin/sh\nmkdir -p "$GRETL_DOC_SANDBOX/output"\nprintf "%s\\n" "$GRETL_DOC_SANDBOX/output"\n');mktemp.chmod(0o700)
            executable=sandbox/'example.sh';executable.write_text(script)
            env=dict(os.environ,GRETL_DOC_SANDBOX=str(sandbox),PATH=str(tools)+os.pathsep+os.environ['PATH'])
            cwd=root if label=='checkout' else dist
            command(label,['bash','--noprofile','--norc','-e','-u',str(executable)],cwd,env)
            output=sandbox/'output';name='jar-message.txt' if 'jar' in label else 'message.txt'
            if (output/name).read_bytes()!=(dist/'examples/minimal-action/message.txt').read_bytes():raise RuntimeError('Wrong quickstart result: '+label)
            report=json.loads((output/('jar-report.json' if 'jar' in label else 'script-report.json')).read_text())
            if report.get('execution',{}).get('exitCode')!=0 or report.get('execution',{}).get('status')!='SUCCEEDED':raise RuntimeError('Quickstart report failed: '+label)
            results[-1]['outputSha256']=hashlib.sha256((output/name).read_bytes()).hexdigest()
    # Execute the landing-page action as printed, rather than assuming its DSL is valid.
    home=(root/'website/index.qmd').read_text();code=re.search(r'```\{\.groovy[^\n]*\}\n(.*?)```',home,re.S).group(1)
    job=evidence/'landing.groovy';job.write_text(code)
    config=evidence/'landing-config.json';config.write_text(json.dumps({'schemaVersion':1,'runtime':{'reportRoot':str(evidence/'landing-reports')}}))
    command('landing',[str(dist/'bin/gretl'),'run',str(job),'--config',str(config),'--work-dir',str(evidence/'landing-work'),'--report',str(evidence/'landing-report.json')],root)
    if 'Hello GRETL Runtime' not in ((evidence/'landing.stdout').read_text()+(evidence/'landing.stderr').read_text()):raise RuntimeError('Landing example output missing')
    for label,target,project in [('subsource',':source:run','source'),('subchecks',':checks:local:run','checks/local')]:
        command(label,[str(dist/'bin/gretl'),'run',str(root/'examples/project-subprojects'),
                       '--target',target,'--config',str(config),'--report',str(evidence/(label+'-report.json'))],root)
        expected=(root/'examples/project-subprojects'/project/'input.txt').read_text().strip()
        if expected not in (evidence/(label+'.stderr')).read_text()+(evidence/(label+'.stdout')).read_text():
            raise RuntimeError('Subproject example did not read its own input: '+label)
    # Both copies execute in their documented CWD. Only a loopback HTTP fixture
    # is served; use the actual published ZIP and compare every extracted byte.
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(root/'examples/download-extract'),**kwargs)
        def log_message(self,*args):pass
    with http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler) as server:
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            for context,cwd in [('checkout',root),('distribution',dist)]:
                for example in ('download-extract','project-pipeline'):
                    label=context+'-'+example;output=evidence/label;output.mkdir()
                    report=evidence/(label+'-report.json')
                    source='examples/'+example+('/job.groovy' if example=='download-extract' else '')
                    argv=[str(dist/'bin/gretl'),'run',source,'--config',str(config),
                          '--work-dir',str(output/'work'),'--report',str(report)]
                    if example=='download-extract':
                        argv+=['--param','quelle=http://127.0.0.1:%d/data.zip'%server.server_port,'--param','ziel='+str(output/'extracted')]
                    else:argv+=['--param',':output='+str(output/'pipeline.txt')]
                    command(label,argv,cwd)
                    execution=json.loads(report.read_text()).get('execution',{})
                    if execution.get('exitCode')!=0 or execution.get('status')!='SUCCEEDED':raise RuntimeError('Example report failed: '+label)
                    if example=='download-extract':
                        with zipfile.ZipFile(cwd/'examples/download-extract/data.zip') as archive:
                            expected={name:archive.read(name) for name in archive.namelist() if not name.endswith('/')}
                        actual={str(p.relative_to(output/'extracted')):p.read_bytes() for p in (output/'extracted').rglob('*') if p.is_file()}
                        if actual!=expected:raise RuntimeError('Extracted example bytes differ: '+label)
                        results[-1]['outputHashes']={p:hashlib.sha256(data).hexdigest() for p,data in actual.items()}
                    else:
                        if (output/'pipeline.txt').read_bytes()!=(cwd/'examples/project-pipeline/prepare/data/input.txt').read_bytes():raise RuntimeError('Pipeline bytes differ: '+label)
                        results[-1]['outputSha256']=hashlib.sha256((output/'pipeline.txt').read_bytes()).hexdigest()
        finally:server.shutdown();thread.join(timeout=5)
    before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'docs/reference/generated').glob('task-*.adoc')}
    command('reference-generate',[str(dist/'bin/gretl'),'operations','--format','adoc','--lang','de','--output',str(root/'docs/reference/generated')],root)
    after={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'docs/reference/generated').glob('task-*.adoc')}
    if len(after)!=51 or before!=after:raise RuntimeError('Generated 51-task reference drift')
    site=evidence/'rendered-website'
    command('quarto',['quarto','render','website/','--output-dir',str(site)],root)
    for name in ['index','features','getting-started','docs']:
        page=site/(name+'.html')
        if not page.is_file():raise RuntimeError('Missing Quarto page: '+name)
        if any(bad in page.read_text() for bad in ['Lorem ipsum','tasks.register','Lightweight control plane']):raise RuntimeError('Obsolete website content: '+name)
    thoth=thoth or os.environ.get('GRETL_THOTH_JAR')
    if not thoth or not Path(thoth).is_file():raise RuntimeError('Provide the existing Thoth Biblios JAR via GRETL_THOTH_JAR')
    # JGit cannot reliably clone a linked detached worktree. Materialize the
    # exact commit in a separate full Git repository, without touching user refs.
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    reference_source=evidence/'reference-source';reference_source.mkdir()
    command('reference-init',['git','init','--quiet',str(reference_source)],root)
    command('reference-fetch',['git','-C',str(reference_source),'fetch','--quiet',str(root),revision],root)
    command('reference-checkout',['git','-C',str(reference_source),'checkout','--quiet','-B','main',revision],root)
    config_dir=reference_source/'docs/reference'
    with tempfile.NamedTemporaryFile(prefix='biblios.verify-',suffix='.yml',dir=config_dir,delete=False) as temporary:
        config=Path(temporary.name)
    try:
        config.write_text((root/'docs/reference/biblios.local.yml.example').read_text().replace('/pfad/zu/gretl-runtime',str(reference_source)))
        command('thoth',['java','-jar',str(Path(thoth).resolve()),'build','--config',str(config),'--use-local-working-tree','--clean','--output',str(evidence/'reference')],reference_source)
    finally:config.unlink(missing_ok=True)
    (evidence/'reference-source.json').write_text(json.dumps({'candidateSha':revision,'sourceHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=reference_source,text=True).strip(),'privateBranch':'main','sourceUrl':str(reference_source)},indent=2)+'\n')
    shutil.rmtree(reference_source)
    html=list((evidence/'reference').rglob('index.html'))
    if not any('SqlExecutor' in p.read_text() and 'IliValidator' in p.read_text() for p in html):raise RuntimeError('Rendered task reference missing')
    value=dict(status='PASS',sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),commands=results,generatedTables=after)
    (evidence/'result.json').write_text(json.dumps(value,indent=2)+'\n')
    return value


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--evidence',type=Path,required=True);p.add_argument('--thoth',type=Path)
    args=p.parse_args();value=run(ROOT,args.evidence,args.thoth);print(json.dumps({'status':value['status'],'sourceRevision':value['sourceRevision'],'commands':[r['name'] for r in value['commands']],'generatedTables':len(value['generatedTables'])}))
