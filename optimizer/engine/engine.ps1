#Requires -Version 5.1
<#
  Lunar Optimizer engine. The app calls this for everything that touches
  Windows settings; reading needs no admin, changing HKLM does (the app then
  starts it elevated). Answers are JSON, on stdout or in -Out.

  Only settings Windows itself offers. Nothing here turns off Defender,
  Windows Update, the firewall or other security features. The value before
  every change is kept in %ProgramData%\LunarOptimizer\backup.json and "undo"
  writes exactly those values back.

  Actions: state | apply -Ids a,b | revert -Ids a,b | undo
           startup-list | startup-set -Arg <json>
           clean-scan | clean-run -Ids a,b
#>
param(
    [string]$Action = 'state',
    [string]$Ids = '',
    [string]$Arg = '',
    [string]$Out = ''
)

$ErrorActionPreference = 'Continue'
# Larger arguments (JSON) come as @file, which survives the quoting of an elevated start.
if ($Arg.StartsWith('@')) { $Arg = Get-Content -Path $Arg.Substring(1) -Raw -Encoding UTF8 }
$ProgressPreference = 'SilentlyContinue'
$DataDir = Join-Path $env:ProgramData 'LunarOptimizer'
$BackupFile = Join-Path $DataDir 'backup.json'
$IsAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

function Write-Result($obj) {
    $json = $obj | ConvertTo-Json -Depth 8 -Compress
    if ($Out) { [IO.File]::WriteAllText($Out, $json, (New-Object Text.UTF8Encoding($false))) } else { [Console]::Out.Write($json) }
}

# ------------------------------------------------------------------ backup
function Get-Backup {
    if (Test-Path $BackupFile) {
        try { return (Get-Content $BackupFile -Raw -Encoding UTF8 | ConvertFrom-Json) } catch {}
    }
    return [pscustomobject]@{ registry = [pscustomobject]@{}; powerScheme = $null }
}
function Save-Backup($b) {
    if (-not (Test-Path $DataDir)) { New-Item -ItemType Directory -Path $DataDir -Force | Out-Null }
    $tmp = "$BackupFile.tmp"
    [IO.File]::WriteAllText($tmp, ($b | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
    Move-Item -Path $tmp -Destination $BackupFile -Force
}

function Get-RegValue([string]$Path, [string]$Name) {
    if (-not (Test-Path $Path)) { return $null }
    $item = Get-ItemProperty -Path $Path -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $item -or -not ($item.PSObject.Properties.Name -contains $Name)) { return $null }
    return $item.$Name
}

# Remembers the original value the first time a setting is touched, so running
# the optimizer twice never overwrites the real originals.
function Set-Reg($Backup, [string]$Tweak, [string]$Path, [string]$Name, $Value, [string]$Type) {
    $key = "$Path|$Name"
    if (-not ($Backup.registry.PSObject.Properties.Name -contains $key)) {
        $current = Get-RegValue $Path $Name
        $kind = $Type
        if ($null -ne $current) { try { $kind = (Get-Item $Path).GetValueKind($Name).ToString() } catch {} }
        $entry = [pscustomobject]@{ tweak = $Tweak; path = $Path; name = $Name; exists = ($null -ne $current); value = $current; type = $kind }
        $Backup.registry | Add-Member -NotePropertyName $key -NotePropertyValue $entry
        Save-Backup $Backup
    }
    if (-not (Test-Path $Path)) { New-Item -Path $Path -Force | Out-Null }
    New-ItemProperty -Path $Path -Name $Name -Value $Value -PropertyType $Type -Force -ErrorAction Stop | Out-Null
}

function Restore-Entry($e) {
    if ($e.exists) {
        if (-not (Test-Path $e.path)) { New-Item -Path $e.path -Force | Out-Null }
        New-ItemProperty -Path $e.path -Name $e.name -Value $e.value -PropertyType $e.type -Force -ErrorAction Stop | Out-Null
    } elseif (Test-Path $e.path) {
        Remove-ItemProperty -Path $e.path -Name $e.name -ErrorAction SilentlyContinue
    }
}

# ------------------------------------------------------------------ services
function Set-ServiceStart($Backup, [string]$Tweak, [string]$Name, [string]$Start) {
    $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if (-not $svc) { return }
    if (-not ($Backup.PSObject.Properties.Name -contains 'services')) { $Backup | Add-Member -NotePropertyName services -NotePropertyValue ([pscustomobject]@{}) }
    if (-not ($Backup.services.PSObject.Properties.Name -contains $Name)) {
        $mode = (Get-CimInstance Win32_Service -Filter "Name='$Name'").StartMode
        $Backup.services | Add-Member -NotePropertyName $Name -NotePropertyValue ([pscustomobject]@{ tweak = $Tweak; start = $mode; running = ($svc.Status -eq 'Running') })
        Save-Backup $Backup
    }
    if ($Start -eq 'Disabled') { Stop-Service -Name $Name -Force -ErrorAction SilentlyContinue }
    Set-Service -Name $Name -StartupType $Start -ErrorAction Stop
}
function Restore-Service($Name, $e) {
    $map = @{ 'Auto' = 'Automatic'; 'Manual' = 'Manual'; 'Disabled' = 'Disabled' }
    $type = $map["$($e.start)"]; if (-not $type) { $type = 'Manual' }
    Set-Service -Name $Name -StartupType $type -ErrorAction Stop
    if ($e.running) { Start-Service -Name $Name -ErrorAction SilentlyContinue }
}

# Registry entries decided at run time (one per network interface).
function Get-NagleEntries {
    $base = 'HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\Interfaces'
    $list = @()
    foreach ($k in Get-ChildItem $base -ErrorAction SilentlyContinue) {
        $p = Get-ItemProperty $k.PSPath -ErrorAction SilentlyContinue
        $ip = "$($p.DhcpIPAddress)$($p.IPAddress)"
        if ($ip -and $ip -notmatch '^0\.0\.0\.0') {
            $path = "$base\$($k.PSChildName)"
            $list += , @($path, 'TcpAckFrequency', 1, 'DWord')
            $list += , @($path, 'TCPNoDelay', 1, 'DWord')
        }
    }
    return , $list
}

# ------------------------------------------------------------------ mouse
function Update-Mouse([int[]]$values) {
    try {
        if (-not ('Lunar.Native' -as [type])) {
            Add-Type -Namespace Lunar -Name Native -MemberDefinition '[DllImport("user32.dll")] public static extern bool SystemParametersInfo(int a, int b, int[] c, int d);'
        }
        [Lunar.Native]::SystemParametersInfo(0x0004, 0, $values, 3) | Out-Null
    } catch {}
}

# ------------------------------------------------------------------ power plan
function Get-ActiveScheme {
    $line = ((powercfg /getactivescheme) | Out-String).Trim()
    $guid = if ($line -match '([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})') { $Matches[1] } else { $null }
    $name = if ($line -match '\((.*)\)') { $Matches[1] } else { '' }
    return [pscustomobject]@{ guid = $guid; name = $name }
}
function Get-LunarScheme {
    $line = (powercfg /list) | Where-Object { $_ -match 'Lunar Gaming' } | Select-Object -First 1
    if ($line -and $line -match '([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})') { return $Matches[1] }
    return $null
}
function Apply-Power($Backup) {
    $active = Get-ActiveScheme
    if (-not $Backup.powerScheme -and $active.name -notmatch 'Lunar Gaming') { $Backup.powerScheme = $active.guid; Save-Backup $Backup }
    $guid = Get-LunarScheme
    if (-not $guid) {
        # "Ultimate Performance"; where Windows doesn't offer it, "High performance".
        $out = (powercfg -duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61 2>$null) | Out-String
        if ($out -notmatch '[0-9a-fA-F]{8}-') { $out = (powercfg -duplicatescheme 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c 2>$null) | Out-String }
        if ($out -notmatch '([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})') { throw 'Energiesparplan konnte nicht angelegt werden.' }
        $guid = $Matches[1]
        powercfg -changename $guid 'Lunar Gaming' 'Lunar Optimizer: volle Leistung fuer Spiele.' | Out-Null
    }
    # USB selective suspend off (mice and headsets never nap), PCIe link power management off.
    powercfg -setacvalueindex $guid 2a737441-1930-4402-8d77-b2bebba308a3 48e6b7a6-50f5-4782-a5d4-53bb8f07e226 0 2>$null | Out-Null
    powercfg -setacvalueindex $guid 501a4d13-42af-4429-9fd1-a8218c268e20 ee12f906-d277-404b-b6da-e5fa1a576df5 0 2>$null | Out-Null
    # No core parking (all cores stay awake), processor never below 100 %.
    powercfg -setacvalueindex $guid 54533251-82be-4824-96c1-47b60b740d00 0cc5b647-c1df-4637-891a-dec35c318583 100 2>$null | Out-Null
    powercfg -setacvalueindex $guid 54533251-82be-4824-96c1-47b60b740d00 893dee8e-2bef-41e0-89c6-b55d0929964c 100 2>$null | Out-Null
    powercfg -setactive $guid | Out-Null
}
function Revert-Power($Backup) {
    if ($Backup.powerScheme) { powercfg -setactive $Backup.powerScheme 2>$null | Out-Null }
    else { powercfg -setactive 381b4222-f694-41f0-9685-ff5bb260df2e 2>$null | Out-Null }
    $lunar = Get-LunarScheme
    if ($lunar) { powercfg -delete $lunar 2>$null | Out-Null }
    $Backup.powerScheme = $null
    Save-Backup $Backup
}

# ------------------------------------------------------------------ tweaks
$MM = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile'
$Tweaks = @(
    @{ id = 'power'; cat = 'Leistung'; admin = $true; reboot = $false; impact = 'hoch'
       name = 'Energiesparplan "Lunar Gaming"'
       desc = 'Alle CPU-Kerne bleiben wach (kein Core Parking) und laufen nie unter vollem Takt, USB-Geräte wie Maus und Headset gehen nie in den Stromsparmodus. Auf Laptops im Akkubetrieb braucht das mehr Strom.'
       custom = $true },
    @{ id = 'gamemode'; cat = 'Leistung'; admin = $false; reboot = $false; impact = 'mittel'
       name = 'Spielmodus an'
       desc = 'Windows gibt dem Spiel im Vordergrund Vorrang und hält Updates während des Spielens zurück.'
       reg = @(
           @('HKCU:\Software\Microsoft\GameBar', 'AutoGameModeEnabled', 1, 'DWord'),
           @('HKCU:\Software\Microsoft\GameBar', 'AllowAutoGameMode', 1, 'DWord')) },
    @{ id = 'gamedvr'; cat = 'Leistung'; admin = $false; reboot = $false; impact = 'hoch'
       name = 'Hintergrund-Aufnahme aus'
       desc = 'Die Xbox Game Bar zeichnet nicht mehr ständig die letzten Minuten auf. Das kostet sonst FPS und Festplattenzugriffe.'
       reg = @(
           @('HKCU:\System\GameConfigStore', 'GameDVR_Enabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\GameDVR', 'AppCaptureEnabled', 0, 'DWord')) },
    @{ id = 'hags'; cat = 'Leistung'; admin = $true; reboot = $true; impact = 'mittel'
       name = 'Hardwarebeschleunigte GPU-Planung'
       desc = 'Die Grafikkarte verwaltet ihren Speicher selbst. Weniger Latenz, wenn Karte und Treiber es können (ab GTX 1000 / RX 5000).'
       reg = @(, @('HKLM:\SYSTEM\CurrentControlSet\Control\GraphicsDrivers', 'HwSchMode', 2, 'DWord')) },
    @{ id = 'priority'; cat = 'Leistung'; admin = $true; reboot = $true; impact = 'mittel'
       name = 'Spiele vor Hintergrund-Aufgaben'
       desc = 'Der Windows-Planer gibt Spielen mehr CPU- und GPU-Zeit als Hintergrund-Diensten.'
       reg = @(
           @($MM, 'SystemResponsiveness', 10, 'DWord'),
           @("$MM\Tasks\Games", 'GPU Priority', 8, 'DWord'),
           @("$MM\Tasks\Games", 'Priority', 6, 'DWord'),
           @("$MM\Tasks\Games", 'Scheduling Category', 'High', 'String'),
           @("$MM\Tasks\Games", 'SFIO Priority', 'High', 'String')) },
    @{ id = 'throttle'; cat = 'Leistung'; admin = $true; reboot = $true; impact = 'mittel'
       name = 'Power Throttling aus'
       desc = 'Windows bremst Programme im Hintergrund nicht mehr künstlich herunter. Hilft, wenn Discord, OBS oder ein Overlay neben dem Spiel laufen.'
       reg = @(, @('HKLM:\SYSTEM\CurrentControlSet\Control\Power\PowerThrottling', 'PowerThrottlingOff', 1, 'DWord')) },
    @{ id = 'foreground'; cat = 'Leistung'; admin = $true; reboot = $false; impact = 'mittel'
       name = 'Vordergrund-Boost'
       desc = 'Das aktive Fenster (dein Spiel) bekommt kürzere, häufigere CPU-Zeitscheiben. Eingaben werden schneller verarbeitet.'
       reg = @(, @('HKLM:\SYSTEM\CurrentControlSet\Control\PriorityControl', 'Win32PrioritySeparation', 38, 'DWord')) },
    @{ id = 'fso'; cat = 'Leistung'; admin = $false; reboot = $false; impact = 'mittel'; optional = $true
       name = 'Vollbildoptimierungen aus'
       desc = 'Echtes Vollbild statt randlosem Fenster, wo das Spiel es anbietet. Bei manchen Spielen weniger Input-Lag, bei anderen kein Unterschied.'
       reg = @(
           @('HKCU:\System\GameConfigStore', 'GameDVR_FSEBehaviorMode', 2, 'DWord'),
           @('HKCU:\System\GameConfigStore', 'GameDVR_HonorUserFSEBehaviorMode', 1, 'DWord'),
           @('HKCU:\System\GameConfigStore', 'GameDVR_DXGIHonorFSEWindowsCompatible', 1, 'DWord')) },
    @{ id = 'mouse'; cat = 'Eingabe'; admin = $false; reboot = $false; impact = 'hoch'
       name = 'Mausbeschleunigung aus'
       desc = '"Zeigerbeschleunigung verbessern" aus: Die Maus bewegt sich 1:1, egal wie schnell du sie ziehst. Wichtig fürs Zielen.'
       reg = @(
           @('HKCU:\Control Panel\Mouse', 'MouseSpeed', '0', 'String'),
           @('HKCU:\Control Panel\Mouse', 'MouseThreshold1', '0', 'String'),
           @('HKCU:\Control Panel\Mouse', 'MouseThreshold2', '0', 'String')) },
    @{ id = 'sticky'; cat = 'Eingabe'; admin = $false; reboot = $false; impact = 'niedrig'
       name = 'Einrastfunktion-Abfrage aus'
       desc = 'Fünfmal Shift im Spiel öffnet nicht mehr das Fenster "Einrastfunktion". Die Funktion selbst bleibt in den Einstellungen verfügbar.'
       reg = @(
           @('HKCU:\Control Panel\Accessibility\StickyKeys', 'Flags', '506', 'String'),
           @('HKCU:\Control Panel\Accessibility\ToggleKeys', 'Flags', '58', 'String'),
           @('HKCU:\Control Panel\Accessibility\Keyboard Response', 'Flags', '122', 'String')) },
    @{ id = 'startupdelay'; cat = 'System'; admin = $false; reboot = $false; impact = 'niedrig'
       name = 'Autostart ohne Wartezeit'
       desc = 'Windows wartet nach dem Anmelden nicht mehr künstlich, bevor Autostart-Programme laden.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Serialize', 'StartupDelayInMSec', 0, 'DWord')) },
    @{ id = 'deliveryopt'; cat = 'Netzwerk'; admin = $true; reboot = $false; impact = 'mittel'
       name = 'Keine Update-Weitergabe'
       desc = 'Windows lädt heruntergeladene Updates nicht mehr über deine Leitung an fremde PCs hoch. Weniger Ping-Spitzen.'
       reg = @(, @('HKLM:\SOFTWARE\Policies\Microsoft\Windows\DeliveryOptimization', 'DODownloadMode', 0, 'DWord')) },
    @{ id = 'nagle'; cat = 'Netzwerk'; admin = $true; reboot = $true; impact = 'mittel'; optional = $true
       name = 'Nagle-Algorithmus aus'
       desc = 'Kleine Netzwerkpakete werden sofort geschickt statt kurz gesammelt. Weniger Verzögerung in Spielen mit TCP (z. B. Minecraft), minimal mehr Datenverkehr.'
       dyn = 'nagle' },
    @{ id = 'telemetry'; cat = 'Datenschutz'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Telemetrie-Dienst aus'
       desc = 'Der Dienst "Benutzererfahrung und Telemetrie" sendet keine Nutzungsdaten mehr an Microsoft und läuft nicht mehr im Hintergrund. Updates und Sicherheit bleiben unberührt.'
       svc = @(, @('DiagTrack', 'Disabled')) },
    @{ id = 'netthrottle'; cat = 'Netzwerk'; admin = $true; reboot = $true; impact = 'niedrig'; optional = $true
       name = 'Netzwerk-Drosselung aus'
       desc = 'Windows bremst Netzwerkverkehr nicht mehr, während Audio oder Video läuft (z. B. Discord-Call beim Spielen).'
       reg = @(, @($MM, 'NetworkThrottlingIndex', -1, 'DWord')) },
    @{ id = 'windowed'; cat = 'Grafik'; admin = $false; reboot = $false; impact = 'mittel'
       name = 'Optimierungen für Fenster-Spiele'
       desc = 'Spiele im Fenster oder randlosen Vollbild laufen mit derselben niedrigen Latenz wie im echten Vollbild, dazu variable Bildwiederholrate (G-Sync/FreeSync) auch im Fenster. Ab Windows 10 21H2.'
       reg = @(, @('HKCU:\Software\Microsoft\DirectX\UserGpuPreferences', 'DirectXUserGlobalSettings', 'SwapEffectUpgradeEnable=1;VRROptimizeEnable=1;', 'String')) },
    @{ id = 'visualfx'; cat = 'Optik'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Visuelle Effekte auf Leistung'
       desc = 'Windows schaltet Schatten, Überblendungen und geglättete Animationen ab. Spürbar auf alten PCs, sieht schlichter aus.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VisualEffects', 'VisualFXSetting', 2, 'DWord')) },
    @{ id = 'menudelay'; cat = 'Optik'; admin = $false; reboot = $false; impact = 'niedrig'
       name = 'Menüs ohne Verzögerung'
       desc = 'Untermenüs öffnen sofort statt nach 0,4 Sekunden.'
       reg = @(, @('HKCU:\Control Panel\Desktop', 'MenuShowDelay', '0', 'String')) },
    @{ id = 'keyboard'; cat = 'Eingabe'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Schnellste Tastenwiederholung'
       desc = 'Gehaltene Tasten wiederholen früher und schneller (kürzeste Verzögerung, höchste Rate).'
       reg = @(
           @('HKCU:\Control Panel\Keyboard', 'KeyboardDelay', '0', 'String'),
           @('HKCU:\Control Panel\Keyboard', 'KeyboardSpeed', '31', 'String')) },
    @{ id = 'gamebarkey'; cat = 'Eingabe'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Xbox-Taste öffnet keine Game Bar'
       desc = 'Die Xbox-Taste am Controller öffnet nicht mehr mitten im Spiel die Game Bar.'
       reg = @(, @('HKCU:\Software\Microsoft\GameBar', 'UseNexusForGameBarEnabled', 0, 'DWord')) },
    @{ id = 'tips'; cat = 'System'; admin = $false; reboot = $false; impact = 'niedrig'
       name = 'Tipps und Werbung aus'
       desc = 'Keine Vorschläge im Startmenü, keine Tipps-Popups, keine still installierten Werbe-Apps. Spart Hintergrundarbeit.'
       reg = @(
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SubscribedContent-338388Enabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SubscribedContent-338389Enabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SubscribedContent-353694Enabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SubscribedContent-353696Enabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SilentInstalledAppsEnabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SystemPaneSuggestionsEnabled', 0, 'DWord'),
           @('HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager', 'SoftLandingEnabled', 0, 'DWord')) },
    @{ id = 'websearch'; cat = 'System'; admin = $false; reboot = $true; impact = 'niedrig'; optional = $true
       name = 'Websuche im Startmenü aus'
       desc = 'Die Startmenü-Suche fragt nicht mehr bei Bing nach. Sucht schneller, findet dafür nur, was auf dem PC ist.'
       reg = @(, @('HKCU:\Software\Policies\Microsoft\Windows\Explorer', 'DisableSearchBoxSuggestions', 1, 'DWord')) },
    @{ id = 'toasts'; cat = 'System'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Benachrichtigungen aus'
       desc = 'Keine Popups unten rechts mehr, auch nicht mitten im Spiel. Das Info-Center bleibt, zeigt aber nichts Neues an.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\PushNotifications', 'ToastEnabled', 0, 'DWord')) },
    @{ id = 'hibernate'; cat = 'System'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Ruhezustand aus'
       desc = 'Löscht die Ruhezustand-Datei (oft 4 bis 16 GB) und schaltet den Schnellstart ab, der manchmal Treiberprobleme mitnimmt. Energiesparen bleibt.'
       custom = $true },
    @{ id = 'sysmain'; cat = 'Dienste'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'SysMain (Superfetch) aus'
       desc = 'Lädt Programme nicht mehr vorab in den Arbeitsspeicher. Nur mit SSD sinnvoll: weniger Festplattenlast und Ruckler beim Start.'
       svc = @(, @('SysMain', 'Disabled')) },
    @{ id = 'wsearch'; cat = 'Dienste'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Windows-Suchindex aus'
       desc = 'Kein ständiges Durchsuchen der Festplatte im Hintergrund. Die Suche im Explorer wird dafür langsamer.'
       svc = @(, @('WSearch', 'Disabled')) },
    @{ id = 'spooler'; cat = 'Dienste'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Druckdienst aus'
       desc = 'Nur, wenn du keinen Drucker nutzt. Ein Dienst weniger, der im Hintergrund läuft.'
       svc = @(, @('Spooler', 'Disabled')) },
    @{ id = 'adid'; cat = 'Datenschutz'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Werbe-ID aus'
       desc = 'Apps bekommen keine Werbe-ID mehr, über die sie dich wiedererkennen.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\AdvertisingInfo', 'Enabled', 0, 'DWord')) },
    @{ id = 'activity'; cat = 'Datenschutz'; admin = $true; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Aktivitätsverlauf aus'
       desc = 'Windows speichert nicht mehr, welche Programme und Dateien du wann benutzt hast.'
       reg = @(
           @('HKLM:\SOFTWARE\Policies\Microsoft\Windows\System', 'PublishUserActivities', 0, 'DWord'),
           @('HKLM:\SOFTWARE\Policies\Microsoft\Windows\System', 'UploadUserActivities', 0, 'DWord')) },
    @{ id = 'transparency'; cat = 'Optik'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Transparenz-Effekte aus'
       desc = 'Taskleiste und Startmenü ohne Durchsichtigkeit. Etwas weniger GPU-Last auf schwachen Grafikkarten.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize', 'EnableTransparency', 0, 'DWord')) },
    @{ id = 'animations'; cat = 'Optik'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Fenster-Animationen aus'
       desc = 'Fenster öffnen und minimieren ohne Animation. Windows fühlt sich schneller an.'
       reg = @(, @('HKCU:\Control Panel\Desktop\WindowMetrics', 'MinAnimate', '0', 'String')) },
    @{ id = 'backgroundapps'; cat = 'System'; admin = $false; reboot = $false; impact = 'niedrig'; optional = $true
       name = 'Store-Apps im Hintergrund aus'
       desc = 'Apps aus dem Microsoft Store laufen nicht mehr heimlich weiter. Mail und Co. melden sich dann nur, wenn sie offen sind.'
       reg = @(, @('HKCU:\Software\Microsoft\Windows\CurrentVersion\BackgroundAccessApplications', 'GlobalUserDisabled', 1, 'DWord')) }
)
function Restore-Hibernate($h) {
    $path = 'HKLM:\SYSTEM\CurrentControlSet\Control\Power'
    if ($null -eq $h.value) { Remove-ItemProperty -Path $path -Name 'HibernateEnabled' -ErrorAction SilentlyContinue }
    elseif ([int]$h.value -ne 0) { powercfg /hibernate on | Out-Null }
}
function Find-Tweak($id) { $Tweaks | Where-Object { $_.id -eq $id } | Select-Object -First 1 }

# The registry entries of a tweak, always as a list of entries. PowerShell
# flattens a one-entry list written as @(, @(...)) inside the table above,
# which would hand back the path's letters instead of the entry.
function Get-Regs($t) {
    if ($t.dyn -eq 'nagle') { $r = Get-NagleEntries } else { $r = $t.reg }
    if ($null -eq $r) { return , @() }
    if ($r.Count -gt 0 -and $r[0] -is [string]) { return , @(, $r) }
    return , $r
}

# DWORDs come back signed (0xFFFFFFFF reads as -1): compare them as 32-bit patterns.
function Get-Norm($x) {
    if ($x -is [int] -or $x -is [long] -or $x -is [uint32]) { return "$(([int64]$x) -band 0xFFFFFFFF)" }
    return "$x"
}
function Test-Applied($t) {
    if ($t.id -eq 'power') { return ((Get-ActiveScheme).name -match 'Lunar Gaming') }
    if ($t.id -eq 'hibernate') { return ((Get-RegValue 'HKLM:\SYSTEM\CurrentControlSet\Control\Power' 'HibernateEnabled') -eq 0) }
    if ($t.svc) {
        foreach ($sv in $t.svc) { $x = Get-Service -Name $sv[0] -ErrorAction SilentlyContinue; if ($x -and "$($x.StartType)" -ne $sv[1]) { return $false } }
        return $true
    }
    $regs = Get-Regs $t
    if ($regs.Count -eq 0) { return $false }
    foreach ($r in $regs) {
        $v = Get-RegValue $r[0] $r[1]
        if ($null -eq $v -or (Get-Norm $v) -ne (Get-Norm $r[2])) { return $false }
    }
    return $true
}

function Apply-Tweak($Backup, $t) {
    if ($t.id -eq 'power') { Apply-Power $Backup; return }
    if ($t.id -eq 'hibernate') {
        if (-not ($Backup.PSObject.Properties.Name -contains 'hibernate')) {
            # The exact old value: 1, 0 or none (PCs without hibernation have no value at all).
            $Backup | Add-Member -NotePropertyName hibernate -NotePropertyValue ([pscustomobject]@{ value = (Get-RegValue 'HKLM:\SYSTEM\CurrentControlSet\Control\Power' 'HibernateEnabled') })
            Save-Backup $Backup
        }
        powercfg /hibernate off | Out-Null
        return
    }
    if ($t.svc) { foreach ($sv in $t.svc) { Set-ServiceStart $Backup $t.id $sv[0] $sv[1] }; return }
    $regs = Get-Regs $t
    foreach ($r in $regs) { Set-Reg $Backup $t.id $r[0] $r[1] $r[2] $r[3] }
    if ($t.id -eq 'mouse') { Update-Mouse @(0, 0, 0) }
}

function Revert-Tweak($Backup, $t) {
    if ($t.id -eq 'power') { Revert-Power $Backup; return }
    if ($t.id -eq 'hibernate') {
        if ($Backup.PSObject.Properties.Name -contains 'hibernate') {
            Restore-Hibernate $Backup.hibernate
            $Backup.PSObject.Properties.Remove('hibernate'); Save-Backup $Backup
        }
        return
    }
    if ($t.svc -and ($Backup.PSObject.Properties.Name -contains 'services')) {
        foreach ($p in @($Backup.services.PSObject.Properties | Where-Object { $_.Value.tweak -eq $t.id })) {
            Restore-Service $p.Name $p.Value
            $Backup.services.PSObject.Properties.Remove($p.Name)
        }
        Save-Backup $Backup
        return
    }
    $keys = @($Backup.registry.PSObject.Properties | Where-Object { $_.Value.tweak -eq $t.id } | ForEach-Object { $_.Name })
    foreach ($k in $keys) {
        Restore-Entry $Backup.registry.$k
        $Backup.registry.PSObject.Properties.Remove($k)
    }
    Save-Backup $Backup
    if ($t.id -eq 'mouse') {
        $m = Get-ItemProperty 'HKCU:\Control Panel\Mouse' -ErrorAction SilentlyContinue
        if ($m) { Update-Mouse @([int]$m.MouseThreshold1, [int]$m.MouseThreshold2, [int]$m.MouseSpeed) }
    }
}

# ------------------------------------------------------------------ startup
# The same switch Task Manager uses: StartupApproved, first byte 2 = on, 3 = off.
$StartupSources = @(
    @{ id = 'hkcu'; label = 'Benutzer'; run = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'; approved = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run'; admin = $false },
    @{ id = 'hklm'; label = 'Alle Benutzer'; run = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Run'; approved = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run'; admin = $true },
    @{ id = 'hklm32'; label = 'Alle Benutzer (32 Bit)'; run = 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run'; approved = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run32'; admin = $true },
    @{ id = 'folder'; label = 'Autostart-Ordner'; dir = [Environment]::GetFolderPath('Startup'); approved = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder'; admin = $false },
    @{ id = 'commonfolder'; label = 'Autostart-Ordner (alle)'; dir = [Environment]::GetFolderPath('CommonStartup'); approved = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder'; admin = $true }
)
function Get-StartupEnabled($approvedPath, $name) {
    # @(): a byte[] returned from a function arrives as loose bytes.
    $v = @(Get-RegValue $approvedPath $name)
    if ($v.Count -lt 1 -or $null -eq $v[0]) { return $true }
    return ((([int]$v[0]) -band 1) -eq 0)
}
function Get-StartupItems {
    $items = @()
    foreach ($s in $StartupSources) {
        if ($s.run) {
            if (-not (Test-Path $s.run)) { continue }
            $props = Get-ItemProperty -Path $s.run
            foreach ($p in $props.PSObject.Properties) {
                if ($p.Name -like 'PS*') { continue }
                $items += [pscustomobject]@{ source = $s.id; sourceLabel = $s.label; name = $p.Name; command = "$($p.Value)"; enabled = (Get-StartupEnabled $s.approved $p.Name); admin = $s.admin }
            }
        } elseif ($s.dir -and (Test-Path $s.dir)) {
            foreach ($f in Get-ChildItem -Path $s.dir -File -ErrorAction SilentlyContinue) {
                if ($f.Name -eq 'desktop.ini') { continue }
                $items += [pscustomobject]@{ source = $s.id; sourceLabel = $s.label; name = $f.Name; command = $f.FullName; enabled = (Get-StartupEnabled $s.approved $f.Name); admin = $s.admin }
            }
        }
    }
    return , $items
}
function Set-StartupItem($source, $name, [bool]$enabled) {
    $s = $StartupSources | Where-Object { $_.id -eq $source } | Select-Object -First 1
    if (-not $s) { throw "Unbekannte Quelle $source" }
    if (-not (Test-Path $s.approved)) { New-Item -Path $s.approved -Force | Out-Null }
    $bytes = New-Object byte[] 12
    if ($enabled) {
        $bytes[0] = [byte]2
    } else {
        $bytes[0] = [byte]3
        [BitConverter]::GetBytes([DateTime]::UtcNow.ToFileTimeUtc()).CopyTo($bytes, 4)
    }
    New-ItemProperty -Path $s.approved -Name $name -Value $bytes -PropertyType Binary -Force -ErrorAction Stop | Out-Null
}

# ------------------------------------------------------------------ cleanup
# Only things Windows and programs rebuild on their own. No shader caches
# (deleting them makes the next game start stutter) and no personal files.
$CleanTargets = @(
    @{ id = 'usertemp'; name = 'Temporäre Dateien'; desc = 'Reste von Installationen und Programmen'; admin = $false; paths = @($env:TEMP) },
    @{ id = 'wintemp'; name = 'Windows-Temp'; desc = 'Temporäre Dateien von Windows selbst'; admin = $true; paths = @((Join-Path $env:SystemRoot 'Temp')) },
    @{ id = 'updates'; name = 'Update-Downloads'; desc = 'Bereits installierte Windows-Updates'; admin = $true; paths = @((Join-Path $env:SystemRoot 'SoftwareDistribution\Download')) },
    @{ id = 'dumps'; name = 'Absturzberichte'; desc = 'Speicherabbilder alter Programmabstürze'; admin = $true; paths = @((Join-Path $env:LOCALAPPDATA 'CrashDumps'), (Join-Path $env:SystemRoot 'Minidump'), (Join-Path $env:ProgramData 'Microsoft\Windows\WER\ReportArchive')) },
    @{ id = 'thumbs'; name = 'Miniaturansichten'; desc = 'Vorschaubilder im Explorer, werden neu erstellt'; admin = $false; paths = @(); files = (Join-Path $env:LOCALAPPDATA 'Microsoft\Windows\Explorer\thumbcache_*.db') },
    @{ id = 'recycle'; name = 'Papierkorb'; desc = 'Gelöschte Dateien endgültig entfernen'; admin = $false; paths = @(); recycle = $true }
)
function Get-FolderSize($path) {
    if (-not $path -or -not (Test-Path $path)) { return 0 }
    $sum = 0
    try { Get-ChildItem -Path $path -Recurse -Force -File -ErrorAction SilentlyContinue | ForEach-Object { $sum += $_.Length } } catch {}
    return $sum
}
function Get-RecycleSize {
    try {
        $shell = New-Object -ComObject Shell.Application
        $sum = 0
        foreach ($i in $shell.NameSpace(10).Items()) { $sum += [int64]$i.Size }
        return $sum
    } catch { return 0 }
}
function Get-CleanSize($c) {
    if ($c.recycle) { return (Get-RecycleSize) }
    $sum = 0
    foreach ($p in $c.paths) { $sum += Get-FolderSize $p }
    if ($c.files) { Get-ChildItem -Path $c.files -Force -ErrorAction SilentlyContinue | ForEach-Object { $sum += $_.Length } }
    return $sum
}
function Invoke-Clean($c) {
    if ($c.recycle) { try { Clear-RecycleBin -Force -ErrorAction Stop } catch {}; return }
    if ($c.id -eq 'updates') { Stop-Service wuauserv, bits -Force -ErrorAction SilentlyContinue }
    foreach ($p in $c.paths) {
        # A path this short would be a drive root: never.
        if (-not $p -or $p.Length -lt 8 -or -not (Test-Path $p)) { continue }
        Get-ChildItem -Path $p -Force -ErrorAction SilentlyContinue | ForEach-Object {
            try { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction Stop } catch {}
        }
    }
    if ($c.files) { Get-ChildItem -Path $c.files -Force -ErrorAction SilentlyContinue | ForEach-Object { try { Remove-Item -LiteralPath $_.FullName -Force -ErrorAction Stop } catch {} } }
    if ($c.id -eq 'updates') { Start-Service bits, wuauserv -ErrorAction SilentlyContinue }
}

# ------------------------------------------------------------------ DNS
# The adapters that actually carry traffic (up, with a default route).
function Get-ActiveAdapters {
    $routes = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty ifIndex -Unique
    Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' -and $routes -contains $_.ifIndex }
}
function Get-DnsState {
    $list = foreach ($a in Get-ActiveAdapters) {
        $servers = @((Get-DnsClientServerAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue).ServerAddresses)
        [pscustomobject]@{ ifIndex = $a.ifIndex; name = $a.Name; description = $a.InterfaceDescription; wireless = ($a.NdisPhysicalMedium -eq 9 -or $a.InterfaceDescription -match 'Wi-?Fi|Wireless|WLAN|802\.11'); speed = "$($a.LinkSpeed)"; servers = $servers }
    }
    return , @($list)
}
function Set-Dns($Backup, $servers, [bool]$reset) {
    if (-not ($Backup.PSObject.Properties.Name -contains 'dns')) { $Backup | Add-Member -NotePropertyName dns -NotePropertyValue ([pscustomobject]@{}) }
    foreach ($a in Get-ActiveAdapters) {
        $key = "$($a.ifIndex)"
        if ($reset) {
            if ($Backup.dns.PSObject.Properties.Name -contains $key) {
                $e = $Backup.dns.$key
                if ($e.static -and $e.static.Count -gt 0) { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ServerAddresses $e.static -ErrorAction Stop }
                else { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ResetServerAddresses -ErrorAction Stop }
                $Backup.dns.PSObject.Properties.Remove($key)
            }
            continue
        }
        if (-not ($Backup.dns.PSObject.Properties.Name -contains $key)) {
            # Empty NameServer in the registry means the addresses came from the router (DHCP).
            $reg = Get-RegValue "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\Interfaces\$($a.InterfaceGuid)" 'NameServer'
            $static = @("$reg" -split '[, ]' | Where-Object { $_ })
            $Backup.dns | Add-Member -NotePropertyName $key -NotePropertyValue ([pscustomobject]@{ static = $static })
        }
        Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ServerAddresses $servers -ErrorAction Stop
    }
    Save-Backup $Backup
    Clear-DnsClientCache -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------------ hardware
# Everything the hardware page shows, in one process (one CIM session)
# instead of a PowerShell start per question.
function Get-Hardware {
    $cim = New-CimSession -ErrorAction SilentlyContinue
    $q = { param($c) if ($cim) { Get-CimInstance -CimSession $cim -ClassName $c -ErrorAction SilentlyContinue } else { Get-CimInstance -ClassName $c -ErrorAction SilentlyContinue } }
    $cpu = & $q Win32_Processor | Select-Object -First 1
    $cs = & $q Win32_ComputerSystem
    $os = & $q Win32_OperatingSystem
    $mods = @(& $q Win32_PhysicalMemory)
    $vcs = @(& $q Win32_VideoController)
    $board = & $q Win32_BaseBoard
    $bat = & $q Win32_Battery | Select-Object -First 1
    $memType = @{ 20 = 'DDR'; 21 = 'DDR2'; 24 = 'DDR3'; 26 = 'DDR4'; 34 = 'DDR5'; 35 = 'LPDDR5' }
    # Real VRAM sizes: AdapterRAM stops at 4 GB, the driver's registry entry doesn't.
    $vram = @{}
    Get-ItemProperty 'HKLM:\SYSTEM\ControlSet001\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}\0*' -ErrorAction SilentlyContinue | ForEach-Object {
        $size = $_.'HardwareInformation.qwMemorySize'
        if ($_.DriverDesc -and $size) { $vram[$_.DriverDesc] = [int64]$size }
    }
    $monitors = @()
    try { $monitors = @(Get-CimInstance -Namespace root\wmi -ClassName WmiMonitorID -ErrorAction Stop | ForEach-Object { (($_.UserFriendlyName | Where-Object { $_ -ne 0 } | ForEach-Object { [char]$_ }) -join '').Trim() }) } catch {}
    $pd = @(); try { $pd = @(Get-PhysicalDisk -ErrorAction Stop) } catch {}
    $gpus = @($vcs | Where-Object { $_.Name })
    $out = [pscustomobject]@{
        platform = 'win32'
        cpu = [pscustomobject]@{ manufacturer = ''; brand = "$($cpu.Name)".Trim(); physicalCores = $cpu.NumberOfCores; cores = $cpu.NumberOfLogicalProcessors
                                 speed = [math]::Round($cpu.CurrentClockSpeed / 1000, 2); speedMax = [math]::Round($cpu.MaxClockSpeed / 1000, 2); socket = $cpu.SocketDesignation }
        mem = [pscustomobject]@{ total = [int64]$cs.TotalPhysicalMemory }
        layout = @($mods | ForEach-Object { [pscustomobject]@{ size = [int64]$_.Capacity; clockSpeed = $(if ($_.ConfiguredClockSpeed) { $_.ConfiguredClockSpeed } else { $_.Speed }); type = $memType[[int]$_.SMBIOSMemoryType] } })
        graphics = [pscustomobject]@{
            controllers = @($gpus | ForEach-Object { [pscustomobject]@{ model = $_.Name; vram = $(if ($vram[$_.Name]) { [math]::Round($vram[$_.Name] / 1MB) } elseif ($_.AdapterRAM) { [math]::Round($_.AdapterRAM / 1MB) } else { $null }) } })
            displays = @($gpus | Where-Object { $_.CurrentHorizontalResolution } | ForEach-Object -Begin { $i = 0 } -Process {
                [pscustomobject]@{ model = $(if ($monitors.Count -gt $i) { $monitors[$i] } else { 'Bildschirm' }); main = ($i -eq 0); currentResX = $_.CurrentHorizontalResolution; currentResY = $_.CurrentVerticalResolution; currentRefreshRate = $_.CurrentRefreshRate }
                $i++ })
        }
        drivers = @($gpus | ForEach-Object { [pscustomobject]@{ name = $_.Name; version = $_.DriverVersion; date = $(try { $_.DriverDate.ToString('yyyy-MM-dd') } catch { $null }); refresh = $_.CurrentRefreshRate; maxRefresh = $_.MaxRefreshRate } })
        osInfo = [pscustomobject]@{ distro = $os.Caption; release = $os.Version; build = $os.BuildNumber }
        disks = @($pd | ForEach-Object { [pscustomobject]@{ type = $(switch ("$($_.MediaType)") { 'SSD' { 'SSD' } 'HDD' { 'HD' } default { if ("$($_.BusType)" -eq 'NVMe') { 'SSD' } else { '' } } }); name = $_.FriendlyName; size = [int64]$_.Size; interfaceType = "$($_.BusType)" } })
        fsSize = @(Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue | Where-Object { $null -ne $_.Used -and ($_.Used + $_.Free) -gt 0 } | ForEach-Object { [pscustomobject]@{ mount = "$($_.Name):"; fs = ''; size = [int64]($_.Used + $_.Free); used = [int64]$_.Used } })
        system = [pscustomobject]@{ manufacturer = $cs.Manufacturer; model = $cs.Model }
        board = [pscustomobject]@{ manufacturer = $board.Manufacturer; model = $board.Product }
        battery = [pscustomobject]@{ hasBattery = [bool]$bat; percent = $bat.EstimatedChargeRemaining; isCharging = ($bat.BatteryStatus -eq 2) }
    }
    if ($cim) { Remove-CimSession $cim }
    return $out
}

# ------------------------------------------------------------------ actions
try {
    $idList = @($Ids -split ',' | Where-Object { $_ })
    switch ($Action) {
        'state' {
            $b = Get-Backup
            $list = foreach ($t in $Tweaks) {
                $ap = Test-Applied $t
                # For a registry tweak that isn't on: what the registry holds (and its type), for diagnosis.
                $actual = $null
                if (-not $ap -and -not $t.svc -and -not $t.custom) { $actual = (@(Get-Regs $t) | ForEach-Object { $v = Get-RegValue $_[0] $_[1]; "$($_[1])=$v [$(if ($null -ne $v) { $v.GetType().Name })]" }) -join '; ' }
                [pscustomobject]@{ id = $t.id; name = $t.name; desc = $t.desc; cat = $t.cat; admin = $t.admin; reboot = $t.reboot; impact = $t.impact
                                   optional = [bool]$t.optional; applied = $ap; actual = $actual }
            }
            Write-Result ([pscustomobject]@{ ok = $true; admin = $IsAdmin; tweaks = @($list); backup = (Test-Path $BackupFile); powerPlan = (Get-ActiveScheme).name })
        }
        'apply' {
            $b = Get-Backup
            $res = foreach ($id in $idList) {
                $t = Find-Tweak $id
                try { Apply-Tweak $b $t; [pscustomobject]@{ id = $id; ok = $true } }
                catch { [pscustomobject]@{ id = $id; ok = $false; error = $_.Exception.Message } }
            }
            Write-Result ([pscustomobject]@{ ok = $true; results = @($res) })
        }
        'revert' {
            $b = Get-Backup
            $res = foreach ($id in $idList) {
                $t = Find-Tweak $id
                try { Revert-Tweak $b $t; [pscustomobject]@{ id = $id; ok = $true } }
                catch { [pscustomobject]@{ id = $id; ok = $false; error = $_.Exception.Message } }
            }
            Write-Result ([pscustomobject]@{ ok = $true; results = @($res) })
        }
        'undo' {
            $b = Get-Backup
            $n = 0
            foreach ($p in @($b.registry.PSObject.Properties)) { try { Restore-Entry $p.Value; $n++ } catch {} }
            if ($b.PSObject.Properties.Name -contains 'hibernate') { Restore-Hibernate $b.hibernate; $n++ }
            if ($b.PSObject.Properties.Name -contains 'dns') { try { Set-Dns $b @() $true; $n++ } catch {} }
            if ($b.PSObject.Properties.Name -contains 'services') { foreach ($p in @($b.services.PSObject.Properties)) { try { Restore-Service $p.Name $p.Value; $n++ } catch {} } }
            if ((Get-LunarScheme) -or $b.powerScheme) { Revert-Power $b }
            $m = Get-ItemProperty 'HKCU:\Control Panel\Mouse' -ErrorAction SilentlyContinue
            if ($m) { Update-Mouse @([int]$m.MouseThreshold1, [int]$m.MouseThreshold2, [int]$m.MouseSpeed) }
            Remove-Item $BackupFile -Force -ErrorAction SilentlyContinue
            Write-Result ([pscustomobject]@{ ok = $true; restored = $n })
        }
        'hardware' { Write-Result ([pscustomobject]@{ ok = $true; hardware = (Get-Hardware) }) }
        # What the start screen needs, in one process: tweaks, startup items, DNS.
        'overview' {
            $list = foreach ($t in $Tweaks) {
                [pscustomobject]@{ id = $t.id; name = $t.name; desc = $t.desc; cat = $t.cat; admin = $t.admin; reboot = $t.reboot; impact = $t.impact
                                   optional = [bool]$t.optional; applied = (Test-Applied $t) }
            }
            $dnsChanged = $false; $bk = Get-Backup; if ($bk.PSObject.Properties.Name -contains 'dns') { $dnsChanged = @($bk.dns.PSObject.Properties).Count -gt 0 }
            Write-Result ([pscustomobject]@{ ok = $true; admin = $IsAdmin; tweaks = @($list); backup = (Test-Path $BackupFile); powerPlan = (Get-ActiveScheme).name
                                             startup = (Get-StartupItems); dns = [pscustomobject]@{ ok = $true; adapters = (Get-DnsState); changed = $dnsChanged } })
        }
        'dns-get' { Write-Result ([pscustomobject]@{ ok = $true; adapters = (Get-DnsState); changed = ((Get-Backup).PSObject.Properties.Name -contains 'dns' -and @((Get-Backup).dns.PSObject.Properties).Count -gt 0) }) }
        'dns-set' {
            $a = $Arg | ConvertFrom-Json
            $b = Get-Backup
            Set-Dns $b @($a.servers) ([bool]$a.reset)
            Write-Result ([pscustomobject]@{ ok = $true; adapters = (Get-DnsState) })
        }
        'startup-list' { Write-Result ([pscustomobject]@{ ok = $true; items = (Get-StartupItems) }) }
        'startup-set' {
            $a = $Arg | ConvertFrom-Json
            Set-StartupItem $a.source $a.name ([bool]$a.enabled)
            Write-Result ([pscustomobject]@{ ok = $true })
        }
        'clean-scan' {
            $list = foreach ($c in $CleanTargets) { [pscustomobject]@{ id = $c.id; name = $c.name; desc = $c.desc; admin = $c.admin; bytes = [int64](Get-CleanSize $c) } }
            Write-Result ([pscustomobject]@{ ok = $true; targets = @($list); freeBytes = [int64](Get-PSDrive C).Free })
        }
        'clean-run' {
            $before = (Get-PSDrive C).Free
            foreach ($id in $idList) { $c = $CleanTargets | Where-Object { $_.id -eq $id } | Select-Object -First 1; if ($c) { Invoke-Clean $c } }
            $after = (Get-PSDrive C).Free
            Write-Result ([pscustomobject]@{ ok = $true; freedBytes = [int64]([math]::Max(0, $after - $before)); freeBytes = [int64]$after })
        }
        default { Write-Result ([pscustomobject]@{ ok = $false; error = "Unbekannte Aktion $Action" }) }
    }
} catch {
    Write-Result ([pscustomobject]@{ ok = $false; error = $_.Exception.Message })
}
