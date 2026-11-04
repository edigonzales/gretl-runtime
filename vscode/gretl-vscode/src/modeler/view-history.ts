import {ViewState,readViewState} from './graph-model';
/** Bounded local view undo, deliberately separate from native text undo. */
export class ViewHistory {
    private current?:ViewState;
    private previous:ViewState[]=[];
    private following:ViewState[]=[];
    constructor(private readonly limit=32){}
    get canUndo():boolean{return this.previous.length>0;}
    get canRedo():boolean{return this.following.length>0;}
    reset():void{this.current=undefined;this.previous=[];this.following=[];}
    begin(view:ViewState):void{const copy=readViewState(view,view.key);if(!copy)return;if(this.current?.key!==view.key)this.reset();this.current=copy;}
    record(view:ViewState):void{
        const copy=readViewState(view,view.key);if(!copy)return;
        if(!this.current||this.current.key!==view.key){this.reset();this.current=copy;return;}
        if(JSON.stringify(this.current)===JSON.stringify(copy))return;
        this.previous.push(this.current);if(this.previous.length>this.limit)this.previous.shift();this.current=copy;this.following=[];
    }
    undo():ViewState|undefined{const next=this.previous.pop();if(!next||!this.current)return;this.following.push(this.current);this.current=next;return readViewState(next,next.key);}
    redo():ViewState|undefined{const next=this.following.pop();if(!next||!this.current)return;this.previous.push(this.current);this.current=next;return readViewState(next,next.key);}
}

/** React Flow commits its final node state after stop callbacks. Save only once it settles. */
export class ViewGestureCommit {
    private generation=0;
    private disposed=false;
    constructor(private readonly frame:(callback:()=>void)=>void,private readonly identity:()=>string|undefined,private readonly save:()=>void){}
    commit():void{const ticket=++this.generation,key=this.identity();this.frame(()=>this.frame(()=>{if(!this.disposed&&key!==undefined&&ticket===this.generation&&this.identity()===key)this.save();}));}
    dispose():void{this.disposed=true;++this.generation;}
}
