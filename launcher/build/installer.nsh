; Nexora colours for the installer's header strip and welcome/finish pages.
!define MUI_BGCOLOR "08080A"
!define MUI_TEXTCOLOR "F0F0F3"

; Replaces electron-builder's default "is the app still running" check.
; That default force-kills too (see allowOnlyOneInstallerInstance.nsh,
; _CHECK_APP_RUNNING), but gives up after ~2 retries (a few seconds) and then
; makes the player close the launcher by hand — "Nexora Launcher kann nicht
; geschlossen werden". An Electron app can take a moment longer to actually
; exit (GPU process, a game instance still shutting down), so this keeps
; force-killing by image name — never the installer's own process, which has
; a different name — for up to ~20 seconds before ever bothering the player,
; and if it's still somehow found after that, proceeds anyway rather than
; blocking the whole update on it.
!macro customCheckAppRunning
  DetailPrint "Nexora Launcher wird beendet..."
  StrCpy $R2 0
  crystal_close_loop:
    nsExec::Exec 'taskkill /F /IM "${APP_EXECUTABLE_FILENAME}"'
    Sleep 1000
    !insertmacro FIND_PROCESS "${APP_EXECUTABLE_FILENAME}" $R0
    IntCmp $R0 0 crystal_close_done
    IntOp $R2 $R2 + 1
    IntCmp $R2 20 crystal_close_done crystal_close_loop crystal_close_done
  crystal_close_done:
!macroend
