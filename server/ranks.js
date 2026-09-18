'use strict'
/**
 * Ranks, read from the published ranks.json the launcher already uses
 * (RankSyncService.ts), refreshed every few minutes. The owner's rank lives
 * only in their own launcher, so owners come from the OWNERS setting.
 */
const RANK_ORDER = ['owner', 'co_owner', 'admin', 'staff', 'developer', 'media', 'crystal_plus', 'member']
const DEFAULT_URL = 'https://raw.githubusercontent.com/Lunar0710/Crystal/main/ranks.json'
const REFRESH_MS = 5 * 60 * 1000

/** Same rule as the launcher's meetsRank: at or above the required rank. */
function meetsRank(rank, required) {
  const have = RANK_ORDER.indexOf(rank), need = RANK_ORDER.indexOf(required)
  return have >= 0 && need >= 0 && have <= need
}

/** Crystal+ perks: every rank above member has them. */
function hasPerks(rank) {
  return meetsRank(rank, 'crystal_plus')
}

class RankBook {
  constructor({ url, owners = [] } = {}) {
    this.url = url || DEFAULT_URL
    this.owners = new Set(owners.map(n => n.toLowerCase()))
    this.grants = {}
    this.timer = null
  }

  rankOf(name) {
    const key = String(name || '').toLowerCase()
    if (this.owners.has(key)) return 'owner'
    const grant = this.grants[key]
    if (!grant || !RANK_ORDER.includes(grant.rank)) return 'member'
    if (typeof grant.expiresAt === 'number' && grant.expiresAt < Date.now()) return 'member'
    return grant.rank
  }

  hasPerks(rank) {
    return hasPerks(rank)
  }

  async refresh() {
    try {
      const res = await fetch(`${this.url}?t=${Date.now()}`, { signal: AbortSignal.timeout(10000) })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const body = await res.json()
      if (body && typeof body.grants === 'object') this.grants = body.grants
    } catch (err) {
      // Keep the last list; a short GitHub outage must not strip everyone's rank.
      console.warn('ranks.json not refreshed:', err.message)
    }
  }

  start() {
    this.refresh()
    this.timer = setInterval(() => this.refresh(), REFRESH_MS)
  }

  stop() {
    clearInterval(this.timer)
  }
}

module.exports = { RankBook, meetsRank, hasPerks, RANK_ORDER }
