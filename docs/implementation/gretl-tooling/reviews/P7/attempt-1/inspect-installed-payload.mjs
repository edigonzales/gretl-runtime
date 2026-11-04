import {createHash} from 'node:crypto';
import {readFile,readdir,stat,writeFile} from 'node:fs/promises';
import path from 'node:path';
const profile='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/profile';
const extensions='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/extensions';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const entries=await readdir(extensions,{withFileTypes:true});
const dirs=entries.filter(e=>e.isDirectory()&&e.name.startsWith('edigonzales.gretl-runtime-vscode')).map(e=>path.join(extensions,e.name));
const installed=[];
for(const dir of dirs){
  const record={dir,found:{}};
  for(const rel of ['package.json','dist/extension.cjs','dist/modeler/view.js','dist/modeler/view.css','dist/modeler/elk-worker.min.js','server/gretl-lsp.jar','server/.gretl-server-sha256']){
    const file=path.join(dir,rel);
    try{
      const bytes=await readFile(file);
      record.found[rel]={path:file,size:bytes.length,sha256:hash(bytes),mtime:(await stat(file)).mtime.toISOString()};
    }catch(error){
      record.found[rel]={path:file,error:error.code};
    }
  }
  try{record.package=JSON.parse(await readFile(path.join(dir,'package.json'),'utf8'));}catch{}
  installed.push(record);
}
const expected={
  server:'f6acda578e81d806da9a016bdfdad00c67ee35a2640bfee21daa4845f63d0465',
  extensionCjs:'d8ec44b3a2c763cca290bc24bb05909b4a564c0055d992b9d032b9ffe020b228',
  viewJs:'e5559c178eb14bdeee388be2e8d68a6042cc5132848e05369d914ad71b4b922f',
  viewCss:'a44a75d325d22412cad5319b2f69070ba1dfe454cc063a5be6005b18bdafe156',
  worker:'dca3a19d9ec37f5634bf5876b4ebd2455c3f43f0d32570136c13c13edaba9bfb'
};
for(const record of installed){
  record.checks={
    server:record.found['server/gretl-lsp.jar']?.sha256===expected.server,
    extensionCjs:record.found['dist/extension.cjs']?.sha256===expected.extensionCjs,
    viewJs:record.found['dist/modeler/view.js']?.sha256===expected.viewJs,
    viewCss:record.found['dist/modeler/view.css']?.sha256===expected.viewCss,
    worker:record.found['dist/modeler/elk-worker.min.js']?.sha256===expected.worker
  };
  record.passed=Object.values(record.checks).every(Boolean);
}
const report={generatedAt:new Date().toISOString(),candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',profile,extensions,installed,expected,passed:installed.length===1&&installed[0].passed};
await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/installed-payload-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
if(!report.passed)process.exitCode=1;
