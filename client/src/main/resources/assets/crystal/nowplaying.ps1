# Prints what Spotify is playing, once a second, for Nexora's Spotify HUD.
# Reads Windows' media controls (the same source as the volume flyout), which
# know the song, the artist and where in the song playback is.
# One line per second: "none", or
# np<TAB>status<TAB>title<TAB>artist<TAB>positionMs<TAB>durationMs<TAB>positionUpdatedAtMs
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]
function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    $task.Result
}
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
while ($true) {
    try {
        $session = $null
        foreach ($s in $manager.GetSessions()) { if ($s.SourceAppUserModelId -like '*Spotify*') { $session = $s } }
        if ($null -eq $session) {
            [Console]::Out.WriteLine('none')
        } else {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $timeline = $session.GetTimelineProperties()
            $status = $session.GetPlaybackInfo().PlaybackStatus
            $title = ($props.Title -replace "`t", ' ')
            $artist = ($props.Artist -replace "`t", ' ')
            $pos = [long]$timeline.Position.TotalMilliseconds
            $dur = [long]$timeline.EndTime.TotalMilliseconds
            $at = $timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
            [Console]::Out.WriteLine("np`t$status`t$title`t$artist`t$pos`t$dur`t$at")
        }
    } catch {
        [Console]::Out.WriteLine('none')
    }
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 1000
}
