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
    [ -e "$2" ] || { log "MISSING node: $2"; return; }
    printf '%s' "$1" > "$2" 2>/dev/null
}

[ -f "$NODES" ]  || { log "ERROR: nodes.prop missing — run detect_nodes"; exit 1; }
[ -f "$CONFIG" ] || { log "ERROR: config.prop missing"; exit 1; }

# ── apply_on_boot guard — checked FIRST before any writes ────────────
APPLY_ON_BOOT=$(get "apply_on_boot")
if [ "$APPLY_ON_BOOT" = "0" ]; then
    log "apply_on_boot=0 — skipping apply"
    exit 0
fi

log "Applying configuration..."

# ── CPU policies ─────────────────────────────────────────────────────
POLICIES=$(node cpu_policies)
for pol in $(printf '%s' "$POLICIES" | tr ',' ' '); do
    [ -z "$pol" ] && continue
    GOV_NODE=$(node "cpu_${pol}_gov")
    MIN_NODE=$(node "cpu_${pol}_min")
    MAX_NODE=$(node "cpu_${pol}_max")
    INFO_MIN=$(node "cpu_${pol}_info_min")
    pol_id="${pol#policy}"
    GOV=$(get "cpu_${pol}_governor")
    MIN=$(get "cpu_policy${pol_id}_min")
    MAX=$(get "cpu_policy${pol_id}_max")

    if [ -n "$GOV" ] && [ -n "$GOV_NODE" ] && [ -e "$GOV_NODE" ]; then
        CUR_GOV=$(cat "$GOV_NODE" 2>/dev/null | tr -d ' \n\r')
        [ "$GOV" != "$CUR_GOV" ] && wr "$GOV" "$GOV_NODE"
    fi
    if [ -n "$MAX" ] && [ -n "$MIN" ]; then
        [ -n "$INFO_MIN" ] && INFO_MIN_VAL=$(cat "$INFO_MIN" 2>/dev/null | tr -d ' \n\r')
        [ -n "$INFO_MIN_VAL" ] && wr "$INFO_MIN_VAL" "$MIN_NODE"
        wr "$MAX" "$MAX_NODE"
        ACTUAL=$(cat "$MAX_NODE" 2>/dev/null | tr -d ' \n\r')
        [ -n "$ACTUAL" ] && [ "$ACTUAL" != "$MAX" ] && wr "$MAX" "$MAX_NODE"
        wr "$MIN" "$MIN_NODE"
    elif [ -n "$MAX" ]; then
        wr "$MAX" "$MAX_NODE"
    elif [ -n "$MIN" ]; then
        wr "$MIN" "$MIN_NODE"
    fi
done

# ── GPU devfreq (config stores MHz; devfreq nodes expect Hz) ─────────
GPU_MAX_NODE=$(node gpu_max_node)
GPU_MIN_NODE=$(node gpu_min_node)
GPU_MAX=$(get gpu_max)
GPU_MIN=$(get gpu_min)
if [ -n "$GPU_MAX" ] && [ -n "$GPU_MIN" ] && \
   [ -n "$GPU_MAX_NODE" ] && [ -n "$GPU_MIN_NODE" ]; then
    GPU_MAX_HZ=$(( GPU_MAX * 1000000 ))
    GPU_MIN_HZ=$(( GPU_MIN * 1000000 ))
    wr "0" "$GPU_MIN_NODE"
    wr "$GPU_MAX_HZ" "$GPU_MAX_NODE"
    wr "$GPU_MIN_HZ" "$GPU_MIN_NODE"
    log "GPU devfreq: min=${GPU_MIN_HZ}Hz max=${GPU_MAX_HZ}Hz"
else
    [ -z "$GPU_MAX_NODE" ] && log "WARN: gpu_max_node not in nodes.prop — GPU freq write skipped"
fi

# ── GPU KGSL tunables (Adreno; no-op if nodes missing) ───────────────
GPU_PL_NODE=$(node gpu_thermal_pwrlevel)
GPU_IT_NODE=$(node gpu_idle_timer)
GPU_PL=$(get gpu_pwrlevel)
GPU_IT=$(get gpu_idle_timer)
[ -n "$GPU_PL" ] && wr "$GPU_PL" "$GPU_PL_NODE"
[ -n "$GPU_IT" ] && wr "$GPU_IT" "$GPU_IT_NODE"

# ── VM ────────────────────────────────────────────────────────────────
wr "$(get vm_swappiness)"             /proc/sys/vm/swappiness
wr "$(get vm_dirty_ratio)"            /proc/sys/vm/dirty_ratio
wr "$(get vm_dirty_background_ratio)" /proc/sys/vm/dirty_background_ratio
wr "$(get vm_vfs_cache_pressure)"     /proc/sys/vm/vfs_cache_pressure

# ── ZRAM — only resize if values differ ──────────────────────────────
ZRAM_SIZE=$(get zram_size)
ZRAM_ALGO=$(get zram_algo)
if [ -n "$ZRAM_SIZE" ] && [ -f /sys/block/zram0/disksize ]; then
    CUR_SIZE=$(cat /sys/block/zram0/disksize 2>/dev/null)
    CUR_ALGO=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null | grep -o '\[.*\]' | tr -d '[]')
    if [ "$CUR_SIZE" != "$ZRAM_SIZE" ] || [ "$CUR_ALGO" != "$ZRAM_ALGO" ]; then
        log "ZRAM: resizing to ${ZRAM_SIZE}B algo=${ZRAM_ALGO}"
        swapoff /dev/block/zram0 2>/dev/null || swapoff /dev/zram0 2>/dev/null || true
        echo 1 > /sys/block/zram0/reset 2>/dev/null
        [ -n "$ZRAM_ALGO" ] && echo "$ZRAM_ALGO" > /sys/block/zram0/comp_algorithm 2>/dev/null
        echo "$ZRAM_SIZE" > /sys/block/zram0/disksize 2>/dev/null
        ZRAM_DEV=$(node zram_device)
        ZRAM_DEV="${ZRAM_DEV:-/dev/block/zram0}"
        mkswap "$ZRAM_DEV" 2>/dev/null
        swapon "$ZRAM_DEV" 2>/dev/null
    else
        log "ZRAM: unchanged (${CUR_SIZE}B, ${CUR_ALGO})"
    fi
fi

# ── I/O ───────────────────────────────────────────────────────────────
IO_SCHED=$(get io_scheduler)
READ_AHEAD=$(get read_ahead_kb)
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && [ -n "$IO_SCHED" ] && echo "$IO_SCHED" > "$q" 2>/dev/null
done
for ra in /sys/block/*/queue/read_ahead_kb; do
    [ -f "$ra" ] && [ -n "$READ_AHEAD" ] && echo "$READ_AHEAD" > "$ra" 2>/dev/null
done

# ── Network ───────────────────────────────────────────────────────────
wr "$(get tcp_algo)" /proc/sys/net/ipv4/tcp_congestion_control
NET_RXQ=$(get net_rxqueuelen)
if [ -n "$NET_RXQ" ]; then
    for iface in $(ls /sys/class/net/ 2>/dev/null); do
        ip link set "$iface" txqueuelen "$NET_RXQ" 2>/dev/null
    done
fi

# ── Thermal ───────────────────────────────────────────────────────────
wr "$(get thermal_profile)" /sys/class/thermal/thermal_message/sconfig
BOOST_NODE=$(node thermal_msg_boost)
BOOST_VAL=$(get thermal_boost)
[ -n "$BOOST_VAL" ] && [ -n "$BOOST_NODE" ] && wr "$BOOST_VAL" "$BOOST_NODE"

log "APPLY done"
