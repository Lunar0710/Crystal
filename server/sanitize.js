'use strict'
/**
 * Everything a client sends about its looks, reduced to what is allowed.
 * The same limits the launcher applies when it writes loadout.json
 * (ipc.ts, cosmetics:syncLoadout), and rank checks on top, so a modified
 * client can neither send oversized shapes nor wear perks it doesn't have.
 */
const CAPES = require('./cape-ranks.json')
const { meetsRank, hasPerks } = require('./ranks')

const SLOTS = ['hat', 'bandana', 'mask', 'wings', 'backpack', 'aura', 'pet']
const ANCHORS = ['head', 'body', 'wing', 'pet']
const EMOTES = ['WAVE', 'CHEER', 'CLAP', 'DANCE', 'BOW', 'FACEPALM', 'POINT']
const MAX_BOXES = 64

const isHex = v => typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v)
const num = (v, limit) => (typeof v === 'number' && Number.isFinite(v) ? Math.max(-limit, Math.min(limit, v)) : 0)

/**
 * A built-in cape id the player may wear, or null. Uploaded capes are never
 * passed on: only Crystal's own designs are shown to other players.
 */
function cleanCape(id, rank) {
  if (typeof id !== 'string' || !Object.prototype.hasOwnProperty.call(CAPES, id)) return null
  const cape = CAPES[id]
  if (!cape.rank) return id
  if (cape.exact) return rank === 'owner' || rank === cape.rank ? id : null
  return meetsRank(rank, cape.rank) ? id : null
}

function cleanLoadout(items, rank) {
  const clean = {}
  if (!items || typeof items !== 'object') return clean
  for (const slot of SLOTS) {
    const item = items[slot]
    if (!item || typeof item !== 'object' || !isHex(item.color)) continue
    if (item.plusOnly && !hasPerks(rank)) continue
    const boxes = (Array.isArray(item.boxes) ? item.boxes : []).slice(0, MAX_BOXES).flatMap(raw => {
      if (!raw || !isHex(raw.color)) return []
      return [{
        x: num(raw.x, 32), y: num(raw.y, 32), z: num(raw.z, 32),
        w: Math.abs(num(raw.w, 32)), h: Math.abs(num(raw.h, 32)), d: Math.abs(num(raw.d, 32)),
        rz: num(raw.rz, 7), color: raw.color, glow: !!raw.glow,
      }]
    })
    clean[slot] = {
      color: item.color,
      secondary: isHex(item.secondary) ? item.secondary : null,
      variant: typeof item.variant === 'string' ? item.variant.slice(0, 32) : null,
      plusOnly: !!item.plusOnly,
      anchor: ANCHORS.includes(item.anchor) ? item.anchor : null,
      boxes,
    }
  }
  return clean
}

module.exports = { cleanCape, cleanLoadout, EMOTES }
