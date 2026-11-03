import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import path from 'node:path';

export const digest = (data: Uint8Array | string): string => createHash('sha256').update(data).digest('hex');
export function sourceDigest(text: string): string {
    const bytes=Buffer.allocUnsafe(text.length*2);
    for(let i=0;i<text.length;i++)bytes.writeUInt16BE(text.charCodeAt(i),i*2);
    return digest(bytes);
}
export function javaMajor(output: string): number | undefined {
    const match = /(?:openjdk|java)(?: version)?\s+"?(\d+)(?:[.\s"-]|$)/i.exec(output);
    return match ? Number(match[1]) : undefined;
}
export async function checkJava(executable: string): Promise<void> {
    if (!executable || executable.includes('\0')) throw new Error('Configure a Java 25 executable in GRETL Runtime: Java Path.');
    try {
        const {stdout, stderr} = await promisify(execFile)(executable, ['-version'], {timeout: 5000, maxBuffer: 65536, windowsHide: true});
        if (javaMajor(stdout + '\n' + stderr) !== 25) throw new Error('VERSION');
    } catch {
        throw new Error('Java 25 is required. Check GRETL Runtime: Java Path (an executable path, without shell arguments).');
    }
}
export interface BundleManifest {schemaVersion: number; extensionVersion: string; sourceCommit: string; files: Record<string,string>}
export async function checkBundle(root: string, configuredJar: string): Promise<{jar: string; manifest: BundleManifest}> {
    try {
        const manifest: BundleManifest = JSON.parse(await readFile(path.join(root, 'server/bundle-manifest.json'), 'utf8'));
        if (manifest.schemaVersion !== 1 || !manifest.files['gretl-lsp.jar'] || Object.keys(manifest.files).length !== 4) throw new Error('MANIFEST');
        for (const [name, expected] of Object.entries(manifest.files)) {
            if (!['gretl-lsp.jar','operations.json','catalog-manifest.json','gretl-protocol-v1.schema.json'].includes(name)) throw new Error('PATH');
            if (digest(await readFile(path.join(root, 'server', name))) !== expected) throw new Error('DIGEST');
        }
        const jar = configuredJar || path.join(root, 'server/gretl-lsp.jar');
        if (configuredJar && !path.isAbsolute(configuredJar)) throw new Error('ABSOLUTE_PATH_REQUIRED');
        if (digest(await readFile(jar)) !== manifest.files['gretl-lsp.jar']) throw new Error('OVERRIDE_DIGEST');
        return {jar, manifest};
    } catch {
        throw new Error('GRETL server bundle is missing or does not match this extension. Reinstall the VSIX or correct Server Jar Path.');
    }
}
export function languageConfiguration(language: string, locale: string): {language:'en'|'de';trusted:true} {
    return {language: language === 'de' || language === 'auto' && locale.toLowerCase().startsWith('de') ? 'de' : 'en', trusted:true};
}
export function localHost(schemes: string[]): boolean {return schemes.every(scheme => scheme === 'file');}
