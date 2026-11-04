import {ViewState,readViewState,object,encodedSize,MAX_VIEW_BYTES} from './graph-model';
interface Memento {get<T>(key:string,defaultValue:T):T;update(key:string,value:unknown):Thenable<void>|Promise<void>}
/** Bounded, serialized storage of view metadata only, independent of all source edits. */
export class ViewStateStore {
    private queue:Promise<unknown>=Promise.resolve();
    private readonly key='gretlRuntime.modelerViews.v1';
    constructor(private readonly state:Memento){}
    private entries():Record<string,unknown>{const value=this.state.get<unknown>(this.key,{});return object(value)&&encodedSize(value)<=16*MAX_VIEW_BYTES+32768?value:{};}
    load(key:string):ViewState|undefined{return readViewState(this.entries()[key],key);}
    save(view:ViewState):Promise<boolean>{
        const normalized=readViewState(view,view.key);if(!normalized)return Promise.resolve(false);
        const operation=this.queue.then(async()=>{
            const entries=Object.entries(this.entries()).filter(([key,value])=>key!==normalized.key&&!!readViewState(value,key)).slice(-15);
            entries.push([normalized.key,normalized]);await this.state.update(this.key,Object.fromEntries(entries));
        });
        this.queue=operation.catch(()=>undefined);return operation.then(()=>true,()=>false);
    }
}
