import {pinnedContext,PinnedSourceContext} from './modeler/source-context';
import * as vscode from 'vscode';
import {spawn,ChildProcess} from 'node:child_process';
import {once} from 'node:events';
import {randomUUID} from 'node:crypto';
import {endOwnedProcess} from './process';
import {LanguageClient,CloseAction,ErrorAction,State,RenameRequest,CodeActionRequest,DocumentSymbolRequest,DocumentSymbol as ProtocolSymbol,TextDocumentEdit,WorkspaceEdit as ProtocolEdit,CodeAction as ProtocolAction} from 'vscode-languageclient/node';
import {checkBundle,checkJava,languageConfiguration,localHost,sourceDigest} from './host';

import {FragmentDocumentCoordinator,FragmentDocument,sourceContexts,membershipUpdate} from './fragment-documents';
import {Stamp,validateEditSources,validateEditContext,EditContextIdentity} from './edit-guard';
import {currentRead} from './read-query';
import {ModelerBackend,ModelerOptions,ModelerCommit} from './modeler/backend';
import {GraphContext,GraphResponse,graphResponse} from './modeler/graph-model';
import {GretlModelerProvider} from './modeler/provider';
import {CatalogResponse,EditIntent,catalogResponse,editIntent} from './modeler/edit-model';
import {EditPlan,applyEditPlan} from './modeler/edit-plan';
interface Context extends EditContextIdentity {sourceStamps:Stamp[];notices?:string[]}
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
    private fragments?:FragmentDocumentCoordinator;
    private readonly standaloneEntries=new Set<string>();
    private readonly sentEntryRoles=new Set<string>();
    private readonly output=vscode.window.createOutputChannel('GRETL Runtime',{log:true});
    private readonly status=vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left,5);
    private readonly actions=new Map<string,{client:LanguageClient;context:Context;edit:ProtocolEdit;expires:number}>();
    private readonly captures=new WeakMap<Context,Map<string,vscode.TextDocument>>();
    private readonly modelerCaptures=new WeakMap<GraphResponse,Context>();
    private modelerEditQueue:Promise<unknown>=Promise.resolve();
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
                this.rememberEntry(doc.uri);await vscode.window.showTextDocument(doc);await this.serial(()=>this.start());
                if(this.client?.isRunning())await this.registerEntries(this.client);
            }),
            vscode.commands.registerCommand('gretlRuntime.useActiveFile',async()=>{
                const doc=vscode.window.activeTextEditor?.document;
                if(doc&&(doc.uri.scheme==='file'||doc.uri.scheme==='untitled')){
                    this.rememberEntry(doc.uri);await vscode.languages.setTextDocumentLanguage(doc,'gretl-runtime');await this.serial(()=>this.start());
                    if(this.client?.isRunning())await this.registerEntries(this.client);
                }
            }),
            vscode.commands.registerCommand('gretlRuntime.selectSourceContext',()=>this.selectSourceContext()),
            vscode.workspace.onDidGrantWorkspaceTrust(()=>this.serial(()=>this.start())),
            vscode.workspace.onDidChangeWorkspaceFolders(()=>{if(!this.stopped)void this.serial(async()=>{await this.stop();await this.start();});}),
            vscode.workspace.onDidOpenTextDocument(doc=>{if(doc.languageId==='gretl-runtime'&&!this.stopped)void this.serial(()=>this.start());void this.useNavigationPin(doc);}),
            vscode.workspace.onDidChangeConfiguration(event=>{
                if(event.affectsConfiguration('gretlRuntime')&&!this.stopped)void this.serial(async()=>{await this.stop();await this.start();});
            })
        );
    }
    private state(message:string){this.status.text=`GRETL: ${message}`;this.status.tooltip=message;this.backendChanges.fire(message==='Ready'&&!!this.instance);}
    private readonly navigationPins=new Map<string,{context:PinnedSourceContext;instance:string;expires:number}>();
    private async useNavigationPin(document:vscode.TextDocument){
        const pin=this.navigationPins.get(document.uri.toString());if(!pin)return;this.navigationPins.delete(document.uri.toString());
        if(pin.instance!==this.instance||Date.now()>pin.expires)return;
        const cancellation=new vscode.CancellationTokenSource();
        try{await this.activateSourceContext(document.uri.toString(),pin.context,cancellation.token);}
        catch(error){this.output.warn(String(error));}finally{cancellation.dispose();}
    }
    private readonly graphSourceContexts=new WeakMap<GraphResponse,{documentId:string;contextToken:string}>();
    private async panelContext(client:LanguageClient,documentId:string,pin:PinnedSourceContext|undefined,token:vscode.CancellationToken,choose:boolean){
        const contexts=sourceContexts(await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId},token));
        let selected=pinnedContext(contexts,pin,!choose);
        if(!selected&&choose&&contexts.contexts.length){
            const pick=await vscode.window.showQuickPick(contexts.contexts.map(c=>({label:c.ownerScope,description:vscode.workspace.asRelativePath(vscode.Uri.parse(c.entryDocument)),detail:c.resourceBase??'No resource base',context:c})),{placeHolder:'Choose the context for this GRETL Modeler panel'},token);selected=pick?.context;
        }
        if(!selected)throw Error(contexts.contexts.length?'Choose a GRETL source context before opening this panel.':'No GRETL owner is known. Use this file as an entry explicitly if intended.');
        return selected;
    }
    async graph(document:vscode.TextDocument,options:ModelerOptions,token:vscode.CancellationToken):Promise<GraphResponse>{
        const client=this.client,instance=this.instance,version=document.version;
        if(!client?.isRunning()||!instance||!vscode.workspace.isTrusted)throw Error('GRETL server is not ready.');
        const normalize=(uri:string)=>vscode.Uri.parse(uri).toString();
        const selected=await this.panelContext(client,document.uri.toString(),options.sourceContext,token,true);options.sourceContext={projectId:selected.projectId,entryDocument:selected.entryDocument,ownerScope:selected.ownerScope,role:selected.role};
        const {value,before,contextToken}=await this.synchronizedGraph(client,{protocolVersion:1,documentId:document.uri.toString(),contextToken:selected.contextToken,includeInnerInvocations:options.includeInnerInvocations,...(options.previewTargets===null?{}:{previewTargets:options.previewTargets})},token,options.sourceContext);
        const response=graphResponse(value);this.graphSourceContexts.set(response,{documentId:document.uri.toString(),contextToken:typeof contextToken==='string'?contextToken:selected.contextToken});
        if(token.isCancellationRequested||this.client!==client||this.instance!==instance||response.context.serverInstanceId!==instance||document.isClosed||document.version!==version)throw new vscode.CancellationError();
        const now=new Map(vscode.workspace.textDocuments.map(d=>[d.uri.toString(),d]));
        for(const stamp of response.context.sourceStamps){const uri=normalize(stamp.documentId),doc=now.get(uri);if(stamp.version!=null&&(!doc||before.get(uri)!==doc||doc.version!==stamp.version||sourceDigest(doc.getText())!==stamp.sha256)||stamp.version==null&&doc)throw new vscode.CancellationError();}
        const capture:Context={...response.context,sourceStamps:response.context.sourceStamps.map(stamp=>({...stamp,sha256:stamp.sha256??null,version:stamp.version??null,incarnation:stamp.incarnation??null,unavailableCode:stamp.unavailableCode??null}))};
        this.captures.set(capture,new Map([...before].filter(([uri])=>capture.sourceStamps.some(s=>normalize(s.documentId)===uri&&s.version!==null))));this.modelerCaptures.set(response,capture);
        return response;
    }
    async catalog(graph:GraphResponse,token:vscode.CancellationToken):Promise<CatalogResponse>{
        const client=this.client,instance=this.instance;
        if(!client?.isRunning()||!instance||graph.context.serverInstanceId!==instance)throw Error('GRETL catalogue session is stale.');
        const result=catalogResponse(await client.sendRequest('gretl/catalog',{protocolVersion:1,catalogSchemaVersion:1},token),instance,graph.context.catalogId);
        if(token.isCancellationRequested||this.client!==client||this.instance!==instance)throw new vscode.CancellationError();return result;
    }
    commit(document:vscode.TextDocument,graph:GraphResponse,intent:EditIntent,token:vscode.CancellationToken):Promise<ModelerCommit>{
        const operation=async()=>{
            const client=this.client,instance=this.instance,capture=this.modelerCaptures.get(graph);
            if(!vscode.workspace.isTrusted||!client?.isRunning()||!instance||!capture||instance!==graph.context.serverInstanceId||!editIntent(intent))throw Error('GRETL edit context is unavailable or stale. Refresh first.');
            const cancelled=()=>token.isCancellationRequested||this.client!==client||this.instance!==instance||document.isClosed;
            let changed:string[]=[];
            const notices=await applyEditPlan<ProtocolEdit,vscode.WorkspaceEdit>(graph.context,{
                cancelled,
                plan:()=>client.sendRequest<EditPlan<ProtocolEdit>>('gretl/planEdit',{protocolVersion:1,documentId:document.uri.toString(),contextToken:this.graphSourceContexts.get(graph)?.contextToken,expectedSnapshotId:graph.context.snapshotId,intent,requireOpenDocuments:true},token),
                guard:edit=>this.current(client,capture,edit),
                validate:(planId,expectedSnapshotId)=>client.sendRequest('gretl/validateEdit',{protocolVersion:1,planId,expectedSnapshotId},token),
                convert:async edit=>(await client.protocol2CodeConverter.asWorkspaceEdit(edit,token))??undefined,
                apply:async edit=>{const applied=await vscode.workspace.applyEdit(edit);if(applied)changed=edit.entries().map(([uri])=>uri.toString());return applied;}
            });
            return {notices,documents:changed};
        };
        const result=this.modelerEditQueue.then(operation,operation);this.modelerEditQueue=result.then(()=>undefined,()=>undefined);return result;
    }
    async activateSourceContext(documentId:string,context:PinnedSourceContext,token:vscode.CancellationToken){
        const client=this.client;if(!client?.isRunning())throw new vscode.CancellationError();await new Promise<void>(resolve=>setImmediate(resolve));await this.fragments?.settled();
        const contexts=sourceContexts(await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId},token));
        const selected=pinnedContext(contexts,context,true);if(!selected)throw Error('Navigation context is no longer available.');
        await client.sendRequest('gretl/selectSourceContext',{protocolVersion:1,includeToolingVersion:1,documentId,contextToken:selected.contextToken,incarnation:contexts.incarnation??null,ownerGeneration:contexts.ownerGeneration},token);
    }
    async navigate(documentId:string,snapshotId:string,elementId:string|undefined,offsetUtf16:number|undefined,token:vscode.CancellationToken,sourceContext?:PinnedSourceContext){
        const client=this.client,instance=this.instance;
        if(!client?.isRunning()||!instance)throw Error('GRETL server is not ready.');
        const selected=sourceContext?await this.panelContext(client,documentId,sourceContext,token,false):undefined;
        const result=await client.sendRequest<Awaited<ReturnType<ModelerBackend['navigate']>>>('gretl/navigation',{protocolVersion:1,documentId,...(selected?{contextToken:selected.contextToken}:{}),expectedSnapshotId:snapshotId,...(elementId===undefined?{offsetUtf16}:{elementId})},token);
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
            synchronize:{fileEvents:vscode.workspace.createFileSystemWatcher('**/{*.groovy,settings.gretl,*.sql}')},
            errorHandler:{
                error:()=>({action:ErrorAction.Shutdown,message:'GRETL protocol failed. See Output and restart the server.'}),
                closed:()=>{
                    if(this.disposed||this.stopped||generation!==this.generation||this.client!==client)return {action:CloseAction.DoNotRestart};
                    const child=this.child;
                    this.instance=undefined;this.client=undefined;this.actions.clear();
                    const fragments=this.fragments;this.fragments=undefined;
                    for(const listener of this.sessionListeners.splice(0))listener.dispose();
                    const now=Date.now();this.crashes=this.crashes.filter(time=>now-time<60000);this.crashes.push(now);
                    this.state('Server stopped');
                    const retry=this.crashes.length<=2;
                    void this.serial(async()=>{
                        await fragments?.dispose();await endOwnedProcess(child);
                        if(this.child===child)this.child=undefined;
                        await client.dispose();
                        if(retry)await this.start();else this.state('Repeated server failure — restart manually');
                    });
                    return {action:CloseAction.DoNotRestart};
                }
            },
            middleware:{
                provideDefinition:async(doc,position,token,next)=>{
                    const version=doc.version,instance=this.instance;
                    const contexts=sourceContexts(await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId:doc.uri.toString()},token));
                    const selected=pinnedContext(contexts);const result=await next(doc,position,token);
                    if(selected&&instance&&this.client===client&&this.instance===instance&&!token.isCancellationRequested&&!doc.isClosed&&doc.version===version){
                        const locations=result?(Array.isArray(result)?result:[result]):[];
                        for(const location of locations){
                            const uri=('targetUri' in location?location.targetUri:location.uri).toString();
                            this.navigationPins.set(uri,{context:selected,instance,expires:Date.now()+10000});
                            while(this.navigationPins.size>16)this.navigationPins.delete(this.navigationPins.keys().next().value!);
                            const open=vscode.workspace.textDocuments.find(d=>!d.isClosed&&d.uri.toString()===uri);if(open)await this.useNavigationPin(open);
                        }
                    }
                    return result;
                },
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
                    let capture:Context|undefined,edit:ProtocolEdit|null|undefined;
                    for(let attempt=0;attempt<3;attempt++){
                        capture=await this.capture(client,doc,token);
                        edit=await client.sendRequest(RenameRequest.type,{textDocument:{uri:doc.uri.toString()},position,newName},token);
                        if(!edit||!await this.openEditTargets(capture,edit))break;
                        if(attempt===2)throw new vscode.CancellationError();
                    }
                    if(!capture)throw new vscode.CancellationError();
                    if(!edit||token.isCancellationRequested)return null;
                    await this.current(client,capture,edit);
                    const result=await client.protocol2CodeConverter.asWorkspaceEdit(edit,token);
                    await this.current(client,capture,edit);
                    return result;
                },
                provideCodeActions:async(doc,range,context,token)=>{
                    let capture=await this.capture(client,doc,token);
                    const params={textDocument:{uri:doc.uri.toString()},range:client.code2ProtocolConverter.asRange(range),context:await client.code2ProtocolConverter.asCodeActionContext(context,token)};
                    let actions=await client.sendRequest(CodeActionRequest.type,params,token);
                    for(let attempt=0;attempt<3&&actions;attempt++){
                        let opened=false;for(const action of actions)if(ProtocolAction.is(action)&&action.edit)opened=await this.openEditTargets(capture,action.edit)||opened;
                        if(!opened)break;if(attempt===2)throw new vscode.CancellationError();
                        capture=await this.capture(client,doc,token);actions=await client.sendRequest(CodeActionRequest.type,params,token);
                    }
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
        client.registerFeature({
            fillClientCapabilities(capabilities){capabilities.experimental={...capabilities.experimental,gretlRuntime:{...capabilities.experimental?.gretlRuntime,includeToolingVersion:1}};},
            initialize(capabilities){if(capabilities.experimental?.gretlRuntime?.includeToolingVersion!==1)throw Error('GRETL include tooling capability is unavailable.');},
            getState:()=>({kind:'static'}),clear(){}
        });
        this.client=client;this.sentEntryRoles.clear();const pendingMembership=new Map<string,ReturnType<typeof membershipUpdate>>();this.fragments=this.fragmentCoordinator(client);
        this.sessionListeners.push(client.onNotification('gretl/sourceMembershipChanged',(message:unknown)=>{
            if(this.client!==client)return;
            if(!this.instance){
                try{const update=membershipUpdate(message),previous=pendingMembership.get(update.workspaceRoot);
                    if((pendingMembership.size<16||previous)&&(!previous||BigInt(update.ownerGeneration)>=BigInt(previous.ownerGeneration)))pendingMembership.set(update.workspaceRoot,update);
                }catch(error){this.output.warn(String(error));}return;
            }
            void this.fragments?.acceptMembership(message).catch(error=>this.output.warn(String(error)));
        }));
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
            await this.registerEntries(client);
            for(const message of pendingMembership.values())await this.fragments?.acceptMembership(message);pendingMembership.clear();
            this.output.info(`Connected server instance ${this.instance}`);
            // SQL buffers feed the same source overlay, without registering GRETL features for ordinary SQL documents.
            await this.synchronizeSql(client);
            this.state('Ready');
        }catch(error){await this.stop();throw error;}
    }
    private rememberEntry(uri:vscode.Uri){
        const id=uri.toString();if(this.standaloneEntries.size>=64&&!this.standaloneEntries.has(id))throw Error('GRETL entry registration limit reached.');
        this.standaloneEntries.add(id);
    }
    private async registerEntries(client:LanguageClient){
        const instance=this.instance;if(!instance||this.client!==client)return;
        for(const id of this.standaloneEntries){
            const uri=vscode.Uri.parse(id);
            if(uri.scheme==='file'&&!vscode.workspace.getWorkspaceFolder(uri)||uri.scheme==='untitled'&&(vscode.workspace.workspaceFolders?.length!==1||!vscode.workspace.textDocuments.some(doc=>doc.uri.toString()===id&&!doc.isClosed)))continue;
            const key=instance+'|'+id;if(this.sentEntryRoles.has(key))continue;
            await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId:id,registerAsEntry:true});
            if(this.client!==client||this.instance!==instance)throw new vscode.CancellationError();this.sentEntryRoles.add(key);
        }
    }
    private fragmentCoordinator(client:LanguageClient):FragmentDocumentCoordinator {
        const views=new WeakMap<vscode.TextDocument,FragmentDocument>(),documents=new WeakMap<FragmentDocument,vscode.TextDocument>();
        const view=(doc:vscode.TextDocument):FragmentDocument=>{
            let value=views.get(doc);if(!value){value={get uri(){return doc.uri.toString();},get languageId(){return doc.languageId;},get version(){return doc.version;},get dirty(){return doc.isDirty;},get closed(){return doc.isClosed;},getText:()=>doc.getText()};views.set(doc,value);documents.set(value,doc);}return value;
        };
        const manual=new Map<string,{document:vscode.TextDocument;version:number}>();
        const close=async(document:FragmentDocument)=>{
            const doc=documents.get(document),key=document.uri,previous=manual.get(key);
            if(previous?.document===doc){manual.delete(key);if(this.client===client&&client.isRunning())await client.sendNotification('textDocument/didClose',{textDocument:{uri:key}});}
        };
        const coordinator=new FragmentDocumentCoordinator({
            instance:()=>this.instance,normalize:uri=>vscode.Uri.parse(uri).toString(),
            owns:(uri,root)=>{const parsed=vscode.Uri.parse(uri),folder=vscode.workspace.getWorkspaceFolder(parsed);return parsed.scheme==='file'&&!!folder&&(!root||folder.uri.toString()===vscode.Uri.parse(root).toString());},
            documents:()=>vscode.workspace.textDocuments.map(view),
            changeLanguage:async(document,language)=>{const doc=documents.get(document);if(!doc||doc.isClosed)throw new vscode.CancellationError();await close(document);return view(await vscode.languages.setTextDocumentLanguage(doc,language));},
            synchronize:async document=>{
                const doc=documents.get(document);if(!doc||doc.isClosed||this.client!==client||!client.isRunning())throw new vscode.CancellationError();
                if(doc.languageId==='gretl-runtime'||doc.languageId==='sql')return;
                const previous=manual.get(doc.uri.toString());
                if(previous?.document!==doc){if(previous)await client.sendNotification('textDocument/didClose',{textDocument:{uri:doc.uri.toString()}});
                    await client.sendNotification('textDocument/didOpen',{textDocument:{uri:doc.uri.toString(),languageId:doc.languageId,version:doc.version,text:doc.getText()}});
                }else if(previous.version!==doc.version)await client.sendNotification('textDocument/didChange',{textDocument:{uri:doc.uri.toString(),version:doc.version},contentChanges:[{text:doc.getText()}]});
                manual.set(doc.uri.toString(),{document:doc,version:doc.version});
            },closed:close,digest:sourceDigest,
            confirm:async(update,buffers)=>{
                if(this.client!==client||this.instance!==update.serverInstanceId)return false;
                const response=await client.sendRequest<{protocolVersion:number;includeToolingVersion:number;current:boolean}>('gretl/confirmSourceMembership',{
                    protocolVersion:1,includeToolingVersion:1,documentId:update.anchorDocument,ownerGeneration:update.ownerGeneration,syncToken:update.syncToken,buffers});
                return this.client===client&&response.protocolVersion===1&&response.includeToolingVersion===1&&response.current;
            }
        });
        this.sessionListeners.push(vscode.workspace.onDidOpenTextDocument(doc=>{void coordinator.opened(view(doc)).catch(error=>this.output.warn(String(error)));}),
            vscode.workspace.onDidChangeTextDocument(event=>coordinator.changed(view(event.document))),vscode.workspace.onDidCloseTextDocument(doc=>coordinator.closed(view(doc))));
        return coordinator;
    }
    private async synchronizedGraph(client:LanguageClient,params:{documentId:string;[key:string]:unknown},token:vscode.CancellationToken,pin?:PinnedSourceContext){
        for(let attempt=0;attempt<4;attempt++){
            if(this.client!==client||token.isCancellationRequested)throw new vscode.CancellationError();
            await this.fragments?.settled();const before=new Map(vscode.workspace.textDocuments.map(d=>[d.uri.toString(),d]));
            if(pin)params.contextToken=(await this.panelContext(client,params.documentId,pin,token,false)).contextToken;
            const value=await client.sendRequest<Graph>('gretl/graph',params,token);
            if(!value.context?.notices?.includes('INCLUDE_MEMBERSHIP_PENDING'))return {value,before,contextToken:params.contextToken};
            await this.fragments?.settled();
            await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId:params.documentId});
        }
        throw new vscode.CancellationError();
    }
    private async selectSourceContext(){
        const doc=vscode.window.activeTextEditor?.document,client=this.client;
        if(!doc||!client?.isRunning()||!this.instance)return;
        const contexts=sourceContexts(await client.sendRequest('gretl/sourceContexts',{protocolVersion:1,includeToolingVersion:1,documentId:doc.uri.toString()}));
        if(contexts.serverInstanceId!==this.instance)throw new vscode.CancellationError();
        if(!contexts.contexts.length){await vscode.window.showInformationMessage('No GRETL entry owns this source.');return;}
        const chosen=await vscode.window.showQuickPick(contexts.contexts.map(c=>({label:c.ownerScope,description:vscode.workspace.asRelativePath(vscode.Uri.parse(c.entryDocument)),detail:c.resourceBase??'No resource base',contextToken:c.contextToken})),{placeHolder:'Choose the GRETL source context'});
        if(!chosen||doc.isClosed)return;
        sourceContexts(await client.sendRequest('gretl/selectSourceContext',{protocolVersion:1,includeToolingVersion:1,documentId:doc.uri.toString(),contextToken:chosen.contextToken,incarnation:contexts.incarnation??null,ownerGeneration:contexts.ownerGeneration}));
        this.backendChanges.fire(true);
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
        const {value:graph}=await this.synchronizedGraph(client,{protocolVersion:1,documentId:doc.uri.toString()},token);
        const context=graph.context;
        if(context?.protocolVersion!==1||context.schemaVersion!==1||context.serverInstanceId!==this.instance||!Array.isArray(context.sourceStamps)||context.sourceStamps.length>4096)throw new Error('GRETL source capture is stale or incompatible. Retry.');
        context.sourceStamps=context.sourceStamps.map(stamp=>({...stamp,sha256:stamp.sha256??null,version:stamp.version??null,incarnation:stamp.incarnation??null,unavailableCode:stamp.unavailableCode??null}));
        const ownedBuffers=new Set(context.sourceStamps.filter(stamp=>stamp.version!==null).map(stamp=>vscode.Uri.parse(stamp.documentId).toString()));
        this.captures.set(context,new Map(vscode.workspace.textDocuments.filter(doc=>ownedBuffers.has(doc.uri.toString())).map(doc=>[doc.uri.toString(),doc])));
        return context;
    }
    private async openEditTargets(capture:Context,edit:ProtocolEdit):Promise<boolean>{
        if(edit.changes&&Object.keys(edit.changes).length||!edit.documentChanges)throw Error('GRETL requires versioned document changes.');
        let opened=false;
        for(const change of edit.documentChanges){
            if(!TextDocumentEdit.is(change))throw Error('GRETL file operations are unsupported.');
            const uri=vscode.Uri.parse(change.textDocument.uri),id=uri.toString();
            if(uri.scheme!=='file'||!vscode.workspace.getWorkspaceFolder(uri)||!capture.sourceStamps.some(s=>vscode.Uri.parse(s.documentId).toString()===id&&s.editable))throw Error('GRETL edit target is outside its captured workspace.');
            if(!vscode.workspace.textDocuments.some(d=>!d.isClosed&&d.uri.toString()===id)){await vscode.workspace.openTextDocument(uri);opened=true;}
        }
        if(opened){await new Promise<void>(resolve=>setImmediate(resolve));await this.fragments?.settled();}
        return opened;
    }
    private async current(client:LanguageClient,capture:Context,edit:ProtocolEdit):Promise<void>{
        if(this.client!==client||capture.serverInstanceId!==this.instance||!client.isRunning())throw new Error('GRETL edit belongs to an obsolete session. Retry.');
        if(edit.changes&&Object.keys(edit.changes).length||!edit.documentChanges)throw new Error('GRETL requires versioned document changes.');
        if(!capture.ownerGeneration||!capture.contextGeneration||!capture.contextDocumentId||!capture.contextToken)throw Error('GRETL edit has no confirmed ownership context.');
        const currentContext=async()=>{
            const graph=await client.sendRequest<Graph>('gretl/graph',{protocolVersion:1,documentId:capture.contextDocumentId,contextToken:capture.contextToken});
            validateEditContext(capture,graph.context);if(graph.context.notices?.includes('INCLUDE_MEMBERSHIP_PENDING'))throw Error('GRETL fragment synchronization is pending.');
        };
        await currentContext();
        const normalize=(uri:string)=>vscode.Uri.parse(uri).toString();
        const stamps=capture.sourceStamps.map(stamp=>({...stamp,documentId:normalize(stamp.documentId)}));
        const changes=edit.documentChanges.map(change=>{
            if(!TextDocumentEdit.is(change))throw new Error('GRETL file operations are unsupported.');
            return {uri:normalize(change.textDocument.uri),version:change.textDocument.version};
        });
        await validateEditSources(stamps,changes,this.captures.get(capture)??new Map(),()=>new Map(vscode.workspace.textDocuments.map(doc=>[doc.uri.toString(),doc])));
        await currentContext();
        if(this.client!==client||capture.serverInstanceId!==this.instance||!client.isRunning())throw new Error('GRETL edit belongs to an obsolete session. Retry.');
    }
    private async stop():Promise<void>{
        ++this.generation;this.instance=undefined;this.actions.clear();
        for(const listener of this.sessionListeners.splice(0))listener.dispose();
        const fragments=this.fragments;this.fragments=undefined;await fragments?.dispose();
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
