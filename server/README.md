# Crystal-Server

Lässt Crystal-Spieler die Emotes und Cosmetics der anderen sehen, wenn sie auf
demselben Minecraft-Server sind. Speichert nichts; nach einem Neustart melden
sich die Clients von selbst wieder.

## Starten

```
npm install
OWNERS=DeinMinecraftName PORT=8787 npm start
```

Dann im Launcher unter Einstellungen die Adresse eintragen, z. B.
`wss://crystal.example.com` (hinter einem HTTPS-Proxy) oder zum Testen
`ws://127.0.0.1:8787`.

| Variable    | Bedeutung                                                   |
|-------------|-------------------------------------------------------------|
| `PORT`      | Port, Standard 8787                                         |
| `OWNERS`    | Minecraft-Namen mit Owner-Rang, durch Komma getrennt        |
| `RANKS_URL` | ranks.json, Standard: die aus dem Crystal-Repo              |

`GET /` liefert den Status (verbundene Spieler, Räume).

## Was geprüft wird

- Jeder Spieler beweist über Mojang, dass er der Account ist, für den er sich ausgibt.
- Die Adresse des Minecraft-Servers verlässt den PC nie, nur ein Hash davon.
- Capes nur aus Crystals eigener Liste und nur mit dem nötigen Rang; hochgeladene Capes bleiben privat.
- Cosmetics mit denselben Größengrenzen wie im Launcher; Crystal+-Teile und Emotes nur mit Crystal+.
- Nachrichten höchstens 16 KB, Emotes und Loadout-Updates mit Obergrenze pro Minute.

Nach neuen Capes im Launcher: `node scripts/export-capes.cjs` (aktualisiert `cape-ranks.json`).

## Test

```
npm test
```
