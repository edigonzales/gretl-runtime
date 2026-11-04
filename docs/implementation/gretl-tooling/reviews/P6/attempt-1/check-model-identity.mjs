import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const p = '/Users/stefan/.cache/gretl-tooling-review/p2-resume-readiness/identity.json';
const bytes = readFileSync(p);
const v = JSON.parse(bytes.toString('utf8'));
const redacted = {};
for (const [k, val] of Object.entries(v)) {
  if (/key|token|secret|auth/i.test(k)) redacted[k] = '<redacted>';
  else redacted[k] = typeof val === 'string' && val.length > 2000 ? val.slice(0, 2000) + '…' : val;
}
const out = {
  path: p, sha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length,
  identity: redacted,
  checks: {
    openCode2: /^2\.\d+\.\d+/.test(String(v.openCodeVersion)),
    provider: v.provider === 'deepseek',
    model: v.model === 'deepseek-flash',
    apiHost: v.apiHost === 'api.deepseek.com',
    modelVersion: v.modelVersion === 'DeepSeek-V4.1-Flash-1017',
    mappingUrl: v.mappingUrl === 'https://api-docs.deepseek.com/quick_start/pricing/',
    mappingTextMentionsAlias: typeof v.mappingText === 'string' && v.mappingText.includes('DeepSeek-V4.1-Flash-1017') && v.mappingText.includes('deepseek-flash'),
    actualProvider: v.actualProvider === 'deepseek',
    actualModel: v.actualModel === 'deepseek-flash',
    effectiveBaseUrl: v.effectiveBaseUrl === 'https://api.deepseek.com',
    fresh: Date.now() - Date.parse(v.checkedAt) < 24 * 60 * 60 * 1000
  }
};
writeFileSync('/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/model-identity-check.json', JSON.stringify(out, null, 2) + '\n');
console.log(JSON.stringify(out, null, 2));
