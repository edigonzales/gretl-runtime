import {open,lstat} from 'node:fs/promises';
import {constants} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {sourceDigest} from './host';

/** Transport preconditions only. DSL semantics and edit generation remain in Java. */
export interface Stamp {documentId:string;sha256:string|null;version:number|null;incarnation:string|null;unavailableCode:string|null;editable:boolean}
export interface BufferView {readonly version:number;readonly isClosed:boolean;getText():string}
export interface ChangedDocument {uri:string;version:number|null}
export type Buffers=Map<string,BufferView>;

export async function diskSourceHash(uri:string):Promise<string|null>{
    const file=fileURLToPath(uri);
    // Do not follow a newly introduced symlink while offering a native edit.
    let current=path.parse(file).root;
    for(const part of path.relative(current,file).split(path.sep)){
        current=path.join(current,part);
        try{const info=await lstat(current);if(info.isSymbolicLink())throw new Error('GRETL source became a symlink. Retry.');if(current===file&&!info.isFile())throw new Error('GRETL source is not a regular file.');}
        catch(error){if((error as NodeJS.ErrnoException).code==='ENOENT')return null;throw error;}
    }
    let handle;
    try{
        handle=await open(file,constants.O_RDONLY|(constants.O_NOFOLLOW??0)|(constants.O_NONBLOCK??0));
        const info=await handle.stat();
        if(!info.isFile())throw new Error('GRETL source is not a regular file.');
        if(info.size>4*1024*1024)throw new Error('GRETL source exceeds its byte limit.');
        const bytes=Buffer.allocUnsafe(4*1024*1024+1);
        let length=0;
        while(length<bytes.length){const result=await handle.read(bytes,length,bytes.length-length,null);if(!result.bytesRead)break;length+=result.bytesRead;}
        if(length===bytes.length)throw new Error('GRETL source exceeds its byte limit.');
        return sourceDigest(new TextDecoder('utf-8',{fatal:true,ignoreBOM:true}).decode(bytes.subarray(0,length)));
    }catch(error){if((error as NodeJS.ErrnoException).code==='ENOENT')return null;throw error;}
    finally{await handle?.close();}
}
export async function validateEditSources(stamps:Stamp[],changes:ChangedDocument[],captured:Buffers,current:()=>Buffers,diskHash=diskSourceHash):Promise<void>{
    const sources=new Map(stamps.map(stamp=>[stamp.documentId,stamp]));
    for(const change of changes){const stamp=sources.get(change.uri);if(!stamp?.editable||stamp.version!==change.version)throw new Error('GRETL edit source precondition differs. Retry.');}
    const assertBuffers=()=>{
        const buffers=current();
        for(const stamp of stamps){
            const doc=buffers.get(stamp.documentId);
            if(stamp.version===null){if(doc)throw new Error('GRETL source was opened after capture. Retry.');}
            else if(!doc||doc.isClosed||doc!==captured.get(stamp.documentId)||doc.version!==stamp.version||sourceDigest(doc.getText())!==stamp.sha256)throw new Error('GRETL buffer changed. Retry.');
        }
    };
    assertBuffers();
    for(const stamp of stamps)if(stamp.version===null){
        if(await diskHash(stamp.documentId)!==stamp.sha256)throw new Error('GRETL file changed. Retry.');
        assertBuffers();
    }
    assertBuffers();
}

export interface EditContextIdentity {
    protocolVersion:number;schemaVersion:number;serverInstanceId:string;snapshotId:string;
    ownerGeneration?:string|null;contextGeneration?:string|null;contextDocumentId?:string|null;contextToken?:string|null;
}
/** Source hashes alone do not notice new owners or a changed editor context. */
export function validateEditContext(expected:EditContextIdentity,current:EditContextIdentity):void {
    for(const key of ['protocolVersion','schemaVersion','serverInstanceId','snapshotId','ownerGeneration','contextGeneration','contextDocumentId','contextToken'] as const)
        if(expected[key]!==current[key])throw new Error('GRETL ownership or source context changed. Retry.');
}
