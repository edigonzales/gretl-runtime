import pathlib,json,hashlib,shutil,zipfile,io
r=pathlib.Path('/Users/stefan/.cache/gretl-tooling-review/P8-candidate-1-gui'); repo=pathlib.Path('/Users/stefan/sources/gretl-runtime/vscode/gretl-vscode'); dest=r/'catalog-canary';dest.mkdir()
for n in ['dist','server']:shutil.copytree(repo/n,dest/'correct'/n)
bundle=json.loads((dest/'correct/server/bundle-manifest.json').read_text());assert bundle['sourceCommit']=='54bb123070fc7bd81aea58b4e1191beecf568cbf';assert not bundle['sourceDirty']
def rewrite(source,replacements):
 out=io.BytesIO()
 with zipfile.ZipFile(io.BytesIO(source)) as src,zipfile.ZipFile(out,'w') as dst:
  assert all(n in src.namelist() for n in replacements)
  for item in src.infolist():dst.writestr(item,replacements.get(item.filename,src.read(item.filename)))
 return out.getvalue()
jar=(dest/'correct/server/gretl-lsp.jar').read_bytes()
with zipfile.ZipFile(io.BytesIO(jar)) as z:catalog=json.loads(z.read('operations.json'))
catalog['schemaVersion']=999
wrongjar=rewrite(jar,{'operations.json':(json.dumps(catalog,separators=(',',':'))+'\n').encode()})
wronghash=hashlib.sha256(wrongjar).hexdigest();bundle['files']['gretl-lsp.jar']=wronghash;bundle['payloadSha256']['server/gretl-lsp.jar']=wronghash
vsix=(dest/'correct/dist/gretl-runtime.vsix').read_bytes();wrongvsix=rewrite(vsix,{'extension/server/gretl-lsp.jar':wrongjar,'extension/server/bundle-manifest.json':(json.dumps(bundle,indent=2)+'\n').encode()});(dest/'wrong-catalog.vsix').write_bytes(wrongvsix)
(dest/'mutation.json').write_text(json.dumps({'kind':'DELIBERATELY_INVALID_CATALOG_GUI_CANARY','baseCandidate':bundle['sourceCommit'],'mutation':'Embedded operations.json schemaVersion 1 -> 999. Package JAR digests updated so the real server catalog loader, rather than a byte mismatch, must reject this input.','originalVsixSha256':hashlib.sha256(vsix).hexdigest(),'wrongVsixSha256':hashlib.sha256(wrongvsix).hexdigest(),'originalJarSha256':hashlib.sha256(jar).hexdigest(),'wrongJarSha256':wronghash},indent=2)+'\n')
print('Prepared isolated wrong-catalog VSIX and untouched current candidate package.')
