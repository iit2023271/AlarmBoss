#!/bin/sh
# AlarmBoss framework-only build (adapted from the proven FloatCam Java build).
# No Gradle. aapt2 + javac + d8 + zipalign + apksigner (v2 only).
set -e
export PATH="$HOME/jdk17/bin:$PATH"
export JAVA_HOME="$HOME/jdk17"
cd "$(dirname "$0")"
SDK="$HOME/android-sdk"
BT="$SDK/build-tools/34.0.0"
PLAT="$SDK/platforms/android-34/android.jar"
JAVAC="$HOME/jdk17/bin/javac"
AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
# Signing key: prefer a keystore in the repo folder (works for anyone who
# clones this repo), fall back to the original builder's absolute path.
if [ -f ./alarm-boss.keystore ]; then
  KS="./alarm-boss.keystore"
elif [ -f "$HOME/workspace/android-app/alarm-boss/alarm-boss.keystore" ]; then
  KS="$HOME/workspace/android-app/alarm-boss/alarm-boss.keystore"
else
  KS=""
fi
if [ -f KEYSTORE_NOTE.txt ]; then
  KS_PASS=$(grep '^ALARMBOSS_STORE_PASSWORD=' KEYSTORE_NOTE.txt | cut -d= -f2-)
  KEY_PASS=$(grep '^ALARMBOSS_KEY_PASSWORD=' KEYSTORE_NOTE.txt | cut -d= -f2-)
  KEY_ALIAS=$(grep '^ALARMBOSS_KEY_ALIAS=' KEYSTORE_NOTE.txt | cut -d= -f2-)
else
  KS_PASS=""; KEY_PASS=""; KEY_ALIAS=""
fi
[ -z "$KEY_ALIAS" ] && KEY_ALIAS="alarmboss"

rm -rf build/compiled.zip build/gen build/obj build/dex build/unsigned.apk build/aligned.apk build/AlarmBoss.apk
mkdir -p build/gen build/obj build/dex

"$AAPT2" compile --dir res -o build/compiled.zip
"$AAPT2" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$PLAT" build/compiled.zip --java build/gen --auto-add-overlay

find src build/gen -name '*.java' > build/sources.txt
cat build/sources.txt
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -cp "$PLAT" -d build/obj @build/sources.txt

"$D8" --lib "$PLAT" --min-api 26 --output build/dex $(find build/obj -name '*.class')

cd build/dex && zip -q ../unsigned.apk classes.dex && cd ../..
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk
if [ -n "$KS" ] && [ -n "$KS_PASS" ] && [ -n "$KEY_PASS" ]; then
  "$APKSIGNER" sign --v1-signing-enabled false --v2-signing-enabled true --ks "$KS" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" --out build/AlarmBoss.apk build/aligned.apk
  "$APKSIGNER" verify --verbose build/AlarmBoss.apk
  "$AAPT2" dump badging build/AlarmBoss.apk | head -20
  ls -lh build/AlarmBoss.apk
else
  echo "No signing keystore found - unsigned build left at build/aligned.apk"
  echo "To sign it with your own key: create a keystore (keytool -genkeypair -keystore alarm-boss.keystore -alias <your-alias> ...),"
  echo "write a KEYSTORE_NOTE.txt with ALARMBOSS_STORE_PASSWORD=<store-password>, ALARMBOSS_KEY_PASSWORD=<key-password> and"
  echo "ALARMBOSS_KEY_ALIAS=<your-alias> (optional, defaults to alarmboss), then re-run ./build.sh."
  echo "Never commit the keystore or KEYSTORE_NOTE.txt - both are gitignored."
  ls -lh build/aligned.apk
fi
