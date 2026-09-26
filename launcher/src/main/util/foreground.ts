import { execFile } from 'child_process'
import { logger } from '../logs/Logger'

/**
 * Brings a freshly started game's window to the front once it exists.
 *
 * Windows lets only the program in the foreground put a new window on top.
 * With the launcher minimised, Minecraft's window opened behind everything
 * else and looked as if the game never started. This waits (up to two
 * minutes: big modpacks take a while) for the window, restores it and gives
 * it focus. A tap of Alt first is the documented way to be allowed to.
 */
export function bringGameToFront(pid: number): void {
  if (process.platform !== 'win32' || !Number.isInteger(pid) || pid <= 0) return
  const script = `
Add-Type -Namespace N -Name W -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(System.IntPtr h);
[DllImport("user32.dll")] public static extern bool ShowWindow(System.IntPtr h, int n);
[DllImport("user32.dll")] public static extern bool IsIconic(System.IntPtr h);
[DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, int f, System.UIntPtr e);
'@
for ($i = 0; $i -lt 240; $i++) {
  $p = Get-Process -Id ${pid} -ErrorAction SilentlyContinue
  if ($null -eq $p) { exit }
  if ($p.MainWindowHandle -ne 0) {
    Start-Sleep -Milliseconds 400
    $h = $p.MainWindowHandle
    if ([N.W]::IsIconic($h)) { [N.W]::ShowWindow($h, 9) | Out-Null }
    [N.W]::keybd_event(0x12, 0, 0, [System.UIntPtr]::Zero)
    [N.W]::keybd_event(0x12, 0, 2, [System.UIntPtr]::Zero)
    [N.W]::SetForegroundWindow($h) | Out-Null
    exit
  }
  Start-Sleep -Milliseconds 500
}`
  execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', script], { windowsHide: true, timeout: 150000 }, err => {
    if (err && !err.killed) logger.debug?.('launcher', 'Spielfenster nicht nach vorne geholt: ' + String(err))
  })
}
