import * as vscode from 'vscode';
import {spawn,ChildProcess} from 'node:child_process';
import {once} from 'node:events';
import {randomUUID} from 'node:crypto';
import {endOwnedProcess} from './process';
import {LanguageClient,CloseAction,ErrorAction,State,RenameRequest,CodeActionRequest,DocumentSymbolRequest,DocumentSymbol as ProtocolSymbol,TextDocumentEdit,WorkspaceEdit as ProtocolEdit,CodeAction as ProtocolAction} from 'vscode-languageclient/node';
import {checkBundle,checkJava,languageConfiguration,localHost,sourceDigest} from './host';

import {Stamp,validateEditSources} from './edit-guard';
import {currentRead} from './read-query';
import {ModelerBackend,ModelerOptions} from './modeler/backend';
import {GraphContext,GraphResponse,graphResponse} from './modeler/graph-model';
import {GretlModelerProvider} from './modeler/provider';
interface Context {protocolVersion:number;schemaVersion:number;serverInstanceId:string;snapshotId:string;sourceStamps:Stamp[]}
interface Graph {context:Context}

class RuntimeHost implements vscode.Disposable,ModelerBackend {
    private client?:LanguageClient;
    private child?:ChildProcess;
    private queue:Promise<void>=Promise.resolve();
    private generation=0;
    private crashes:number[]=[];
    private stopped=false;
    private disposed=false;
    private instance?:string;
    private readonly output=vscode.window.createOutputChannel('GRETL Runtime',{log:true});
    private readonly status=vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left,5);
    private readonly actions=new Map<string,{client:LanguageClient;context:Context;edit:ProtocolEdit;expires:number}>();
    private readonly captures=new WeakMap<Context,Map<string,vscode.TextDocument>>();
    private readonly sessionListeners:vscode.Disposable[]=[];
    private readonly listeners:vscode.Disposable[]=[];
    private readonly backendChanges=new vscode.EventEmitter<boolean>();
    private readonly analyses=new vscode.EventEmitter<GraphContext>();
    readonly onDidChange=this.backendChanges.event;
    readonly onDidAnalyze=this.analyses.event;
    constructor(private readonly extension:vscode.ExtensionContext){
        this.status.command='gretlRuntime.showOutput';this.status.show();this.state('Stopped');
        this.listeners.push(
            vscode.commands.registerCommand('gretlRuntime.showOutput',()=>this.output.show()),
            vscode.commands.registerCommand('gretlRuntime.applyCodeAction',async(id:unknown)=>{
                const action=typeof id==='string'?this.actions.get(id):undefined;
                if(typeof id==='string')this.actions.delete(id);
                if(!action||action.expires<Date.now())throw new Error('GRETL quick fix expired. Request it again.');
                await this.current(action.client,action.context,action.edit);
                const converted=await action.client.protocol2CodeConverter.asWorkspaceEdit(action.edit);
                await this.current(action.client,action.context,action.edit);
                if(!converted||!await vscode.workspace.applyEdit(converted))throw new Error('GRETL quick fix could not be applied. Request it again.');
            }),
            vscode.commands.registerCommand('gretlRuntime.stopServer',()=>{this.stopped=true;return this.serial(()=>this.stop());}),
            vscode.commands.registerCommand('gretlRuntime.restartServer',()=>{this.stopped=false;this.crashes=[];return this.serial(async()=>{await this.stop();await this.start();});}),
            vscode.commands.registerCommand('gretlRuntime.newJob',async()=>{
                const doc=await vscode.workspace.openTextDocument({language:'gretl-runtime',content:"// GRETL Runtime job\naction('hello') {\n    println 'Hello from GRETL Runtime'\n}\ndefaults 'hello'\n"});
                await vscode.window.showTextDocument(doc);
            }),
            vscode.commands.registerCommand('gretlRuntime.useActiveFile',async()=>{
                const doc=vscode.window.activeTextEditor?.document;
                if(doc&&(doc.uri.scheme==='file'||doc.uri.scheme==='untitled'))await vscode.languages.setTextDocumentLanguage(doc,'gretl-runtime');
            }),
            vscode.workspace.onDidGrantWorkspaceTrust(()=>this.serial(()=>this.start())),
            vscode.workspace.onDidChangeWorkspaceFolders(()=>{if(!this.stopped)void this.serial(async()=>{await this.stop();await this.start();});}),
            vscode.workspace.onDidOpenTextDocument(doc=>{if(doc.languageId==='gretl-runtime'&&!this.stopped)void this.serial(()=>this.start());}),
            vscode.workspace.onDidChangeConfiguration(event=>{
                if(event.affectsConfiguration('gretlRuntime')&&!this.stopped)void this.serial(async()=>{await this.stop();await this.start();});
            })
        );
    }
    private state(message:string){this.status.text=`GRETL: ${message}`;this.status.tooltip=message;this.backendChanges.fire(message==='Ready'&&!!this.instance);}
    async graph(document:vscode.TextDocument,options:ModelerOptions,token:vscode.CancellationToken):Promise<GraphResponse>{
        const client=this.client,instance=this.instance,version=document.version;
        if(!client?.isRunning()||!instance||!vscode.workspace.isTrusted)throw Error('GRETL server is not ready.');
        const normalize=(uri:string)=>vscode.Uri.parse(uri).toString();
        const before=new Map(vscode.workspace.textDocuments.map(d=>[d.uri.toString(),d]));
        const response=graphResponse(await client.sendRequest('gretl/graph',{protocolVersion:1,documentId:document.uri.toString(),includeInnerInvocations:options.includeInnerInvocations,...(options.previewTargets===null?{}:{previewTargets:options.previewTargets})},token));
        if(token.isCancellationRequested||this.client!==client||this.instance!==instance||response.context.serverInstanceId!==instance||document.isClosed||document.version!==version)throw new vscode.CancellationError();
        const now=new Map(vscode.workspace.textDocuments.map(d=>[d.uri.toString(),d]));
        for(const stamp of response.context.sourceStamps){const uri=normalize(stamp.documentId),doc=now.get(uri);if(stamp.version!=null&&(!doc||before.get(uri)!==doc||doc.version!==stamp.version||sourceDigest(doc.getText())!==stamp.sha256)||stamp.version==null&&doc)throw new vscode.CancellationError();}
        return response;
    }
    async navigate(documentId:string,snapshotId:string,elementId:string|undefined,offsetUtf16:number|undefined,token:vscode.CancellationToken){
        const client=this.client,instance=this.instance;
        if(!client?.isRunning()||!instance)throw Error('GRETL server is not ready.');
        const result=await client.sendRequest<Awaited<ReturnType<ModelerBackend['navigate']>>>('gretl/navigation',{protocolVersion:1,documentId,expectedSnapshotId:snapshotId,...(elementId===undefined?{offsetUtf16}:{elementId})},token);
        if(token.isCancellationRequested||this.client!==client||this.instance!==instance||result.context.serverInstanceId!==instance||result.context.snapshotId!==snapshotId)throw new vscode.CancellationError();return result;
    }
    private serial(operation:()=>Promise<void>):Promise<void>{
        this.queue=this.queue.then(operation,operation).catch(error=>{this.state('Error — see Output');this.output.error(String(error instanceof Error?error.message:error));});return this.queue;
    }
    activate(){return this.serial(()=>this.start());}
    private async start():Promise<void>{
        if(this.client||this.disposed||this.stopped)return;
        if(this.child){await endOwnedProcess(this.child);this.child=undefined;}
        if(!vscode.workspace.isTrusted){this.state('Workspace trust required');return;}
        if(!localHost((vscode.workspace.workspaceFolders??[]).map(folder=>folder.uri.scheme))){this.state('Local filesystem workspace required');return;}
        if(!vscode.workspace.workspaceFolders?.length){this.state('Open a local workspace folder');return;}
        this.state('Starting');
        const config=vscode.workspace.getConfiguration('gretlRuntime');
        const java=config.get<string>('javaPath','java');
        const bundle=await checkBundle(this.extension.extensionPath,config.get<string>('serverJarPath',''));
        await checkJava(java);
        if(this.disposed||this.stopped)return;
        const generation=++this.generation;
        const client:LanguageClient=new LanguageClient('gretl-runtime','GRETL Runtime',async()=>{
            if(generation!==this.generation||this.disposed)throw new Error('Obsolete server start');
            const child=spawn(java,['-jar',bundle.jar,'--stdio'],{stdio:'pipe',shell:false,windowsHide:true});
            this.child=child;
            this.output.info(`Starting bundled server; PID ${child.pid??'pending'}; candidate ${bundle.manifest.sourceCommit}; SHA256 ${bundle.manifest.files['gretl-lsp.jar']}`);
            child.stderr.on('data',(bytes:Buffer)=>this.output.warn(bytes.toString('utf8').slice(0,4096)));
            await once(child,'spawn');
            // Stream transport: this host owns process termination. The client must
            // not schedule a delayed PID-based kill after the process has exited.
            return {reader:child.stdout,writer:child.stdin,detached:true};
        },{
            documentSelector:[{scheme:'file',language:'gretl-runtime'},{scheme:'untitled',language:'gretl-runtime'}],
            initializationOptions:languageConfiguration(config.get<string>('language','auto'),vscode.env.language),
            outputChannel:this.output,
            synchronize:{fileEvents:vscode.workspace.createFileSystemWatcher('**/{job.groovy,settings.gretl,*.sql}')},
            errorHandler:{
                error:()=>({action:ErrorAction.Shutdown,message:'GRETL protocol failed. See Output and restart the server.'}),
                closed:()=>{
                    if(this.disposed||this.stopped||generation!==this.generation||this.client!==client)return {action:CloseAction.DoNotRestart};
                    const child=this.child;
                    this.instance=undefined;this.client=undefined;this.actions.clear();
                    for(const listener of this.sessionListeners.splice(0))listener.dispose();
                    const now=Date.now();this.crashes=this.crashes.filter(time=>now-time<60000);this.crashes.push(now);
                    this.state('Server stopped');
                    const retry=this.crashes.length<=2;
                    void this.serial(async()=>{
                        await endOwnedProcess(child);
                        if(this.child===child)this.child=undefined;
                        await client.dispose();
                        if(retry)await this.start();else this.state('Repeated server failure — restart manually');
                    });
                    return {action:CloseAction.DoNotRestart};
                }
            },
            middleware:{
                provideDocumentSymbols:async(doc,token)=>{
                    const version=doc.version;
                    const current=()=>this.client===client&&client.isRunning()&&!token.isCancellationRequested&&!doc.isClosed&&doc.version===version;
                    const symbols=await currentRead(()=>client.sendRequest(DocumentSymbolRequest.type,{textDocument:{uri:doc.uri.toString()}},token),current);
                    if(!symbols?.length)return [];
                    const converted=ProtocolSymbol.is(symbols[0])
                        ?await client.protocol2CodeConverter.asDocumentSymbols(symbols as ProtocolSymbol[],token)
                        :await client.protocol2CodeConverter.asSymbolInformations(symbols as import('vscode-languageclient/node').SymbolInformation[],token);
                    if(!current())throw new vscode.CancellationError();
                    return converted;
                },
                provideRenameEdits:async(doc,position,newName,token)=>{
                    const capture=await this.capture(client,doc,token);
                    const edit=await client.sendRequest(RenameRequest.type,{textDocument:{uri:doc.uri.toString()},position,newName},token);
                    if(!edit||token.isCancellationRequested)return null;
                    await this.current(client,capture,edit);
                    const result=await client.protocol2CodeConverter.asWorkspaceEdit(edit,token);
                    await this.current(client,capture,edit);
                    return result;
                },
                provideCodeActions:async(doc,range,context,token)=>{
                    const capture=await this.capture(client,doc,token);
                    const params={textDocument:{uri:doc.uri.toString()},range:client.code2ProtocolConverter.asRange(range),context:await client.code2ProtocolConverter.asCodeActionContext(context,token)};
                    const actions=await client.sendRequest(CodeActionRequest.type,params,token);
                    if(!actions||token.isCancellationRequested)return [];
                    for(const action of actions)if(ProtocolAction.is(action)&&action.edit)await this.current(client,capture,action.edit);
                    const converted=await client.protocol2CodeConverter.asCodeActionResult(actions,token);
                    for(const action of actions)if(ProtocolAction.is(action)&&action.edit)await this.current(client,capture,action.edit);
                    for(let i=0;i<actions.length;i++){
                        const raw=actions[i],action=converted[i];
                        if(ProtocolAction.is(raw)&&raw.edit&&action instanceof vscode.CodeAction){
                            const id=randomUUID();
                            while(this.actions.size>=32)this.actions.delete(this.actions.keys().next().value!);
                            this.actions.set(id,{client,context:capture,edit:raw.edit,expires:Date.now()+60000});
                            action.edit=undefined;
                            action.command={title:action.title,command:'gretlRuntime.applyCodeAction',arguments:[id]};
                        }
                    }
                    return converted;
                }
            }
        });
        this.client=client;
        this.sessionListeners.push(client.onNotification('gretl/analysisReady',(message:{context:GraphContext})=>{if(this.client===client&&message.context?.serverInstanceId===this.instance)this.analyses.fire(message.context);}));
        const watcher=(client.clientOptions.synchronize?.fileEvents);
        if(watcher&&!Array.isArray(watcher))this.sessionListeners.push(watcher);
        this.sessionListeners.push(client.onDidChangeState(event=>{if(this.client===client&&event.newState===State.Running)this.state('Ready');}));
        try{
            await client.start();
            const catalog=await client.sendRequest<{protocolVersion:number;schemaVersion:number;serverInstanceId:string;state:string}>('gretl/catalog',{protocolVersion:1,catalogSchemaVersion:1});
            if(catalog.protocolVersion!==1||catalog.schemaVersion!==1||catalog.state!=='AVAILABLE'||!catalog.serverInstanceId)throw new Error('Incompatible GRETL catalogue protocol');
            if(this.client!==client)throw new Error('Obsolete GRETL session');
            this.instance=catalog.serverInstanceId;
            this.output.info(`Connected server instance ${this.instance}`);
            // SQL buffers feed the same source overlay, without registering GRETL features for ordinary SQL documents.
            await this.synchronizeSql(client);
            this.state('Ready');
        }catch(error){await this.stop();throw error;}
    }
    private async synchronizeSql(client:LanguageClient){
        const owns=(doc:vscode.TextDocument)=>doc.languageId==='sql'&&doc.uri.scheme==='file'&&!!vscode.workspace.getWorkspaceFolder(doc.uri);
        const send=(method:string,params:unknown)=>{if(this.client===client&&client.isRunning())void client.sendNotification(method,params);};
        const open=(doc:vscode.TextDocument)=>{if(owns(doc))send('textDocument/didOpen',{textDocument:{uri:doc.uri.toString(),languageId:'sql',version:doc.version,text:doc.getText()}});};
        this.sessionListeners.push(vscode.workspace.onDidOpenTextDocument(open),vscode.workspace.onDidChangeTextDocument(event=>{
            if(owns(event.document))send('textDocument/didChange',{textDocument:{uri:event.document.uri.toString(),version:event.document.version},contentChanges:[{text:event.document.getText()}]});
        }),vscode.workspace.onDidCloseTextDocument(doc=>{if(owns(doc))send('textDocument/didClose',{textDocument:{uri:doc.uri.toString()}});}));
        for(const doc of vscode.workspace.textDocuments)open(doc);
    }
    private async capture(client:LanguageClient,doc:vscode.TextDocument,token:vscode.CancellationToken):Promise<Context>{
        const graph=await client.sendRequest<Graph>('gretl/graph',{protocolVersion:1,documentId:doc.uri.toString()},token);
        const context=graph.context;
        if(context?.protocolVersion!==1||context.schemaVersion!==1||context.serverInstanceId!==this.instance||!Array.isArray(context.sourceStamps)||context.sourceStamps.length>4096)throw new Error('GRETL source capture is stale or incompatible. Retry.');
        context.sourceStamps=context.sourceStamps.map(stamp=>({...stamp,sha256:stamp.sha256??null,version:stamp.version??null,incarnation:stamp.incarnation??null,unavailableCode:stamp.unavailableCode??null}));
        const ownedBuffers=new Set(context.sourceStamps.filter(stamp=>stamp.version!==null).map(stamp=>vscode.Uri.parse(stamp.documentId).toString()));
        this.captures.set(context,new Map(vscode.workspace.textDocuments.filter(doc=>ownedBuffers.has(doc.uri.toString())).map(doc=>[doc.uri.toString(),doc])));
        return context;
    }
    private async current(client:LanguageClient,capture:Context,edit:ProtocolEdit):Promise<void>{
        if(this.client!==client||capture.serverInstanceId!==this.instance||!client.isRunning())throw new Error('GRETL edit belongs to an obsolete session. Retry.');
        if(edit.changes&&Object.keys(edit.changes).length||!edit.documentChanges)throw new Error('GRETL requires versioned document changes.');
        const normalize=(uri:string)=>vscode.Uri.parse(uri).toString();
        const stamps=capture.sourceStamps.map(stamp=>({...stamp,documentId:normalize(stamp.documentId)}));
        const changes=edit.documentChanges.map(change=>{
            if(!TextDocumentEdit.is(change))throw new Error('GRETL file operations are unsupported.');
            return {uri:normalize(change.textDocument.uri),version:change.textDocument.version};
        });
        await validateEditSources(stamps,changes,this.captures.get(capture)??new Map(),()=>new Map(vscode.workspace.textDocuments.map(doc=>[doc.uri.toString(),doc])));
        if(this.client!==client||capture.serverInstanceId!==this.instance||!client.isRunning())throw new Error('GRETL edit belongs to an obsolete session. Retry.');
    }
    private async stop():Promise<void>{
        ++this.generation;this.instance=undefined;this.actions.clear();
        for(const listener of this.sessionListeners.splice(0))listener.dispose();
        const client=this.client;const child=this.child;this.client=undefined;
        if(client){try{await client.stop(3000);}catch{this.output.warn('Server shutdown timed out. Terminating the owned process.');}finally{await client.dispose();}}
        await endOwnedProcess(child);
        if(this.child===child)this.child=undefined;
        this.state('Stopped');
    }
    async deactivate(){this.disposed=true;await this.serial(()=>this.stop());this.dispose();}
    dispose(){this.disposed=true;for(const listener of this.listeners.splice(0))listener.dispose();this.backendChanges.dispose();this.analyses.dispose();this.status.dispose();this.output.dispose();}
}
let host:RuntimeHost|undefined;
export async function activate(context:vscode.ExtensionContext){host=new RuntimeHost(context);context.subscriptions.push(new GretlModelerProvider(context,host));await host.activate();}
export async function deactivate(){await host?.deactivate();host=undefined;}
