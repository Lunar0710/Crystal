import { PLAYER_CERTIFICATE_KEYS, PROFILE_PROPERTY_KEYS } from './mojangKeys.js'

/**
 * Who a client is, proven with Mojang's own signatures instead of asking
 * Mojang (its session server refuses Cloudflare's network with 403).
 *
 * Every Minecraft account gets a key pair from Mojang for chat signing; its
 * public half comes with Mojang's signature over the account's UUID, the
 * expiry and the key ("publicKeySignatureV2"). The client signs this
 * server's challenge with the private half, which never leaves the player's
 * PC, and sends the account's profile textures, which Mojang signs too and
 * which carry the account's name. So:
 *   1. Mojang signed this key for this UUID, and it hasn't expired;
 *   2. the challenge was signed with that key (the sender owns the account);
 *   3. Mojang says this UUID is called this name.
 * No login token is ever sent anywhere.
 *
 * proof = { uuid (32 hex), publicKey, keySignature, expiresAt (ms), signature, textures: { value, signature } }
 * with every binary field base64.
 */

const b64 = s => Uint8Array.from(atob(s), c => c.charCodeAt(0))

const keyCache = new Map()
async function importKey(spkiBase64, hash) {
  const id = hash + spkiBase64
  if (!keyCache.has(id)) {
    keyCache.set(id, crypto.subtle.importKey('spki', b64(spkiBase64), { name: 'RSASSA-PKCS1-v1_5', hash }, false, ['verify']))
  }
  return keyCache.get(id)
}

async function signedByAny(keys, hash, signature, data) {
  for (const key of keys) {
    try {
      if (await crypto.subtle.verify('RSASSA-PKCS1-v1_5', await importKey(key, hash), signature, data)) return true
    } catch { /* try the next key */ }
  }
  return false
}

/** What the client signs: fixed text plus this connection's challenge, so a signature can't be reused elsewhere. */
export function challengeText(serverId) {
  return `nexora-login:${serverId}`
}

/** { id (32 hex), name } when the proof holds, else null. */
export async function verifyProof(proof, serverId) {
  try {
    if (!proof || typeof proof !== 'object') return null
    const uuid = String(proof.uuid || '').replace(/-/g, '').toLowerCase()
    if (!/^[0-9a-f]{32}$/.test(uuid)) return null
    const expiresAt = Number(proof.expiresAt)
    if (!Number.isFinite(expiresAt) || expiresAt < Date.now()) return null

    // 1. Mojang's signature over UUID (two longs), expiry (a long, ms) and the key's DER bytes.
    const publicKey = b64(String(proof.publicKey))
    const payload = new Uint8Array(24 + publicKey.length)
    for (let i = 0; i < 16; i++) payload[i] = parseInt(uuid.slice(i * 2, i * 2 + 2), 16)
    new DataView(payload.buffer).setBigInt64(16, BigInt(expiresAt))
    payload.set(publicKey, 24)
    if (!(await signedByAny(PLAYER_CERTIFICATE_KEYS, 'SHA-1', b64(String(proof.keySignature)), payload))) return null

    // 2. The challenge, signed with that key.
    const playerKey = await crypto.subtle.importKey('spki', publicKey, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify'])
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', playerKey, b64(String(proof.signature)), new TextEncoder().encode(challengeText(serverId)))
    if (!ok) return null

    // 3. The name, from the profile textures Mojang signed for this UUID.
    const value = String(proof.textures?.value || '')
    if (!(await signedByAny(PROFILE_PROPERTY_KEYS, 'SHA-1', b64(String(proof.textures?.signature || '')), new TextEncoder().encode(value)))) return null
    const textures = JSON.parse(new TextDecoder().decode(b64(value)))
    if (String(textures.profileId || '').toLowerCase() !== uuid || typeof textures.profileName !== 'string') return null
    return { id: uuid, name: textures.profileName }
  } catch {
    return null
  }
}
