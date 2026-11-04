from pathlib import Path
import json,hashlib,subprocess,zipfile,shutil
r=Path('/Users/stefan/.cache/gretl-tooling-review/P8-candidate-1-gui');cache=r.parent;repo=Path('/Users/stefan/sources/gretl-runtime');identity=json.loads((r/'final-identity.json').read_text());bundle=identity['product']['bundle'];sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
ext=r/'extensions/edigonzales.gretl-runtime-vscode-0.1.0';checks=[]
with zipfile.ZipFile(identity['product']['vsix']) as z:expected=json.loads(z.read('extension/package.json'))
installed=json.loads((ext/'package.json').read_text());installed.pop('__metadata',None);assert installed==expected
for f,h in bundle['payloadSha256'].items():
 if f=='package.json':continue
 assert sha(ext/f)==h,f
 checks.append({'path':str(ext/f),'sha256':h})
rows=[x.strip() for x in subprocess.check_output(['ps','-axo','pid=,ppid=,rss=,command='],text=True).splitlines() if '/P8-candidate-1-gui/extensions/' in x and 'gretl-lsp.jar' in x and 'java' in x];assert len(rows)==1
(r/'final-installed-verification.json').write_text(json.dumps({'passed':True,'candidate':bundle['sourceCommit'],'vsixSha256':identity['product']['sha256'],'serverSha256':bundle['files']['gretl-lsp.jar'],'installedPayloads':checks,'packageJsonEqualAfterRemovingOnlyVsCodeMetadata':True,'actualSingleServerProcess':rows[0]},indent=2)+'\n')
produced=r/'generated-original';produced.mkdir();shutil.copyfile(r/'workspace ä space/project-created/job.groovy',produced/'job.groovy');guihash=sha(produced/'job.groovy');fixture=repo/'gretl-integration-tests/src/test/resources/jobs/modeler-created/job.groovy';assert guihash==sha(fixture)=='7a551b46f463b2c483c1936b18fbd7f4a5d9c530e481e1f2834842c9be3b7782'
runtime=json.loads((cache/'p8-c2-final-runtime-canary/qualification.json').read_text());assert runtime['passed'] and runtime['candidateSha']==bundle['sourceCommit'];control=runtime['results'][0];assert control['inputSha256']==guihash and control['oracleExitCode']==control['runtimeExitCode']==0
(r/'G25-exact-runtime-input-identity.json').write_text(json.dumps({'passed':True,'actualGuiSource':str(produced/'job.groovy'),'guiSourceSha256':guihash,'originalRuntimeFixture':str(fixture),'fixtureSha256':sha(fixture),'runtimeOracleQualification':str(cache/'p8-c2-final-runtime-canary/qualification.json'),'actualScriptExecution':control['execution'],'runtimeMode':control['mode'],'runtimeExitCode':control['runtimeExitCode'],'note':'Actual native P8-created saved bytes equal the unchanged original Runtime fixture bytes. Parameter URL override changes no Groovy input.'},indent=2)+'\n')
cases={
'G01':['G01-final-package-ready'],
'G02':['G02-operation-suggestions-trigger','G02-operation-accepted','G02-field-suggestions','G02-field-accepted','G02-enum-suggestions','G02-enum-accepted','G02-parameter-suggestions','G02-parameter-accepted'],
'G03':['G03-operation-hover-confirmed','G03-field-hover','G03-output-hover-inside','G03-signature'],
'G04':['G04-output-problem','G04-output-navigation','G04-german-output-diagnostic','G04-language-and-source-restored'],
'G05':['G05-result-definition','G05-task-references','G05-parameter-definition','G05-subproject-definition'],
'G06':['G06-rename','G06-rename-undo-verified','G06-rename-redo-verified'],
'G07':['G07-required-dependency-fix-menu','G07-applied-postimage'],
'G08':['G08-outline','G08-dirty-subproject-sql','G08-dirty-settings-problems'],
'G09':['G09-final-dirty-before-restart','G09-final-dirty-after-restart','G09-final-postrestart-completion','G09-final-postrestart-native-rename','G09-final-restored'],
'G10':['G10-untrusted','G10-missing-java-path','G10-missing-bundle-path','G10-valid-paths-restored'],
'G11':['G11-G12-main-graph','G11-pinned-main-other-editor'],
'G12':['G11-G12-main-graph','G12-inner-hidden','G12-inner-visible'],
'G13':['G13-before-drag','G13-after-native-drag-actual-coordinates','G13-task-filter','G13-final-before-pan','G13-final-after-pan'],
'G14':['G14-node-source','G14-text-to-graph-confirmed','G14-result-edge-source'],
'G15':['G15-recovery-baseline','G15-isolated-dirty-partial','G15-native-save-prompt','G15-isolated-panel-closed','G15-isolated-dirty-reopened'],
'G16':['G16-long-title-dark','G16-long-title-light','G16-long-title-high-contrast','G16-empty-graph','G16-all-owned-panels-closed','G17-two-tasks-created'],
'G17':['G17-native-empty-file','G17-empty-modeler','G17-parameter-created','G17-download-created','G17-two-tasks-created'],
'G18':['G18-typed-config-draft','G18-typed-config-applied','G18-typed-config-undo','G18-secret-draft-before-cancel','G18-cancel-unchanged','G18-nested-list-map-exact-draft','G18-nested-list-map-applied','G18-nested-list-map-undo'],
'G19':['G19-pointer-after-draft','G19-after-only-created','G19-cycle-draft','G19-cycle-rejected'],
'G20':['G20-binding-draft','G20-result-plus-data-edge','G20-default-saved'],
'G21':['G21-referenced-delete-disabled','G21-modeler-rename-four-references','G21-unused-before-delete-confirm','G21-unused-delete-confirmed'],
'G22':['G22-last-data-path-rejected','G22-transitive-direct-disconnect-allowed','G22-transitive-last-path-rejected'],
'G23':['G23-opaque-action-details','G23-simple-field-edited'],
'G24':['G24-undo-defaults','G24-undo-binding-and-dependency','G24-redo-both','G24-view-before','G24-view-dragged','G24-view-undone','G24-view-redone'],
'G25':['G25-before-close','G25-job-closed','G25-text-reopened','G25-modeler-reopened'],
'G26':['G26-two-native-edits-confirmed','G26-other-task-definition-confirmed','G26-other-subproject-definition-confirmed','G26-current-other-root-graph'],
'G27':['G27-settings-before-conflict','G27-external-settings-conflict','G27-stale-delete-draft-after-native-history'],
'G28':['G28-dirty-before-stop','G28-server-stopped-stale','G28-dirty-after-restart','G28-readonly-source','G28-readonly-edit-rejected','G28-wrong-catalog-blocked','G28-wrong-catalog-output','G28-correct-catalog-restored'],
'G29':['G29-large-500-opened','G29-large-latest-dirty-source','G29-large-latest-selected','G29-all-panels-closed']}
for case,names in cases.items():
 for n in names:
  m=json.loads((r/'cases'/f'{n}.json').read_text());assert sha(r/'cases'/m['image'])==m['imageSha256'];assert sha(r/'cases'/f'{n}.ax.txt')==m['axSha256']
index={'state':'IMPLEMENTER_G01_G29_COMPLETE_G30_INDEPENDENT_REVIEW_REQUIRED','candidateSha':bundle['sourceCommit'],'finalVsixSha256':identity['product']['sha256'],'finalServerSha256':bundle['files']['gretl-lsp.jar'],'platform':'macOS 27 arm64, VS Code 1.144.0, private profile','cases':cases,'G30':'Must be authored and executed by the independent reviewer through the native bridge. No prefilled acceptance.','provenance':['All P8 raw images retain their actual B5/C2-standard/C2-final package identities. No relabelling.','C1-C2 and standard-final effective-content comparison records prove all four installed client payloads and Java class/resource data identical; only catalogue-manifest JSON member ordering differs in JAR entries. Whole-JAR byte identity is not claimed.','G15 preserves the native Save-on-close prompt and then reopens the saved broken source. It does not claim the reopened saved document stays dirty.','Earlier ineffective keyboard, selection and unconfirmed-delete attempts remain separate original observations and are not accepted positive cases.','Node coordinates use screenshot pixels, not half-sized retina coordinates. Actual geometry changes and UTF-16 snapshot hashes are checked.','The private Electron HTTP proxy is configured to a closed loopback port. This is no claim of OS-wide network isolation. Linux server qualification separately uses Docker network none.'],'supplemental':['verified-progress.json','final-installed-verification.json','G25-exact-runtime-input-identity.json','G23-byte-preservation.json','G26-buffer-isolation.json','C1-C2-effective-content-comparison.json','standard-final-effective-content-comparison.json']}
(r/'qualification.json').write_text(json.dumps(index,indent=2)+'\n');print(json.dumps({'indexedGuiCases':len(cases),'rawPositiveCaptures':sum(map(len,cases.values())),'finalServerPid':rows[0].split()[0],'generatedInputSha256':guihash}))
