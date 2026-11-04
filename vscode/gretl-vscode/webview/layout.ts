import ELK,{ElkNode,ELK as ElkEngine} from 'elkjs/lib/elk-api';
import {GraphNode,GraphEdge,Position,RequestGate,MAX_NODES} from '../src/modeler/graph-model';

export interface Geometry {id:string;x:number;y:number;width:number;height:number}
export interface WorkerResource {url:string;dispose():void}
export async function localWorkerResource(uri:string,signal:AbortSignal):Promise<WorkerResource> {
    const response=await fetch(uri,{signal});if(!response.ok)throw Error('LAYOUT_WORKER_RESOURCE');
    const source=await response.arrayBuffer();if(source.byteLength>4*1024*1024)throw Error('LAYOUT_WORKER_RESOURCE_LIMIT');
    const url=URL.createObjectURL(new Blob([source],{type:'text/javascript'}));
    return {url,dispose:()=>URL.revokeObjectURL(url)};
}
/** A real local ELK worker, with one bounded request and no synchronous fallback. */
export class LayoutService {
    private readonly requests=new RequestGate();
    private active?:ElkEngine;
    private reject?:()=>void;
    private disposed=false;
    private loading?:AbortController;
    constructor(private readonly workerUri:string,private readonly resourceLoader=localWorkerResource){}
    cancel():void{this.requests.next();this.loading?.abort();this.active?.terminateWorker();this.active=undefined;this.reject?.();this.reject=undefined;}
    async layout(nodes:GraphNode[],edges:GraphEdge[],positions:Record<string,Position>):Promise<Geometry[]> {
        if(this.disposed)throw Error('LAYOUT_DISPOSED');
        this.cancel();const ticket=this.requests.next();if(nodes.length>MAX_NODES)throw Error('GRAPH_VIEW_LIMIT');if(!nodes.length)return [];
        const tree=new Map<string,ElkNode>();
        // Reserve enough lines for the bounded card title, including wide glyphs.
        // Geometry is presentation only; the complete title stays in Details.
        const titleLines=(node:GraphNode)=>Math.max(1,Math.ceil(Math.min(node.title.length,120)/16));
        for(const node of nodes)tree.set(node.id,{id:node.id,width:244,height:node.kind==='PROJECT'?Math.max(70,32+titleLines(node)*18):112+Math.max(0,titleLines(node)-2)*18+Math.max(0,Math.ceil((node.operationId?.length??0)/28)-1)*15,children:[]});
        const roots:ElkNode[]=[];
        for(const node of nodes){const value=tree.get(node.id)!;if(node.parentId&&tree.has(node.parentId))tree.get(node.parentId)!.children!.push(value);else roots.push(value);}
        for(const node of nodes){const value=tree.get(node.id)!;if(value.children?.length)value.layoutOptions={'elk.padding':`[top=${Math.max(48,32+titleLines(node)*18)},left=24,bottom=24,right=24]`,'elk.direction':'DOWN','elk.spacing.nodeNode':'32','elk.layered.spacing.nodeNodeBetweenLayers':'70'};}
        const ids=new Set(nodes.map(n=>n.id));
        const loading=this.loading=new AbortController();let resource:WorkerResource|undefined,elk:ElkEngine|undefined;
        let rejectLayout:((reason:Error)=>void)|undefined;
        const timer=setTimeout(()=>{loading.abort();elk?.terminateWorker();rejectLayout?.(Error('LAYOUT_TIMEOUT'));},30000);
        try {
            resource=await this.resourceLoader(this.workerUri,loading.signal);
            if(!this.requests.accepts(ticket)||loading.signal.aborted)throw Error('LAYOUT_CANCELLED');
            elk=this.active=new ELK({workerFactory:()=>new Worker(resource!.url)});
            const cancelled=new Promise<never>((_,reject)=>{rejectLayout=reject;this.reject=()=>reject(Error('LAYOUT_CANCELLED'));});
            const result=await Promise.race([elk.layout({id:'root',children:roots,edges:edges.filter(e=>ids.has(e.source)&&ids.has(e.target)).map(e=>({id:e.id,sources:[e.source],targets:[e.target]})),layoutOptions:{'elk.algorithm':'layered','elk.direction':'DOWN','elk.hierarchyHandling':'INCLUDE_CHILDREN','elk.edgeRouting':'ORTHOGONAL','elk.spacing.nodeNode':'32','elk.layered.spacing.nodeNodeBetweenLayers':'70','elk.padding':'[top=48,left=24,bottom=24,right=24]'}}),cancelled]);
            if(!this.requests.accepts(ticket))throw Error('LAYOUT_CANCELLED');
            const geometry:Geometry[]=[];const definitions=new Map(nodes.map(n=>[n.id,n]));
            const visit=(values:ElkNode[])=>{for(const value of values){if(!definitions.has(value.id)||geometry.length>=MAX_NODES)throw Error('LAYOUT_PAYLOAD_INVALID');const node=definitions.get(value.id)!;const parent=node.parentId?definitions.get(node.parentId):undefined;const saved=node.presentationKey?positions[node.presentationKey]:undefined;
                const sameParent=saved?.parentKey===(parent?.presentationKey??null);const x=sameParent?saved.x:value.x??0,y=sameParent?saved.y:value.y??0;
                const g={id:value.id,x:parent?Math.max(16,x):x,y:parent?Math.max(48,y):y,width:value.width??244,height:value.height??112};
                if(!Object.values(g).filter(v=>typeof v==='number').every(v=>Number.isFinite(v)&&Math.abs(Number(v))<=1e7))throw Error('LAYOUT_PAYLOAD_INVALID');geometry.push(g);visit(value.children??[]);
            }};visit(result.children??[]);
            const byId=new Map(geometry.map(g=>[g.id,g]));
            for(const node of [...nodes].reverse()){if(!node.parentId)continue;const child=byId.get(node.id)!,parent=byId.get(node.parentId);if(parent){parent.width=Math.max(parent.width,child.x+child.width+24);parent.height=Math.max(parent.height,child.y+child.height+24);}}
            return geometry;
        } finally {clearTimeout(timer);elk?.terminateWorker();resource?.dispose();if(this.loading===loading)this.loading=undefined;if(this.active===elk){this.active=undefined;this.reject=undefined;}}
    }
    dispose():void{this.disposed=true;this.cancel();this.requests.dispose();}
}
