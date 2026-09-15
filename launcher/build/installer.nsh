; Crystal colours for the installer's header strip and welcome/finish pages.
!define MUI_BGCOLOR "0D0F14"
!define MUI_TEXTCOLOR "E4E8F0"

; Replaces the "Crystal Launcher cannot be closed, close it manually" dialog.
; An update starts the installer while the old launcher is still shutting
; down (or a second launcher window is open in the background), so instead of
; asking the player, the installer closes it itself: politely first, then by
; force. By image name only, never the process tree, so the installer itself
; (started by the launcher) is never affected.
!macro customCheckAppRunning
  nsExec::Exec 'taskkill /IM "${APP_EXECUTABLE_FILENAME}"'
  Sleep 1500
  nsExec::Exec 'taskkill /F /IM "${APP_EXECUTABLE_FILENAME}"'
  Sleep 1000
!macroend
