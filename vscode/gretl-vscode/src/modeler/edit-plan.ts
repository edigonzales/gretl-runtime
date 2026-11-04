/** Native edit orchestration. All semantic decisions and replacements come from Java. */
export interface EditIdentity {serverInstanceId:string;snapshotId:string}
export interface EditPlan<Edit> {context:EditIdentity;capability:{state:string;reasons:{code:string;message:string}[];requiredOpenDocuments:string[]};planId?:string|null;edit?:Edit|null;notices:string[]}
export interface EditPlanPort<Edit,Native> {
    cancelled():boolean;
    plan():Promise<EditPlan<Edit>>;
    guard(edit:Edit):Promise<void>;
    validate(planId:string,snapshotId:string):Promise<{protocolVersion:number;current:boolean;reasons:string[]}>;
    convert(edit:Edit):Promise<Native|undefined>;
    apply(edit:Native):Promise<boolean>;
}
export class ModelerEditRejected extends Error {
    constructor(readonly reasons:string[],readonly documentsToOpen:string[]=[]){super(reasons.join('; ')||'The edit was rejected.');}
}
/** One confirmation produces at most one native WorkspaceEdit and one text undo entry. */
export async function applyEditPlan<Edit,Native>(identity:EditIdentity,port:EditPlanPort<Edit,Native>):Promise<string[]> {
    const active=()=>{if(port.cancelled())throw new ModelerEditRejected(['EDIT_CANCELLED']);};
    active();const plan=await port.plan();active();
    if(plan.context.serverInstanceId!==identity.serverInstanceId||plan.context.snapshotId!==identity.snapshotId)throw new ModelerEditRejected(['EDIT_STALE_PLAN']);
    if(plan.capability.state!=='ALLOWED'||!plan.edit||!plan.planId)throw new ModelerEditRejected(plan.capability.reasons.map(r=>r.code+': '+r.message),plan.capability.requiredOpenDocuments);
    await port.guard(plan.edit);active();
    const validation=await port.validate(plan.planId,identity.snapshotId);active();
    if(validation.protocolVersion!==1||!validation.current)throw new ModelerEditRejected(validation.reasons.length?validation.reasons:['EDIT_STALE_PLAN']);
    const native=await port.convert(plan.edit);active();
    if(!native)throw new ModelerEditRejected(['EDIT_CONVERSION_FAILED']);
    // Conversion is asynchronous and VS Code's converter discards LSP versions.
    // Recheck every captured source/incarnation immediately before native apply.
    await port.guard(plan.edit);active();
    if(!await port.apply(native))throw new ModelerEditRejected(['EDIT_APPLY_FAILED']);
    return plan.notices;
}
