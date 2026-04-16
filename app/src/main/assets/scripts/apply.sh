#!/system/bin/sh
MODDIR=/data/data/dev.garnetforge.app/files/garnetforge
NODES="$MODDIR/nodes.prop"
CONFIG="$MODDIR/config.prop"
LOG=/data/data/dev.garnetforge.app/files/garnetforge/garnetforge.log

log() { printf '[%s] APPLY: %s\n' "$(date '+%H:%M:%S')" "$1" >> "$LOG"; }

get() {
    local k; k="$(printf '%s' "$1" | sed 's/[.[*^$]/\\&/g')"
    grep "^${k}=" "$CONFIG" 2>/dev/null | head -1 | cut -d= -f2-
}
node() {
    local k; k="$(printf '%s' "$1" | sed 's/[.[*^$]/\\&/g')"
    grep "^${k}=" "$NODES" 2>/dev/null | head -1 | cut -d= -f2-
}
wr() {
    [ -z "$1" ] || [ -z "$2" ] && return
    [ -e "$2" ] || return
    printf '%s' "$1" > "$2" 2>/dev/null
}

[ -f "$NODES" ]  || { log "ERROR: nodes.prop missing — run detect_nodes"; exit 1; }
[ -f "$CONFIG" ] || { log "ERROR: config.prop missing"; exit 1; }

# CPU policies
POLICIES=$(node cpu_policies)
for pol in $(printf '%s' "$POLICIES" | tr ',' ' '); do
    [ -z "$pol" ] && continue
    GOV_NODE=$(node "cpu_${pol}_gov"); MIN_NODE=$(node "cpu_${pol}_min")
    MAX_NODE=$(node "cpu_${pol}_max"); pol_id="${pol#policy}"
    INFO_MIN=$(node "cpu_${pol}_info_min")
    GOV=$(get "cpu_${pol}_governor"); MIN=$(get "cpu_policy${pol_id}_min"); MAX=$(get "cpu_policy${pol_id}_max")
    if [ -n "$GOV" ] && [ -n "$GOV_NODE" ] && [ -e "$GOV_NODE" ]; then
        CUR_GOV=$(cat "$GOV_NODE" 2>/dev/null | tr -d ' \n\r')
        [ "$GOV" != "$CUR_GOV" ] && wr "$GOV" "$GOV_NODE"
    fi
    if [ -n "$MAX" ] && [ -n "$MIN" ]; then
        [ -n "$INFO_MIN" ] && wr "$INFO_MIN" "$MIN_NODE"
        wr "$MAX" "$MAX_NODE"
        ACTUAL=$(cat "$MAX_NODE" 2>/dev/null | tr -d ' \n\r')
        [ -n "$ACTUAL" ] && [ "$ACTUAL" != "$MAX" ] && wr "$MAX" "$MAX_NODE"
        wr "$MIN" "$MIN_NODE"
    elif [ -n "$MAX" ]; then wr "$MAX" "$MAX_NODE"
    elif [ -n "$MIN" ]; then wr "$MIN" "$MIN_NODE"
    fi
done

# GPU
GPU_MAX_NODE=$(node gpu_max_mhz); GPU_MIN_NODE=$(node gpu_min_mhz)
GPU_MAX=$(get gpu_max); GPU_MIN=$(get gpu_min)
[ -n "$GPU_MAX" ] && [ -n "$GPU_MIN" ] && { wr "0" "$GPU_MIN_NODE"; wr "$GPU_MAX" "$GPU_MAX_NODE"; wr "$GPU_MIN" "$GPU_MIN_NODE"; }

# VM
wr "$(get vm_swappiness)"             /proc/sys/vm/swappiness
wr "$(get vm_dirty_ratio)"            /proc/sys/vm/dirty_ratio
wr "$(get vm_dirty_background_ratio)" /proc/sys/vm/dirty_background_ratio
wr "$(get vm_vfs_cache_pressure)"     /proc/sys/vm/vfs_cache_pressure

# ZRAM — only resize if values differ from current
ZRAM_SIZE=$(get zram_size)
ZRAM_ALGO=$(get zram_algo)
if [ -n "$ZRAM_SIZE" ] && [ -f /sys/block/zram0/disksize ]; then
    CUR_SIZE=$(cat /sys/block/zram0/disksize 2>/dev/null)
    CUR_ALGO=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null | grep -o '\[.*\]' | tr -d '[]')
    if [ "$CUR_SIZE" != "$ZRAM_SIZE" ] || [ "$CUR_ALGO" != "$ZRAM_ALGO" ]; then
        swapoff /dev/block/zram0 2>/dev/null
        echo 1 > /sys/block/zram0/reset 2>/dev/null
        [ -n "$ZRAM_ALGO" ] && echo "$ZRAM_ALGO" > /sys/block/zram0/comp_algorithm 2>/dev/null
        echo "$ZRAM_SIZE" > /sys/block/zram0/disksize 2>/dev/null
        mkswap /dev/block/zram0 2>/dev/null
        swapon /dev/block/zram0 2>/dev/null
    fi
fi

# I/O scheduler + read-ahead
IO_SCHED=$(get io_scheduler)
READ_AHEAD=$(get read_ahead_kb)
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && [ -n "$IO_SCHED" ] && echo "$IO_SCHED" > "$q" 2>/dev/null
done
for ra in /sys/block/*/queue/read_ahead_kb; do
    [ -f "$ra" ] && [ -n "$READ_AHEAD" ] && echo "$READ_AHEAD" > "$ra" 2>/dev/null
done

# Network
wr "$(get tcp_algo)" /proc/sys/net/ipv4/tcp_congestion_control
NET_RXQ=$(get net_rxqueuelen)
if [ -n "$NET_RXQ" ]; then
    for iface in $(ls /sys/class/net/ 2>/dev/null); do
        ip link set "$iface" txqueuelen "$NET_RXQ" 2>/dev/null
    done
fi

# Thermal
wr "$(get thermal_profile)" /sys/class/thermal/thermal_message/sconfig

log "APPLY done"

# ── Apply on boot check ──────────────────────────────────────────────
APPLY_ON_BOOT=$(get "apply_on_boot")
[ "$APPLY_ON_BOOT" = "0" ] && log "apply_on_boot=0 — skipping apply" && exit 0

log "Applying configuration..."
