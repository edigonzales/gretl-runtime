import {ChildProcess} from 'node:child_process';
export async function endOwnedProcess(child:ChildProcess|undefined):Promise<void>{
    if(!child||child.pid===undefined||child.exitCode!==null||child.signalCode!==null)return;
    await new Promise<void>((resolve,reject)=>{
        let hard:NodeJS.Timeout|undefined,deadline:NodeJS.Timeout|undefined;
        const finish=()=>{if(hard)clearTimeout(hard);if(deadline)clearTimeout(deadline);child.removeListener('exit',finish);resolve();};
        child.once('exit',finish);
        hard=setTimeout(()=>{if(child.exitCode===null&&child.signalCode===null)child.kill('SIGKILL');},1000);
        deadline=setTimeout(()=>{child.removeListener('exit',finish);reject(new Error('Owned GRETL server did not terminate. Restart is blocked.'));},2500);
        child.kill('SIGTERM');
    });
}
