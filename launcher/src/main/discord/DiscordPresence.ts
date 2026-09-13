import { Client } from '@xhayper/discord-rpc'
import Store from 'electron-store'
import { logger } from '../logs/Logger'

/**
 * Discord Application ID. Not a secret — every client using Rich Presence
 * ships its own ID publicly. Create the app at discord.com/developers, upload
 * the Crystal logo as an art asset named "crystal", and paste the ID here.
 */
const APPLICATION_ID = process.env.CRYSTAL_DISCORD_APP_ID || ''

export interface PresenceState {
  /** Short line, e.g. "Spielt Minecraft 1.21.11". */
  details?: string
  /** Second line, e.g. the instance name. */
  state?: string
  /** When the current activity started, for Discord's elapsed timer. */
  startedAt?: number
}

/**
 * Rich Presence for the launcher.
 *
 * Discord being closed is the normal case, not an error: connecting simply
 * fails and everything here becomes a no-op, so a missing Discord must never
 * surface as a broken launcher. Reconnection is attempted lazily on the next
 * update rather than on a timer, to avoid hammering the socket forever on a
 * machine that has no Discord installed at all.
 */
export class DiscordPresence {
  private client: Client | null = null
  private connecting: Promise<boolean> | null = null
  private connected = false
  private lastState: PresenceState | null = null
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  isEnabled(): boolean {
    // Defaults to on — it's a visible feature people expect to just work.
    return this.store.get('discord.enabled', true) as boolean
  }

  setEnabled(enabled: boolean): void {
    this.store.set('discord.enabled', enabled)
    if (!enabled) this.clear()
    else if (this.lastState) this.update(this.lastState)
  }

  /** False until a Discord application id is compiled in, in which case presence can never connect. */
  isConfigured(): boolean {
    return !!APPLICATION_ID
  }

  isConnected(): boolean {
    return this.connected
  }

  private async connect(): Promise<boolean> {
    if (this.connected) return true
    if (!APPLICATION_ID) return false
    if (this.connecting) return this.connecting

    this.connecting = (async () => {
      try {
        const client = new Client({ clientId: APPLICATION_ID })
        client.on('disconnected', () => {
          this.connected = false
          this.client = null
        })
        await client.login()
        this.client = client
        this.connected = true
        logger.info('launcher', 'Discord Rich Presence verbunden')
        return true
      } catch {
        // Discord not running / not installed — expected, stays quiet.
        this.connected = false
        this.client = null
        return false
      } finally {
        this.connecting = null
      }
    })()

    return this.connecting
  }

  async update(state: PresenceState): Promise<void> {
    this.lastState = state
    if (!this.isEnabled()) return
    if (!(await this.connect())) return

    try {
      await this.client?.user?.setActivity({
        details: state.details,
        state: state.state,
        startTimestamp: state.startedAt,
        largeImageKey: 'crystal',
        largeImageText: 'Crystal Client',
        instance: false,
      })
    } catch (err) {
      logger.debug?.('launcher', `Discord-Presence-Update fehlgeschlagen: ${String(err)}`)
      this.connected = false
      this.client = null
    }
  }

  async clear(): Promise<void> {
    if (!this.connected) return
    try {
      await this.client?.user?.clearActivity()
    } catch {
      // Nothing to recover from — presence is cosmetic.
    }
  }

  /** Back to the default "sitting in the launcher" line. */
  idle(): Promise<void> {
    return this.update({ details: 'Im Launcher', startedAt: Date.now() })
  }

  playing(instanceName: string, gameVersion: string): Promise<void> {
    return this.update({
      details: `Spielt Minecraft ${gameVersion}`,
      state: instanceName,
      startedAt: Date.now(),
    })
  }
}
