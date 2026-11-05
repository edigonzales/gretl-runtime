/** Data-only custom-protocol values. Numeric scalars never pass through JS Number. */
export interface SymbolKey {namespace:'TASK'|'PARAMETER'|'CLEANUP';qualifiedName:string}
export type EditableValue={kind:'Null'}|{kind:'Text'|'Boolean'|'Integer'|'Long'|'BigInteger'|'Decimal'|'JobFileRef'|'WorkPathRef'|'SecretAliasRef';scalar:string}
    |{kind:'List';items:EditableValue[]}|{kind:'Map';entries:Record<string,EditableValue>}
    |{kind:'ParameterRef';symbol:SymbolKey}|{kind:'ResultFieldRef';symbol:SymbolKey;field:string};
export interface ParameterDeclaration {name:string;description:string;type:string;required:boolean;defaultValue?:EditableValue|null}
export type EditIntent={kind:'AddTask';name:string;operationId:string;insertionOffset:number;fields:Record<string,EditableValue>}
    |{kind:'AddAction';name:string;insertionOffset:number}
    |{kind:'AddParameter';insertionOffset:number;parameter:ParameterDeclaration}
    |{kind:'SetField'|'SetTaskOption';target:SymbolKey;field:string;value:EditableValue}
    |{kind:'RemoveField';target:SymbolKey;field:string}
    |{kind:'ConfigureTask';target:SymbolKey;fields:Record<string,EditableValue>;removedFields:string[];options:Record<string,EditableValue>}
    |{kind:'AddDependency'|'RemoveDependency';producer:SymbolKey;target:SymbolKey;edgeKind:'DEPENDS_ON'|'AFTER'|'FINALIZED_BY'}
    |{kind:'BindResult';producer:SymbolKey;outputField:string;target:SymbolKey;field:string}
    |{kind:'RenameSymbol';target:SymbolKey;name:string}
    |{kind:'DeleteTask';target:SymbolKey}
    |{kind:'SetDefaults';targets:SymbolKey[]};
export interface ValueShape {kind:string;runtimeType:string;children:ValueShape[]}
export interface InputDescriptor {name:string;description:string;required:boolean;hasDefault:boolean;defaultValue?:EditableValue|null;allowedValues:string[];shape:ValueShape;pathRole?:string|null;sensitive:boolean;hints:Record<string,string>}
export interface OutputDescriptor {name:string;description:string;shape:ValueShape;nullable:boolean}
export interface OperationDescriptor {operationId:string;symbol:string;description:string;origin:string;inputs:InputDescriptor[];outputs:OutputDescriptor[];voidResult:boolean}
export interface CatalogResponse {protocolVersion:1;schemaVersion:1;serverInstanceId:string;state:string;catalog?:{catalogId:string;operations:OperationDescriptor[];coverage:string};notices:string[]}
export interface FormField {present:boolean;value?:EditableValue|null;reason?:string|null}
export interface FormProjection {nodes:Record<string,{fields:Record<string,FormField>;options:Record<string,FormField>}>;insertions:{documentId:string;insertionOffset:number;addTask:unknown;addAction:unknown;addParameter:unknown}[];defaultsDocument:string;defaults:SymbolKey[]}
const object=(v:unknown):v is Record<string,unknown>=>v!==null&&typeof v==='object'&&!Array.isArray(v)&&(Object.getPrototypeOf(v)===Object.prototype||Object.getPrototypeOf(v)===null);
const text=(v:unknown,max=65536):v is string=>typeof v==='string'&&v.length<=max;
const keys=(v:Record<string,unknown>,allowed:string[])=>Object.keys(v).every(k=>allowed.includes(k));
export function symbolKey(v:unknown):v is SymbolKey{return object(v)&&keys(v,['namespace','qualifiedName'])&&['TASK','PARAMETER','CLEANUP'].includes(String(v.namespace))&&text(v.qualifiedName);}
/** Validates transport structure and budgets only; field compatibility remains Core-owned. */
export function editableValue(v:unknown,depth=0,budget={remaining:4096}):v is EditableValue {
    if(!object(v)||depth>16||--budget.remaining<0)return false;
    const nested=(x:unknown)=>editableValue(x,depth+1,budget);
    switch(v.kind){
        case 'Null':return keys(v,['kind']);
        case 'Text':return keys(v,['kind','scalar'])&&text(v.scalar);
        case 'Boolean':return keys(v,['kind','scalar'])&&(v.scalar==='true'||v.scalar==='false');
        case 'Integer':case 'Long':case 'BigInteger':return keys(v,['kind','scalar'])&&text(v.scalar,4096)&&/^-?\d+$/.test(v.scalar);
        case 'Decimal':return keys(v,['kind','scalar'])&&text(v.scalar,4096)&&/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(v.scalar);
        case 'JobFileRef':case 'WorkPathRef':return keys(v,['kind','scalar'])&&text(v.scalar,4096);
        case 'SecretAliasRef':return keys(v,['kind','scalar'])&&text(v.scalar,1024);
        case 'ParameterRef':return keys(v,['kind','symbol'])&&symbolKey(v.symbol);
        case 'ResultFieldRef':return keys(v,['kind','symbol','field'])&&symbolKey(v.symbol)&&text(v.field,1024);
        case 'List':return keys(v,['kind','items'])&&Array.isArray(v.items)&&v.items.length<=1024&&v.items.every(nested);
        case 'Map':return keys(v,['kind','entries'])&&object(v.entries)&&Object.keys(v.entries).length<=1024&&Object.entries(v.entries).every(([k,x])=>text(k)&&nested(x));
        default:return false;
    }
}
/** Draft completeness only; symbol existence and value compatibility remain server decisions. */
export function completeDraftValue(value:EditableValue):boolean {
    if(!editableValue(value))return false;
    switch(value.kind){
        case 'ParameterRef':return value.symbol.qualifiedName.length>0;
        case 'ResultFieldRef':return value.symbol.qualifiedName.length>0&&value.field.length>0;
        case 'List':return value.items.every(completeDraftValue);
        case 'Map':return Object.values(value.entries).every(completeDraftValue);
        default:return true;
    }
}
export function capabilityReason(value:unknown):string {
    if(!object(value)||value.state==='ALLOWED'||!Array.isArray(value.reasons))return '';
    return value.reasons.filter(object).map(r=>[text(r.code,128)?r.code:'',text(r.message)?r.message:''].filter(Boolean).join(': ')).filter(Boolean).join('; ');
}
/** Gson may emit unused nullable record components. They are not editable data. */
export function projectedValue(v:unknown,depth=0,budget={remaining:4096}):EditableValue {
    if(!object(v)||depth>16||--budget.remaining<0||!keys(v,['kind','scalar','items','entries','symbol','field']))throw Error('FORM_VALUE_INVALID');
    const clean=Object.fromEntries(Object.entries(v).filter(([,value])=>value!==null&&value!==undefined));
    if(clean.kind==='List'&&Array.isArray(clean.items))clean.items=clean.items.map(x=>projectedValue(x,depth+1,budget));
    if(clean.kind==='Map'&&object(clean.entries))clean.entries=Object.fromEntries(Object.entries(clean.entries).map(([k,x])=>[k,projectedValue(x,depth+1,budget)]));
    if(!editableValue(clean))throw Error('FORM_VALUE_INVALID');return clean;
}
export function formProjection(v:unknown,nodeIds:Set<string>,sourceIds:Set<string>):FormProjection {
    if(!object(v)||!object(v.nodes)||Object.keys(v.nodes).length>2000||!Array.isArray(v.insertions)||v.insertions.length>4096||!text(v.defaultsDocument,8192)||!sourceIds.has(v.defaultsDocument)||!Array.isArray(v.defaults)||v.defaults.length>1024||!v.defaults.every(symbolKey))throw Error('FORM_PROJECTION_INVALID');
    const nodes=Object.fromEntries(Object.entries(v.nodes).map(([id,node])=>{
        if(!nodeIds.has(id)||!object(node))throw Error('FORM_NODE_INVALID');
        const fields=(values:unknown)=>{
            if(!object(values)||Object.keys(values).length>1024)throw Error('FORM_FIELDS_INVALID');
            return Object.fromEntries(Object.entries(values).map(([key,field])=>{if(!text(key,1024)||!object(field)||typeof field.present!=='boolean'||field.reason!=null&&!text(field.reason))throw Error('FORM_FIELD_INVALID');return[key,{present:field.present,value:field.value==null?null:projectedValue(field.value),reason:field.reason??null}];}));
        };
        return[id,{fields:fields(node.fields),options:fields(node.options)}];
    }));
    for(const i of v.insertions)if(!object(i)||!text(i.documentId,8192)||!sourceIds.has(i.documentId)||!Number.isSafeInteger(i.insertionOffset)||Number(i.insertionOffset)<0)throw Error('FORM_INSERTION_INVALID');
    return {...v,nodes} as unknown as FormProjection;
}
/** Accepts only the existing named Core intents, never webview-supplied text edits or code. */
export function editIntent(v:unknown):v is EditIntent {
    if(!object(v))return false;
    const target=()=>symbolKey(v.target),insertion=()=>Number.isSafeInteger(v.insertionOffset)&&Number(v.insertionOffset)>=0;
    let allowed:string[];
    switch(v.kind){
        case 'AddTask':if(!text(v.name)||!text(v.operationId,1024)||!insertion()||!object(v.fields)||Object.keys(v.fields).length>128||!Object.entries(v.fields).every(([k,x])=>text(k,1024)&&editableValue(x)))return false;allowed=['name','operationId','insertionOffset','fields'];break;
        case 'AddAction':if(!text(v.name)||!insertion())return false;allowed=['name','insertionOffset'];break;
        case 'AddParameter':{const p=v.parameter;if(!insertion()||!object(p)||!keys(p,['name','description','type','required','defaultValue'])||!text(p.name)||!text(p.description)||!text(p.type,128)||typeof p.required!=='boolean'||p.defaultValue!=null&&!editableValue(p.defaultValue))return false;allowed=['insertionOffset','parameter'];break;}
        case 'SetField':case 'SetTaskOption':if(!target()||!text(v.field,1024)||!editableValue(v.value))return false;allowed=['target','field','value'];break;
        case 'RemoveField':if(!target()||!text(v.field,1024))return false;allowed=['target','field'];break;
        case 'ConfigureTask':if(!target()||!object(v.fields)||!object(v.options)||Object.keys(v.fields).length>128||Object.keys(v.options).length>128||![...Object.entries(v.fields),...Object.entries(v.options)].every(([k,x])=>text(k,1024)&&editableValue(x))||!Array.isArray(v.removedFields)||v.removedFields.length>128||v.removedFields.some(x=>!text(x,1024)))return false;allowed=['target','fields','removedFields','options'];break;
        case 'AddDependency':case 'RemoveDependency':if(!target()||!symbolKey(v.producer)||!['DEPENDS_ON','AFTER','FINALIZED_BY'].includes(String(v.edgeKind)))return false;allowed=['target','producer','edgeKind'];break;
        case 'BindResult':if(!target()||!symbolKey(v.producer)||!text(v.field,1024)||!text(v.outputField,1024))return false;allowed=['target','producer','field','outputField'];break;
        case 'RenameSymbol':if(!target()||!text(v.name))return false;allowed=['target','name'];break;
        case 'DeleteTask':if(!target())return false;allowed=['target'];break;
        case 'SetDefaults':if(!Array.isArray(v.targets)||v.targets.length>1024||!v.targets.every(symbolKey))return false;allowed=['targets'];break;
        default:return false;
    }
    return keys(v,['kind',...allowed]);
}

/** Removes catalogue-only empty containers and retains every numeric digit. */
export function catalogValue(v:unknown,depth=0,budget={remaining:4096}):EditableValue {
    if(!object(v)||depth>16||--budget.remaining<0)throw Error('CATALOG_VALUE_INVALID');
    let value:unknown;
    if(v.kind==='List'&&Array.isArray(v.items)&&v.items.length<=1024)value={kind:'List',items:v.items.map(x=>catalogValue(x,depth+1,budget))};
    else if(v.kind==='Map'&&object(v.entries)&&Object.keys(v.entries).length<=1024)value={kind:'Map',entries:Object.fromEntries(Object.entries(v.entries).map(([k,x])=>[k,catalogValue(x,depth+1,budget)]))};
    else if(v.kind==='Null')value={kind:'Null'};
    else value={kind:v.kind==='Integer'?'BigInteger':v.kind,scalar:v.scalar};
    if(!editableValue(value))throw Error('CATALOG_VALUE_INVALID');return value;
}
export function catalogResponse(value:unknown,serverInstanceId:string,catalogId?:string):CatalogResponse {
    if(!object(value)||JSON.stringify(value).length>16*1024*1024||value.protocolVersion!==1||value.schemaVersion!==1||value.serverInstanceId!==serverInstanceId||!text(value.state,128)||!Array.isArray(value.notices)||value.notices.some(x=>!text(x)))throw Error('CATALOG_RESPONSE_INVALID');
    if(value.state!=='AVAILABLE')return {protocolVersion:1,schemaVersion:1,serverInstanceId,state:value.state,notices:value.notices as string[]};
    const c=value.catalog;if(!object(c)||!text(c.catalogId,128)||catalogId!==undefined&&c.catalogId!==catalogId||!text(c.coverage,128)||!Array.isArray(c.operations)||c.operations.length>1024)throw Error('CATALOG_IDENTITY_INVALID');
    const shape=(v:unknown,depth=0):v is ValueShape=>object(v)&&depth<=16&&text(v.kind,128)&&text(v.runtimeType,1024)&&Array.isArray(v.children)&&v.children.length<=16&&v.children.every(x=>shape(x,depth+1));
    const names=new Set<string>();
    const operations=c.operations.map(o=>{
        if(!object(o)||!text(o.operationId,1024)||names.has(o.operationId)||!text(o.symbol,1024)||!text(o.description)||!text(o.origin,4096)||typeof o.voidResult!=='boolean'||!Array.isArray(o.inputs)||o.inputs.length>128||!Array.isArray(o.outputs)||o.outputs.length>128)throw Error('CATALOG_OPERATION_INVALID');
        names.add(o.operationId);
        const inputNames=new Set<string>(),outputNames=new Set<string>();
        const inputs=o.inputs.map(i=>{
            if(!object(i)||!text(i.name,1024)||inputNames.has(i.name)||!text(i.description)||typeof i.required!=='boolean'||typeof i.hasDefault!=='boolean'||typeof i.sensitive!=='boolean'||!Array.isArray(i.allowedValues)||i.allowedValues.length>1024||i.allowedValues.some(x=>!text(x))||!shape(i.shape)||i.pathRole!=null&&!text(i.pathRole,128)||!object(i.hints)||Object.entries(i.hints).some(([k,v])=>!text(k,1024)||!text(v)))throw Error('CATALOG_INPUT_INVALID');
            inputNames.add(i.name);return {...i,defaultValue:i.defaultValue==null?null:catalogValue(i.defaultValue)} as unknown as InputDescriptor;
        });
        const outputs=o.outputs.map(i=>{if(!object(i)||!text(i.name,1024)||outputNames.has(i.name)||!text(i.description)||typeof i.nullable!=='boolean'||!shape(i.shape))throw Error('CATALOG_OUTPUT_INVALID');outputNames.add(i.name);return i as unknown as OutputDescriptor;});
        return {...o,inputs,outputs} as unknown as OperationDescriptor;
    });
    return {protocolVersion:1,schemaVersion:1,serverInstanceId,state:value.state,catalog:{catalogId:c.catalogId,coverage:c.coverage,operations},notices:value.notices as string[]};
}
