#!/system/bin/sh
# GarnetForge bootstrap — runs once from /data/adb/garnetforge_bootstrap.sh
# Creates app data directory with correct permissions, then self-deletes.
APPDIR="/data/data/dev.garnetforge.app/files/garnetforge"
APPFILES="/data/data/dev.garnetforge.app/files"
SELF="/data/adb/garnetforge_bootstrap.sh"

mkdir -p "$APPFILES" 2>/dev/null
mkdir -p "$APPDIR"   2>/dev/null
# Set ownership to the app's UID so the app can also access its own files dir
APPUID=$(stat -c '%u' /data/data/dev.garnetforge.app 2>/dev/null || echo "")
[ -n "$APPUID" ] && chown -R "$APPUID:$APPUID" "$APPFILES" 2>/dev/null
chmod 771 "$APPFILES" 2>/dev/null
chmod 771 "$APPDIR"   2>/dev/null

# Self-delete
rm -f "$SELF" 2>/dev/null
