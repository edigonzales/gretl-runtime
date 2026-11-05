import * as vscode from 'vscode';
import {randomUUID} from 'node:crypto';
import {sourceDigest} from '../host';
import {ModelerBackend,ModelerOptions} from './backend';
import {GraphResponse,ViewState,ViewStatus,RequestGate,modelerMessage,readViewState,viewKey} from './graph-model';
import {ViewStateStore} from './view-state-store';
import {ModelerEditRejected} from './edit-plan';

const VIEW_TYPE='gretlRuntime.modeler';
const normalize=(uri:string)=>vscode.Uri.parse(uri).toString();
const escape=(value:string)=>value.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;').replaceAll('>','&gt;');

export class GretlModelerProvider implements vscode.CustomTextEditorProvider,vscode.Disposable {
    private readonly panels=new Set<ModelerPanelController>();
    private readonly languageTransitions=new Map<string,Promise<vscode.TextDocument>>();
    private readonly listeners:vscode.Disposable[]=[];
    private readonly store:ViewStateStore;
    private disposed=false;
    constructor(private readonly extension:vscode.ExtensionContext,private readonly backend:ModelerBackend){
        this.store=new ViewStateStore(extension.workspaceState);
        this.listeners.push(vscode.window.registerCustomEditorProvider(VIEW_TYPE,this,{supportsMultipleEditorsPerDocument:true,webviewOptions:{retainContextWhenHidden:true}}),
            // Read-only support/evidence query; never changes the view or source.
            vscode.commands.registerCommand('gretlRuntime.inspectModeler',()=>Promise.all([...this.panels].filter(p=>p.visible).map(p=>p.inspect()))),
            vscode.commands.registerCommand('gretlRuntime.openModeler',async(resource?:unknown)=>{
                const uri=resource instanceof vscode.Uri?resource:vscode.window.activeTextEditor?.document.uri;
                if(!uri&&[...this.panels].some(panel=>panel.active))return;
                if(!uri||uri.scheme!=='file'||!vscode.workspace.getWorkspaceFolder(uri)){void vscode.window.showInformationMessage('Open a local GRETL job in a workspace first.');return;}
                const column=vscode.window.activeTextEditor?.viewColumn??vscode.ViewColumn.Active;
                const doc=await vscode.workspace.openTextDocument(uri);
                if(doc.languageId!=='gretl-runtime')await vscode.languages.setTextDocumentLanguage(doc,'gretl-runtime');
                await vscode.commands.executeCommand('vscode.openWith',uri,VIEW_TYPE,{viewColumn:column});
            }),
            vscode.commands.registerCommand('gretlRuntime.showSelectionInModeler',async()=>{
                const editor=vscode.window.activeTextEditor;
                if(!editor){void vscode.window.showInformationMessage('Select a position in the GRETL source editor first.');return;}
                const exact=[...this.panels].filter(p=>p.matches(editor.document.uri,true));
                const candidates=exact.length?exact:[...this.panels].filter(p=>p.matches(editor.document.uri,false));
                if(!candidates.length){void vscode.window.showInformationMessage('No matching GRETL Modeler panel is open.');return;}
                const chosen=candidates.length===1?candidates[0]:(await vscode.window.showQuickPick(candidates.map(controller=>({label:controller.label,controller})),{placeHolder:'Choose the pinned modeler panel'}))?.controller;
                if(chosen)await chosen.selectSource(editor,true);
            }));
    }
    async resolveCustomTextEditor(document:vscode.TextDocument,panel:vscode.WebviewPanel,token:vscode.CancellationToken):Promise<void> {
        if(this.disposed)return;
        if(this.panels.size>=8||document.uri.scheme!=='file'||!vscode.workspace.getWorkspaceFolder(document.uri)){
            panel.webview.html='<html><body><p>GRETL Modeler requires a local workspace and supports at most eight open panels.</p></body></html>';return;
        }
        // Explicitly opened non-job sources can revert to Groovy on workbench
        // restart. Restore the GRETL language before pinning the new incarnation.
        if(document.languageId!=='gretl-runtime'){
            const key=document.uri.toString();let pending=this.languageTransitions.get(key);
            if(!pending){pending=Promise.resolve(vscode.workspace.openTextDocument(document.uri)).then(current=>current.languageId==='gretl-runtime'?current:vscode.languages.setTextDocumentLanguage(current,'gretl-runtime'));this.languageTransitions.set(key,pending);}
            try{document=await pending;}finally{if(this.languageTransitions.get(key)===pending)this.languageTransitions.delete(key);}
        }
        if(token.isCancellationRequested||this.disposed)return;
        if(this.panels.size>=8){panel.webview.html='<html><body><p>GRETL Modeler supports at most eight open panels.</p></body></html>';return;}
        const occupied=new Set([...this.panels].filter(p=>p.matches(document.uri,true)).map(p=>p.viewSlot));
        let slot=0;while(occupied.has(slot))slot++;
        const controller=new ModelerPanelController(this.extension,this.backend,this.store,document,panel,slot,()=>this.panels.delete(controller));
        this.panels.add(controller);if(token.isCancellationRequested)controller.dispose();
    }
    dispose():void{this.disposed=true;for(const panel of [...this.panels])panel.dispose();this.languageTransitions.clear();for(const listener of this.listeners.splice(0))listener.dispose();}
}

class ModelerPanelController implements vscode.Disposable {
    private readonly id=randomUUID();
    private readonly requests=new RequestGate();
    private readonly selections=new RequestGate();
    private readonly listeners:vscode.Disposable[]=[];
    private pending?:vscode.CancellationTokenSource;
    private selection?:vscode.CancellationTokenSource;
    private reveal?:vscode.CancellationTokenSource;
    private edit?:vscode.CancellationTokenSource;
    private requiredSources?:{requestId:string;uris:string[]};
    private timer?:ReturnType<typeof setTimeout>;
    private graph?:GraphResponse;
    private status:ViewStatus='LOADING';
    private ready=false;
    private disposed=false;
    private options:ModelerOptions={includeInnerInvocations:false,previewTargets:null};
    private view?:ViewState;
    private inspection?:{id:string;resolve:(value:unknown)=>void;timer:ReturnType<typeof setTimeout>};
    constructor(private readonly extension:vscode.ExtensionContext,private readonly backend:ModelerBackend,private readonly store:ViewStateStore,
                private readonly document:vscode.TextDocument,private readonly panel:vscode.WebviewPanel,readonly viewSlot:number,private readonly removed:()=>void){
        const assets=vscode.Uri.joinPath(extension.extensionUri,'dist','modeler');
        panel.webview.options={enableScripts:true,localResourceRoots:[assets]};
        const nonce=randomUUID().replaceAll('-','');
        const uri=(name:string)=>panel.webview.asWebviewUri(vscode.Uri.joinPath(assets,name)).toString();
        const bootstrap=JSON.stringify({protocolVersion:1,panelId:this.id,viewSlot,documentId:document.uri.toString(),label:vscode.workspace.asRelativePath(document.uri),workerUri:uri('elk-worker.min.js')}).replaceAll('<','\\u003c').replaceAll('>','\\u003e').replaceAll('&','\\u0026');
        panel.webview.html=`<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src ${escape(panel.webview.cspSource)} 'unsafe-inline'; worker-src blob:; connect-src ${escape(panel.webview.cspSource)}; img-src ${escape(panel.webview.cspSource)} data:; font-src ${escape(panel.webview.cspSource)};"><link rel="stylesheet" href="${escape(uri('view.css'))}"><title>GRETL Modeler</title></head><body><div id="root"></div><script id="bootstrap" type="application/json">${bootstrap}</script><script nonce="${nonce}" src="${escape(uri('view.js'))}"></script></body></html>`;
        this.listeners.push(panel.webview.onDidReceiveMessage(value=>{void this.message(value).catch(error=>this.failure(error));}),panel.onDidDispose(()=>this.dispose()),
            panel.onDidChangeViewState(()=>{if(panel.visible&&this.status==='STALE')this.refresh('Panel visible');}),
            vscode.workspace.onDidChangeTextDocument(event=>{if(event.contentChanges.length&&this.owns(event.document.uri))this.invalidate('Source changed');}),
            vscode.workspace.onDidCloseTextDocument(doc=>{if(this.owns(doc.uri))this.invalidate('Source closed');}),
            vscode.window.onDidChangeTextEditorSelection(event=>{void this.selectSource(event.textEditor,false);}),
            backend.onDidChange(available=>{this.invalidate(available?'Server ready':'Server unavailable',available);}),
            backend.onDidAnalyze(context=>{if(this.graph&&context.projectId===this.graph.context.projectId&&context.snapshotId!==this.graph.context.snapshotId)this.invalidate('Project changed');}));
    }
    private owns(uri:vscode.Uri):boolean{return uri.toString()===this.document.uri.toString()||!!this.graph?.context.sourceStamps.some(s=>normalize(s.documentId)===uri.toString());}
    matches(uri:vscode.Uri,onlyPinned:boolean):boolean{return onlyPinned?this.document.uri.toString()===uri.toString():this.owns(uri);}
    get label():string{return `${vscode.workspace.asRelativePath(this.document.uri)} · editor group ${this.panel.viewColumn??'hidden'}`;}
    get active():boolean{return this.panel.active;}
    get visible():boolean{return this.panel.visible;}
    inspect():Promise<unknown>{
        this.finishInspection({unavailable:'superseded'});
        if(!this.ready||!this.graph||this.disposed)return Promise.resolve({unavailable:'not ready'});
        return new Promise(resolve=>{const id=randomUUID(),timer=setTimeout(()=>this.finishInspection({unavailable:'timeout'}),3000);this.inspection={id,resolve,timer};this.post({kind:'inspect',requestId:id,snapshotId:this.graph!.context.snapshotId});});
    }
    private finishInspection(value:unknown):void{const pending=this.inspection;if(pending){this.inspection=undefined;clearTimeout(pending.timer);pending.resolve(value);}}
    private post(value:Record<string,unknown>):void{if(!this.disposed&&this.ready)void this.panel.webview.postMessage({protocolVersion:1,panelId:this.id,...value});}
    private state(status:ViewStatus,reason:string):void{this.status=status;this.post({kind:'state',status,reason});}
    private invalidate(reason:string,refresh=true):void {
        this.requests.next();this.pending?.cancel();this.selections.next();this.selection?.cancel();this.reveal?.cancel();this.edit?.cancel();
        this.state(this.graph?'STALE':'LOADING',reason);clearTimeout(this.timer);
        if(refresh&&this.ready&&this.panel.visible)this.timer=setTimeout(()=>this.refresh(reason),120);
    }
    private refresh(reason:string):void {
        if(!this.ready||this.disposed)return;const ticket=this.requests.next();this.pending?.cancel();this.pending?.dispose();
        const cancellation=this.pending=new vscode.CancellationTokenSource();this.state(this.graph?'STALE':'LOADING',reason);
        void this.backend.graph(this.document,this.options,cancellation.token).then(async graph=>{
            if(!this.requests.accepts(ticket)||cancellation.token.isCancellationRequested)return;
            const key=viewKey(graph,this.document.uri.toString(),this.viewSlot);this.view=this.view?.key===key?this.view:this.store.load(key);
            this.graph=graph;this.status=graph.payload.nodes.length||graph.payload.parameters.length||graph.payload.cleanup.length?'FRESH':'EMPTY';
            this.panel.title=`${vscode.workspace.asRelativePath(this.document.uri)}${this.options.sourceContext?' · '+this.options.sourceContext.ownerScope:''}`;
            this.post({kind:'graph',graph,status:this.status,key,viewState:this.view,options:{includeInnerInvocations:this.options.includeInnerInvocations,previewTargets:this.options.previewTargets},sourceContext:this.options.sourceContext});
            try{const catalog=await this.backend.catalog(graph,cancellation.token);if(this.graph===graph&&!cancellation.token.isCancellationRequested)this.post({kind:'catalog',snapshotId:graph.context.snapshotId,catalog});}
            catch(error){if(!cancellation.token.isCancellationRequested&&this.graph===graph)this.post({kind:'catalogUnavailable',reason:error instanceof Error?error.message:String(error)});}
        }).catch(error=>{if(this.requests.accepts(ticket)&&!cancellation.token.isCancellationRequested)this.failure(error);});
    }
    private async message(value:unknown):Promise<void>{
        const message=modelerMessage(value,this.id);if(!message||this.disposed)return;
        switch(message.kind){
            case 'openRequiredSources':{
                const required=this.requiredSources;if(required?.requestId!==message.requestId||!this.graph)return;this.requiredSources=undefined;
                for(const uri of required.uris){
                    if(this.disposed)return;
                    if(!this.graph.context.sourceStamps.some(s=>normalize(s.documentId)===normalize(uri)))throw Error('The requested source is no longer part of this snapshot.');
                    const parsed=vscode.Uri.parse(uri);if(parsed.scheme!=='file'||!vscode.workspace.getWorkspaceFolder(parsed))throw Error('Only captured local workspace sources can be opened.');
                    await vscode.workspace.openTextDocument(parsed);
                }
                this.invalidate('Required sources opened. Reopen the draft against the refreshed graph.');return;
            }
            case 'undoText':case 'redoText':case 'saveSource':{
                if(this.edit||this.document.isClosed)return;
                if(message.kind==='saveSource'){if(!await this.document.save())this.post({kind:'notice',reason:'The source could not be saved.'});return;}
                await vscode.window.showTextDocument(this.document,{viewColumn:this.sourceColumn(),preserveFocus:false});
                if(this.disposed||this.document.isClosed||vscode.window.activeTextEditor?.document!==this.document)return;
                await vscode.commands.executeCommand(message.kind==='undoText'?'undo':'redo');return;
            }
            case 'intent':{
                if(!this.graph||!['FRESH','EMPTY'].includes(this.status)||message.snapshotId!==this.graph.context.snapshotId||this.edit){this.post({kind:'editResult',requestId:message.requestId,applied:false,reasons:['The source changed or another edit is pending. Refresh and confirm again.']});return;}
                this.requiredSources=undefined;const graph=this.graph,token=this.edit=new vscode.CancellationTokenSource();
                try{const notices=await this.backend.commit(this.document,graph,message.intent,token.token);this.post({kind:'editResult',requestId:message.requestId,applied:true,notices});if(!this.disposed)this.invalidate('Edit applied');}
                catch(error){const uris=error instanceof ModelerEditRejected?error.documentsToOpen:[];if(uris.length)this.requiredSources={requestId:message.requestId,uris};this.post({kind:'editResult',requestId:message.requestId,applied:false,reasons:error instanceof ModelerEditRejected?error.reasons:[error instanceof Error?error.message:String(error)],requiredOpenDocuments:uris});}
                finally{if(this.edit===token)this.edit=undefined;token.dispose();}return;
            }
            case 'inspection':if(this.inspection?.id===message.requestId&&this.graph?.context.snapshotId===message.snapshotId)this.finishInspection({documentId:this.document.uri.toString(),version:this.document.version,dirty:this.document.isDirty,status:this.status,context:this.graph.context,view:message.view});return;
            case 'ready':this.ready=true;this.refresh('Open');return;
            case 'refresh':this.refresh('Refresh');return;
            case 'options':this.options={sourceContext:this.options.sourceContext,includeInnerInvocations:message.includeInnerInvocations,previewTargets:message.previewTargets};this.invalidate('View changed');return;
            case 'problems':await vscode.commands.executeCommand('workbench.actions.view.problems');return;
            case 'source':await vscode.window.showTextDocument(this.document,{viewColumn:this.sourceColumn(),preserveFocus:false});return;
            case 'viewState':if(this.graph&&message.snapshotId===this.graph.context.snapshotId){const view=readViewState(message.state,viewKey(this.graph,this.document.uri.toString(),this.viewSlot));if(view){this.view=view;if(!await this.store.save(view))this.post({kind:'notice',reason:'View positions could not be persisted.'});}}return;
            case 'reveal':{
                if(!this.graph||!['FRESH','EMPTY'].includes(this.status)||message.snapshotId!==this.graph.context.snapshotId)return;
                this.reveal?.cancel();this.reveal?.dispose();
                const graph=this.graph,token=this.reveal=new vscode.CancellationTokenSource();
                try {
                    const result=await this.backend.navigate(this.document.uri.toString(),graph.context.snapshotId,message.elementId,undefined,token.token,this.options.sourceContext);
                    if(this.disposed||token.token.isCancellationRequested||this.graph!==graph||!['FRESH','EMPTY'].includes(this.status)||!result.location)return;
                    const location=result.location,stamp=graph.context.sourceStamps.find(s=>normalize(s.documentId)===normalize(location.uri));if(!stamp?.sha256)return;
                    const document=await vscode.workspace.openTextDocument(vscode.Uri.parse(location.uri));if(this.disposed||token.token.isCancellationRequested||this.graph!==graph)return;if(sourceDigest(document.getText())!==stamp.sha256){this.invalidate('Navigation source changed');return;}
                    const r=location.range;if(![r.start.line,r.start.character,r.end.line,r.end.character].every(n=>Number.isSafeInteger(n)&&n>=0))return;
                    const range=new vscode.Range(r.start.line,r.start.character,r.end.line,r.end.character);
                    const editor=await vscode.window.showTextDocument(document,{viewColumn:this.sourceColumn(),preserveFocus:false,selection:range});editor.revealRange(range,vscode.TextEditorRevealType.InCenterIfOutsideViewport);
                    if(result.sourceContext)await this.backend.activateSourceContext?.(location.uri,result.sourceContext,token.token);
                } catch(error){if(!this.disposed&&!token.token.isCancellationRequested)throw error;}
                finally {if(this.reveal===token)this.reveal=undefined;token.dispose();}return;
            }
        }
    }
    private sourceColumn():vscode.ViewColumn{return this.panel.viewColumn===vscode.ViewColumn.One?vscode.ViewColumn.Two:vscode.ViewColumn.One;}
    async selectSource(editor:vscode.TextEditor,focus:boolean):Promise<void>{
        if(!this.graph||this.disposed||!this.ready||!this.owns(editor.document.uri)||!['FRESH','EMPTY'].includes(this.status))return;
        const graph=this.graph,ticket=this.selections.next();this.selection?.cancel();this.selection?.dispose();const token=this.selection=new vscode.CancellationTokenSource();
        // A revealed declaration selects a half-open source range. Its end can
        // lie outside the declaration; map the start to retain that selection.
        const position=editor.selection.isEmpty?editor.selection.active:editor.selection.start;
        try {const result=await this.backend.navigate(editor.document.uri.toString(),graph.context.snapshotId,undefined,editor.document.offsetAt(position),token.token,this.options.sourceContext);
            if(this.graph!==graph||!this.selections.accepts(ticket)||token.token.isCancellationRequested)return;
            this.post({kind:'selection',snapshotId:graph.context.snapshotId,elements:result.elements,focus});if(focus)this.panel.reveal(this.panel.viewColumn,false);
        }catch{/* Selection races are retried on the next source selection or graph refresh. */}
    }
    private failure(error:unknown):void{if(!this.disposed)this.state(this.graph?'STALE':'ERROR',error instanceof Error?error.message:String(error));}
    dispose():void{if(this.disposed)return;this.disposed=true;this.graph=undefined;this.finishInspection({unavailable:'disposed'});clearTimeout(this.timer);this.requests.dispose();this.selections.dispose();this.pending?.cancel();this.pending?.dispose();this.selection?.cancel();this.selection?.dispose();this.reveal?.cancel();this.reveal?.dispose();this.edit?.cancel();this.edit?.dispose();for(const listener of this.listeners.splice(0))listener.dispose();this.removed();}
}
