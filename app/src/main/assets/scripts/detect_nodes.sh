#!/system/bin/sh
# GarnetForge — Intelligent Node Detection
# Discovers all tunable kernel nodes dynamically for ANY device.
# Output: nodes.prop (key=path pairs) used by the app at runtime.
MODDIR=/data/data/dev.garnetforge.app/files/garnetforge
NODES="$MODDIR/nodes.prop"
LOG="$MODDIR/garnetforge.log"

log() { printf '[%s] DETECT: %s\n' "$(date '+%H:%M:%S')" "$1" >> "$LOG"; }
log "=== Intelligent node detection v2 started ==="
> "$NODES"
rec() { printf '%s=%s\n' "$1" "$2" >> "$NODES"; }

# ── Helper: first existing path from candidates ───────────────────────
first_node() {
    for p in "$@"; do [ -e "$p" ] && { echo "$p"; return; }; done; echo ""
}
# ── Helper: read value safely ─────────────────────────────────────────
rcat() { cat "$1" 2>/dev/null || true; }

# ════════════════════════════════════════════════════════════════════════
# 1. CPU POLICIES — discover all, classify little/big/prime clusters
# ════════════════════════════════════════════════════════════════════════
POLS=""
LITTLE_POL="" BIG_POL="" PRIME_POL=""
LITTLE_MAX=0  BIG_MAX=0  PRIME_MAX=0

for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    [ -d "$d" ] || continue
    p="${d%/}"; p="${p##*/}"
    POLS="${POLS:+$POLS,}$p"
    rec "cpu_${p}_gov"      "$d/scaling_governor"
    rec "cpu_${p}_min"      "$d/scaling_min_freq"
    rec "cpu_${p}_max"      "$d/scaling_max_freq"
    rec "cpu_${p}_cur"      "$d/scaling_cur_freq"
    rec "cpu_${p}_info_min" "$d/cpuinfo_min_freq"
    rec "cpu_${p}_info_max" "$d/cpuinfo_max_freq"

    # Available frequencies
    if [ -f "$d/scaling_available_frequencies" ]; then
        AVAIL=$(rcat "$d/scaling_available_frequencies" | tr ' ' ',' | sed 's/^,//;s/,$//')
        [ -n "$AVAIL" ] && rec "cpu_${p}_avail_freqs" "$AVAIL"
    fi

    # Available governors
    if [ -f "$d/scaling_available_governors" ]; then
        GOVS=$(rcat "$d/scaling_available_governors" | tr ' ' ',' | sed 's/^,//;s/,$//')
        [ -n "$GOVS" ] && rec "cpu_${p}_avail_govs" "$GOVS"
    fi

    # Classify cluster: highest max_freq = prime/big, lowest = little
    pmax=$(rcat "$d/cpuinfo_max_freq" | tr -d '[:space:]')
    pmax=${pmax:-0}
    if [ "$pmax" -gt "$PRIME_MAX" ] 2>/dev/null; then
        PRIME_POL="$p"; PRIME_MAX="$pmax"
    elif [ "$pmax" -gt "$BIG_MAX" ] 2>/dev/null; then
        BIG_POL="$p"; BIG_MAX="$pmax"
    fi
    if [ -z "$LITTLE_POL" ] || [ "$pmax" -lt "$LITTLE_MAX" ] 2>/dev/null; then
        LITTLE_POL="$p"; LITTLE_MAX="$pmax"
    fi

    # walt/schedutil tunable directories
    for sched in walt schedutil; do
        td="$d/${sched}"
        [ -d "$td" ] || td="/sys/devices/system/cpu/cpufreq/$sched"
        if [ -d "$td" ]; then
            for tunable in hispeed_freq hispeed_load target_loads go_hispeed_load above_hispeed_delay \
                           up_rate_limit_us down_rate_limit_us; do
                [ -f "$td/$tunable" ] && rec "cpu_${p}_${sched}_${tunable}" "$td/$tunable"
            done
        fi
    done

    # per-policy schedutil
    for tunable in up_rate_limit_us down_rate_limit_us; do
        [ -f "$d/schedutil/$tunable" ] && rec "cpu_${p}_schedutil_${tunable}" "$d/schedutil/$tunable"
    done
done
rec "cpu_policies" "$POLS"
rec "cpu_little_policy" "$LITTLE_POL"
rec "cpu_big_policy" "$BIG_POL"
[ -n "$PRIME_POL" ] && [ "$PRIME_POL" != "$BIG_POL" ] && rec "cpu_prime_policy" "$PRIME_POL"
log "CPU policies: $POLS  little=$LITTLE_POL big=$BIG_POL prime=$PRIME_POL"

# ── Per-core freq + online ────────────────────────────────────────────
NCORES=0
for c in $(seq 0 15); do
    node="/sys/devices/system/cpu/cpu${c}/cpufreq/scaling_cur_freq"
    if [ -f "$node" ]; then
        rec "cpu${c}_cur_freq" "$node"
        NCORES=$((c + 1))
    fi
    online_node="/sys/devices/system/cpu/cpu${c}/online"
    [ -f "$online_node" ] && rec "cpu${c}_online" "$online_node"

    # Core temperature
    for tz in /sys/class/thermal/thermal_zone*/; do
        type=$(rcat "${tz}type" | tr '[:upper:]' '[:lower:]')
        case "$type" in
            *cpu${c}*|*core${c}*) rec "cpu${c}_temp" "${tz}temp"; break ;;
        esac
    done
done
rec "cpu_core_count" "$NCORES"

# ── CPU boost / energy model ──────────────────────────────────────────
for bp in /sys/devices/system/cpu/cpu_boost/parameters/input_boost_enabled \
           /sys/module/cpu_boost/parameters/input_boost_enabled \
           /sys/module/msm_performance/parameters/cpu_max_freq; do
    [ -f "$bp" ] && rec "cpu_boost_node" "$bp" && break
done

# ── CPU idle governor ─────────────────────────────────────────────────
IDLE_GOV_NODE=$(first_node /sys/devices/system/cpu/cpuidle/current_driver \
                            /sys/module/lpm_levels/parameters)
[ -n "$IDLE_GOV_NODE" ] && rec "cpu_idle_driver" "$IDLE_GOV_NODE"

# ════════════════════════════════════════════════════════════════════════
# 2. GPU — detect devfreq, KGSL, Mali, PowerVR, etc.
# ════════════════════════════════════════════════════════════════════════
GPU_DEVFREQ=""
GPU_VENDOR=""
# Adreno / KGSL
for d in /sys/class/devfreq/*kgsl* /sys/class/devfreq/*3d0* /sys/class/devfreq/*gpu*; do
    [ -f "$d/cur_freq" ] && GPU_DEVFREQ="$d" && GPU_VENDOR="adreno" && break
done
# Mali
if [ -z "$GPU_DEVFREQ" ]; then
    for d in /sys/class/devfreq/*mali* /sys/bus/platform/drivers/mali/*/devfreq; do
        [ -f "$d/cur_freq" ] && GPU_DEVFREQ="$d" && GPU_VENDOR="mali" && break
    done
fi
# PowerVR
if [ -z "$GPU_DEVFREQ" ]; then
    for d in /sys/class/devfreq/*pvrsrvkm* /sys/class/devfreq/*gpu0*; do
        [ -f "$d/cur_freq" ] && GPU_DEVFREQ="$d" && GPU_VENDOR="powervr" && break
    done
fi

rec "gpu_vendor" "$GPU_VENDOR"
if [ -n "$GPU_DEVFREQ" ]; then
    rec "gpu_devfreq_dir"  "$GPU_DEVFREQ"
    rec "gpu_cur_freq"     "$GPU_DEVFREQ/cur_freq"
    rec "gpu_max_node"     "$GPU_DEVFREQ/max_freq"
    rec "gpu_min_node"     "$GPU_DEVFREQ/min_freq"
    rec "gpu_governor_node" "$GPU_DEVFREQ/governor"
    [ -f "$GPU_DEVFREQ/available_governors" ] && rec "gpu_avail_govs_node" "$GPU_DEVFREQ/available_governors"

    # Available frequencies — prefer KGSL direct list
    AVAIL_GPU=""
    [ -f "$GPU_DEVFREQ/available_frequencies" ] && AVAIL_GPU=$(rcat "$GPU_DEVFREQ/available_frequencies")
    if [ -z "$AVAIL_GPU" ] || [ "$(echo "$AVAIL_GPU" | wc -w)" -lt 2 ]; then
        for kf in /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies \
                  /sys/kernel/gpu/gpu_available_frequencies \
                  /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies; do
            [ -f "$kf" ] && AVAIL_GPU=$(rcat "$kf") && break
        done
    fi
    if [ -n "$AVAIL_GPU" ]; then
        FMT=$(printf '%s' "$AVAIL_GPU" | tr '\n ' ',,' | tr -s ',' | sed 's/^,//;s/,$//')
        rec "gpu_avail_freqs" "$FMT"
    fi
fi

# KGSL-specific tunables (Adreno)
KGSL_DIR="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL_DIR" ]; then
    rec "gpu_kgsl_dir" "$KGSL_DIR"
    for node in thermal_pwrlevel pwrlevel min_pwrlevel max_pwrlevel \
                idle_timer force_clk_on bus_split wake_nice \
                max_gpuclk devfreq/governor; do
        [ -e "$KGSL_DIR/$node" ] && rec "gpu_${node//\//_}" "$KGSL_DIR/$node"
    done
fi

# Mali-specific
if [ "$GPU_VENDOR" = "mali" ]; then
    for d in /sys/class/misc/mali0 /sys/devices/platform/mali.0 /sys/module/mali; do
        if [ -d "$d" ]; then
            for node in dvfs_enable dvfs_governor core_mask; do
                [ -f "$d/$node" ] && rec "gpu_mali_${node}" "$d/$node"
            done
        fi
    done
fi

# ════════════════════════════════════════════════════════════════════════
# 3. MEMORY / VM
# ════════════════════════════════════════════════════════════════════════
for node in swappiness dirty_ratio dirty_background_ratio dirty_writeback_centisecs \
            vfs_cache_pressure min_free_kbytes extra_free_kbytes overcommit_memory \
            page-cluster drop_caches stat_interval; do
    [ -f "/proc/sys/vm/$node" ] && rec "vm_${node//-/_}" "/proc/sys/vm/$node"
done

# ZRAM
ZRAM_DEV=""
for z in /dev/block/zram0 /dev/zram0; do [ -b "$z" ] && ZRAM_DEV="$z" && break; done
[ -n "$ZRAM_DEV" ] && rec "zram_device" "$ZRAM_DEV"
for zf in disksize comp_algorithm mm_stat; do
    [ -f "/sys/block/zram0/$zf" ] && rec "zram_${zf}" "/sys/block/zram0/$zf"
done
# Supported algorithms
[ -f /sys/block/zram0/comp_algorithm ] && \
    rec "zram_avail_algos" "$(rcat /sys/block/zram0/comp_algorithm | tr -d '[]' | tr ' ' ',')"

# LMK / LMKD tunables
for lmk in /sys/module/lowmemorykiller/parameters/minfree \
            /sys/module/lowmemorykiller/parameters/adj; do
    [ -f "$lmk" ] && rec "lmk_${lmk##*/}" "$lmk"
done

# KSM (Kernel Same-page Merging)
KSM="/sys/kernel/mm/ksm"
if [ -d "$KSM" ]; then
    for f in run sleep_millisecs pages_to_scan; do
        [ -f "$KSM/$f" ] && rec "ksm_$f" "$KSM/$f"
    done
fi

# ════════════════════════════════════════════════════════════════════════
# 4. THERMAL — detect zones, engines, profiles
# ════════════════════════════════════════════════════════════════════════
# Thermal message (Xiaomi/MIUI/Lunaris)
for tf in /sys/class/thermal/thermal_message/sconfig \
          /sys/class/thermal/thermal_message/cpu_limits \
          /sys/class/thermal/thermal_message/gpu_limits \
          /sys/class/thermal/thermal_message/boost; do
    [ -e "$tf" ] && rec "thermal_msg_${tf##*/}" "$tf"
done

# Qualcomm thermal engine / QTI
for qf in /sys/devices/virtual/thermal/thermal_manager/zone_config \
          /sys/module/msm_thermal/parameters/enabled \
          /sys/module/msm_thermal/parameters/core_limit_temp_degC \
          /proc/sys/kernel/perf_cpu_time_max_percent; do
    [ -e "$qf" ] && rec "thermal_qti_${qf##*/}" "$qf"
fi

# Discover all thermal zones — record name+type for temp reading
TZ_CPU="" TZ_GPU="" TZ_DDR="" TZ_SKIN=""
for tz in /sys/class/thermal/thermal_zone*/; do
    tztype=$(rcat "${tz}type" | tr '[:upper:]' '[:lower:]')
    idx="${tz%/}"; idx="${idx##*thermal_zone}"
    rec "tz_${idx}_type" "$tztype"
    rec "tz_${idx}_temp" "${tz}temp"
    # Classify
    case "$tztype" in
        *cpu*|*tsens_tz_sensor*[0-9]) [ -z "$TZ_CPU" ] && TZ_CPU="${tz}temp" ;;
        *gpu*|*kgsl*) [ -z "$TZ_GPU" ] && TZ_GPU="${tz}temp" ;;
        *ddr*|*mem*) [ -z "$TZ_DDR" ] && TZ_DDR="${tz}temp" ;;
        *skin*|*xo*|*back*|*quiet*) [ -z "$TZ_SKIN" ] && TZ_SKIN="${tz}temp" ;;
    esac
done
[ -n "$TZ_CPU" ]  && rec "thermal_zone_cpu"  "$TZ_CPU"
[ -n "$TZ_GPU" ]  && rec "thermal_zone_gpu"  "$TZ_GPU"
[ -n "$TZ_DDR" ]  && rec "thermal_zone_ddr"  "$TZ_DDR"
[ -n "$TZ_SKIN" ] && rec "thermal_zone_skin" "$TZ_SKIN"

# Fallback: use tsens sensors sorted by index for SM7435
# cpuTempC = zone67, gpuTempC = zone31, ddrTempC = zone43 (discovered from existing code)
[ -f /sys/class/thermal/thermal_zone67/temp ] && rec "thermal_zone_cpu_fb" "/sys/class/thermal/thermal_zone67/temp"
[ -f /sys/class/thermal/thermal_zone31/temp ] && rec "thermal_zone_gpu_fb" "/sys/class/thermal/thermal_zone31/temp"
[ -f /sys/class/thermal/thermal_zone43/temp ] && rec "thermal_zone_ddr_fb" "/sys/class/thermal/thermal_zone43/temp"

# ════════════════════════════════════════════════════════════════════════
# 5. I/O SCHEDULERS — all block devices
# ════════════════════════════════════════════════════════════════════════
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] || continue
    dev="${q%/queue/scheduler}"; dev="${dev##*/}"
    case "$dev" in ram*|loop*|zram*) continue ;; esac
    rec "io_scheduler_${dev}" "$q"
    [ -f "${q%scheduler}read_ahead_kb" ]     && rec "io_read_ahead_${dev}"     "${q%scheduler}read_ahead_kb"
    [ -f "${q%scheduler}nr_requests" ]       && rec "io_nr_requests_${dev}"    "${q%scheduler}nr_requests"
    [ -f "${q%scheduler}iostats" ]           && rec "io_iostats_${dev}"        "${q%scheduler}iostats"
    [ -f "${q%scheduler}rotational" ]        && rec "io_rotational_${dev}"     "${q%scheduler}rotational"
done
# Best block device for main storage
for bd in sda mmcblk0 nvme0n1 sdc ufs0 sde; do
    if [ -f "/sys/block/$bd/queue/scheduler" ]; then
        rec "io_main_dev" "$bd"
        rec "io_scheduler_node" "/sys/block/$bd/queue/scheduler"
        rec "io_read_ahead_node" "/sys/block/$bd/queue/read_ahead_kb"
        break
    fi
done

# ════════════════════════════════════════════════════════════════════════
# 6. NETWORK
# ════════════════════════════════════════════════════════════════════════
rec "net_tcp_congestion" "/proc/sys/net/ipv4/tcp_congestion_control"
[ -f /proc/sys/net/ipv4/tcp_congestion_control ] && \
    rec "net_tcp_avail_algos" "$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null | tr ' ' ',')"
for nf in rmem_max wmem_max tcp_rmem tcp_wmem; do
    [ -f "/proc/sys/net/core/$nf" ] && rec "net_core_${nf}" "/proc/sys/net/core/$nf"
done
# Primary interface
for iface in $(ls /sys/class/net/ 2>/dev/null); do
    case "$iface" in lo|dummy*|rmnet*) continue ;; esac
    [ -f "/sys/class/net/$iface/tx_queue_len" ] && rec "net_primary_iface" "$iface" && break
done
rec "net_txqueuelen_node" "/proc/sys/net/core/netdev_max_backlog"

# ════════════════════════════════════════════════════════════════════════
# 7. BATTERY / POWER
# ════════════════════════════════════════════════════════════════════════
for bp in /sys/class/power_supply/battery /sys/class/power_supply/Battery; do
    [ -d "$bp" ] || continue
    rec "battery_dir" "$bp"
    for f in temp capacity status health charge_type voltage_now current_now \
              charge_counter batt_therm_secondary fast_charge_level; do
        [ -f "$bp/$f" ] && rec "battery_${f}" "$bp/$f"
    done
    break
done

# USB / charging detection
for cp in /sys/class/power_supply/usb/online \
          /sys/class/power_supply/USB/online \
          /sys/class/power_supply/usb_pd/online; do
    [ -f "$cp" ] && rec "charger_online_node" "$cp" && break
done

# Fast charge (MIUI/OnePlus/etc)
for fc in /sys/class/power_supply/battery/charge_type \
          /sys/kernel/fast_charge/enabled \
          /sys/module/asus_chg/parameters/fast_charging_enable; do
    [ -e "$fc" ] && rec "fast_charge_node" "$fc" && break
done

# ════════════════════════════════════════════════════════════════════════
# 8. DISPLAY — brightness, refresh rate
# ════════════════════════════════════════════════════════════════════════
for br in /sys/class/backlight/*/brightness /sys/class/leds/lcd-backlight/brightness; do
    [ -f "$br" ] && rec "display_brightness" "$br" && break
done
for mr in /sys/class/drm/card0-DSI-1/modes \
          /sys/class/graphics/fb0/modes; do
    [ -f "$mr" ] && rec "display_modes" "$mr" && break
done

# ════════════════════════════════════════════════════════════════════════
# 9. DEVICE IDENTITY — for dashboard info
# ════════════════════════════════════════════════════════════════════════
rec "device_soc" "$(getprop ro.board.platform 2>/dev/null)"
rec "device_hw"  "$(getprop ro.hardware 2>/dev/null)"
rec "device_gpu" "$(getprop ro.hardware.gralloc 2>/dev/null)"
rec "device_cpu_variant" "$(getprop ro.product.cpu.abi 2>/dev/null)"

# Total RAM
TOTAL_RAM=$(awk '/MemTotal/{print $2}' /proc/meminfo 2>/dev/null)
[ -n "$TOTAL_RAM" ] && rec "mem_total_kb" "$TOTAL_RAM"

# ════════════════════════════════════════════════════════════════════════
# 10. ENTROPY / RANDOM
# ════════════════════════════════════════════════════════════════════════
[ -f /proc/sys/kernel/random/read_wakeup_threshold ] && \
    rec "entropy_read_threshold" "/proc/sys/kernel/random/read_wakeup_threshold"

log "Detection complete — $(wc -l < "$NODES") nodes recorded"
