#!/bin/sh
# Builds Life Tracker (friends template) into Life-Tracker-Friends.apk. See README.md.
set -eu
: "${ANDROID_JAR:?Path to platform 34 android.jar}"
: "${BUILD_TOOLS:?Path to build-tools 30.0.3 or newer (aapt, d8, zipalign, apksigner)}"
: "${SIGNING_KEYSTORE:?Path to YOUR keystore (never commit it)}"
: "${SIGNING_ALIAS:?Key alias}"
: "${SIGNING_STORE_PASSWORD:?Keystore password}"
APP=app/src/main; OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/cls" "$OUT/dex"
"$BUILD_TOOLS/aapt" package -f -m -A "$APP/assets" -M "$APP/AndroidManifest.xml" -S "$APP/res" -I "$ANDROID_JAR" -J "$OUT/gen" -F "$OUT/base.apk" --min-sdk-version 26 --target-sdk-version 34 --version-code 4 --version-name 1.2.0
javac --release 11 -cp "$ANDROID_JAR" -d "$OUT/cls" "$OUT/gen/com/lifetracker/app/R.java" $APP/java/com/lifetracker/app/*.java
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/cls" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" --ks-pass env:SIGNING_STORE_PASSWORD --out Life-Tracker-Friends.apk "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --print-certs Life-Tracker-Friends.apk
