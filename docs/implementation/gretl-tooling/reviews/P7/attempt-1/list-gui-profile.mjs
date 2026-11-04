import {readdir} from 'node:fs/promises';
import path from 'node:path';
const root='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/profile';
async function walk(dir,depth=0,out=[]){
  if(depth>3)return out;
  let entries;try{entries=await readdir(dir,{withFileTypes:true});}catch(error){out.push({path:dir,error:error.code});return out;}
  out.push({path:dir,entries:entries.map(e=>e.name+(e.isDirectory()?'/':''))});
  for(const entry of entries)if(entry.isDirectory()&&['extensions','extensions.json','User','globalStorage','CachedExtensionVSIXs'].includes(entry.name))await walk(path.join(dir,entry.name),depth+1,out);
  return out;
}
console.log(JSON.stringify(await walk(root),null,2));
