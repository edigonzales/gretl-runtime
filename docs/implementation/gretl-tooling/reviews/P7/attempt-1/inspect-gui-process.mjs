import {spawnSync} from 'node:child_process';
import {readdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
const ps=spawnSync('/bin/ps',['-axo','pid=,command='],{encoding:'utf8',maxBuffer:16*1024*1024});
const lines=ps.stdout.split('\n').filter(line=>line.includes('Visual Studio Code.app/Contents/MacOS/Electron')||line.includes('P7-candidate-3-gui')||line.includes('gretl-runtime-vscode'));
const root='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui';
let entries=[];try{entries=await readdir(root,{withFileTypes:true});}catch{}
const report={generatedAt:new Date().toISOString(),processes:lines,rootEntries:entries.map(e=>({name:e.name,kind:e.isDirectory()?'directory':'file'})),exit:ps.status};
await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/gui-process-inspection.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
