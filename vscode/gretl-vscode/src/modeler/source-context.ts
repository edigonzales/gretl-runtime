import type {SourceContexts} from '../fragment-documents';
export type PinnedSourceContext=Pick<SourceContexts['contexts'][number],'projectId'|'entryDocument'|'ownerScope'|'role'>;
/** Renew only the same application. Editor selection and other panels never replace a pin. */
export function pinnedContext(contexts:SourceContexts,pin?:PinnedSourceContext,acrossSource=false):SourceContexts['contexts'][number]|undefined {
    if(pin){
        const found=contexts.contexts.filter(c=>c.projectId===pin.projectId&&c.entryDocument===pin.entryDocument&&c.ownerScope===pin.ownerScope&&(acrossSource||c.role===pin.role));
        if(found.length!==1)throw Error('The pinned GRETL source context is no longer available. Reopen the modeler and choose a context.');
        return found[0];
    }
    if(contexts.contexts.length===1)return contexts.contexts[0];
    return contexts.contexts.find(c=>c.contextToken===contexts.selectedToken);
}
