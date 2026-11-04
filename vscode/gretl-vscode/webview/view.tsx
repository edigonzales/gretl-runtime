import React,{useState,useEffect,useRef,useCallback,memo} from 'react';
import {createRoot} from 'react-dom/client';
import {ReactFlow,ReactFlowProvider,Background,Controls,MiniMap,Handle,Position as HandlePosition,MarkerType,BaseEdge,EdgeText,EdgeProps,applyNodeChanges,applyEdgeChanges,useReactFlow,useNodesInitialized,Node,Edge,NodeProps,NodeChange,EdgeChange} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import './view.css';
import {GraphResponse,GraphNode,GraphEdge,ViewState,ViewStatus,Position,graphResponse,viewKey,reconcilePositions,readViewState,object} from '../src/modeler/graph-model';
import {LayoutService} from './layout';

declare function acquireVsCodeApi():{postMessage(message:unknown):void;getState():unknown;setState(state:ViewState):void};
const vscode=acquireVsCodeApi();
const bootstrap=JSON.parse(document.getElementById('bootstrap')!.textContent!);
const send=(message:Record<string,unknown>)=>vscode.postMessage({protocolVersion:1,panelId:bootstrap.panelId,...message});
type FlowNode=Node<{definition:GraphNode;container:boolean}>;
type FlowEdge=Edge<{definition:GraphEdge;offset:number}>;
const labels={TASK:'Task',ACTION:'Action',PROJECT:'Project',DYNAMIC_CODE:'Dynamic code',INNER_INVOCATION:'Inner invocation',PARAMETER:'Parameter',CLEANUP:'Cleanup'};
const Card=memo(function Card({data,selected}:NodeProps<FlowNode>){const n=data.definition;return <div className={`graph-card kind-${n.kind.toLowerCase()} ${data.container?'container-card':''} ${selected?'selected':''}`}>
    {n.kind!=='PROJECT'&&<><Handle type="target" position={HandlePosition.Top}/><Handle type="source" position={HandlePosition.Bottom}/></>}
    <span className="node-kind">{labels[n.kind]}{n.diagnostics.length>0&&<span className="problem-count" title="Diagnostics">{n.diagnostics.length}</span>}</span>
    <strong title={n.title}>{n.title.length>120?n.title.slice(0,117)+'…':n.title}</strong>
    {!data.container&&<span className="operation">{n.operationId??(n.kind==='DYNAMIC_CODE'?'Not statically expandable':'Source declaration')}</span>}
    {n.kind==='INNER_INVOCATION'&&<span>Explanatory child</span>}
</div>});
const nodeTypes={gretl:Card};
const edgeStyle={DEPENDS_ON:{stroke:'var(--vscode-charts-blue)',strokeWidth:2},AFTER:{stroke:'var(--vscode-charts-orange)',strokeWidth:2,strokeDasharray:'9 5'},RESULT_BINDING:{stroke:'var(--vscode-charts-purple)',strokeWidth:3,strokeDasharray:'2 4'}};
const Connection=memo(function Connection({id,sourceX,sourceY,targetX,targetY,data,label,style,markerEnd,selected}:EdgeProps<FlowEdge>){
    const offset=data?.offset??0,delta=Math.max(45,Math.abs(targetY-sourceY)/2),path=`M ${sourceX} ${sourceY} C ${sourceX+offset} ${sourceY+delta}, ${targetX+offset} ${targetY-delta}, ${targetX} ${targetY}`;
    return <><BaseEdge id={id} path={path} markerEnd={markerEnd} interactionWidth={20} style={{...style,...(selected?{strokeWidth:4}: {})}}/><EdgeText x={(sourceX+targetX)/2+offset*.75} y={(sourceY+targetY)/2} label={label} labelStyle={{fill:'var(--vscode-editor-foreground)',fontSize:11}} labelBgStyle={{fill:'var(--vscode-editor-background)',stroke:'var(--vscode-panel-border)'}} labelBgPadding={[5,3]} labelBgBorderRadius={3}/></>;
});
const edgeTypes={gretl:Connection};
function App(){
    const [graph,setGraph]=useState<GraphResponse>(),[status,setStatus]=useState<ViewStatus>('LOADING'),[reason,setReason]=useState('Opening static graph…');
    const [nodes,setNodes]=useState<FlowNode[]>([]),[edges,setEdges]=useState<FlowEdge[]>([]),[selected,setSelected]=useState<string>();
    const [detailsOpen,setDetailsOpen]=useState(()=>window.matchMedia('(min-width: 1000px)').matches);
    const [filter,setFilter]=useState({TASK:true,ACTION:true,DYNAMIC_CODE:true}),[options,setOptions]=useState({includeInnerInvocations:false,previewTargets:null as string[]|null}),[layoutBusy,setLayoutBusy]=useState(false);
    const flow=useReactFlow<FlowNode,FlowEdge>();const service=useRef<LayoutService>(undefined),latest=useRef<{graph?:GraphResponse;key?:string;view?:ViewState;status:ViewStatus}>({status:'LOADING'}),layoutSequence=useRef(0),renderedSnapshot=useRef<string>(undefined);
    const initialized=useNodesInitialized(),pendingViewport=useRef<{sequence:number;view?:ViewState}>(undefined);
    useEffect(()=>{
        const pending=pendingViewport.current;if(!initialized||!flow.viewportInitialized||!pending)return;
        let cancelled=false,frame=0;
        // Node measurement and the pane's first resize can arrive in different
        // frames during custom-editor restoration. Apply the viewport only
        // after both have committed; never persist an intermediate transform.
        frame=requestAnimationFrame(()=>{frame=requestAnimationFrame(()=>{
            if(cancelled||pending.sequence!==layoutSequence.current)return;
            const applied=pending.view?flow.setViewport(pending.view.viewport):flow.fitView({padding:0.15,maxZoom:1});
            void applied.then(ok=>{if(ok&&!cancelled&&pendingViewport.current===pending)pendingViewport.current=undefined;});
        });});
        return()=>{cancelled=true;cancelAnimationFrame(frame);};
    },[initialized,nodes,flow]);
    const saveView=useCallback(()=>{
        const current=latest.current;if(pendingViewport.current||!flow.viewportInitialized||!current.graph||!current.key||!['FRESH','EMPTY'].includes(current.status)||renderedSnapshot.current!==current.graph.context.snapshotId)return;
        const values=flow.getNodes(),byId=new Map(values.map(n=>[n.id,n.data.definition])),positions:Record<string,Position>={};
        if(values.length!==current.graph.payload.nodes.length||values.some(n=>!current.graph!.payload.nodes.some(definition=>definition.id===n.id)))return;
        const counts=new Map<string,number>();for(const n of values){const key=n.data.definition.presentationKey;if(key)counts.set(key,(counts.get(key)??0)+1);}
        for(const n of values){const d=n.data.definition,key=d.presentationKey;if(key&&counts.get(key)===1)positions[key]={...n.position,parentKey:d.parentId?byId.get(d.parentId)?.presentationKey??null:null};}
        const view={schemaVersion:1 as const,key:current.key,positions,viewport:flow.getViewport()};
        if(!readViewState(view,current.key))return;current.view=view;vscode.setState(view);send({kind:'viewState',snapshotId:current.graph.context.snapshotId,state:view});
    },[flow]);
    const install=useCallback(async(value:GraphResponse,stored:unknown,key:string,reset=false)=>{
        if(!service.current)return;const sequence=++layoutSequence.current;setLayoutBusy(true);
        const previous=latest.current.view;const recovered=reset?undefined:readViewState(vscode.getState(),key)??readViewState(stored,key)??(previous?.key===key?previous:undefined);
        latest.current={graph:value,key,view:recovered,status:'FRESH'};
        try {
            const positions=reset?{}:reconcilePositions(value,recovered,key);const geometry=await service.current.layout(value.payload.nodes,value.payload.edges,positions);
            if(sequence!==layoutSequence.current||latest.current.graph!==value||latest.current.status==='STALE')return;
            const byId=new Map(geometry.map(g=>[g.id,g]));const hasChildren=new Set(value.payload.nodes.map(n=>n.parentId).filter(Boolean));
            pendingViewport.current={sequence,view:recovered};
            renderedSnapshot.current=value.context.snapshotId;
            setNodes(value.payload.nodes.map(definition=>{const g=byId.get(definition.id)!;return {id:definition.id,type:'gretl',parentId:definition.parentId??undefined,extent:definition.parentId?'parent' as const:undefined,position:{x:g.x,y:g.y},style:{width:g.width,height:g.height},data:{definition,container:hasChildren.has(definition.id)||definition.kind==='PROJECT'},ariaLabel:`${labels[definition.kind]} ${definition.title}`};}));
            const parallel=new Map<string,GraphEdge[]>();for(const edge of value.payload.edges){const key=JSON.stringify([edge.source,edge.target]);parallel.set(key,[...(parallel.get(key)??[]),edge]);}
            setEdges(value.payload.edges.map(definition=>{const siblings=parallel.get(JSON.stringify([definition.source,definition.target]))!;const offset=Math.max(-420,Math.min(420,(siblings.indexOf(definition)-(siblings.length-1)/2)*140));return {id:definition.id,type:'gretl',source:definition.source,target:definition.target,data:{definition,offset},label:definition.kind==='RESULT_BINDING'?`${definition.outputField??'result'} → ${definition.inputField??'input'}${definition.schemaKnown?'':' (unknown schema)'}`:definition.kind==='AFTER'?'after · order only':'dependsOn',style:edgeStyle[definition.kind],markerEnd:{type:MarkerType.ArrowClosed,color:edgeStyle[definition.kind].stroke},ariaLabel:`${definition.kind} ${definition.source} to ${definition.target}`};}));
        }catch(error){if(sequence===layoutSequence.current&&latest.current.status!=='STALE'){setReason(error instanceof Error?error.message:String(error));setStatus('ERROR');latest.current.status='ERROR';}}
        finally {if(sequence===layoutSequence.current)setLayoutBusy(false);}
    },[flow]);
    useEffect(()=>{
        service.current=new LayoutService(bootstrap.workerUri);
        const receive=(event:MessageEvent)=>{const message=event.data;if(!object(message)||message.protocolVersion!==1||message.panelId!==bootstrap.panelId)return;
            if(message.kind==='inspect'&&typeof message.requestId==='string'&&message.requestId.length<=128&&latest.current.graph?.context.snapshotId===message.snapshotId){
                const rect=(element:Element|null)=>element?.getBoundingClientRect().toJSON();
                send({kind:'inspection',requestId:message.requestId,snapshotId:message.snapshotId,view:{viewport:flow.getViewport(),viewportInitialized:flow.viewportInitialized,pendingViewport:pendingViewport.current,canvas:rect(document.querySelector('.canvas')),transform:document.querySelector('.react-flow__viewport')?.getAttribute('style'),nodes:flow.getNodes().map(n=>({id:n.id,position:n.position,measured:n.measured,parentId:n.parentId,hidden:n.hidden})),domNodes:[...document.querySelectorAll('.react-flow__node')].slice(0,2000).map(n=>({id:n.getAttribute('data-id'),style:n.getAttribute('style'),rect:rect(n),visibility:getComputedStyle(n).visibility,opacity:getComputedStyle(n).opacity}))}});
            }else if(message.kind==='graph'){
                try{const value=graphResponse(message.graph);const key=viewKey(value,bootstrap.documentId,bootstrap.viewSlot);if(message.key!==key)return;setGraph(value);setStatus(message.status as ViewStatus);setReason('');if(object(message.options))setOptions(message.options as typeof options);setSelected(undefined);void install(value,message.viewState,key);}catch(error){setStatus('ERROR');setReason(String(error));}
            }else if(message.kind==='notice'){setReason(String(message.reason??''));}
            else if(message.kind==='state'&&['EMPTY','LOADING','FRESH','STALE','ERROR'].includes(String(message.status))){const next=message.status as ViewStatus;setStatus(next);setReason(String(message.reason??''));latest.current.status=next;if(next!=='FRESH'&&next!=='EMPTY'){++layoutSequence.current;service.current?.cancel();setLayoutBusy(false);}}
            else if(message.kind==='selection'&&latest.current.graph?.context.snapshotId===message.snapshotId&&Array.isArray(message.elements)&&['FRESH','EMPTY'].includes(latest.current.status)){
                const ids=new Set(message.elements);setNodes(previous=>previous.map(n=>({...n,selected:ids.has(n.id)})));setEdges(previous=>previous.map(e=>({...e,selected:ids.has(e.id)})));setSelected(message.elements[0] as string|undefined);
                if(message.focus)requestAnimationFrame(()=>{void flow.fitView({nodes:flow.getNodes().filter(n=>ids.has(n.id)),maxZoom:1,padding:0.5,duration:200});});
            }
        };window.addEventListener('message',receive);send({kind:'ready'});
        return()=>{window.removeEventListener('message',receive);++layoutSequence.current;service.current?.dispose();};
    },[install,flow]);
    const reveal=(id:string)=>{if(graph&&['FRESH','EMPTY'].includes(status))send({kind:'reveal',snapshotId:graph.context.snapshotId,elementId:id});};
    const all=graph?[...graph.payload.nodes,...graph.payload.parameters,...graph.payload.cleanup]:[],item=all.find(n=>n.id===selected),edge=graph?.payload.edges.find(e=>e.id===selected);
    const preview=graph?.payload.preview;const selectedSymbols=preview?.available?new Set(preview.orderedSymbols):undefined;
    const hiddenIds=new Set(nodes.filter(n=>n.data.definition.kind in filter&&!filter[n.data.definition.kind as keyof typeof filter]||selectedSymbols&&n.data.definition.symbol?.namespace==='TASK'&&!selectedSymbols.has(n.data.definition.symbol.qualifiedName)).map(n=>n.id));
    const visibleNodes=nodes.map(n=>({...n,hidden:hiddenIds.has(n.id)||(n.parentId?hiddenIds.has(n.parentId):false)}));
    const visibleIds=new Set(visibleNodes.filter(n=>!n.hidden).map(n=>n.id));const visibleEdges=edges.map(e=>({...e,hidden:!visibleIds.has(e.source)||!visibleIds.has(e.target)}));
    const changeOptions=(next:typeof options)=>{setOptions(next);send({kind:'options',...next});};
    const onNodesChange=useCallback((changes:NodeChange<FlowNode>[])=>setNodes(previous=>applyNodeChanges(changes.filter(c=>c.type!=='remove'&&c.type!=='add'),previous)),[]);
    const onEdgesChange=useCallback((changes:EdgeChange<FlowEdge>[])=>setEdges(previous=>applyEdgeChanges(changes.filter(c=>c.type!=='remove'&&c.type!=='add'),previous)),[]);
    const viewMode=options.previewTargets===null?'definitions':options.previewTargets.length?'target:'+options.previewTargets[0]:'defaults';
    return <main className={`modeler status-${status.toLowerCase()}`}>
        <header><div><h1>GRETL Modeler</h1><span className="source-label" title={bootstrap.documentId}>{bootstrap.label}</span></div><div className="header-actions"><button onClick={()=>send({kind:'source'})}>Show source</button><button onClick={()=>send({kind:'refresh'})}>Refresh</button></div></header>
        <section className="toolbar" aria-label="Graph view controls"><label>View <select aria-label="Graph view" value={viewMode} onChange={event=>changeOptions({...options,previewTargets:event.target.value==='definitions'?null:event.target.value==='defaults'?[]:[event.target.value.slice(7)]})}><option value="definitions">All definitions</option><option value="defaults">Static default-target preview</option>{all.filter(n=>n.symbol?.namespace==='TASK').map(n=><option key={n.id} value={'target:'+n.symbol!.qualifiedName}>Preview {n.title}</option>)}</select></label>
            {(['TASK','ACTION','DYNAMIC_CODE'] as const).map(kind=><label key={kind}><input type="checkbox" checked={filter[kind]} onChange={e=>setFilter({...filter,[kind]:e.target.checked})}/>{labels[kind]}</label>)}
            <label><input type="checkbox" checked={options.includeInnerInvocations} onChange={e=>changeOptions({...options,includeInnerInvocations:e.target.checked})}/>Inner invocations</label>
            <button disabled={!graph||layoutBusy} onClick={()=>{if(graph)void install(graph,undefined,viewKey(graph,bootstrap.documentId,bootstrap.viewSlot),true);}}>Reset layout</button>
            <button aria-expanded={detailsOpen} onClick={()=>setDetailsOpen(!detailsOpen)}>Details</button><button disabled={!selected||!['FRESH','EMPTY'].includes(status)} onClick={()=>{if(selected)reveal(selected);}}>Reveal selection</button>
        </section>
        <section className="status-bar" role="status"><strong>{status}</strong><span>{graph?.payload.status==='PARTIAL'?'Static analysis is partial.':graph?.payload.status==='INVALID'?'Source has diagnostics.':graph?'Static definitions · no runtime execution':''}</span>{layoutBusy&&<span>Layout…</span>}<span>{reason}</span>{preview&&!preview.available&&<span className="warning">Target preview unavailable: {preview.reason}. Showing definitions.</span>}{preview?.available&&<span>Static target closure · {preview.orderedSymbols.length} declarations</span>}</section>
        <div className="content"><section className="canvas" aria-label="GRETL static graph">{!graph&&<div className="empty-message">{status==='ERROR'?reason:'Loading static declarations…'}</div>}{graph&&status==='ERROR'&&<div className="empty-message" role="alert">{reason}</div>}{graph&&status!=='ERROR'&&graph.payload.nodes.length===0&&!layoutBusy&&<div className="empty-message">No statically known task declarations. Parameters, cleanup and diagnostics remain available.</div>}
            <ReactFlow<FlowNode,FlowEdge> nodes={visibleNodes} edges={visibleEdges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onNodeClick={(_,node)=>setSelected(node.id)} onNodeDoubleClick={(_,node)=>reveal(node.id)} onEdgeClick={(_,edge)=>setSelected(edge.id)} onEdgeDoubleClick={(_,edge)=>reveal(edge.id)} onNodeDragStop={saveView} onMoveEnd={saveView} nodesConnectable={false} nodeDragThreshold={0} deleteKeyCode={null} minZoom={0.05} maxZoom={4}><Background/><Controls showInteractive={false}/><MiniMap pannable zoomable/></ReactFlow>
            <div className="legend"><span>━━ dependsOn</span><span>┄┄ after · order</span><span>┈┈ result binding</span></div>
        </section>{detailsOpen&&<aside aria-label="Graph details"><button className="close-details" onClick={()=>setDetailsOpen(false)}>Close details</button>
            <section><h2>Selection</h2>{item?<><h3 className="full-title">{item.title}</h3><p>{labels[item.kind]}{item.operationId?' · '+item.operationId:''}</p>{item.kind==='INNER_INVOCATION'&&<p>Explanatory invocation inside its action. Not an independently scheduled task.</p>}{item.kind==='DYNAMIC_CODE'&&<p>Dynamic source is visible, but is not expanded or executed.</p>}<button disabled={!['FRESH','EMPTY'].includes(status)} onClick={()=>reveal(item.id)}>Reveal source</button>{item.inputs.length>0&&<p>Inputs: {item.inputs.map(i=>i.name).join(', ')}</p>}{item.outputs.length>0&&<p>Outputs: {item.outputs.map(i=>i.name).join(', ')}</p>}</>:edge?<><p>{edge.kind}</p><p>{edge.outputField??''} → {edge.inputField??''}</p><button disabled={!['FRESH','EMPTY'].includes(status)} onClick={()=>reveal(edge.id)}>Reveal binding source</button></>:<p>Select a node or edge. Double-click to reveal its source.</p>}</section>
            {graph&&(['parameters','cleanup'] as const).map(key=><section key={key}><h2>{key==='parameters'?'Parameters':'Cleanup'}</h2><p className="muted">Separate from the task DAG.</p>{graph.payload[key].length?graph.payload[key].map(n=><button className="declaration-link" key={n.id} onClick={()=>{setSelected(n.id);reveal(n.id);}}>{n.title}</button>):<p>None statically declared.</p>}</section>)}
            <section><h2>Diagnostics <span>{graph?.payload.diagnostics.length??0}</span></h2><button onClick={()=>send({kind:'problems'})}>Open Problems</button><ul className="diagnostics">{graph?.payload.diagnostics.slice(0,100).map((d,i)=><li key={i}><strong>{d.code}</strong><span>{d.message}</span></li>)}</ul>{(graph?.payload.diagnostics.length??0)>100&&<p>More diagnostics in Problems.</p>}</section>
        </aside>}</div>
        <footer>View positions are stored separately. Dragging, filtering and layout never edit the Groovy source.</footer>
    </main>;
}
createRoot(document.getElementById('root')!).render(<ReactFlowProvider><App/></ReactFlowProvider>);
