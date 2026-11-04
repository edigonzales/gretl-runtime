import {readFile} from 'node:fs/promises';
import {validateReviewArtifacts} from '/Users/stefan/.cache/gretl-tooling-review/P6-review-1/product/tools/gretl-tooling/review.mjs';
const dir='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const json=async p=>JSON.parse(await readFile(p,'utf8'));
const request=await json(`${dir}/request.json`);
const verdict=await json(`${dir}/resume-1/verdict.json`);
const report=await readFile(`${dir}/resume-1/review.md`,'utf8');
const completion=await json(`${dir}/process.json`);
try{
  const result=validateReviewArtifacts(request,verdict,report,completion);
  console.log(`validateReviewArtifacts: ${result}`);
}catch(error){
  console.error(error);
  process.exit(1);
}
