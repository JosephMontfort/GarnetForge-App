#!/system/bin/sh
# Run this after clean reboot to dump all tunable node default values.
# Usage: su -c "sh /path/to/dump_nodes.sh"
printf "=== GarnetForge Node Defaults Dump ===\n"
printf "CPU Policies:\n"
for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    p="${d%/}"; p="${p##*/}"
    printf "  %s governor=%s min=%s max=%s\n" "$p" \
        "$(cat ${d}scaling_governor 2>/dev/null)" \
        "$(cat ${d}scaling_min_freq 2>/dev/null)" \
        "$(cat ${d}scaling_max_freq 2>/dev/null)"
done
printf "GPU:\n"
for d in /sys/class/devfreq/*/; do
    [ -f "$d/cur_freq" ] || continue
    printf "  max_freq=%s min_freq=%s\n" "$(cat ${d}max_freq 2>/dev/null)" "$(cat ${d}min_freq 2>/dev/null)"
    KGSL="${d}device/kgsl/kgsl-3d0"
    [ -f "$KGSL/max_pwrlevel" ] && printf "  max_pwrlevel=%s\n" "$(cat $KGSL/max_pwrlevel 2>/dev/null)"
    [ -f "$KGSL/min_pwrlevel" ] && printf "  min_pwrlevel=%s\n" "$(cat $KGSL/min_pwrlevel 2>/dev/null)"
    [ -f "$KGSL/idle_timer" ]   && printf "  idle_timer=%s\n"   "$(cat $KGSL/idle_timer   2>/dev/null)"
    break
done
printf "VM:\n"
printf "  swappiness=%s dirty_ratio=%s dirty_bg=%s vfs_pressure=%s\n" \
    "$(cat /proc/sys/vm/swappiness 2>/dev/null)" \
    "$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)" \
    "$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)" \
    "$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)"
printf "ZRAM:\n"
printf "  disksize=%s algo=%s\n" \
    "$(cat /sys/block/zram0/disksize 2>/dev/null)" \
    "$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)"
printf "I/O:\n"
for q in /sys/block/*/queue/read_ahead_kb; do
    [ -f "$q" ] && printf "  read_ahead_kb=%s (from %s)\n" "$(cat $q 2>/dev/null)" "$q" && break
done
for q in /sys/block/*/queue/scheduler; do
    [ -f "$q" ] && printf "  scheduler=%s\n" "$(cat $q 2>/dev/null)" && break
done
printf "Network:\n"
printf "  tcp_algo=%s\n" "$(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)"
printf "Thermal:\n"
printf "  sconfig=%s boost=%s\n" \
    "$(cat /sys/class/thermal/thermal_message/sconfig 2>/dev/null)" \
    "$(cat /sys/class/thermal/thermal_message/boost 2>/dev/null)"
printf "=== Done ===\n"
