import type * as vscode from 'vscode';
import type {PinnedSourceContext} from './source-context';
import type {GraphContext,GraphResponse} from './graph-model';
import type {CatalogResponse,EditIntent} from './edit-model';
export interface ModelerCommit {notices:string[];documents:string[]}
export interface ModelerOptions {includeInnerInvocations:boolean;previewTargets:string[]|null;sourceContext?:PinnedSourceContext}
export interface ModelerBackend {
    readonly onDidChange:vscode.Event<boolean>;
    readonly onDidAnalyze:vscode.Event<GraphContext>;
    graph(document:vscode.TextDocument,options:ModelerOptions,token:vscode.CancellationToken):Promise<GraphResponse>;
    catalog(graph:GraphResponse,token:vscode.CancellationToken):Promise<CatalogResponse>;
    commit(document:vscode.TextDocument,graph:GraphResponse,intent:EditIntent,token:vscode.CancellationToken):Promise<ModelerCommit>;
    activateSourceContext?(documentId:string,context:PinnedSourceContext,token:vscode.CancellationToken):Promise<void>;
    navigate(documentId:string,snapshotId:string,elementId:string|undefined,offsetUtf16:number|undefined,token:vscode.CancellationToken,sourceContext?:PinnedSourceContext):Promise<{context:GraphContext;location?:{uri:string;range:{start:{line:number;character:number};end:{line:number;character:number}}}|null;elements:string[];sourceContext?:PinnedSourceContext|null}>;
}
