#!/system/bin/sh
MODDIR=/data/adb/garnetforge
NODES="$MODDIR/nodes.prop"
log() { printf '[%s] DETECT: %s\n' "$(date '+%H:%M:%S')" "$1" >> "$MODDIR/garnetforge.log"; }
log "=== Node detection started ==="
> "$NODES"
record() { printf '%s=%s\n' "$1" "$2" >> "$NODES"; }

# CPU policies
POLS=""
for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    p="${d%/}"; p="${p##*/}"
    [ -d "$d" ] || continue
    POLS="${POLS:+$POLS,}$p"
    record "cpu_${p}_gov"      "$d/scaling_governor"
    record "cpu_${p}_min"      "$d/scaling_min_freq"
    record "cpu_${p}_max"      "$d/scaling_max_freq"
    record "cpu_${p}_info_min" "$d/cpuinfo_min_freq"
    record "cpu_${p}_info_max" "$d/cpuinfo_max_freq"
    record "cpu_${p}_cur"      "$d/scaling_cur_freq"
done
record "cpu_policies" "$POLS"
log "CPU policies: $POLS"

# Per-core frequencies (individual core nodes)
for c in 0 1 2 3 4 5 6 7; do
    node="/sys/devices/system/cpu/cpu${c}/cpufreq/scaling_cur_freq"
    [ -f "$node" ] && record "cpu${c}_cur_freq" "$node"
    online_node="/sys/devices/system/cpu/cpu${c}/online"
    [ -f "$online_node" ] && record "cpu${c}_online" "$online_node"
done
log "Per-core nodes recorded"

# GPU
for d in /sys/class/devfreq/*/; do
    [ -f "$d/cur_freq" ] || continue
    record "gpu_cur_freq"  "$d/cur_freq"
    record "gpu_max_mhz"   "$d/max_freq"
    record "gpu_min_mhz"   "$d/min_freq"
    break
done

# Thermal
[ -e /sys/class/thermal/thermal_message/sconfig ] && record "thermal_sconfig" "/sys/class/thermal/thermal_message/sconfig"

# I/O — detect first block device scheduler
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && record "io_scheduler_node" "$q" && break
done

# ZRAM
[ -f /sys/block/zram0/disksize ]        && record "zram_disksize"   "/sys/block/zram0/disksize"
[ -f /sys/block/zram0/comp_algorithm ]  && record "zram_comp_algo"  "/sys/block/zram0/comp_algorithm"

log "Detection complete — $(wc -l < "$NODES") nodes"
