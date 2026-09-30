// Checks the crash window's "Windows ran out of memory" guess for a silent
// exit (src/main/minecraft/SilentMemoryExit.ts). Run: node scripts/test-silent-memory-exit.cjs
const fs = require('fs')
const path = require('path')
const assert = require('assert')
const ts = require('typescript')

const source = fs.readFileSync(path.join(__dirname, '../src/main/minecraft/SilentMemoryExit.ts'), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const mod = { exports: {} }
new Function('module', 'exports', 'require', js)(mod, mod.exports, require)
const { silentMemoryExit } = mod.exports

const header = '# Nexora launch 2026-09-30T10:00:00Z\n# jvmArgs:   -Xmx8192M -Xms1024M -XX:+UseG1GC\n\n'
const loading = [
  '[10:00:01] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.17.2',
  '[10:00:01] [main/INFO]: Loading 212 mods:',
  '\t- sodium 0.6.13',
  '[10:00:09] [main/INFO]: Compatibility level set to JAVA_21',
  '[10:00:12] [main/WARN]: Reference map \'foo-refmap.json\' for foo.mixins.json could not be read.',
  '[10:00:14] [main/INFO]: Mixing SomeMixin from bar.mixins.json into net.minecraft.class_310',
].join('\n')
const launcherMessage = '\nMinecraft hat sich beim Start beendet (Exit-Code 1).\n\nLog: C:\\x\\crystal-launch.log\n'
const facts = over => ({
  log: header + loading + launcherMessage,
  currentRam: 8192,
  totalMemMb: 16 * 1024 - 300,
  freshNativeCrash: false,
  freshCrashReport: false,
  ...over,
})

// The case from the user's PC: 8 GB on 16 GB, log stops while mixins load.
const hit = silentMemoryExit(facts())
assert.ok(hit, 'silent exit with 8 GB on 16 GB is detected')
assert.strictEqual(hit.id, 'silent-out-of-memory')
assert.strictEqual(hit.fix.kind, 'lower-ram')
assert.strictEqual(hit.fix.ram, 4096)

// 8 GB PC: half of the physical memory is the smaller one.
assert.strictEqual(silentMemoryExit(facts({ totalMemMb: 8 * 1024 - 200, log: header.replace('8192', '6144') + loading + launcherMessage })).fix.ram, 3584)

// Never when the game explained itself.
assert.strictEqual(silentMemoryExit(facts({ freshNativeCrash: true })), null, 'fresh hs_err')
assert.strictEqual(silentMemoryExit(facts({ freshCrashReport: true })), null, 'fresh crash report')
const withTrace = header + loading + '\njava.lang.RuntimeException: Mixin transformation failed\n\tat org.spongepowered.asm.mixin.Foo.bar(Foo.java:12)' + launcherMessage
assert.strictEqual(silentMemoryExit(facts({ log: withTrace })), null, 'exception at the end')
assert.strictEqual(silentMemoryExit(facts({ log: header + loading + '\n---- Minecraft Crash Report ----' + launcherMessage })), null, 'crash report in log')
assert.strictEqual(silentMemoryExit(facts({ log: header + loading + '\n[10:01:00] [Render thread/INFO]: Stopping!' + launcherMessage })), null, 'clean stop')
assert.strictEqual(silentMemoryExit(facts({ log: header + loading + '\n[10:00:30] [Render thread/INFO]: Sound engine started' + launcherMessage })), null, 'reached the menu')

// Other exit codes, no game output, a small heap: no guess.
assert.strictEqual(silentMemoryExit(facts({ log: header + loading + launcherMessage.replace('Exit-Code 1', 'Exit-Code 0') })), null, 'exit 0')
assert.strictEqual(silentMemoryExit(facts({ log: header + launcherMessage })), null, 'Java never wrote a game line')
assert.strictEqual(silentMemoryExit(facts({ log: header.replace('8192', '4096') + loading + launcherMessage })), null, 'already 4 GB')

// The "after start" message and Windows' commitment-limit status also count.
assert.ok(silentMemoryExit(facts({ log: header + loading + '\nMinecraft ist abgestürzt:\nExit-Code -1073741523\n' })), 'commitment limit status')

console.log('silent-memory-exit: all checks passed')
