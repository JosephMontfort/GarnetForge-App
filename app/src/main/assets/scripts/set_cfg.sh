#!/system/bin/sh
KEY="$1"; VAL="$2"
CONFIG="/data/data/dev.garnetforge.app/files/garnetforge/config.prop"
TMP="${CONFIG}.tmp"
[ -z "$KEY" ] && exit 1
[ -f "$CONFIG" ] || exit 1
KEY_ESC="$(printf '%s' "$KEY" | sed 's/[.[*^$]/\\&/g')"
grep -v "^${KEY_ESC}=" "$CONFIG" 2>/dev/null | grep -v "^$" > "$TMP"
printf '%s=%s\n' "$KEY" "$VAL" >> "$TMP"
cat "$TMP" > "$CONFIG"
rm -f "$TMP"
