#!/usr/bin/env bash
# Build Fortuner Launcher tanpa Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Butuh JDK 17 dan Android SDK (build-tools 34.0.0 + platforms/android-34).
# Jalankan di Git Bash:  ./build.sh            (APK ada di build/)
#                       ./build.sh install    (sekaligus pasang ke headunit lewat ADB)
set -euo pipefail
cd "$(dirname "$0")"

VERSION_NAME="1.0.0"
VERSION_CODE="1"
MIN_SDK=24
TARGET_SDK=34

to_unix() { if command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else echo "$1"; fi; }
LOCAL="$(to_unix "${LOCALAPPDATA:-$HOME/AppData/Local}")"
SDK="$(to_unix "${ANDROID_HOME:-$LOCAL/Android/Sdk}")"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME="$(ls -d "$LOCAL"/Programs/jdk-17* 2>/dev/null | head -1)"; fi
JAVA_HOME="$(to_unix "$JAVA_HOME")"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
ADB="${ADB:-$LOCAL/Android/platform-tools/adb.exe}"
DEVICE="${DEVICE:-192.168.18.78:5555}"

for f in "$BT/aapt2.exe" "$BT/aapt.exe" "$BT/d8.bat" "$BT/zipalign.exe" "$BT/apksigner.bat" "$PLATFORM" "$JAVA_HOME/bin/javac.exe"; do
  [ -e "$f" ] || { echo "Tidak ditemukan: $f" >&2; exit 1; }
done

OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] Kompilasi resource"
"$BT/aapt2.exe" compile --dir res -o "$OUT/res/res.zip"

echo "[2/6] Link manifest dan resource"
"$BT/aapt2.exe" link -I "$PLATFORM" --manifest AndroidManifest.xml \
  --java "$OUT/gen" --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  -o "$OUT/app.unsigned.apk" "$OUT/res/res.zip"

echo "[3/6] Kompilasi Java"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 --release 8 -classpath "$PLATFORM" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] Dex dan aset"
(cd "$OUT/classes" && jar cf ../classes.jar .)
"$BT/d8.bat" --release --min-api $MIN_SDK --lib "$PLATFORM" --output "$OUT/dex" "$OUT/classes.jar"
(cd "$OUT/dex" && "$BT/aapt.exe" add ../app.unsigned.apk classes.dex >/dev/null)
# Aset ditambahkan dengan aapt (bukan aapt2 -A) karena aapt2 di Windows menulis path aset dengan backslash.
find assets -type f | sort > "$OUT/assets.txt"
"$BT/aapt.exe" add "$OUT/app.unsigned.apk" $(cat "$OUT/assets.txt") >/dev/null

echo "[5/6] Zipalign"
"$BT/zipalign.exe" -p -f 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

echo "[6/6] Tanda tangan"
mkdir -p keystore
if [ ! -f keystore/release.jks ]; then
  head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24 > keystore/password.txt
  keytool -genkeypair -keystore keystore/release.jks -alias fortuner -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$(cat keystore/password.txt)" -keypass "$(cat keystore/password.txt)" \
    -dname "CN=Fortuner Launcher" >/dev/null
  echo "    Keystore baru dibuat di keystore/ (simpan baik-baik untuk update berikutnya)"
fi
APK="$OUT/FortunerLauncher-$VERSION_NAME.apk"
"$BT/apksigner.bat" sign --ks keystore/release.jks --ks-key-alias fortuner \
  --ks-pass file:keystore/password.txt \
  --out "$APK" "$OUT/app.aligned.apk"
"$BT/apksigner.bat" verify "$APK"
echo "APK: $APK ($(du -h "$APK" | cut -f1))"

if [ "${1:-}" = "install" ]; then
  "$ADB" -s "$DEVICE" install -r "$APK"
fi
