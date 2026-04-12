#!/system/bin/sh
# Reads current values of all tunable nodes as defaults (run after clean boot).
MODDIR=/data/data/dev.garnetforge.app/files/garnetforge
NODES="$MODDIR/nodes.prop"
DEF="$MODDIR/defaults.prop"
LOG="$MODDIR/garnetforge.log"

log() { printf '[%s] DEFAULTS: %s\n' "$(date '+%H:%M:%S')" "$1" >> "$LOG"; }
log "=== Default detection started ==="
> "$DEF"
rec() { printf '%s=%s\n' "$1" "$2" >> "$DEF"; }

get_node() {
    local k; k="$(printf '%s' "$1" | sed 's/[.[*^$]/\\&/g')"
    grep "^${k}=" "$NODES" 2>/dev/null | head -1 | cut -d= -f2-
}

# CPU governors
for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    p="${d%/}"; p="${p##*/}"
    rec "default_cpu_${p}_governor" "$(cat "${d}scaling_governor" 2>/dev/null)"
    rec "default_cpu_${p}_min"      "$(cat "${d}scaling_min_freq" 2>/dev/null)"
    rec "default_cpu_${p}_max"      "$(cat "${d}scaling_max_freq" 2>/dev/null)"
done

# GPU
GPU_DIR=""
for d in /sys/class/devfreq/*kgsl*/ /sys/class/devfreq/*gpu*/ /sys/class/devfreq/*3d*/ /sys/class/devfreq/*mali*/; do
    if [ -f "$d/cur_freq" ]; then GPU_DIR="$d"; break; fi
done
if [ -n "$GPU_DIR" ]; then
    rec "default_gpu_max" "$(cat "${GPU_DIR}/max_freq" 2>/dev/null)"
    rec "default_gpu_min" "$(cat "${GPU_DIR}/min_freq" 2>/dev/null)"
    PWRLEVEL_DIR="${GPU_DIR}/device/kgsl/kgsl-3d0"
    [ -f "$PWRLEVEL_DIR/max_pwrlevel" ] && rec "default_gpu_max_pwrlevel" "$(cat "$PWRLEVEL_DIR/max_pwrlevel" 2>/dev/null)"
    [ -f "$PWRLEVEL_DIR/min_pwrlevel" ] && rec "default_gpu_min_pwrlevel" "$(cat "$PWRLEVEL_DIR/min_pwrlevel" 2>/dev/null)"
    [ -f "$PWRLEVEL_DIR/idle_timer" ]   && rec "default_gpu_idle_timer"   "$(cat "$PWRLEVEL_DIR/idle_timer"   2>/dev/null)"
fi

# VM
rec "default_vm_swappiness"              "$(cat /proc/sys/vm/swappiness 2>/dev/null)"
rec "default_vm_dirty_ratio"             "$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)"
rec "default_vm_dirty_background_ratio"  "$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)"
rec "default_vm_vfs_cache_pressure"      "$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)"

# ZRAM
rec "default_zram_size"  "$(cat /sys/block/zram0/disksize 2>/dev/null)"
rec "default_zram_algo"  "$(cat /sys/block/zram0/comp_algorithm 2>/dev/null | grep -o '\[.*\]' | tr -d '[]')"

# I/O
for q in /sys/block/*/queue/read_ahead_kb; do
    [ -f "$q" ] && rec "default_read_ahead_kb" "$(cat "$q" 2>/dev/null)" && break
done
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && rec "default_io_scheduler" "$(cat "$q" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]')" && break
done

# Network
rec "default_tcp_algo"       "$(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)"

# Thermal
rec "default_thermal_sconfig" "$(cat /sys/class/thermal/thermal_message/sconfig 2>/dev/null)"
rec "default_thermal_boost"   "$(cat /sys/class/thermal/thermal_message/boost 2>/dev/null)"

log "Defaults captured: $(wc -l < "$DEF") entries"
