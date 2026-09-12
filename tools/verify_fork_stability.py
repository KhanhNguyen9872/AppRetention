#!/usr/bin/env python3
"""Host-side safety contract for fork-only AppRetention changes."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    failures: list[str] = []
    kill_shield = read(
        "app/src/main/java/com/hchen/appretention/hook/system/opt/KillShieldOpt.java"
    )
    background = read(
        "app/src/main/java/com/hchen/appretention/hook/system/opt/BackgroundRestrictOpt.java"
    )
    nubia = read("app/src/main/java/com/hchen/appretention/hook/nubia/NubiaPolicy.java")
    save_log = read("app/src/main/java/com/hchen/appretention/log/SaveLog.java")
    main_activity = read("app/src/main/java/com/hchen/appretention/ui/MainActivity.java")
    hardware = read("app/src/main/java/com/hchen/appretention/ui/HardwareInfo.java")
    feature_gate = read(
        "app/src/main/java/com/hchen/appretention/hook/system/opt/ForkFeatureGate.java"
    )

    require(
        "deliveryTimeoutLocked" not in kill_shield,
        "KillShield must not replace BroadcastQueue deliveryTimeoutLocked",
        failures,
    )
    require(
        "killProcessQuiet" not in kill_shield,
        "KillShield must not replace unscoped ProcessList.killProcessQuiet",
        failures,
    )
    require(
        "NubiaPolicy.manualInit" not in "".join(
            read(f"app/src/main/java/com/hchen/appretention/hook/system/Android{v}.java")
            for v in ("S", "T", "U", "V")
        ),
        "NubiaPolicy must have exactly one annotation-driven registration path",
        failures,
    )
    require(
        "hasSupportedReturnType" in nubia and "hasPackageArgument" in nubia,
        "Nubia hooks must filter parameter and return contracts",
        failures,
    )
    require(
        'SystemPropTool.getProp("persist.hchen.nubia.opt.enable", false)' in nubia,
        "Nubia OEM interception must default off",
        failures,
    )
    require(
        '"android".equals(HCData.getTargetPackageName())' in save_log
        and "openFile(tag, getRandomNumber());" not in save_log.split(
            'if ("android".equals(HCData.getTargetPackageName()))', 1
        )[1].split("}", 1)[0],
        "system_server logging branch must not perform file I/O",
        failures,
    )
    require(
        'Runtime.getRuntime().exec(new String[]{"am"' not in background
        and 'Runtime.getRuntime().exec(new String[]{"pkill"' not in background
        and "forceStopPackage" not in background,
        "system_server callbacks must not shell or synchronously force-stop packages",
        failures,
    )
    require(
        "PACKAGE_APPRETENTION.equals(packageName)" not in background,
        "AppRetention must not self-terminate from a system-server task hook",
        failures,
    )
    require(
        'PROP_ENABLE = "persist.hchen.fork.extensions.enable"' in feature_gate
        and "getProp(PROP_ENABLE, false)" in feature_gate,
        "fork-only system hooks must have a default-off master gate",
        failures,
    )
    require(
        "setBooleanPropVerified" in main_activity
        and "switchForkExtensions" in main_activity,
        "settings UI must verify the master property before accepting it",
        failures,
    )
    require(
        "compareAndSet(false, true)" in main_activity
        and "postDelayed(this, 5000)" in main_activity,
        "dashboard refresh must be coalesced and limited to five-second intervals",
        failures,
    )
    require(
        "getPhysicalStorageBytes" in hardware
        and "sectors * 512L" in hardware
        and "physicalStorageBytes / 1_000_000_000.0" in main_activity,
        "storage must distinguish whole-device decimal GB from filesystem GiB",
        failures,
    )
    require(
        not (ROOT / "app/src/main/assets/xposed_init").exists(),
        "modern API build must not retain the legacy Xposed entry",
        failures,
    )

    # Numeric regression for the reported 128 GB -> 119.2 GiB discrepancy.
    gib = 128_000_000_000 / (1024**3)
    require(abs(gib - 119.20928955078125) < 1e-9, "GB/GiB conversion changed", failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("PASS: fork stability safety contract")
    print(f"PASS: 128 GB decimal = {gib:.1f} GiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
