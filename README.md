[简体中文](README_CN.md)

# Accessibility Keep-Alive

An LSPosed module tailored for ColorOS 15 to prevent accessibility permissions from being revoked when apps are stopped upon being removed from recent tasks.

## Installation

1. Install the APK.
2. Enable the module in LSPosed.
3. For the module scope, only select "System Framework" (marked with the "Recommended" tag).
4. Reboot the device.
5. Launch "Accessibility Keep-Alive" and grant Root access.
6. Select the accessibility apps you wish to protect.

## Build

```bash
./gradlew :app:assembleDebug
```

The output APK will be located at `app/build/outputs/apk/debug/`.

## Design

- Hooks run within the `android`/`system_server` scope.
- The whitelist is stored in Secure Settings and written via Root.
- Only targets apps killed via recent tasks removal; does not intercept manual "Force stop" actions from system Settings.

## Tested Devices

- OnePlus 12 (PJD110)
- ColorOS 15.0.2
- Android 15 / API 35
- KernelSU 32302
- LSPosed Zygisk 2.0.4 (7741)

## License

GPL-3.0-only