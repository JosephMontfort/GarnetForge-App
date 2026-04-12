#!/system/bin/sh
MODDIR=/data/data/dev.garnetforge.app/files/garnetforge
NODES="$MODDIR/nodes.prop"
log() { printf '[%s] DETECT: %s\n' "$(date '+%H:%M:%S')" "$1" >> "$MODDIR/garnetforge.log"; }
log "=== Node detection started ==="
> "$NODES"
record() { printf '%s=%s\n' "$1" "$2" >> "$NODES"; }

# CPU policies + available frequencies
POLS=""
for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    p="${d%/}"; p="${p##*/}"
    [ -d "$d" ] || continue
    POLS="${POLS:+$POLS,}$p"
    record "cpu_${p}_gov"      "$d/scaling_governor"
    record "cpu_${p}_min"      "$d/scaling_min_freq"
    record "cpu_${p}_max"      "$d/scaling_max_freq"
    record "cpu_${p}_cur"      "$d/scaling_cur_freq"
    record "cpu_${p}_info_min" "$d/cpuinfo_min_freq"
    record "cpu_${p}_info_max" "$d/cpuinfo_max_freq"
    # Available frequencies — space-separated Hz values
    if [ -f "$d/scaling_available_frequencies" ]; then
        AVAIL=$(cat "$d/scaling_available_frequencies" 2>/dev/null | tr ' ' ',' | sed 's/^,//;s/,$//')
        [ -n "$AVAIL" ] && record "cpu_${p}_avail_freqs" "$AVAIL"
    fi
done
record "cpu_policies" "$POLS"
log "CPU policies: $POLS"

# Per-core freq + online nodes
for c in 0 1 2 3 4 5 6 7; do
    node="/sys/devices/system/cpu/cpu${c}/cpufreq/scaling_cur_freq"
    [ -f "$node" ] && record "cpu${c}_cur_freq" "$node"
    online_node="/sys/devices/system/cpu/cpu${c}/online"
    [ -f "$online_node" ] && record "cpu${c}_online" "$online_node"
done

# GPU
GPU_DIR=""
for d in /sys/class/devfreq/*kgsl*/ /sys/class/devfreq/*gpu*/ /sys/class/devfreq/*3d*/ /sys/class/devfreq/*mali*/; do
    if [ -f "$d/cur_freq" ]; then GPU_DIR="$d"; break; fi
done
if [ -n "$GPU_DIR" ]; then
    record "gpu_cur_freq"  "$GPU_DIR/cur_freq"
    record "gpu_max_mhz"   "$GPU_DIR/max_freq"
    record "gpu_min_mhz"   "$GPU_DIR/min_freq"
    
    AVAIL_GPU=""
    if [ -f "$GPU_DIR/available_frequencies" ]; then
        AVAIL_GPU=$(cat "$GPU_DIR/available_frequencies" 2>/dev/null)
    fi
    
    # Fallback to direct KGSL node if empty or only 1 frequency
    if [ -z "$AVAIL_GPU" ] || [ $(echo "$AVAIL_GPU" | wc -w) -lt 2 ]; then
        if [ -f "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies" ]; then
            AVAIL_GPU=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null)
        fi
    fi

    if [ -n "$AVAIL_GPU" ]; then
        AVAIL_GPU_FMT=$(echo "$AVAIL_GPU" | tr '
' ' ' | tr -s ' ' | sed 's/ $//' | tr ' ' ',')
        [ -n "$AVAIL_GPU_FMT" ] && record "gpu_avail_freqs" "$AVAIL_GPU_FMT"
    fi
fi

# Thermal
[ -e /sys/class/thermal/thermal_message/sconfig ] && record "thermal_sconfig" "/sys/class/thermal/thermal_message/sconfig"

# I/O
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && record "io_scheduler_node" "$q" && break
done

# ZRAM
[ -f /sys/block/zram0/disksize ]       && record "zram_disksize"  "/sys/block/zram0/disksize"
[ -f /sys/block/zram0/comp_algorithm ] && record "zram_comp_algo" "/sys/block/zram0/comp_algorithm"

log "Detection complete — $(wc -l < "$NODES") nodes"
