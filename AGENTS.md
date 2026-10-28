# Local Android workflow

- Use the Android SDK and Android Virtual Devices installed through Android Studio on this laptop. Do not install another emulator stack.
- Before UI testing, check `adb devices` and reuse an existing usable Android Studio emulator. Do not start a duplicate instance of the same AVD.
- If no emulator is running, use an existing Android Studio Device Manager AVD. Start only one emulator, only when UI/device verification is needed. Prefer a visible emulator for interactive review; use headless mode only when requested or needed for unattended tests.
- Preserve the existing AVD memory configuration unless a specific test needs a change. The installed Medium Phone AVD currently uses 2048 MB of guest RAM.
- Stop emulators started for the task when testing is finished. Leave emulators opened by the user running.
- Stop project-specific Gradle/compiler daemons started for a task after build/test work. Identify ownership before stopping anything; never terminate all Java or emulator processes indiscriminately.
- Keep the current UI update local. Do not push changes or upload a release until the user explicitly authorizes it.
