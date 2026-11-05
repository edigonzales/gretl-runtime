/** Server-confirmed membership, preserving document text and explicit language choices. */
export interface FragmentDocument {
    readonly uri:string;readonly languageId:string;readonly version:number;readonly dirty:boolean;readonly closed:boolean;
    getText():string;
}
export interface MembershipUpdate {
    protocolVersion:1;includeToolingVersion:1;serverInstanceId:string;workspaceRoot:string;anchorDocument:string;
    ownerGeneration:string;syncToken:string;ownersComplete:boolean;members:{documentId:string;contextCount:number}[];
}
export interface SourceContexts {
    protocolVersion:1;includeToolingVersion:1;serverInstanceId:string;documentId:string;ownerGeneration:string;contextGeneration:string;
    incarnation?:string|null;ownersComplete:boolean;selectionRequired:boolean;selectedToken?:string|null;
    contexts:{contextToken:string;projectId:string;entryDocument:string;ownerScope:string;resourceBase?:string|null;role:'ENTRY'|'FRAGMENT'}[];
}
const object=(value:unknown):value is Record<string,unknown>=>!!value&&typeof value==='object'&&!Array.isArray(value);
const bounded=(value:unknown,max=8192):value is string=>typeof value==='string'&&value.length>0&&value.length<=max;
const revision=(value:unknown):value is string=>typeof value==='string'&&/^[0-9]{1,19}$/.test(value);
const uuid=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export function membershipUpdate(value:unknown):MembershipUpdate {
    if(!object(value)||value.protocolVersion!==1||value.includeToolingVersion!==1||!uuid(value.serverInstanceId)
        ||!bounded(value.workspaceRoot)||!bounded(value.anchorDocument)||!revision(value.ownerGeneration)||!uuid(value.syncToken)
        ||typeof value.ownersComplete!=='boolean'||!Array.isArray(value.members)||value.members.length>512
        ||!value.members.every(m=>object(m)&&bounded(m.documentId)&&Number.isInteger(m.contextCount)&&Number(m.contextCount)>0&&Number(m.contextCount)<=4096)
        ||new Set(value.members.map(m=>(m as {documentId:string}).documentId)).size!==value.members.length)throw Error('Invalid GRETL source membership.');
    return value as unknown as MembershipUpdate;
}
export function sourceContexts(value:unknown):SourceContexts {
    if(!object(value)||value.protocolVersion!==1||value.includeToolingVersion!==1||!uuid(value.serverInstanceId)||!bounded(value.documentId)
        ||!revision(value.ownerGeneration)||!revision(value.contextGeneration)||value.incarnation!=null&&!revision(value.incarnation)
        ||typeof value.ownersComplete!=='boolean'||typeof value.selectionRequired!=='boolean'||value.selectedToken!=null&&!uuid(value.selectedToken)
        ||!Array.isArray(value.contexts)||value.contexts.length>512||!value.contexts.every(c=>object(c)&&uuid(c.contextToken)&&bounded(c.projectId,65536)
            &&bounded(c.entryDocument)&&bounded(c.ownerScope,4096)&&(c.resourceBase==null||bounded(c.resourceBase))&&(c.role==='ENTRY'||c.role==='FRAGMENT'))
        ||new Set(value.contexts.map(c=>(c as {contextToken:string}).contextToken)).size!==value.contexts.length
        ||value.selectedToken!=null&&!value.contexts.some(c=>(c as {contextToken:string}).contextToken===value.selectedToken))throw Error('Invalid GRETL source contexts.');
    return value as unknown as SourceContexts;
}
export interface FragmentHost {
    instance():string|undefined;
    normalize(uri:string):string;
    owns(uri:string,root?:string):boolean;
    documents():readonly FragmentDocument[];
    changeLanguage(document:FragmentDocument,language:string):Promise<FragmentDocument>;
    synchronize(document:FragmentDocument):Promise<void>;
    closed(document:FragmentDocument):Promise<void>;
    confirm(update:MembershipUpdate,buffers:{documentId:string;version:number|null;sha256:string|null}[]):Promise<boolean>;
    digest(text:string):string;
}
export class FragmentDocumentCoordinator {
    private readonly latest=new Map<string,MembershipUpdate>();
    private readonly members=new Map<string,Set<string>>();
    private readonly automatic=new Map<string,{document:FragmentDocument;previous:string}>();
    private readonly overrides=new Set<string>();
    private readonly transitioning=new Set<string>();
    private queue:Promise<void>=Promise.resolve();
    private disposed=false;
    constructor(private readonly host:FragmentHost){}
    isMember(uri:string):boolean {const key=this.host.normalize(uri);return [...this.members.values()].some(set=>set.has(key));}
    acceptMembership(raw:unknown):Promise<void> {
        const update=membershipUpdate(raw);
        if(this.disposed||update.serverInstanceId!==this.host.instance())return Promise.resolve();
        if(!this.host.owns(update.anchorDocument,update.workspaceRoot)||update.members.some(m=>!this.host.owns(m.documentId,update.workspaceRoot)))return Promise.reject(Error('GRETL membership outside workspace.'));
        const root=this.host.normalize(update.workspaceRoot),previous=this.latest.get(root);
        if(previous&&(BigInt(update.ownerGeneration)<BigInt(previous.ownerGeneration)
                ||update.ownerGeneration===previous.ownerGeneration&&update.syncToken!==previous.syncToken))return Promise.resolve();
        this.latest.set(root,update);
        return this.enqueue(()=>this.apply(root,update));
    }
    private enqueue(operation:()=>Promise<void>):Promise<void>{
        const next=this.queue.then(operation,operation);this.queue=next.catch(()=>undefined);return next;
    }
    async settled():Promise<void>{await this.queue;}
    async opened(document:FragmentDocument):Promise<void>{
        if(!this.isMember(document.uri)||this.transitioning.has(this.host.normalize(document.uri)))return;
        // A user language change delivers close/open in the same event turn. A real reopen comes after close cleanup.
        await Promise.resolve();this.refreshMember(document.uri);
    }
    changed(document:FragmentDocument):void {
        if(this.isMember(document.uri))this.refreshMember(document.uri);
    }
    closed(document:FragmentDocument):void {
        const key=this.host.normalize(document.uri);void this.host.closed(document);
        void Promise.resolve().then(()=>{
            if(this.transitioning.has(key))return;
            const automatic=this.automatic.get(key),replacement=this.host.documents().find(d=>this.host.normalize(d.uri)===key&&!d.closed);
            if(automatic?.document===document){
                if(replacement&&replacement.languageId!=='gretl-runtime')this.overrides.add(key);
                this.automatic.delete(key);
            }
            if(!replacement)this.overrides.delete(key);
            if(this.isMember(key))this.refreshMember(key);
        });
    }
    private refreshMember(uri:string):void{
        if(this.disposed)return;const key=this.host.normalize(uri);
        for(const [root,update] of this.latest)if(update.members.some(m=>this.host.normalize(m.documentId)===key))void this.enqueue(()=>this.apply(root,update));
    }
    private async apply(root:string,update:MembershipUpdate):Promise<void>{
        const current=()=>!this.disposed&&this.host.instance()===update.serverInstanceId&&this.latest.get(root)===update;
        if(!current())return;
        const previous=this.members.get(root)??new Set<string>(),next=new Set(update.members.map(m=>this.host.normalize(m.documentId)));
        this.members.set(root,next);
        for(const uri of previous)if(!next.has(uri)&&!this.isMember(uri))await this.restore(uri);
        for(const uri of next){
            if(!current())return;let doc=this.host.documents().find(d=>this.host.normalize(d.uri)===uri&&!d.closed);
            if(!doc)continue;
            if(doc.languageId==='groovy'&&!this.overrides.has(uri)){
                const text=doc.getText(),dirty=doc.dirty,previousLanguage=doc.languageId;this.transitioning.add(uri);
                try{
                    const changed=await this.host.changeLanguage(doc,'gretl-runtime');
                    if(changed.getText()!==text||dirty&&!changed.dirty)throw Error('GRETL language transition changed the fragment buffer.');
                    this.automatic.set(uri,{document:changed,previous:previousLanguage});doc=changed;
                }finally{this.transitioning.delete(uri);}
            }
            await this.host.synchronize(doc);
        }
        if(!current())return;
        const open=new Map(this.host.documents().filter(d=>!d.closed).map(d=>[this.host.normalize(d.uri),d]));
        const buffers=update.members.map(m=>{const doc=open.get(this.host.normalize(m.documentId));return {documentId:m.documentId,version:doc?.version??null,sha256:doc?this.host.digest(doc.getText()):null};});
        await this.host.confirm(update,buffers);
    }
    private async restore(uri:string):Promise<void>{
        const record=this.automatic.get(uri);this.automatic.delete(uri);this.overrides.delete(uri);
        if(!record)return;const document=this.host.documents().find(d=>this.host.normalize(d.uri)===uri&&!d.closed);
        if(document!==record.document||document.languageId!=='gretl-runtime')return;
        this.transitioning.add(uri);try{await this.host.changeLanguage(document,record.previous);}finally{this.transitioning.delete(uri);}
    }
    async dispose():Promise<void>{
        if(this.disposed)return;this.disposed=true;await this.queue;
        for(const uri of [...this.automatic.keys()])await this.restore(uri);
        this.latest.clear();this.members.clear();this.overrides.clear();
    }
}
