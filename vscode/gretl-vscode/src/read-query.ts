/** A transient workspace generation change must not become a cached empty outline. */
export async function currentRead<T>(request:()=>Promise<T>,current:()=>boolean):Promise<T> {
    for(let attempt=0;;attempt++) {
        if(!current())throw new Error('GRETL read belongs to an obsolete document or session.');
        try {
            const result=await request();
            if(!current())throw new Error('GRETL read belongs to an obsolete document or session.');
            return result;
        } catch(error) {
            if(attempt>=2||!current()||!(error&&typeof error==='object'&&'code' in error&&error.code===-32801))throw error;
        }
    }
}
