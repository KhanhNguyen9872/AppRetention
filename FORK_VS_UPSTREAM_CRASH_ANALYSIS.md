# AppRetention Fork vs Upstream `new`: SystemUI Freeze / Soft-Reboot Analysis

## 1. Status and scope

- Analysis date: 2026-09-12
- Stable reference reported by the tester: [`HChenX/AppRetention`, branch `new`](https://github.com/HChenX/AppRetention/tree/new)
- Upstream reference fetched and inspected: `b86a826a7042d16eb1b99ea6f31b7cfaa56fb494`
- Fork reference fetched and inspected: `efe110a607066150875c645775a7605a3db349b0`
- Merge base: `b86a826a7042d16eb1b99ea6f31b7cfaa56fb494`
- Fork-only range: `upstream/new..efe110a`
- Runtime report: the upstream build does not reproduce the freeze; only the fork intermittently freezes SystemUI, can trigger a soft reboot, and can freeze again immediately after reboot.
- This document is research only. No production hook or UI implementation has been changed.

## 2. Corrected conclusion

The original project already makes aggressive memory-management changes, but those shared changes cannot by themselves explain a regression observed only in the fork. The cause must be in the fork-only delta.

The fork does not introduce one isolated defect. It adds several high-risk operations inside `system_server`, and their effects compound:

1. global suppression of broadcast delivery timeouts and some process kills;
2. unsafe, and on Nubia/RedMagic duplicated, OEM hook registration;
3. synchronous file logging with a `flush()` for hot hook events inside `system_server`;
4. re-entrant force-stop/self-termination paths invoked from AMS/ATMS task callbacks;
5. UI switches that can display OFF without proving the system property used at boot is OFF;
6. more permissive LMKD thresholds on high-RAM devices;
7. aggressive root polling while the dashboard is open.

The highest-probability causal chain is:

```text
fork-only system_server hook fires
  -> broadcast/process cleanup is suppressed or AMS/ATMS is re-entered
  -> synchronous hook logging and/or force-stop work blocks a critical thread
  -> SystemUI loses responsive framework services
  -> system_server Watchdog kills/restarts system_server
  -> module injects the same hooks again
  -> freeze/crash loop after the soft reboot
```

AOSP Watchdog explicitly kills the system process when watched system threads remain overdue, which matches the observed soft-reboot shape: <https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/Watchdog.java>.

## 3. Upstream-to-fork behavior matrix

| Area | Upstream `new` (`b86a826`) | Fork (`efe110a`) | Regression relevance |
|---|---|---|---|
| Xposed entry | Legacy Xposed entry backed by private HookTool submodule | Rewritten to libxposed API 101 with a vendored 2026 HookTool | Runtime migration risk; not sufficient alone to identify the crash |
| Android 16 routing | Android 15 only | Android 15, 16, and every later SDK through `upward=true` | High compatibility risk on changed OEM framework internals |
| KillShield | Absent | Hooks `killLocked`, `killProcessQuiet`, and `deliveryTimeoutLocked` | P0 |
| Doze blanket exemption | Absent | Hooks multiple public/internal whitelist checks | P1 |
| Nubia/RedMagic policy | Absent | Broad name-based OEM hooks, registered through two paths | P0 on Nubia/RedMagic |
| App hibernation override | Absent | Globally overrides hibernation setters/getters | P1 |
| AutoStart override | Absent | Broad name-based hooks over Nubia, Xiaomi, and Oplus classes | P1 |
| Background restriction | Absent | Hooks Task, RecentTasks, ATMS, AMS and ProcessList lifecycle paths | P0/P1 |
| Direct system-server log files | Absent in this form | Synchronous file write and flush for hook logs | P0/P1 amplifier |
| LMKD discount | Fixed divisor 3 | Divisor 3, 4, or 5 based on RAM | P1, strongest on 12/16 GB devices |
| Dashboard hardware/process scan | No fork dashboard | Root process scan and sysfs/root polling every 3 seconds | P1 amplifier while the app is open |
| Storage display | No fork dashboard metric | `/data` filesystem bytes shown as `GB` after binary conversion | Confirmed display defect, unrelated to SystemUI root cause |

## 4. P0 findings

### RC-1: `KillShieldOpt` suppresses the broadcast queue's recovery path globally

Introduced by fork commit `d7b676b`.

Relevant source:

- `app/src/main/java/com/hchen/appretention/hook/system/opt/KillShieldOpt.java:151-166`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/KillShieldOpt.java:133-149`

The hook enumerates every method named `deliveryTimeoutLocked` in `BroadcastQueueModernImpl` and returns without calling the original method. It does not inspect the timed-out receiver, package, UID, process type, or whether the target is on an explicit keep-alive list.

In AOSP, `deliveryTimeoutLocked` finishes the active timed-out receiver and demotes its process queue from the running set. Suppressing the entire method can leave a timed-out queue in an invalid/stuck state instead of recovering it:

<https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/BroadcastQueueModernImpl.java>

The same class also intercepts a vendor `killProcessQuiet(int, String)` overload using only a reason-string match. Unlike its `ProcessRecord.killLocked` hook, this path has no user-app check. If an OEM exposes that overload, the hook can block a kill without proving that the PID belongs to a non-system application.

Why this fits the report:

- it exists only in the fork;
- it runs in `system_server`;
- failure may be intermittent and workload dependent;
- a stuck broadcast/AMS path can make SystemUI appear to be the crashing component even though SystemUI is not directly scoped.

### RC-2: Nubia/RedMagic hooks are registered twice and use unsafe method-name matching

This is the strongest device-specific finding if the affected device is Nubia/RedMagic/MyOS.

Relevant source:

- `app/src/main/java/com/hchen/appretention/hook/nubia/NubiaPolicy.java:24`
- `app/src/main/java/com/hchen/appretention/hook/nubia/NubiaPolicy.java:47-49`
- `app/src/main/java/com/hchen/appretention/hook/nubia/NubiaPolicy.java:64-74`
- `app/src/main/java/com/hchen/appretention/hook/nubia/NubiaPolicy.java:77-160`
- `app/src/main/java/com/hchen/appretention/hook/system/AndroidS.java:84-86`
- `app/src/main/java/com/hchen/appretention/hook/system/AndroidT.java:93-95`
- `app/src/main/java/com/hchen/appretention/hook/system/AndroidU.java:94-97`
- `app/src/main/java/com/hchen/appretention/hook/system/AndroidV.java:94-96`

`NubiaPolicy` already has `@HookEntrance(targetPackage = "android", targetBrand = "nubia")`, so generated `EntranceMap` dispatches it as an independent hook. Every Android 12-16 adapter also calls `NubiaPolicy.manualInit()` when the ROM matches Nubia/ZTE/RedMagic. Therefore the same OEM methods can receive two hook layers.

The policy then hooks every declared method whose name contains broad tokens such as `clean`, `kill`, `autoclean`, `purge`, or `terminate`. It does not use an audited method/signature allowlist.

`handleSafeReturn()` is not actually safe for all matched methods. It handles only `void`, boolean, and int explicitly, then returns `null` for every other type. A matched primitive `long`, float, double, or another non-null contract can therefore fail at the framework call site. Even when the return type accepts `null`, replacing unknown OEM methods wholesale can violate required state transitions.

This explains why the behavior can differ by OS/MyOS version: the set and signatures of OEM methods vary by build.

### RC-3: hot hook logs perform synchronized disk I/O and `flush()` inside `system_server`

Introduced by fork commit `284a225`.

Relevant source:

- `app/src/main/java/com/hchen/appretention/log/SaveLog.java:232-240`
- `app/src/main/java/com/hchen/appretention/log/SaveLog.java:338-349`
- `app/src/main/java/com/hchen/appretention/log/XposedLog.java:39-163`

For target package `android`, every custom `XposedLog.log*()` call enters the synchronized `SaveLog.saveLogContent()`, opens/uses an app-private log file, writes one line, and immediately calls `BufferedWriter.flush()`.

The new KillShield, Nubia and BackgroundRestrict callbacks log from AMS/ATMS/BroadcastQueue hot paths. This produces three hazards:

1. critical framework threads serialize on one Java monitor;
2. those threads can block on filesystem I/O;
3. duplicate Nubia hooks can duplicate the I/O per event.

Opening the Logs page also executes `chmod -R 777` on the log directory (`MainActivity.java:1596-1599`). On a device where the system process could not previously write the directory, this can make the synchronous logging path start succeeding, which is a plausible reason interaction with the app makes the freeze happen sooner.

### RC-4: AppRetention self-termination can re-enter task removal/force-stop paths

Introduced across `d4de23d`, `039b373`, and `f5375da`.

Relevant source:

- `app/src/main/java/com/hchen/appretention/hook/system/opt/BackgroundRestrictOpt.java:51-54`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/BackgroundRestrictOpt.java:135-230`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/BackgroundRestrictOpt.java:533-603`

The fork hooks:

- `RecentTasks.remove(Task)`;
- every `Task` method whose name starts with `remove`;
- every `ActivityTaskManagerService.removeTask` overload;
- activity stop/foreground transitions;
- every `ProcessList.startProcessLocked` overload.

`shouldTerminateOnTaskRemoved()` always targets AppRetention itself, even when the restriction list is empty and Immediate Kill is OFF.

From a task-removal callback, `terminatePackage()` can:

1. scan `/proc` synchronously;
2. send SIGKILL twice;
3. obtain ActivityManager and synchronously call `forceStopPackage()` for user 0 and all users;
4. spawn `am force-stop` and `pkill -9 -f` processes.

Force-stopping a package can itself remove tasks. Calling it from a task-removal hook creates a credible re-entry/recursion or lock-order failure path inside AMS/ATMS. Hooking several overlapping removal layers makes the same package termination eligible multiple times for one user action.

This is a direct explanation for “pressing/removing/opening the app makes the freeze occur sooner,” while the other always-active hooks explain why it may still happen without that interaction.

## 5. Why turning the three switches OFF is not a valid isolation test

Assuming the three referenced switches are Tiered ADJ, KillShield, and Doze:

Relevant source:

- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:587-611`
- `app/src/main/java/com/hchen/appretention/ui/RootTool.java:13-43`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/KillShieldOpt.java:32-46`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/DeviceIdleOpt.java:21-25,74-76`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/ApplyAdjOpt.java:222-249,300-302`

There are four separate problems:

1. The UI writes SharedPreferences immediately but writes the boot-time system property on a single-thread asynchronous executor.
2. The property-write path ignores all failures and performs no read-back verification.
3. KillShield and Doze read their enable property only during `init()`. Turning the switch off does not unhook an already-installed callback.
4. Tiered ADJ is not the master ApplyAdj switch. Turning it off only selects another clamping branch; `persist.hchen.adj.opt.enable` remains true by default and has no corresponding UI control.

Additionally, BackgroundRestrict hooks have no master enable gate, direct system-server logging has no switch, and the AppRetention self-termination rule is always active.

Result: the UI can show all three controls as OFF while the current `system_server` still has the hooks installed, and even the next boot is not proven safe unless the actual `persist.hchen.*` values were read back successfully.

## 6. P1 amplifiers

### RC-5: the fork delays LMKD intervention further on high-RAM devices

Relevant source:

- `app/src/main/java/com/hchen/appretention/hook/system/opt/OomLevelsOpt.java:45-75`
- `app/src/main/java/com/hchen/appretention/hook/system/opt/OomLevelsOpt.java:192-200`

Upstream divides `mOomMinFree` by 3. The fork changes this to:

- divisor 3 below approximately 12 GB RAM;
- divisor 4 at approximately 12 GB;
- divisor 5 at approximately 16 GB.

This makes low-memory intervention progressively later on the high-RAM gaming devices most likely to run RedMagic/MyOS. It cannot alone explain the upstream/fork difference on lower-RAM devices, but it compounds KillShield and the existing retention hooks.

AOSP describes LMKD as the component that reacts to high memory pressure to avoid starvation and thrashing: <https://source.android.com/docs/core/perf/lmkd>.

### RC-6: opening the dashboard creates an additional root/process I/O load

Relevant source:

- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:92-94`
- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:213-223`
- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:1107-1185`
- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:1404-1427`
- `app/src/main/java/com/hchen/appretention/ui/RootTool.java:172-205`
- `app/src/main/java/com/hchen/appretention/ui/HardwareInfo.java:167-250`
- `app/src/main/java/com/hchen/appretention/ui/RootTool.java:318-340`

Every three seconds while the dashboard is visible, the fork submits a hardware refresh and a full process scan. The process scan executes a root shell over all processes. GPU detection can try nine load paths and five frequency paths, spawning a separate root `cat` for each inaccessible path until a working path is cached.

`RootTool.runCommand()` has no timeout. Hardware refresh has no in-flight/coalescing guard, and `Executors.newFixedThreadPool(2)` uses an unbounded work queue. A slow or hung `su` command can therefore make refresh tasks accumulate.

This is probably an accelerator rather than the original trigger because it runs in the application process, not `system_server`.

### RC-7: Android 16 and later reuse Android 15 hooks without an exact compatibility boundary

Relevant source:

- `app/src/main/java/com/hchen/appretention/HookInit.java:35-47,58-75`
- `app/src/main/java/com/hchen/appretention/hook/system/AndroidV.java:77-96`
- `app/build.gradle:38-44`

The fork is still compiled and targeted against SDK 34, but routes SDK 35, SDK 36, and every future SDK to `AndroidV` through `upward=true`. Newly added hooks also discover OEM methods by broad names instead of exact versioned signatures.

This is not proof that libxposed API 101 itself is faulty. It is proof that the fork claims a wider runtime contract than it has source-level adapters for.

### RC-8: packaging keeps both legacy and modern Xposed entry declarations

The built APK contains both:

- `assets/xposed_init`
- `META-INF/xposed/java_init.list`
- `META-INF/xposed/module.prop`
- `META-INF/xposed/scope.list`

The same `HookInit` class is named by both entry formats. A modern framework should normally select the modern metadata, but retaining both formats makes loader behavior framework-version dependent and should be removed or explicitly justified during implementation.

This is a compatibility risk, not a confirmed root cause.

## 7. Storage display defect

Relevant source:

- `app/src/main/java/com/hchen/appretention/ui/MainActivity.java:1158-1177`
- `app/src/main/java/com/hchen/appretention/ui/RootTool.java:89-145` (older/unreferenced `/data` `df` implementation)

The active dashboard path runs `StatFs(Environment.getDataDirectory())`, so it measures only the `/data` filesystem. It then divides bytes by `1024^3` but labels the value `GB`.

For a marketed 128 GB device:

```text
128,000,000,000 bytes / 1024^3 = 119.2 GiB
apparent loss when mislabeled as GB = 8.8
```

The remaining smaller difference is expected because `/data` excludes boot, metadata, system/vendor/super partitions, filesystem metadata, and reserved blocks. Android `StatFs` reports the filesystem at the supplied path, not whole-device physical capacity: <https://developer.android.com/reference/android/os/StatFs>. Android devices also contain multiple dedicated and dynamic partitions: <https://source.android.com/docs/core/architecture/partitions>.

The correct implementation should expose two explicitly named values rather than pretending they are the same metric:

1. `Usable internal storage`: `/data`, displayed in GiB;
2. `Physical device capacity`: the parent UFS/eMMC block device, displayed in decimal GB.

Partition sizes must not simply be summed because dynamic/logical partitions can overlap a parent `super` device and cause double counting.

## 8. Confidence assessment

| Finding | Static confidence | Runtime confirmation needed |
|---|---:|---|
| KillShield globally suppresses broadcast timeout recovery | High | Confirm stuck queue/watchdog trace |
| NubiaPolicy is registered twice on Nubia/RedMagic | High | Confirm affected device brand and duplicate init logs |
| NubiaPolicy may return null for unsupported return contracts | High | Record actual matched OEM methods/signatures |
| Direct system-server logging synchronously flushes hot events | High | Correlate I/O/blocking stacks with freeze |
| BackgroundRestrict can re-enter force-stop from task removal | High | Capture AMS/ATMS stack or recursion evidence |
| Switch OFF does not prove runtime hook/property OFF | High | Read back `persist.hchen.*` before and after reboot |
| Adaptive LMKD threshold worsens high-RAM pressure | Medium-high | Capture PSI, swap, LMKD and memory state |
| Dashboard root polling accelerates failure | Medium | Compare closed-app vs open-dashboard timing |
| libxposed migration or dual entry is independently causal | Low-medium | Framework-specific loader trace required |

The report deliberately does not claim one exact stack trace because no affected Android device was connected during analysis.

## 9. Runtime evidence required before declaring the issue closed

Collect one failing run with the fork and one control run with upstream on the same device/OS. Do not open the dashboard during the first phase; then repeat with the dashboard open.

```sh
adb shell su -c 'getprop | grep -E "persist\.hchen\.(killshield|doze|nubia|hibernation|autostart|adj|oom|restrict)"'
adb shell su -c 'dumpsys meminfo'
adb shell su -c 'cat /proc/pressure/memory'
adb shell su -c 'cat /proc/meminfo'
adb shell su -c 'dumpsys activity broadcasts'
adb logcat -b all -v threadtime > appretention-failure-logcat.txt
adb shell su -c 'dumpsys dropbox --print system_server_watchdog' > appretention-watchdog.txt
adb shell su -c 'ls -lt /data/anr /data/tombstones' > appretention-crash-files.txt
```

Signals that would confirm the leading hypotheses:

- `WATCHDOG KILLING SYSTEM PROCESS`;
- system-server stacks blocked in BroadcastQueue, AMS, ATMS, `SaveLog`, `FileWriter.flush`, or `forceStopPackage`;
- repeated `NubiaPolicy initialized` or duplicated intercept logs;
- a broadcast queue that retains an active timed-out receiver;
- growing memory PSI/full stalls, swap exhaustion, or delayed LMKD activity;
- system properties still true while UI switches show OFF.

## 10. Proposed implementation order — pending approval

No item below has been implemented yet.

### Phase A: minimum P0 safety patch

1. Remove the global `deliveryTimeoutLocked` replacement.
2. Remove the unscoped `killProcessQuiet` replacement.
3. Remove `NubiaPolicy.manualInit()` from Android adapters; keep exactly one registration path.
4. Replace Nubia/AutoStart name-substring discovery with exact class, method, parameters, and return-type contracts per supported OS.
5. Never replace an unknown primitive/object return with null.
6. Disable synchronous file logging from hook callbacks in `system_server`; use bounded asynchronous logging or logcat-only diagnostics.
7. Remove AppRetention self-termination from system-server Task/ATMS hooks.
8. Remove `Runtime.exec("am force-stop")` and `pkill` from system-server callbacks.

### Phase B: make controls truthful and fail-safe

1. Add one master fork-extension property, default OFF on unknown OS/OEM combinations.
2. Read the enable guard inside callbacks or reboot-gate the UI explicitly; do not imply live unhooking.
3. Persist properties transactionally, read them back, display failure, and never let SharedPreferences override a failed system-property write.
4. Separate `Tiered ADJ` from the actual `ApplyAdjOpt` master control.
5. Add a recovery/safe-mode path that leaves upstream behavior intact.

### Phase C: restore upstream parity before retuning

1. Restore upstream LMKD divisor behavior as the initial safe baseline.
2. Reintroduce high-RAM tuning only behind measured PSI/LMKD tests.
3. Do not change the shared upstream retention hooks in the first regression fix unless runtime evidence implicates them.

### Phase D: dashboard and storage

1. Coalesce dashboard refreshes and add an in-flight guard.
2. Batch sysfs reads into one bounded root command with a timeout.
3. Stop retrying every unavailable GPU path every three seconds.
4. Display `/data` as usable GiB and whole-device capacity separately as decimal GB.

## 11. Acceptance matrix for the future implementation

The patch should not be considered complete until all of these are observed on the affected device:

1. 10 cold boots without SystemUI crash or system-server watchdog restart.
2. 30 minutes idle with the module enabled and dashboard closed.
3. 30 minutes with dashboard open and periodic refresh active.
4. Repeated Home/Recents/app switching and AppRetention task removal.
5. Broadcast timeout recovery remains functional.
6. Stock LMKD still reacts under controlled memory pressure.
7. Every UI switch matches a verified system-property read-back before and after reboot.
8. Nubia/RedMagic hooks initialize exactly once and only exact audited methods are hooked.
9. Upstream keep-alive behavior remains intact for the intended app allowlist.
10. Storage labels distinguish GiB `/data` capacity from decimal whole-device GB.

## 12. Verification performed for this research artifact

- Fresh `git fetch` confirmed upstream `new` at `b86a826a7042d16eb1b99ea6f31b7cfaa56fb494`.
- Fresh `git fetch` confirmed fork `new` at `efe110a607066150875c645775a7605a3db349b0`.
- Merge-base and fork-only commit range were inspected.
- Current fork `:app:assembleDebug` completed successfully with JDK 21.
- No ADB device was connected, so no runtime watchdog/tombstone claim is presented as observed fact.
- No tracked unit or instrumentation tests exist in the repository.
- `:app:lintDebug` reports 49 errors and 188 warnings, dominated by string formatting and missing translations; this is separate from the SystemUI diagnosis.
- Upstream points to private submodule `HChenX/HookTool_module` at `16aada2b01c952b467ca427e1ff23d48796ba313`; it was not publicly retrievable, so the internal legacy-vs-vendored HookTool implementation cannot be fully diffed.

## 13. Implementation applied after approval

Implementation status: source-complete and host-verified; affected-device runtime verification remains pending.

Applied safety changes:

1. Added the default-off master property `persist.hchen.fork.extensions.enable` and a verified UI switch. When it is absent or false, fork-only system-server behavior is not registered and ApplyAdj/LMKD retain the upstream fork baseline.
2. Removed the global BroadcastQueue `deliveryTimeoutLocked` and unscoped `killProcessQuiet` replacements.
3. Removed duplicate `NubiaPolicy.manualInit()` registration. Nubia policy now defaults off, checks the master gate at callback time, and rejects methods without a package string plus a supported return contract.
4. Restricted DeviceIdle, AppHibernation and AutoStart candidate signatures and placed them behind the master gate; App Hibernation, AutoStart, and Nubia defaults are OFF.
5. Removed synchronous file writes and per-event `flush()` from `system_server` hook logging. Diagnostics remain available through the Xposed/logcat sink.
6. Reduced BackgroundRestrict to bounded hooks, removed generic Task/ATMS removal interception, removed AppRetention self-termination, and removed synchronous `forceStopPackage`, `am force-stop`, `pkill`, and `/proc` fallback work from system-server callbacks.
7. Restricted AndroidV routing to SDK 35 and 36 instead of automatically applying it to all future Android releases.
8. Removed the legacy `assets/xposed_init` entry so the API 101 build has one modern entry format.
9. Changed dashboard refresh from three to five seconds, added an in-flight hardware guard, placed an eight-second timeout around root commands, and batched privileged GPU fallback reads into one command.
10. Split storage semantics: `/data` is labeled GiB while whole-device block capacity is separately displayed in decimal GB.
11. Added `tools/verify_fork_stability.py` as a host-side regression contract.

Observed host verification after implementation:

```text
PASS: fork stability safety contract
PASS: 128 GB decimal = 119.2 GiB
BUILD SUCCESSFUL (:app:assembleDebug)
BUILD SUCCESSFUL (:app:assembleRelease)
```

`lintDebug` now has no `StringFormatInvalid` errors. It still exits nonzero with
46 pre-existing `MissingTranslation` errors in the incomplete Chinese locale;
those unrelated translations were deliberately not fabricated as part of the
SystemUI stability patch.

The implementation intentionally does not claim that the device freeze is closed until the acceptance matrix in section 11 is executed on the affected phone.
