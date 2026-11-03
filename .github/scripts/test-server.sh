#!/usr/bin/env bash
# Starts a real Paper server with the built plugin and fails if the plugin
# does not enable cleanly. Usage: test-server.sh <plugin-jar> <minecraft-version>
set -euo pipefail

JAR="$1"
MC_VERSION="${2:-26.3}"
UA="ZelDuelsAnnouncer-CI (https://github.com/${GITHUB_REPOSITORY:-korosensei4014-commits/ZelDuelsAnnouncer})"
API="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"

rm -rf run && mkdir -p run/plugins
cp "$JAR" run/plugins/

echo "Looking up the latest Paper ${MC_VERSION} build..."
URL=$(curl -fsSL -H "User-Agent: $UA" "$API/latest" | jq -r '.downloads."server:default".url // empty' || true)
if [ -z "$URL" ]; then
  URL=$(curl -fsSL -H "User-Agent: $UA" "$API" | jq -r '.[0].downloads."server:default".url // empty')
fi
if [ -z "$URL" ]; then
  echo "::error::Could not find a Paper ${MC_VERSION} download"
  exit 1
fi
echo "Downloading $URL"
curl -fsSL -H "User-Agent: $UA" -o run/paper.jar "$URL"

cd run
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
EOF

mkfifo console
java -Xms1G -Xmx2G -jar paper.jar --nogui < console > server.log 2>&1 &
PID=$!
exec 3> console   # keep the console pipe open so we can type "stop" later

for _ in $(seq 1 420); do
  if grep -q 'Done (' server.log; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done

echo "stop" >&3 || true
for _ in $(seq 1 60); do
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done
kill "$PID" 2>/dev/null || true
exec 3>&-

echo "================ server.log ================"
cat server.log
echo "============================================"

fail() { echo "::error::$1"; exit 1; }

grep -q 'Done (' server.log || fail "Paper server did not finish starting"
grep -q 'Enabling ZelDuelsAnnouncer' server.log || fail "ZelDuelsAnnouncer was not enabled"
if grep -qE 'Could not load .*ZelDuelsAnnouncer|Error occurred while enabling ZelDuelsAnnouncer' server.log; then
  fail "ZelDuelsAnnouncer failed to load"
fi
if grep -q 'com\.korosensei\.zelduelsannouncer' server.log; then
  fail "A stack trace from ZelDuelsAnnouncer appeared in the log"
fi
echo "ZelDuelsAnnouncer loaded on Paper ${MC_VERSION} without errors."
