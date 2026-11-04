import {cp,mkdir,writeFile,chmod} from 'node:fs/promises';
import path from 'node:path';

/** Controlled source inputs, never expected results inferred from the implementation. */
export async function prepareQualificationFixtures(workspace){
  const main=path.join(workspace,'project-main'),other=path.join(workspace,'project-other'),created=path.join(workspace,'project-created');
  await cp(main,other,{recursive:true,force:true});
  const put=async(relative,text)=>{const target=path.join(created,relative);await mkdir(path.dirname(target),{recursive:true});await writeFile(target,text);};
  await mkdir(created,{recursive:true});
  await put('inner.groovy',"action('innerDemo') { ctx.run(HttpDownload) { url='https://example.invalid/inner'; output=workPath('inner.zip') } }\n");
  for(const [prefix,key] of [['','root'],['sub/','sub']]){
    await put('sqlcase/'+prefix+'job.groovy',"task('query', DuckDbSqlExecutor) { inMemory=true; sqlFiles=[file('sql/query.sql')]; sqlParameters=["+key+":'ok'] }\n");
    await put('sqlcase/'+prefix+'sql/query.sql','select ${'+key+'};\n');
  }
  await put('sqlcase/settings.gretl',"include 'sub'\ndefaults ':query'\n");
  const marker=path.join(created,'MUST_NOT_EXECUTE').replaceAll('\\','\\\\').replaceAll("'","\\'");
  await put('complex/job.groovy',("// Preserve CRLF, comments and emoji 😀.\nparameter('sourceUrl', defaultValue:'https://example.invalid/a.zip')\ntask('download', HttpDownload) {\n    url=parameter('sourceUrl')\n    output=workPath('archive.zip')\n    overwrite=false // preserve this comment\n}\naction('opaque', dependsOn:'download') {\n    def greeting=\"hello ${'world'} 😀\"\n    new File('"+marker+"').text=greeting\n}\ndefaults 'opaque'\n").replaceAll('\n','\r\n'));
  await put('transitive/job.groovy',"task('producer', HttpDownload) { url='https://example.invalid/a.zip'; output=workPath('a.zip') }\naction('bridge', dependsOn:'producer') { println 'bridge' }\ntask('consumer', Extract, dependsOn:['producer','bridge'], after:'producer') { archive=result('producer').file; compression='ZIP'; outputDirectory=workPath('out') }\ndefaults 'consumer'\n");
  await put('readonly/job.groovy',"action('locked') { println 'read only' }\n");
  await chmod(path.join(created,'readonly/job.groovy'),0o444);
  await put('long.groovy',"action('long_"+'descriptive_name_'.repeat(12)+"') { println 'long title' }\n");
  await put('large.groovy',Array.from({length:500},(_,i)=>"action('node"+i+"'"+(i?", dependsOn:'node"+(i-1)+"'":"")+") { println "+i+" }").join('\n')+'\n');
  const openedWorkspace=path.join(workspace,'gretl-final-acceptance.code-workspace');
  await writeFile(openedWorkspace,JSON.stringify({folders:[{name:'GRETL main',path:'project-main'},{name:'Other GRETL',path:'project-other'},{name:'Created GRETL',path:'project-created'}]},null,2)+'\n');
  return openedWorkspace;
}
