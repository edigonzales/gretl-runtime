// Test driver only. Never packaged with the product VSIX. Native GUI actions
// remain the authority for visible interactions; these API results supplement them.
const vscode=require('vscode');
const fs=require('node:fs/promises');
const path=require('node:path');
let lastOffered;
exports.activate=function(context){
 const directory=process.env.GRETL_GUI_EVIDENCE||vscode.workspace.getConfiguration('gretlEvidence').get('directory');
 if(!directory)throw Error('GRETL_GUI_EVIDENCE must name a private evidence directory');
 let sequence=0;
 context.subscriptions.push(vscode.commands.registerCommand('gretlEvidence.captureBuffers',async()=>{
  const {createHash}=require('node:crypto');
  const result={time:new Date().toISOString(),readOnly:true,documents:vscode.workspace.textDocuments.filter(d=>d.uri.scheme==='file'&&vscode.workspace.getWorkspaceFolder(d.uri)).map(d=>({uri:d.uri.toString(),version:d.version,dirty:d.isDirty,text:d.getText(),sha256:createHash('sha256').update(d.getText(),'utf8').digest('hex')})),activeEditor:vscode.window.activeTextEditor?.document.uri.toString(),selection:vscode.window.activeTextEditor?.selection};
  await fs.mkdir(directory,{recursive:true});
  const filename=path.join(directory,`buffers-${Date.now()}.json`);
  result.modelers=await vscode.commands.executeCommand('gretlRuntime.inspectModeler');
  await fs.writeFile(filename,JSON.stringify(result,null,2)+'\n');
  vscode.window.setStatusBarMessage(`GRETL buffer evidence saved: ${path.basename(filename)}`,5000);
 }));
 context.subscriptions.push(vscode.commands.registerCommand('gretlEvidence.capture',async()=>{
  const editor=vscode.window.activeTextEditor;
  if(!editor)throw Error('No active text editor');
  const doc=editor.document,uri=doc.uri,position=editor.selection.active;
  const product=vscode.extensions.getExtension('edigonzales.gretl-runtime-vscode');
  const result={time:new Date().toISOString(),sequence:++sequence,extension:{id:product?.id,version:product?.packageJSON.version,path:product?.extensionPath,active:product?.isActive},trusted:vscode.workspace.isTrusted,
   document:{uri:uri.toString(),language:doc.languageId,version:doc.version,dirty:doc.isDirty,text:doc.getText()},position,
   documents:vscode.workspace.textDocuments.map(d=>({uri:d.uri.toString(),language:d.languageId,version:d.version,dirty:d.isDirty})),
   diagnostics:vscode.languages.getDiagnostics().map(([u,ds])=>({uri:u.toString(),items:ds.map(d=>({code:d.code,message:d.message,range:d.range,severity:d.severity,source:d.source}))}))};
  for(const [name,command,args]of[
   ['completion','vscode.executeCompletionItemProvider',[uri,position]],
   ['hover','vscode.executeHoverProvider',[uri,position]],
   ['signature','vscode.executeSignatureHelpProvider',[uri,position]],
   ['definitions','vscode.executeDefinitionProvider',[uri,position]],
   ['references','vscode.executeReferenceProvider',[uri,position]],
   ['symbols','vscode.executeDocumentSymbolProvider',[uri]],
   ['actions','vscode.executeCodeActionProvider',[uri,new vscode.Range(position,position)]]
  ]){try{result[name]=await vscode.commands.executeCommand(command,...args);}catch(error){result[name]={error:String(error)};}}
  const offered=result.actions?.find(action=>action.command?.command==='gretlRuntime.applyCodeAction');
  if(offered)lastOffered={command:offered.command,uri:uri.toString(),version:doc.version};
  await fs.mkdir(directory,{recursive:true});
  const filename=path.join(directory,`capture-${String(sequence).padStart(3,'0')}.json`);
  await fs.writeFile(filename,JSON.stringify(result,null,2)+'\n');
  vscode.window.setStatusBarMessage(`GRETL evidence saved: ${path.basename(filename)}`,5000);
 }));
};

// Explicit host tests complement, and never replace, native workbench checks.
const assert=require('node:assert/strict');
const originalActivate=exports.activate;
exports.activate=function(context){
 originalActivate(context);
 context.subscriptions.push(vscode.commands.registerCommand('gretlEvidence.verifyProviders',async()=>{
  const directory=process.env.GRETL_GUI_EVIDENCE||vscode.workspace.getConfiguration('gretlEvidence').get('directory');
  const doc=vscode.window.activeTextEditor?.document;
  assert.ok(doc&&doc.languageId==='gretl-runtime'&&doc.uri.path.endsWith('/job.groovy'),'Open the root editor fixture');
  const text=doc.getText(),version=doc.version,uri=doc.uri;
  const at=(needle,delta=0,last=false)=>{const index=last?text.lastIndexOf(needle):text.indexOf(needle);assert.ok(index>=0,'Missing fixture anchor '+needle);return doc.positionAt(index+delta);};
  const run=async(command,...args)=>{
   let timer;try{return await Promise.race([vscode.commands.executeCommand(command,...args),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('Provider timeout: '+command)),10000);})]);}finally{clearTimeout(timer);}
  };
  const checks=[];
  const check=async(name,operation)=>{try{await operation();checks.push({name,passed:true});}catch(error){checks.push({name,passed:false,error:String(error)});}};
  const completions=async position=>(await run('vscode.executeCompletionItemProvider',uri,position))?.items??[];
  const has=(items,label)=>items.some(item=>(typeof item.label==='string'?item.label:item.label.label)===label);
  await check('installed product identity',async()=>{const product=vscode.extensions.getExtension('edigonzales.gretl-runtime-vscode');assert.ok(product?.isActive);assert.equal(product.packageJSON.version,'0.1.0');assert.ok(!product.extensionPath.includes('/sources/'));});
  await check('operation completion',async()=>assert.ok(has(await completions(at('HttpDownload',4)),'HttpDownload')));
  await check('field completion',async()=>assert.ok(has(await completions(at('url=',2)),'url')));
  await check('enum completion',async()=>assert.ok(has(await completions(at("'ZIP'",2)),"ZIP")));
  await check('parameter completion',async()=>assert.ok(has(await completions(at("parameter('sourceUrl')",14,true)),'sourceUrl')));
  for(const [label,needle,delta] of [['operation','HttpDownload',4],['field','url=',1],['result output',".file",2]])await check(label+' hover',async()=>assert.ok((await run('vscode.executeHoverProvider',uri,at(needle,delta)))?.length>0));
  await check('DSL signature help',async()=>assert.ok((await run('vscode.executeSignatureHelpProvider',uri,at('HttpDownload',4)))?.signatures?.length>0));
  for(const [label,needle,delta,last] of [['dependency',"dependsOn: 'download'",16,false],['result',"result('download')",10,false],['parameter',"parameter('sourceUrl')",14,true]])await check(label+' definition',async()=>{const positions=label==='dependency'?at(text.includes("dependsOn: 'download'")?"dependsOn: 'download'":"dependsOn:'download'",text.includes("dependsOn: 'download'")?15:14):at(needle,delta,last);const values=await run('vscode.executeDefinitionProvider',uri,positions);assert.equal(values?.length,1);assert.equal((values[0].uri??values[0].targetUri).toString(),uri.toString());});
  await check('qualified subproject definition',async()=>{const values=await run('vscode.executeDefinitionProvider',uri,at(":sub:child",5));assert.equal(values?.length,1);assert.ok((values[0].uri??values[0].targetUri).path.endsWith('/sub/job.groovy'));});
  await check('task references',async()=>assert.ok((await run('vscode.executeReferenceProvider',uri,at("task('download'",8)))?.length>=2));
  await check('hierarchical outline',async()=>{const symbols=await run('vscode.executeDocumentSymbolProvider',uri);for(const name of ['sourceUrl','download','extract','done'])assert.ok(symbols.some(symbol=>symbol.name===name),name);});
  await check('native rename edit offers all three literal references',async()=>{const edit=await run('vscode.executeDocumentRenameProvider',uri,at("task('download'",8),'renamedDownload');assert.equal(edit?.get(uri)?.length,3);assert.equal(doc.getText(),text);});
  await check('foreign Groovy and Gradle retain their language',async()=>{const folder=vscode.workspace.getWorkspaceFolder(uri);for(const name of ['foreign.groovy','build.gradle']){const foreign=await vscode.workspace.openTextDocument(vscode.Uri.joinPath(folder.uri,name));assert.equal(foreign.languageId,'groovy');}});
  await check('queries preserve the current buffer',async()=>{assert.equal(doc.version,version);assert.equal(doc.getText(),text);});
  const product=vscode.extensions.getExtension('edigonzales.gretl-runtime-vscode');
  const report={completedAt:new Date().toISOString(),kind:'HOST_PROVIDER_TESTS_NOT_GUI_ACCEPTANCE',product:{id:product?.id,path:product?.extensionPath,version:product?.packageJSON.version},uri:uri.toString(),bufferVersion:version,checks,passed:checks.every(check=>check.passed)};
  await fs.mkdir(directory,{recursive:true});const file=path.join(directory,'provider-tests-'+Date.now()+'.json');await fs.writeFile(file,JSON.stringify(report,null,2)+'\n');
  if(!report.passed)throw Error('Installed provider tests failed: '+checks.filter(check=>!check.passed).map(check=>check.name).join(', '));
  vscode.window.setStatusBarMessage('GRETL installed provider checks passed ('+checks.length+')',10000);
 }));
};

const activateProviders=exports.activate;
exports.activate=function(context){
 activateProviders(context);
 context.subscriptions.push(vscode.commands.registerCommand('gretlEvidence.verifyStaleQuickFix',async()=>{
  const directory=process.env.GRETL_GUI_EVIDENCE||vscode.workspace.getConfiguration('gretlEvidence').get('directory');
  const doc=vscode.window.activeTextEditor?.document;
  assert.ok(lastOffered&&doc&&doc.uri.toString()===lastOffered.uri,'Capture an offered quick fix before changing its buffer');
  const version=doc.version,text=doc.getText();assert.ok(version>lastOffered.version,'Change the buffer after capturing the offered fix');
  let rejection;
  try{await vscode.commands.executeCommand(lastOffered.command.command,...lastOffered.command.arguments);}catch(error){rejection=String(error);}
  const passed=!!rejection&&/buffer changed|source precondition/.test(rejection)&&doc.version===version&&doc.getText()===text;
  const report={completedAt:new Date().toISOString(),kind:'STALE_QUICK_FIX_HOST_TEST',uri:doc.uri.toString(),offeredVersion:lastOffered.version,currentVersion:version,rejection,unchanged:doc.getText()===text,passed};
  await fs.mkdir(directory,{recursive:true});await fs.writeFile(path.join(directory,'stale-quickfix-'+Date.now()+'.json'),JSON.stringify(report,null,2)+'\n');
  assert.ok(passed,'Stale offered fix must be rejected specifically for its changed source, without mutating the buffer');
  vscode.window.setStatusBarMessage('GRETL stale offered fix rejected without a buffer change',10000);
 }));
};
