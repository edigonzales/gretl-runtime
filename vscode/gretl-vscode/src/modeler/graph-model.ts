import {EditIntent,FormProjection,editIntent,formProjection} from './edit-model';
/** Wire data from the Java core. No Groovy parsing, binding or graph-selection rules live here. */
export type NodeKind='TASK'|'ACTION'|'DYNAMIC_CODE'|'PROJECT'|'INNER_INVOCATION'|'PARAMETER'|'CLEANUP';
export type EdgeKind='DEPENDS_ON'|'AFTER'|'RESULT_BINDING';
export interface Anchor {documentId:string;span?:{startUtf16:number;endUtf16:number}|null}
export interface Diagnostic {code:string;severity:'ERROR'|'WARNING'|'INFORMATION';message:string;anchor:Anchor}
export interface SourceStamp {documentId:string;sha256?:string|null;version?:number|null;incarnation?:string|null;editable:boolean;unavailableCode?:string|null}
export interface GraphContext {protocolVersion:1;schemaVersion:1;serverInstanceId:string;projectId:string;workspaceGeneration:string;snapshotId:string;catalogId?:string;sourceStamps:SourceStamp[];completeness:Record<string,boolean>;notices:string[]}
export interface GraphNode {id:string;presentationKey?:string|null;symbol?:{namespace:string;qualifiedName:string}|null;anchor:Anchor;kind:NodeKind;title:string;operationId?:string|null;parentId?:string|null;editCapabilities:Record<string,{state:string;reason?:string;[key:string]:unknown}>;inputs:{name:string;[key:string]:unknown}[];outputs:{name:string;[key:string]:unknown}[];diagnostics:Diagnostic[]}
export interface GraphEdge {id:string;source:string;target:string;kind:EdgeKind;anchor:Anchor;outputField?:string|null;inputField?:string|null;schemaKnown:boolean}
export interface GraphResponse {context:GraphContext;payload:{status:'COMPLETE'|'PARTIAL'|'INVALID';nodes:GraphNode[];edges:GraphEdge[];parameters:GraphNode[];cleanup:GraphNode[];diagnostics:Diagnostic[];forms?:FormProjection|null;preview?:{available:boolean;reason?:string|null;targets:string[];orderedSymbols:string[]}|null}}
export interface Position {x:number;y:number;parentKey:string|null}
export interface ViewState {schemaVersion:1;key:string;positions:Record<string,Position>;viewport:{x:number;y:number;zoom:number}}
export type ViewStatus='EMPTY'|'LOADING'|'FRESH'|'STALE'|'ERROR';
export const MAX_NODES=2000,MAX_VIEW_BYTES=524288;
export function object(value:unknown):value is Record<string,unknown>{return value!==null&&typeof value==='object'&&!Array.isArray(value)&&(Object.getPrototypeOf(value)===Object.prototype||Object.getPrototypeOf(value)===null);}
export function encodedSize(value:unknown):number{try{return new TextEncoder().encode(JSON.stringify(value)).length;}catch{return Infinity;}}
const text=(v:unknown,max=65536):v is string=>typeof v==='string'&&v.length<=max;
const finite=(v:unknown):v is number=>typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e7;
export function validAnchor(v:unknown):v is Anchor {
    return object(v)&&text(v.documentId,8192)&&(v.span==null||object(v.span)&&Number.isSafeInteger(v.span.startUtf16)&&Number.isSafeInteger(v.span.endUtf16)&&Number(v.span.startUtf16)>=0&&Number(v.span.endUtf16)>=Number(v.span.startUtf16));
}
/** Bounds the trusted-server DTO before handing it to the UI/worker; this is transport validation only. */
export function graphResponse(v:unknown):GraphResponse {
    if(!object(v)||encodedSize(v)>16*1024*1024||!object(v.context)||!object(v.payload))throw Error('GRAPH_PAYLOAD_INVALID');
    const c=v.context,p=v.payload;
    if(c.protocolVersion!==1||c.schemaVersion!==1||!text(c.serverInstanceId,128)||!text(c.snapshotId,128)||!text(c.projectId,16384)||!text(c.workspaceGeneration,32)||!Array.isArray(c.sourceStamps)||c.sourceStamps.length>4096)throw Error('GRAPH_CONTEXT_INVALID');
    for(const s of c.sourceStamps)if(!object(s)||!text(s.documentId,8192)||s.version!=null&&(!Number.isSafeInteger(s.version)||Number(s.version)<0)||s.sha256!=null&&!/^[a-f0-9]{64}$/.test(String(s.sha256)))throw Error('GRAPH_STAMP_INVALID');
    if(!['COMPLETE','PARTIAL','INVALID'].includes(String(p.status)))throw Error('GRAPH_STATUS_INVALID');
    for(const key of ['nodes','parameters','cleanup','edges','diagnostics'])if(!Array.isArray(p[key]))throw Error('GRAPH_COLLECTION_INVALID');
    const nodes=[...(p.nodes as unknown[]),...(p.parameters as unknown[]),...(p.cleanup as unknown[])];
    if(nodes.length>MAX_NODES||(p.edges as unknown[]).length>10000||(p.diagnostics as unknown[]).length>10000)throw Error('GRAPH_VIEW_LIMIT');
    const ids=new Set<string>();
    for(const n of nodes){
        if(!object(n)||!text(n.id,128)||ids.has(n.id)||!text(n.title)||!['TASK','ACTION','DYNAMIC_CODE','PROJECT','INNER_INVOCATION','PARAMETER','CLEANUP'].includes(String(n.kind))||!validAnchor(n.anchor)||!Array.isArray(n.inputs)||!Array.isArray(n.outputs)||!Array.isArray(n.diagnostics)||!object(n.editCapabilities))throw Error('GRAPH_NODE_INVALID');
        if(n.presentationKey!=null&&!text(n.presentationKey,131072)||n.parentId!=null&&!text(n.parentId,128))throw Error('GRAPH_NODE_INVALID');
        ids.add(n.id);
    }
    for(const n of nodes as GraphNode[])if(n.parentId&&!ids.has(n.parentId))throw Error('GRAPH_PARENT_INVALID');
    const parents=new Map((nodes as GraphNode[]).map(n=>[n.id,n.parentId]));
    for(const n of nodes as GraphNode[]){let parent=n.parentId;let depth=0;while(parent){if(parent===n.id||++depth>4)throw Error('GRAPH_PARENT_INVALID');parent=parents.get(parent);}}
    for(const edge of p.edges as unknown[]){if(!object(edge)||!text(edge.id,128)||ids.has(edge.id)||!ids.has(String(edge.source))||!ids.has(String(edge.target))||!['DEPENDS_ON','AFTER','RESULT_BINDING'].includes(String(edge.kind))||!validAnchor(edge.anchor))throw Error('GRAPH_EDGE_INVALID');ids.add(edge.id);}
    for(const d of p.diagnostics as unknown[])if(!object(d)||!text(d.code,128)||!text(d.message)||!validAnchor(d.anchor)||!['ERROR','WARNING','INFORMATION'].includes(String(d.severity)))throw Error('GRAPH_DIAGNOSTIC_INVALID');
    if(p.preview!=null){const preview=p.preview;if(!object(preview)||typeof preview.available!=='boolean'||!Array.isArray(preview.targets)||!Array.isArray(preview.orderedSymbols)||preview.targets.some(t=>!text(t))||preview.orderedSymbols.some(t=>!text(t)))throw Error('GRAPH_PREVIEW_INVALID');}
    if(p.forms!=null)p.forms=formProjection(p.forms,new Set(nodes.map(n=>(n as GraphNode).id)),new Set((c.sourceStamps as SourceStamp[]).map(s=>s.documentId)));
    return v as unknown as GraphResponse;
}
export function viewKey(graph:GraphResponse,documentId:string,slot=0):string{return JSON.stringify(slot?[1,graph.context.projectId,documentId,slot]:[1,graph.context.projectId,documentId]);}
export function readViewState(value:unknown,key:string):ViewState|undefined {
    if(!object(value)||value.schemaVersion!==1||value.key!==key||encodedSize(value)>MAX_VIEW_BYTES||!object(value.positions)||!object(value.viewport))return;
    const v=value.viewport;if(!finite(v.x)||!finite(v.y)||typeof v.zoom!=='number'||!Number.isFinite(v.zoom)||v.zoom<0.05||v.zoom>4)return;
    const entries=Object.entries(value.positions);if(entries.length>MAX_NODES)return;
    for(const [k,p]of entries)if(k.length>131072||!object(p)||!finite(p.x)||!finite(p.y)||p.parentKey!==null&&!text(p.parentKey,131072))return;
    return {schemaVersion:1,key,positions:Object.fromEntries(entries.map(([k,p])=>{const position=p as unknown as Position;return[k,{x:position.x,y:position.y,parentKey:position.parentKey}];})),viewport:{x:v.x,y:v.y,zoom:v.zoom}};
}
/** Never reuse an ambiguous presentation identity or a position from another project/document. */
export function reconcilePositions(graph:GraphResponse,state:ViewState|undefined,key:string):Record<string,Position> {
    if(!state||state.key!==key)return {};
    const all=[...graph.payload.nodes,...graph.payload.parameters,...graph.payload.cleanup],counts=new Map<string,number>();
    for(const n of all)if(n.presentationKey)counts.set(n.presentationKey,(counts.get(n.presentationKey)??0)+1);
    const byId=new Map(all.map(n=>[n.id,n]));const result=new Map<string,Position>();
    for(const n of all){const k=n.presentationKey;if(!k||counts.get(k)!==1)continue;const p=state.positions[k];const parentKey=n.parentId?byId.get(n.parentId)?.presentationKey??null:null;if(p&&p.parentKey===parentKey)result.set(k,{x:p.x,y:p.y,parentKey:p.parentKey});}
    return Object.fromEntries(result);
}
export class RequestGate {
    private sequence=0;private disposed=false;
    next():number{return ++this.sequence;}
    accepts(ticket:number):boolean{return !this.disposed&&ticket===this.sequence;}
    dispose():void{this.disposed=true;++this.sequence;}
}
export type ModelerMessage={protocolVersion:1;panelId:string;kind:'ready'|'refresh'|'source'|'problems'}
    |{protocolVersion:1;panelId:string;kind:'intent';requestId:string;snapshotId:string;intent:EditIntent}
    |{protocolVersion:1;panelId:string;kind:'openRequiredSources';requestId:string}
    |{protocolVersion:1;panelId:string;kind:'undoText'|'redoText'|'saveSource'}
    |{protocolVersion:1;panelId:string;kind:'inspection';requestId:string;snapshotId:string;view:Record<string,unknown>}
    |{protocolVersion:1;panelId:string;kind:'reveal';snapshotId:string;elementId:string}
    |{protocolVersion:1;panelId:string;kind:'options';previewTargets:string[]|null;includeInnerInvocations:boolean}
    |{protocolVersion:1;panelId:string;kind:'viewState';snapshotId:string;state:ViewState};
export function modelerMessage(value:unknown,panelId:string):ModelerMessage|undefined {
    if(!object(value)||encodedSize(value)>MAX_VIEW_BYTES+16384||value.protocolVersion!==1||value.panelId!==panelId)return;
    const base=['protocolVersion','panelId','kind'];let fields:string[];
    switch(value.kind){
        case 'openRequiredSources':if(!text(value.requestId,128))return;fields=[...base,'requestId'];break;
        case 'undoText':case 'redoText':case 'saveSource':fields=base;break;
        case 'intent':if(!text(value.requestId,128)||!text(value.snapshotId,128)||!editIntent(value.intent))return;fields=[...base,'requestId','snapshotId','intent'];break;
        case 'inspection':if(!text(value.requestId,128)||!text(value.snapshotId,128)||!object(value.view))return;fields=[...base,'requestId','snapshotId','view'];break;
        case 'ready':case 'refresh':case 'source':case 'problems':fields=base;break;
        case 'reveal':if(!text(value.snapshotId,128)||!text(value.elementId,128))return;fields=[...base,'snapshotId','elementId'];break;
        case 'options':if(typeof value.includeInnerInvocations!=='boolean'||value.previewTargets!==null&&(!Array.isArray(value.previewTargets)||value.previewTargets.length>1024||value.previewTargets.some(v=>!text(v))))return;fields=[...base,'previewTargets','includeInnerInvocations'];break;
        case 'viewState':if(!text(value.snapshotId,128)||!object(value.state)||!text(value.state.key,32768)||!readViewState(value.state,value.state.key))return;fields=[...base,'snapshotId','state'];break;
        default:return;
    }
    if(Object.keys(value).some(k=>!fields.includes(k)))return;
    return value as unknown as ModelerMessage;
}
