#!/usr/bin/env bash
# Runs the already-built, consistently signed application and instrumentation APKs.
set -euo pipefail
app_apk=$(find "$GITHUB_WORKSPACE/device-apks/app" -name '*.apk' -print -quit)
test_apk=$(find "$GITHUB_WORKSPACE/device-apks/test" -name '*.apk' -print -quit)
test -n "$app_apk" && test -n "$test_apk"
adb install -r "$app_apk"
adb install -r "$test_apk"
set +e
adb shell am instrument -w com.aistudio.aurapulse.kxmpzq.test/androidx.test.runner.AndroidJUnitRunner \
  2>&1 | tee "$RUNNER_TEMP/aurapulse-device-tests.log"
result=${PIPESTATUS[0]}
set -e
# Collect the synthetic-track player screenshot while the emulator is still running.
adb pull /sdcard/aurapulse-player.png "$GITHUB_WORKSPACE/device-player.png" || true
# A manually dispatched, synthetic-only UI inspection run can surface the
# screenshot in check annotations when artifact downloads are inaccessible.
if [ "${GITHUB_EVENT_NAME:-}" = workflow_dispatch ] && [ -f "$GITHUB_WORKSPACE/device-player.png" ]; then
  python3 - <<'PYFRAME'
import base64
from pathlib import Path
encoded = base64.b64encode(Path('device-player.png').read_bytes()).decode('ascii')
for index, start in enumerate(range(0, len(encoded), 55000)):
    print(f'::notice title=PLAYER_FRAME_{index:03d}::{encoded[start:start + 55000]}')
PYFRAME
fi
if [ "$result" -ne 0 ] || ! grep -Eq 'OK \([0-9]+ tests?\)' "$RUNNER_TEMP/aurapulse-device-tests.log"; then
  python3 tools/report_device_failures.py "$RUNNER_TEMP/aurapulse-device-tests.log"
  exit 1
fi
