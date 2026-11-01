#!/usr/bin/env python3
"""SHA-bound independent OpenCode V2 review, Python 3.9 standard library only.

Exit codes: 0 validated PASS, 10 FAIL, 20 BLOCKED, 30 internal failure.
The launcher never commits, resets or modifies the implementation checkout.
"""
from __future__ import annotations
import argparse
import dataclasses
import datetime
import hashlib
from html.parser import HTMLParser
import json
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path
from typing import Optional
import xml.etree.ElementTree as ET

MODEL = 'deepseek/deepseek-flash'
CHECKPOINT = 'DeepSeek-V4.1-Flash-1017'
PRICING = 'https://api-docs.deepseek.com/quick_start/pricing/'
REQUIRED_IDS = {p: tuple('%s-%02d' % (p, i) for i in range(1, 7)) for p in ('P0','P1','P2','P3')}
REQUIRED_IDS['P3'] = tuple(x for p in ('P0','P1','P2','P3') for x in REQUIRED_IDS[p])
BEGIN, END = 'GRETL_REVIEW_JSON_BEGIN', 'GRETL_REVIEW_JSON_END'
MAX_STREAM = 32 * 1024 * 1024


class Blocked(Exception):
    pass


class Failed(Exception):
    pass


@dataclasses.dataclass(frozen=True)
class ReviewRequest:
    phase: str
    phase_base_sha: str
    candidate_sha: str
    initial_baseline_sha: str
    repository_root: Path
    specification_file: Path


@dataclasses.dataclass(frozen=True)
class CommandEvidence:
    argv: tuple[str, ...]
    cwd: Path
    return_code: int
    stdout_path: Path
    stderr_path: Path
    started_at_utc: str
    finished_at_utc: str


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False, default=str) + '\n')


def redact(text):
    for key, value in os.environ.items():
        if value and len(value) > 7 and any(x in key.upper() for x in ('TOKEN','SECRET','PASSWORD','API_KEY')):
            text = text.replace(value, '[REDACTED]')
    text = re.sub(r'(?i)(authorization[\"\s:]+bearer\s+)[^\s\"\\]+', r'\1[REDACTED]', text)
    return re.sub(r'\bsk-[A-Za-z0-9_-]{12,}', '[REDACTED]', text)


def git(root, *args):
    p = subprocess.run(['git', *args], cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       timeout=60, text=True)
    if p.returncode:
        raise Blocked('git %s: %s' % (' '.join(args), redact(p.stderr)))
    return p.stdout.strip()


def run_command(argv, cwd, destination, timeout=3600, environment=None):
    """Bounded process group, streamed to private temporary files; persist redacted logs."""
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=False, mode=0o700)
    started = now()
    failure = None
    code = -1
    with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
        proc = subprocess.Popen(list(argv), cwd=cwd, env=environment, stdout=out, stderr=err,
                                shell=False, start_new_session=True)
        try:
            code = proc.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as exc:
            failure = type(exc).__name__
            os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait(timeout=5)
        finally:
            for stream, name in [(out, 'stdout.log'), (err, 'stderr.log')]:
                size = stream.tell()
                stream.seek(0)
                text = stream.read(MAX_STREAM + 1).decode('utf-8', errors='strict')
                if size > MAX_STREAM:
                    failure = 'output limit exceeded'
                (destination / name).write_text(redact(text[:MAX_STREAM]))
    evidence = CommandEvidence(tuple(argv), Path(cwd), code, destination/'stdout.log',
                               destination/'stderr.log', started, now())
    save(destination/'command.json', dict(dataclasses.asdict(evidence), failure=failure,
         stdoutSha256=digest(evidence.stdout_path), stderrSha256=digest(evidence.stderr_path)))
    if failure:
        raise Blocked('%s: %s' % (argv[0], failure))
    if code:
        raise Blocked('Command exit %s: %s (logs: %s)' % (code, argv, destination))
    return evidence


ORACLE_TEST='gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/OperationMetadataTest.java'
ORACLE_MOVES={
 'docs/implementation/improvement-plan/operation-inventory.json':'docs/history/contracts/operation-baseline.json',
 'docs/implementation/improvement-plan/phase-2-java-moves.json':'docs/history/contracts/java-type-moves.json'}


def path_only_oracle_test_move(root,base,path):
    if path!=ORACLE_TEST:return False
    original=subprocess.check_output(['git','show',base+':'+path],cwd=root)
    expected=original
    for old,new in ORACLE_MOVES.items():
        if original.count(old.encode())!=1:return False
        expected=expected.replace(old.encode(),new.encode())
    target=root/path
    return target.is_file() and target.read_bytes()==expected


def validate_request(r):
    if r.phase not in REQUIRED_IDS:
        raise Blocked('Unknown phase')
    for sha in (r.phase_base_sha, r.candidate_sha, r.initial_baseline_sha):
        if not re.fullmatch('[0-9a-f]{40}', sha):
            raise Blocked('Full immutable SHA required')
        if git(r.repository_root, 'rev-parse', sha+'^{commit}') != sha:
            raise Blocked('Not a commit')
    git(r.repository_root, 'merge-base','--is-ancestor',r.phase_base_sha,r.candidate_sha)
    git(r.repository_root, 'merge-base','--is-ancestor',r.initial_baseline_sha,r.phase_base_sha)
    if git(r.repository_root,'rev-parse','HEAD') != r.candidate_sha or git(r.repository_root,'status','--porcelain=v1','--untracked-files=all'):
        raise Blocked('Implementation checkout must be clean at candidate')
    try:
        specification_path=str(r.specification_file.relative_to(r.repository_root))
    except ValueError:
        raise Blocked('Specification must be inside the candidate checkout')
    if not r.specification_file.is_file():
        raise Blocked('Missing specification')
    git(r.repository_root,'ls-files','--error-unmatch',specification_path)
    diff = git(r.repository_root,'diff','--name-only',r.phase_base_sha+'..'+r.candidate_sha).splitlines()
    prefixes = ('scripts/cleanup_', 'scripts/verify_cleanup_', 'tests/cleanup_review/', 'docs/maintenance/cleanup/')
    if r.phase == 'P0' and any(p!='.gitignore' and not p.startswith(prefixes) for p in diff):
        raise Blocked('P0 scope violation')
    if r.phase in ('P1','P2') and any(('/src/main/java/' in p or '/src/test/' in p or p.endswith('pom.xml'))
            and not (r.phase=='P2' and path_only_oracle_test_move(r.repository_root,r.phase_base_sha,p)) for p in diff):
        raise Blocked('Product/test/build mutation outside documentation phase')
    baseline_spec=git(r.repository_root,'show',r.initial_baseline_sha+':'+specification_path)
    if baseline_spec!=r.specification_file.read_text().strip():
        raise Blocked('Specification changed from the authorised baseline')
    if r.phase != 'P0':
        previous = 'P%d' % (int(r.phase[1])-1)
        files = git(r.repository_root,'diff-tree','--no-commit-id','--name-only','-r',r.phase_base_sha).splitlines()
        pattern = r'docs/maintenance/cleanup/reviews/'+previous+r'-[a-f0-9]{7}-review\.(?:md|json)'
        if len(files)!=2 or any(not re.fullmatch(pattern,p) for p in files):
            raise Blocked('Prior phase base must be proof-only commit')
        proof_path=next(p for p in files if p.endswith('.json'))
        proof=json.loads(git(r.repository_root,'show',r.phase_base_sha+':'+proof_path))
        parent=git(r.repository_root,'rev-parse',r.phase_base_sha+'^')
        if proof.get('candidateSha')!=parent or proof.get('verdict')!='PASS' or proof.get('phase')!=previous:
            raise Blocked('Prior proof is not bound to its candidate parent')


def allocate_evidence_dir(r):
    if r.phase not in REQUIRED_IDS or not re.fullmatch('[0-9a-f]{40}',r.candidate_sha):
        raise Blocked('Invalid phase/SHA cannot name an evidence directory')
    parent = Path(tempfile.gettempdir()).resolve()/'gretl-runtime-cleanup-review'/r.phase/r.candidate_sha
    parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    for attempt in range(1,10000):
        path = parent/('attempt-%02d' % attempt)
        try:
            path.mkdir(mode=0o700)
            return path
        except FileExistsError:
            continue
    raise Blocked('Evidence attempts exhausted')


def create_detached_worktree(r, evidence_dir):
    worktree = (evidence_dir/'worktree').resolve()
    git(r.repository_root, 'worktree','add','--detach',str(worktree),r.candidate_sha)
    verify_worktree_unchanged(worktree,r)
    save(evidence_dir/'initial-worktree.json', {'head':git(worktree,'rev-parse','HEAD'), 'status':git(worktree,'status','--porcelain=v1')})
    return worktree


def verify_worktree_unchanged(worktree,r, allowed=()):
    if git(worktree,'rev-parse','HEAD') != r.candidate_sha:
        raise Blocked('Review HEAD changed')
    if git(worktree,'diff','--name-only') or git(worktree,'diff','--cached','--name-only'):
        raise Blocked('Tracked review files changed')
    unknown = git(worktree,'ls-files','--others','--exclude-standard').splitlines()
    if set(unknown)-set(allowed):
        raise Blocked('Unexpected untracked review files: '+str(unknown))


def snapshot_reports(worktree,destination,full=False):
    rows, counts = [], dict(tests=0,failures=0,errors=0,skipped=0)
    ns={'m':'http://maven.apache.org/POM/4.0.0'}
    pom=ET.parse(worktree/'pom.xml').getroot()
    modules=[m.text for m in pom.findall('m:modules/m:module',ns)]+(['gretl-geotools-probe'] if full else [])
    for source in sorted(p for module in modules for p in (worktree/module/'target').glob('*-reports/TEST-*.xml')):
        if source.name.startswith('TEST-'):
            suite=ET.parse(source).getroot()
            for key in counts:
                counts[key] += int(suite.get(key,'0'))
            relative=source.relative_to(worktree)
            target=destination/'reports'/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(redact(source.read_text(encoding='utf-8')))
            rows.append({'path':str(relative),'sha256':digest(target)})
    if not counts['tests'] or any(counts[k] for k in ('failures','errors','skipped')):
        raise Blocked('Incomplete or failing/skipped test reports: '+str(counts))
    artifacts=[]
    for source in sorted((worktree/'gretl-dist/target').glob('*-bin.*')):
        artifacts.append({'path':str(source.relative_to(worktree)),'sha256':digest(source)})
    for name in ['package-catalog.json','SHA256SUMS','inventory.json']:
        source=worktree/'gretl-dist/target/dist'/name
        if source.is_file():
            shutil.copyfile(source,destination/name)
    job_root=worktree/'gretl-integration-tests/target/job-evidence'
    if job_root.exists():
        for source in job_root.rglob('*.json'):
            if source.name not in {'execution.json','package-inventory.json','report.json'}: continue
            target=destination/'job-evidence'/source.relative_to(job_root)
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(redact(source.read_text(encoding='utf-8')))
    qualification=worktree/'gretl-integration-tests/target/qualification'
    if qualification.exists():
        shutil.copytree(qualification,destination/'qualification')
    result=dict(counts=counts,reports=rows,artifacts=artifacts)
    save(destination/'reports.json',result)
    return result


def qualify_environment(worktree, evidence_dir):
    """Fail before live inference/builds if a mandatory local prerequisite is absent."""
    if sys.version_info < (3, 9):
        raise Blocked('Python 3.9+ required')
    results = {}
    checks = [('python', [sys.executable, '--version']), ('git', ['git', '--version']),
              ('java', ['java', '-version']), ('maven', ['./mvnw', '-version']),
              ('docker', ['docker', 'info', '--format', '{{.ServerVersion}}']),
              ('os', ['uname', '-a'])]
    for name, argv in checks:
        run = run_command(argv, worktree, evidence_dir/('environment-'+name), timeout=60)
        results[name] = run.stdout_path.read_text() + run.stderr_path.read_text()
    if not re.search(r'(?:openjdk|java) version "25(?:[.\"]|$)', results['java']):
        raise Blocked('JDK 25 required: environment-java')
    if not re.search(r'Apache Maven 3\.', results['maven']):
        raise Blocked('Maven wrapper did not identify Apache Maven 3')
    if not re.search(r'^\d+\.\d+', results['docker'].strip()):
        raise Blocked('Docker daemon did not return a server version')
    save(evidence_dir/'environment.json', {'status':'QUALIFIED', 'python':sys.version,
                                         'checks':list(results), 'checkedAt':now()})


def run_independent_gates(worktree,r,evidence_dir):
    runs=[]
    if not (evidence_dir/'environment.json').exists():
        qualify_environment(worktree,evidence_dir)
    save(evidence_dir/'source.json',{'head':git(worktree,'rev-parse','HEAD'),'status':git(worktree,'status','--porcelain=v1'),'python':sys.version,'JAVA_TOOL_OPTIONS':redact(os.environ.get('JAVA_TOOL_OPTIONS',''))})
    for name,argv in [('python',[sys.executable,'-m','unittest','discover','-s','tests/cleanup_review','-v']),
                      ('standard',['./mvnw','-B','clean','verify']),
                      ('full',['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify'])]:
        run=run_command(argv,worktree,evidence_dir/name,timeout=3600,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
        runs.append(run)
        if name!='python':
            audit=[sys.executable,'scripts/audit-job-evidence.py']+(['--full'] if name=='full' else [])+['--source',r.candidate_sha]
            run_command(audit,worktree,evidence_dir/(name+'-audit'),timeout=120)
            snapshot_reports(worktree,evidence_dir/name,full=(name=='full'))
    run_command([sys.executable,'scripts/cleanup_inventory.py','--check'],worktree,evidence_dir/'invariants',timeout=120,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    run_command([sys.executable,'scripts/verify_cleanup_docs.py']+(['--audit'] if r.phase=='P0' else []),worktree,evidence_dir/'docs-audit',timeout=120,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    if r.phase!='P0':
        run_command([sys.executable,'scripts/cleanup_doc_checks.py','--evidence',str(evidence_dir/'docs-runtime')],worktree,evidence_dir/'docs-runtime-command',timeout=1200,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    if r.phase in ('P2','P3'):
        run_command([sys.executable,'scripts/cleanup_history_check.py'],worktree,evidence_dir/'history-check',timeout=180,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    if r.phase=='P3':
        run_command([sys.executable,'scripts/cleanup_final_check.py'],worktree,evidence_dir/'final-check',timeout=180,environment=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    # clean in the second build cannot destroy the first gate's external evidence.
    visible=worktree/'target/cleanup-review-evidence'
    visible.mkdir(parents=True)
    for name in ('python','standard','full','standard-audit','full-audit','invariants','docs-audit','environment-java','environment-maven','environment-os','environment-python','environment-git','environment-docker'):
        shutil.copytree(evidence_dir/name,visible/name)
    for name in ['source.json','provider.json','preflight.json','environment.json','official-model-mapping.html']:
        if (evidence_dir/name).exists(): shutil.copyfile(evidence_dir/name,visible/name)
    for name in ['smoke','smoke-provenance','deny-edit','deny-shell']:
        if (evidence_dir/name).is_dir(): shutil.copytree(evidence_dir/name,visible/name)
    if r.phase!='P0':
        shutil.copytree(evidence_dir/'docs-runtime',visible/'docs-runtime')
        shutil.copytree(evidence_dir/'docs-runtime-command',visible/'docs-runtime-command')
    if r.phase in ('P2','P3'):
        shutil.copytree(evidence_dir/'history-check',visible/'history-check')
    if r.phase=='P3':
        shutil.copytree(evidence_dir/'final-check',visible/'final-check')
    save(visible/'manifest.json', {'candidateSha':r.candidate_sha,'commands':[dataclasses.asdict(x) for x in runs],
         'environment':{'python':sys.version,'platform':platform.platform()}})
    for p in visible.rglob('*'):
        if p.is_file(): p.chmod(0o400)
    return runs


def discover_opencode_v2(r):
    override=os.environ.get('OPENCODE_BIN')
    candidates=[override,shutil.which('opencode'),
                '/Applications/OpenCode.app/Contents/Resources/opencode-cli',str(Path.home()/'.opencode/bin/opencode')]
    for name in dict.fromkeys(x for x in candidates if x):
        executable=Path(name)
        if not executable.is_file(): continue
        try:
            version=subprocess.check_output([str(executable),'--version'],timeout=30,text=True).strip()
        except (OSError,subprocess.SubprocessError):
            continue
        if re.fullmatch(r'(?:opencode v?|v)?2\.\d+\.\d+',version):
            return executable.resolve()
        if override and name==override:
            raise Blocked('Explicit OPENCODE_BIN is not OpenCode V2')
    raise Blocked('No qualified OpenCode V2 CLI; V1 is not the Desktop version')


def prepare_opencode_environment(r):
    if os.environ.get('GRETL_REVIEW_MODEL',MODEL)!=MODEL:
        raise Blocked('Forbidden model override')
    # Preserve HOME/XDG/DB so the existing V2 credential account is reused.
    env={k:v for k,v in os.environ.items() if k in {
        'HOME','USER','LOGNAME','PATH','SHELL','TMPDIR','LANG','LC_ALL','JAVA_HOME',
        'XDG_CONFIG_HOME','XDG_DATA_HOME','XDG_STATE_HOME','OPENCODE_DB','DEEPSEEK_API_KEY',
        'SSL_CERT_FILE','SSL_CERT_DIR','HTTPS_PROXY','HTTP_PROXY','NO_PROXY'}}
    env['PYTHONDONTWRITEBYTECODE']='1'
    return env


def review_config(r):
    # Exact command allowlist prevents options such as git diff --output or git show --output.
    commands=['git status --porcelain=v1','git ls-files',
              'git diff --name-status --find-renames '+r.phase_base_sha+'..'+r.candidate_sha,
              'git diff '+r.phase_base_sha+'..'+r.candidate_sha,
              'git diff '+r.initial_baseline_sha+'..'+r.candidate_sha]
    bases=list(dict.fromkeys([r.phase_base_sha]+([r.initial_baseline_sha] if r.phase=='P3' else [])))
    for base in bases:
        commands.append('git diff --name-status --find-renames '+base+'..'+r.candidate_sha)
        for path in git(r.repository_root,'diff','--name-only','--no-renames','--diff-filter=D',base+'..'+r.candidate_sha).splitlines():
            if re.fullmatch(r'[A-Za-z0-9_./-]+',path):
                commands.append('git show '+base+':'+path)
    rules=[{'action':'*','resource':'*','effect':'deny'}]
    rules += [{'action':x,'resource':'*','effect':'allow'} for x in ('read','glob','grep')]
    rules += [{'action':'read','resource':x,'effect':'deny'} for x in ('.env*','*.env','**/.env*','**/auth.json','**/opencode.db*','.git','.git/*')]
    rules += [{'action':'shell','resource':x,'effect':'allow'} for x in dict.fromkeys(commands)]
    return {'$schema':'https://opencode.ai/config.json', 'model':MODEL,
            'providers':{'deepseek':{'settings':{'baseURL':'https://api.deepseek.com'},'models':{'deepseek-flash':{'modelID':'deepseek-flash'}}}},
            'permissions':rules,'tool_output':{'max_lines':100000 if r.phase in ('P2','P3') else 20000,'max_bytes':8388608 if r.phase in ('P2','P3') else 2097152},
            'agents':{'cleanup-review':{'description':'Independent read-only cleanup reviewer','mode':'primary','model':MODEL,'permissions':rules}}}


def verify_official_mapping(html):
    """Bind the API-name column to MODEL VERSION, not a historical footnote."""
    class ModelTable(HTMLParser):
        def __init__(self):
            super().__init__(); self.cell=None; self.row=[]; self.rows=[]
        def handle_starttag(self,tag,attrs):
            if tag=='tr': self.row=[]
            if tag in ('td','th'): self.cell=[]
        def handle_data(self,data):
            if self.cell is not None: self.cell.append(data)
        def handle_endtag(self,tag):
            if tag in ('td','th') and self.cell is not None:
                self.row.append(' '.join(''.join(self.cell).split())); self.cell=None
            if tag=='tr' and self.row: self.rows.append(self.row)
    parser=ModelTable(); parser.feed(html)
    column=None; versions=[]
    for row in parser.rows:
        if row[0]=='MODEL':
            columns=[i for i,value in enumerate(row) if re.sub(r'\(\d+\)$','',value)=='deepseek-flash']
            column=columns[0] if len(columns)==1 else None
        elif row[0]=='MODEL VERSION' and column is not None and len(row)>column:
            versions.append(row[column]); column=None
    if versions!=[CHECKPOINT]:
        raise Blocked('Official API-name/version column mapping changed or ambiguous; specification revision required')


def inspect_v2_auth_and_models(executable,r,evidence_dir,env):
    runs={}
    for name,args in [('version',['--version']),('help',['run','--help']),('auth',['auth','list','--format','json']),('models',['models'])]:
        runs[name]=run_command([str(executable),*args],r.repository_root,evidence_dir/('opencode-'+name),timeout=60,environment=env)
    help_text=runs['help'].stdout_path.read_text()
    if any(x not in help_text for x in ['--model','--format','--agent']):
        raise Blocked('Unsupported V2 flags')
    auth=json.loads(runs['auth'].stdout_path.read_text())
    if not any(p['id']=='deepseek' and p.get('connections') for p in auth):
        raise Blocked('Existing V2 direct DeepSeek connection not accessible in current profile')
    if MODEL not in runs['models'].stdout_path.read_text().splitlines():
        raise Blocked('Required model unavailable')
    req=urllib.request.Request(PRICING,headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req,timeout=30) as response:
        if response.geturl().split('/')[2]!='api-docs.deepseek.com':
            raise Blocked('Unexpected official mapping host')
        body=response.read(2*1024*1024+1)
        if len(body)>2*1024*1024: raise Blocked('Official mapping response truncated')
        html=body.decode('utf-8')
    verify_official_mapping(html)
    (evidence_dir/'official-model-mapping.html').write_text(html)
    meta={'executable':str(executable),'executableSha256':digest(executable),'version':runs['version'].stdout_path.read_text().strip(),
          'model':MODEL,'apiModelId':'deepseek-flash','provider':'deepseek','endpoint':'https://api.deepseek.com',
          'checkpointMapping':CHECKPOINT,'mappingUrl':PRICING,'mappingSha256':digest(evidence_dir/'official-model-mapping.html'),'checkedAt':now()}
    save(evidence_dir/'provider.json',meta)
    return meta


def parse_stream(path):
    data=Path(path).read_bytes()
    if not data or len(data)>MAX_STREAM: raise Blocked('Empty/oversized JSONL')
    try:
        events=[json.loads(line) for line in data.decode('utf-8',errors='strict').splitlines() if line.strip()]
    except (ValueError,UnicodeError) as exc:
        raise Blocked('Invalid JSONL: '+str(exc))
    if any(not isinstance(x,dict) or x.get('type')=='error' for x in events):
        raise Blocked('OpenCode error/invalid event')
    return events


def final_review_text(events):
    """Bind the sole final report to V2's stopped assistant message (§4.3.8).

    Earlier neutral tool-progress messages are not the final answer. Never accept
    an earlier verdict, an unbound preamble, multiple reports or final commentary.
    """
    if not events or events[-1].get('type')!='step_finish' or events[-1].get('part',{}).get('reason')!='stop':
        raise Blocked('Stream ended without final completion')
    texts=[(n,e.get('part',{})) for n,e in enumerate(events) if e.get('type')=='text']
    all_text='\n'.join(part.get('text','') for _,part in texts)
    if all_text.count(BEGIN)!=1 or all_text.count(END)!=1:
        raise Blocked('Missing/ambiguous final review markers')
    final_id=events[-1]['part'].get('messageID')
    if final_id:
        final=[(n,p) for n,p in texts if p.get('messageID')==final_id]
        if not final:raise Blocked('Final report not bound to stopped message')
        first=final[0][0]
        for index,part in texts:
            if part.get('messageID')==final_id:continue
            mid=part.get('messageID');progress=part.get('text','')
            completed=any(index<n<first and e.get('type')=='step_finish'
                          and e.get('part',{}).get('messageID')==mid
                          and e['part'].get('reason')=='tool-calls' for n,e in enumerate(events))
            if not mid or index>=first or not completed or re.search(
                    r'\b(?:PASS|FAIL|BLOCKED|verdict|approved|passed|failed|Freigabe|abgenommen|bestanden)\b',progress,re.I):
                raise Blocked('Contradictory or unbound intermediate review text')
        if any(e.get('type')=='step_finish' and e.get('part',{}).get('reason')=='stop' for e in events[:-1]):
            raise Blocked('Multiple final completions')
        text='\n'.join(p.get('text','') for _,p in final)
    else:
        # Minimal qualified fixtures/older event shape: no trustworthy message
        # boundary exists, so retain the strict no-preamble whole-stream rule.
        text=all_text
    before,tail=text.split(BEGIN) if BEGIN in text else ('', '')
    if END not in tail:raise Blocked('Final markers outside stopped message')
    body,after=tail.split(END)
    if before.strip() or after.strip():raise Blocked('Contradictory/nonfinal review text')
    return text


def parse_final_review(jsonl_path):
    text=final_review_text(parse_stream(jsonl_path))
    body=text.split(BEGIN)[1].split(END)[0]
    try: review=json.loads(body)
    except ValueError as exc: raise Blocked('Invalid review JSON: '+str(exc))
    return review


def validate_session(executable,run,worktree,evidence_dir,env):
    worktree=worktree.resolve()
    evidence_dir.mkdir(parents=True,exist_ok=False,mode=0o700)
    events=parse_stream(run.stdout_path)
    sessions={e.get('sessionID') for e in events}
    if len(sessions)!=1 or not next(iter(sessions),None):
        raise Blocked('Missing/ambiguous session provenance')
    session=next(iter(sessions))
    export=run_command([str(executable),'session','export',session,'--sanitize'],worktree,evidence_dir/'session-export',timeout=60,environment=env)
    data=json.loads(export.stdout_path.read_text())
    info=data.get('info',{})
    expected={'id':'deepseek-flash','providerID':'deepseek'}
    if info.get('id')!=session or info.get('agent')!='cleanup-review' or info.get('outcome')!='succeeded':
        raise Blocked('Session did not complete independently')
    started=datetime.datetime.fromisoformat(run.started_at_utc).timestamp()*1000
    finished=datetime.datetime.fromisoformat(run.finished_at_utc).timestamp()*1000
    created=info.get('time',{}).get('created',0)
    if not started-2000 <= created <= finished+2000:
        raise Blocked('Session was not freshly created for this process')
    models=[info.get('model',{})]+[m.get('model',{}) for m in data.get('messages',[]) if m.get('type')=='assistant']
    if len(models)<2 or any(any(m.get(k)!=v for k,v in expected.items()) for m in models):
        raise Blocked('Wrong provider/API model in actual session')
    location_param='location[directory]='+str(worktree)
    location=run_command([str(executable),'api','location.get','--param',location_param],worktree,
                         evidence_dir/'effective-location',timeout=60,environment=env)
    resolved=json.loads(location.stdout_path.read_text()).get('directory','')
    if Path(resolved).resolve()!=worktree.resolve(): raise Blocked('Provider metadata resolved a different location')
    provider=run_command([str(executable),'api','provider.get','--param','providerID=deepseek','--param',location_param],
                         worktree,evidence_dir/'effective-provider',timeout=60,environment=env)
    payload=json.loads(provider.stdout_path.read_text())
    if Path(payload.get('location',{}).get('directory','')).resolve()!=worktree.resolve():
        raise Blocked('Provider metadata is not bound to review worktree')
    metadata=payload['data']
    if metadata.get('package')!='@opencode/ai/providers/deepseek' or metadata.get('id')!='deepseek' or metadata.get('integrationID')!='deepseek' or metadata.get('settings',{}).get('baseURL') not in ('https://api.deepseek.com','https://api.deepseek.com/v1'):
        raise Blocked('Effective provider is not direct DeepSeek')
    save(evidence_dir/'session-provenance.json',{'sessionID':session,'model':expected,'endpoint':metadata['settings']['baseURL'],
        'jsonlSha256':digest(run.stdout_path),'sanitizedExportSha256':digest(export.stdout_path)})
    return session


def live_preflight(executable,worktree,r,evidence_dir,env):
    # Effective V2 edit denials remove write tools from the advertised catalogue.
    # Request the tool; require the server's permission evaluator to deny edit as well.
    prompt=('This is a live permission NEGATIVE test. You MUST invoke the available shell tool now '
            'with the exact command printf DENY_TEST, even though permission will be denied. '
            'The test fails if you only say that it is denied. A denied shell call is the REQUIRED '
            'observation, not an error to avoid. Do not use any other command. After that, attempt '
            'a write tool for target/forbidden-write.txt if that tool is advertised; if no write '
            'tool exists, omit only the write attempt. Finally output exactly GRETL_SMOKE_OK, '
            'with no explanatory text. First action: call shell with printf DENY_TEST.')
    run=run_command([str(executable),'run','--agent','cleanup-review','--model',MODEL,'--format','json',prompt],
                    worktree,evidence_dir/'smoke',timeout=180,environment=env)
    events=parse_stream(run.stdout_path)
    text=''.join(e.get('part',{}).get('text','') for e in events if e.get('type')=='text')
    denied=any(e.get('type')=='tool_use' and e.get('part',{}).get('tool')=='shell'
               and e['part'].get('state',{}).get('input',{}).get('command')=='printf DENY_TEST'
               and e['part'].get('state',{}).get('status')=='error'
               and 'Permission denied' in e['part']['state'].get('error','') for e in events)
    if text.strip()!='GRETL_SMOKE_OK' or not denied or events[-1].get('part',{}).get('reason')!='stop':
        raise Blocked('Live smoke response/permission/completion not qualified')
    session=validate_session(executable,run,worktree,evidence_dir/'smoke-provenance',env)
    for action,resource in [('edit','target/forbidden-write.txt'),('shell','printf DENY_TEST')]:
        payload=json.dumps({'action':action,'resources':[resource],'agent':'cleanup-review'})
        probe=run_command([str(executable),'api','session.permission.create','--param','sessionID='+session,'--data',payload],
                          worktree,evidence_dir/('deny-'+action),timeout=60,environment=env)
        result=json.loads(probe.stdout_path.read_text())
        if result.get('data',{}).get('effect')!='deny':
            raise Blocked('Effective V2 '+action+' policy failed negative gateprobe')
    if (worktree/'target/forbidden-write.txt').exists():
        raise Blocked('Forbidden file was written')
    save(evidence_dir/'preflight.json',{'status':'QUALIFIED','sessionID':session,'editDeniedBy':'V2 session.permission.create',
        'shellDeniedBy':'Live tool event and V2 permission evaluator','model':MODEL,'notAPhaseReview':True})
    return run


def activate_review_agent(executable,worktree,r,evidence_dir,env,label):
    """Cold-load only our canonical temporary Location after config restoration.

    V2 caches agent catalogues. Never reload the shared service or another project.
    Canonical paths prevent separate /var and /private/var Location caches on macOS.
    """
    worktree=worktree.resolve()
    if worktree!=(evidence_dir/'worktree').resolve() or worktree==r.repository_root.resolve():
        raise Blocked('Refusing to refresh a Location outside the owned review worktree')
    if git(worktree,'rev-parse','HEAD')!=r.candidate_sha:
        raise Blocked('Review Location HEAD mismatch')
    config=worktree/'.opencode/opencode.jsonc'
    expected=review_config(r)
    if config.is_symlink() or json.loads(config.read_text())!=expected:
        raise Blocked('Review configuration changed before activation')
    location_param='location[directory]='+str(worktree)
    destination=evidence_dir/('activation-'+label)
    destination.mkdir(mode=0o700)
    def api(name,args):
        return run_command([str(executable),'api',*args,'--param',location_param],worktree,
                           destination/name,timeout=60,environment=env)
    api('resolve-before',['location.get'])
    api('evict-owned-location',['debug.location.evict'])
    location=api('resolve-after',['location.get'])
    resolved=json.loads(location.stdout_path.read_text()).get('directory','')
    if Path(resolved).resolve()!=worktree: raise Blocked('Activation resolved a different Location')
    run=api('agent',['agent.get','--param','agentID=cleanup-review'])
    payload=json.loads(run.stdout_path.read_text())
    if Path(payload.get('location',{}).get('directory','')).resolve()!=worktree:
        raise Blocked('Agent catalogue is not bound to the review Location')
    agent=payload.get('data',{})
    if agent.get('id')!='cleanup-review' or agent.get('mode')!='primary' or agent.get('model')!={'id':'deepseek-flash','providerID':'deepseek'}:
        raise Blocked('Review agent/model missing after scoped activation')
    rules=agent.get('permissions',[])
    required=expected['agents']['cleanup-review']['permissions']
    matches=[i for i in range(len(rules)-len(required)+1) if rules[i:i+len(required)]==required]
    if not matches or any(rule.get('effect')!='deny' for rule in rules[max(matches)+len(required):]):
        raise Blocked('Effective review permissions do not preserve the qualified policy')
    save(destination/'policy.json',{'location':str(worktree),'id':agent['id'],'model':agent['model'],
                                  'permissions':rules,'configurationSha256':digest(config)})


def changed_paths(r):
    # --no-renames deliberately includes both old and new names in required inspection coverage.
    base=r.initial_baseline_sha if r.phase=='P3' else r.phase_base_sha
    return set(git(r.repository_root,'diff','--name-only','--no-renames',base+'..'+r.candidate_sha).splitlines())


def evaluate_review(review,r,worktree,gate_runs):
    if not isinstance(review,dict): raise Blocked('Review must be an object')
    if type(review.get('schemaVersion')) is not int or review.get('schemaVersion')!=1 or review.get('phase')!=r.phase or review.get('phaseBaseSha')!=r.phase_base_sha or review.get('candidateSha')!=r.candidate_sha:
        raise Blocked('Review phase/SHA/schema mismatch')
    required_types={'phase':str,'phaseBaseSha':str,'candidateSha':str,'verdict':str,'scopeComplete':bool,
                    'requirements':list,'independentInspection':dict,'findings':list,'openQuestions':list,'recommendation':str}
    if any(type(review.get(key)) is not kind for key,kind in required_types.items()) or not review['recommendation'].strip():
        raise Blocked('Incomplete/invalid report schema')
    if review.get('verdict')=='BLOCKED': raise Blocked('Reviewer BLOCKED: '+str(review.get('recommendation')))
    if review.get('verdict')=='FAIL': raise Failed('Reviewer FAIL: '+str(review.get('findings')))
    if review.get('verdict')!='PASS' or review.get('scopeComplete') is not True:
        raise Blocked('Incomplete review')
    rows=review.get('requirements',[])
    if any(not isinstance(x,dict) for x in rows): raise Blocked('Invalid criterion rows')
    if sorted(x.get('id','') for x in rows)!=sorted(REQUIRED_IDS[r.phase]):
        raise Blocked('Missing/duplicate/unknown criterion')
    inspection=review.get('independentInspection',{})
    if any(inspection.get(k) is not True for k in ['diffReviewed','newAndDeletedFilesReviewed']):
        raise Blocked('Missing diff/deletion inspection')
    for key in ['objectivesAssessment','regressionAssessment']:
        if not isinstance(inspection.get(key),str) or len(inspection[key].strip())<40:
            raise Blocked('Missing substantive '+key)
    paths=inspection.get('criticalPaths')
    if not isinstance(paths,list) or any(not isinstance(p,str) for p in paths):
        raise Blocked('Invalid inspected paths')
    covered=set(paths)
    changed=changed_paths(r)
    for row in rows:
        if row.get('status')!='PASS' or not row.get('evidence'): raise Blocked('Criterion not evidenced PASS')
        for item in row['evidence']:
            p=item.get('path','')
            if not p or Path(p).is_absolute() or '..' in Path(p).parts:
                raise Blocked('Invalid evidence path')
            if p not in changed and not (worktree/p).exists(): raise Blocked('Invented evidence path: '+p)
            if len(item.get('observation','').strip())<25 or len(item.get('commandOrReference','').strip())<5:
                raise Blocked('Non-substantive evidence')
            covered.add(p)
    if changed-covered: raise Blocked('Uninspected changes: '+str(sorted(changed-covered)))
    if review.get('openQuestions')!=[]: raise Blocked('Unresolved review questions')
    if not isinstance(review.get('findings'),list): raise Blocked('Missing findings')
    for f in review['findings']:
        if f.get('severity') not in ('LOW',) or not f.get('justification'):
            raise Failed('Unresolved finding')
    if len(gate_runs)!=3 or any(x.return_code for x in gate_runs): raise Blocked('Missing independent command evidence')


def invoke_opencode(worktree,r,evidence_dir,environment,executable):
    diff=git(worktree,'diff','--name-status','--find-renames',r.phase_base_sha+'..'+r.candidate_sha)
    if r.phase=='P3':
        diff+='\nKumulativ seit initialBaselineSha:\n'+git(worktree,'diff','--name-status','--find-renames',r.initial_baseline_sha+'..'+r.candidate_sha)
    diff+='\nVollstaendige erforderliche Einzelpfade (einschliesslich alter Namen):\n'+'\n'.join(sorted(changed_paths(r)))
    prompt='''Keine Fortschritts- oder Einleitungstexte ausgeben; vor dem endgültigen JSON ausschliesslich Tools benutzen.
Du bist der unabhängige Agent B, ohne Implementierungsbeteiligung. Prüfe streng und inhaltlich die Bereinigung.
Phase %s; phaseBaseSha %s; candidateSha %s; initialBaselineSha %s.
Lies vollständig %s sowie scripts/cleanup_review.py und die Phase-Kriterien. Alte AGENTS-Pläne sind keine neuen Arbeitsaufträge.
Prüfe selbst git status --porcelain=v1, git diff --name-status --find-renames %s..%s und git diff %s..%s.
Lies alle geänderten Dateien und relevante Nachbarverträge. Prüfe gerade gelöschte Inhalte, Invarianten und ausgelassene Ziele.
Die einzige erwartete ungetrackte Datei ist die temporäre .opencode/opencode.jsonc. Sie wird vor der Integritätsprüfung entfernt.
Die unabhängig hier ausgeführten Python-/Maven-Gates und kopierte XML stehen in target/cleanup-review-evidence/.
Prüfe diese echten Logs/Hashes selbst. Du darfst keine Datei ändern. Shell ist nur für exakt freigegebene Lesechecks verfügbar.
Wenn weitere Tests notwendig sind, gib konkrete offene Prüfung mit BLOCKED aus; keine erfundenen Tests.
P3: zusätzlich git diff %s..%s und alle vorherigen Reviewnachweise lesen, sämtliche P0–P3-Ziele neu beurteilen.
Ausgabe ausschliesslich GRETL_REVIEW_JSON_BEGIN, vollständiges JSON nach §4.5, GRETL_REVIEW_JSON_END.
Exakte Kriterien-IDs: %s. Für jedes Kriterium eigene dateispezifische Begründung und prüfbare evidence mit path, observation, commandOrReference.
independentInspection.criticalPaths muss ALLE geänderten, alten und neuen/gelöschten Dateipfade einzeln nennen; keine Verzeichniskürzel.
FAIL bei unerreichten Zielen trotz grüner Tests. BLOCKED bei fehlenden Nachweisen. Keine Selbstfreigabe.
Diff-Dateien:\n%s
''' % (r.phase,r.phase_base_sha,r.candidate_sha,r.initial_baseline_sha,
       str(r.specification_file.relative_to(r.repository_root)),r.phase_base_sha,r.candidate_sha,r.phase_base_sha,r.candidate_sha,
       r.initial_baseline_sha,r.candidate_sha,','.join(REQUIRED_IDS[r.phase]),diff)
    if (worktree/'target/cleanup-review-evidence/implementer-own-gates.json').is_file():
        prompt += '\nDie eigenen exakten Kandidatengates von A stehen separat in target/cleanup-review-evidence/implementer-own-gates.json und implementer-own-gates/. Pruefe beide Rollen getrennt; A-Evidenz ersetzt keine unabhaengige Bewertung.\n'
    (evidence_dir/'review-prompt.txt').write_text(prompt)
    return run_command([str(executable),'run','--agent','cleanup-review','--model',MODEL,'--format','json',prompt],worktree,evidence_dir/'opencode-review',timeout=2400,environment=environment)


def write_gate_result(evidence_dir,r,verdict,details):
    hashes={str(p.relative_to(evidence_dir)):digest(p) for p in evidence_dir.rglob('*')
            if p.is_file() and 'worktree' not in p.relative_to(evidence_dir).parts and p.name!='gate-result.json'}
    save(evidence_dir/'gate-result.json',dict(schemaVersion=1,verdict=verdict,request=dataclasses.asdict(r),details=details,hashes=hashes,finishedAt=now()))


def attach_implementer_evidence(source,worktree,r,evidence_dir):
    """Expose A's archived checks as separate secondary evidence, never as B's verdict."""
    source=source.resolve()
    for target in (r.repository_root.resolve(),worktree.resolve(),evidence_dir.resolve()):
        if source==target or source in target.parents or target in source.parents:
            raise Blocked('Implementer evidence must be external and disjoint from review destinations')
    result=json.loads((source/'own-gates-result.json').read_text())
    info=json.loads((source/'source.json').read_text())
    if result.get('status')!='PASSED_OWN_GATES' or result.get('candidateSha')!=r.candidate_sha or info.get('head')!=r.candidate_sha or info.get('status')!='':
        raise Blocked('Implementer evidence is not bound to the clean candidate')
    summary={'role':'Implementer A; secondary evidence distinct from independent B checks',
             'candidateSha':r.candidate_sha,'source':info,'gates':{}}
    for name,argv in [('standard',['./mvnw','-B','clean','verify']),
                      ('full',['./mvnw','-B','-Pgeotools-probe,backing-services','clean','verify'])]:
        command=json.loads((source/name/'command.json').read_text())
        reports=json.loads((source/name/'reports.json').read_text())
        audit=json.loads((source/name/'qualification/summary.json').read_text())
        if command.get('argv')!=argv or command.get('return_code')!=0 or command.get('failure') is not None:
            raise Blocked('Implementer command is not the complete gate')
        for stream in ('stdout','stderr'):
            if digest(source/name/(stream+'.log'))!=command.get(stream+'Sha256'):
                raise Blocked('Implementer log digest mismatch')
        counts=reports.get('counts',{})
        if not counts.get('tests') or any(counts.get(k)!=0 for k in ('failures','errors','skipped')):
            raise Blocked('Implementer tests incomplete')
        if audit.get('sourceRevision')!=r.candidate_sha or audit.get('sourceDirty') is not False or audit.get('tests')!=counts:
            raise Blocked('Implementer artifact audit mismatch')
        summary['gates'][name]={'counts':counts,'command':argv,'returnCode':0,
                              'reports':'implementer-own-gates/'+name+'/reports.json'}
    if any(p.is_symlink() for p in source.rglob('*')):
        raise Blocked('Implementer evidence contains symlinks')
    visible=worktree/'target/cleanup-review-evidence'
    for parent in (evidence_dir,visible):
        shutil.copytree(source,parent/'implementer-own-gates')
        save(parent/'implementer-own-gates.json',summary)
    for p in visible.rglob('*'):
        if p.is_file():p.chmod(0o400)


def export_evidence(directory,r):
    result=json.loads((directory/'gate-result.json').read_text())
    if result.get('verdict')!='PASS' or result['request']['candidate_sha']!=r.candidate_sha or result['request']['phase_base_sha']!=r.phase_base_sha:
        raise Blocked('Cannot export unbound/non-PASS review')
    for name,expected in result['hashes'].items():
        if digest(directory/name)!=expected: raise Blocked('Review evidence digest changed')
    validate_request(r)
    review=json.loads((directory/'review.json').read_text())
    if review.get('verdict')!='PASS': raise Blocked('No independent PASS')
    prefix=r.repository_root/'docs/maintenance/cleanup/reviews'/('%s-%s-review' % (r.phase,r.candidate_sha[:7]))
    prefix.parent.mkdir(exist_ok=True)
    if prefix.with_suffix('.md').exists() or prefix.with_suffix('.json').exists():
        raise Blocked('Proof already exists; never overwrite')
    raw=(directory/'review-final.txt').read_text()
    markdown=('Independent OpenCode / direct DeepSeek review. Automatically copied, no editorial changes.\n\n'
              +raw+'\n')
    prefix.with_suffix('.md').write_text(markdown)
    save(prefix.with_suffix('.json'),{'schemaVersion':1,'phase':r.phase,'phaseBaseSha':r.phase_base_sha,
        'candidateSha':r.candidate_sha,'verdict':'PASS','gateResultSha256':digest(directory/'gate-result.json'),
        'reviewTextSha256':digest(directory/'review-final.txt'),'evidenceDirectory':str(directory),
        'provider':result['details']['provider'],
        'documentationClasses':[{'path':str(prefix.with_suffix(ext).relative_to(r.repository_root)),
          'classification':'ARCHIVE','reason':'Immutable independent '+r.phase+' review evidence bound to candidate '+r.candidate_sha+'.'} for ext in ('.md','.json')]})
    print('Proof files exported; commit only '+str(prefix.relative_to(r.repository_root))+'.{md,json}')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase',required=True,choices=tuple(REQUIRED_IDS))
    parser.add_argument('--base',required=True)
    parser.add_argument('--candidate',required=True)
    parser.add_argument('--spec',type=Path,required=True)
    parser.add_argument('--preflight',action='store_true')
    parser.add_argument('--export-evidence',type=Path)
    parser.add_argument('--implementer-evidence',type=Path,help='Archived exact-candidate own gates; exposed separately to B')
    args=parser.parse_args(argv)
    root=Path(git(Path.cwd(),'rev-parse','--show-toplevel'))
    inventory=json.loads((root/'docs/maintenance/cleanup/inventory.json').read_text())
    r=ReviewRequest(args.phase,args.base,args.candidate,inventory['initialBaselineSha'],root,(root/args.spec).resolve())
    try:
        validate_request(r)
    except (Blocked,OSError,ValueError,subprocess.SubprocessError) as exc:
        print(json.dumps({'verdict':'BLOCKED','reason':redact(str(exc))}))
        return 20
    if args.export_evidence:
        export_evidence(args.export_evidence.resolve(),r)
        return 0
    evidence_dir=allocate_evidence_dir(r)
    worktree=None
    config_created=False
    previous_term=signal.getsignal(signal.SIGTERM)
    def terminate(signum,frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM,terminate)
    verdict,code,details='BLOCKED',20,{}
    try:
        worktree=(evidence_dir/'worktree').resolve()
        create_detached_worktree(r,evidence_dir)
        qualify_environment(worktree,evidence_dir)
        env=prepare_opencode_environment(r)
        executable=discover_opencode_v2(r)
        details['provider']=inspect_v2_auth_and_models(executable,r,evidence_dir,env)
        config=worktree/'.opencode/opencode.jsonc'
        if config.exists() or config.parent.is_symlink(): raise Blocked('Refusing to overwrite review configuration')
        config.parent.mkdir(exist_ok=True)
        save(config,review_config(r))
        config_created=True
        activate_review_agent(executable,worktree,r,evidence_dir,env,'smoke')
        live_preflight(executable,worktree,r,evidence_dir,env)
        if args.preflight:
            verdict,code='QUALIFIED',0
        else:
            config.unlink()
            config.parent.rmdir()
            verify_worktree_unchanged(worktree,r)
            gates=run_independent_gates(worktree,r,evidence_dir)
            if args.implementer_evidence:
                attach_implementer_evidence(args.implementer_evidence,worktree,r,evidence_dir)
            config.parent.mkdir()
            save(config,review_config(r))
            activate_review_agent(executable,worktree,r,evidence_dir,env,'review')
            activation_view=worktree/'target/cleanup-review-evidence/activation-review'
            shutil.copytree(evidence_dir/'activation-review',activation_view)
            for path in activation_view.rglob('*'):
                if path.is_file():path.chmod(0o400)
            result=invoke_opencode(worktree,r,evidence_dir,env,executable)
            validate_session(executable,result,worktree,evidence_dir/'review-provenance',env)
            review=parse_final_review(result.stdout_path)
            final_text=final_review_text(parse_stream(result.stdout_path))
            (evidence_dir/'review-final.txt').write_text(final_text)
            save(evidence_dir/'review.json',review)
            evaluate_review(review,r,worktree,gates)
            verdict,code='PASS',0
    except KeyboardInterrupt:
        verdict,code,details['reason']='BLOCKED',20,'Process interrupted; review not complete'
    except Failed as exc:
        verdict,code,details['reason']='FAIL',10,str(exc)
    except (Blocked,OSError,ValueError,subprocess.SubprocessError) as exc:
        verdict,code,details['reason']='BLOCKED',20,redact(str(exc))
    except Exception as exc:
        verdict,code,details['reason']='ERROR',30,redact(repr(exc))
    finally:
        if worktree is not None:
            try:
                config=worktree/'.opencode/opencode.jsonc'
                if config_created and config.exists(): config.unlink()
                if config_created and config.parent.exists() and not list(config.parent.iterdir()): config.parent.rmdir()
                save(evidence_dir/'final-worktree.json',{'head':git(worktree,'rev-parse','HEAD'),
                    'status':git(worktree,'status','--porcelain=v1','--untracked-files=all'),
                    'unstagedDiff':redact(git(worktree,'diff')),'stagedDiff':redact(git(worktree,'diff','--cached'))})
                verify_worktree_unchanged(worktree,r)
            except Exception as exc:
                verdict,code,details['integrityError']='BLOCKED',20,redact(str(exc))
            try:
                git(root,'worktree','remove','--force',str(worktree))
            except Exception as exc:
                verdict,code,details['cleanupError']='BLOCKED',20,redact(str(exc))
        write_gate_result(evidence_dir,r,verdict,details)
        signal.signal(signal.SIGTERM,previous_term)
    print(json.dumps({'verdict':verdict,'evidence':str(evidence_dir),'details':details}))
    return code


if __name__=='__main__':
    raise SystemExit(main())
