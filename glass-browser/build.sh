#!/usr/bin/env bash
# Builds GlassBrowser.apk from source without Gradle.
# Requires: JDK 17+, Android SDK build-tools 34 and platform android-34.
set -euo pipefail

ANDROID_SDK="${ANDROID_SDK:-$HOME/Android/Sdk}"
BT="${BUILD_TOOLS:-$ANDROID_SDK/build-tools/34.0.0}"
PLAT="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-34/android.jar}"
KS="${KEYSTORE:-$PWD/glass.keystore}"
OUT="$PWD/build"

rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== compile resources =="
"$BT/aapt2" compile --dir app/res -o "$OUT/res/res.zip"

echo "== link resources =="
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT" \
  --manifest app/AndroidManifest.xml -A app/assets \
  --java "$OUT/gen" --auto-add-overlay "$OUT/res/res.zip"

echo "== javac =="
mkdir -p "$OUT/src/com/rajok/glassbrowser"
cp app/java/com/rajok/glassbrowser/*.java "$OUT/src/com/rajok/glassbrowser/"
javac -nowarn -source 8 -target 8 -bootclasspath "$PLAT" -encoding UTF-8 \
  -d "$OUT/classes" $(find "$OUT/gen" "$OUT/src" -name '*.java')

echo "== dex =="
"$BT/d8" --lib "$PLAT" --min-api 24 --release --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "== package =="
cd "$OUT" && cp base.apk unsigned.apk && (cd dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 unsigned.apk aligned.apk

if [ ! -f "$KS" ]; then
  echo "== generating debug keystore =="
  keytool -genkeypair -keystore "$KS" -storepass glassbrowser -keypass glassbrowser \
    -alias glass -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Glass Browser, O=Rajok, C=IN"
fi

echo "== sign =="
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:glassbrowser --key-pass pass:glassbrowser \
  --ks-key-alias glass --out "$OLDPWD/GlassBrowser.apk" aligned.apk
"$BT/apksigner" verify --print-certs "$OLDPWD/GlassBrowser.apk" | head -3
echo "built: $OLDPWD/GlassBrowser.apk"
