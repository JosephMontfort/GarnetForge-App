#!/system/bin/sh
# GarnetForge Diagnostic Report Generator
MODDIR=/data/data/dev.garnetforge.app/files/garnetforge
OUT="$MODDIR/diagnostic_report.txt"
NODES="$MODDIR/nodes.prop"
CONFIG="$MODDIR/config.prop"
LOG="$MODDIR/garnetforge.log"
> "$OUT"
say() { printf '%s\n' "$1" >> "$OUT"; }
hr()  { say "══════════════════════════════════════"; }
hdr() { hr; say "  $1"; hr; }

say "GarnetForge Diagnostic Report"
say "Generated: $(date)"
say "Device: $(getprop ro.product.model) | $(getprop ro.build.display.id)"
hr

hdr "DEVICE & KERNEL"
say "Kernel:    $(uname -r)"
say "SoC:       $(getprop ro.board.platform)"
say "Android:   $(getprop ro.build.version.release) (API $(getprop ro.build.version.sdk))"
say "Arch:      $(uname -m)"
say "Uptime:    $(uptime)"

hdr "CPU LIVE STATE"
for d in /sys/devices/system/cpu/cpufreq/policy*/; do
    p="${d%/}"; p="${p##*/}"
    gov=$(cat "$d/scaling_governor" 2>/dev/null)
    min=$(cat "$d/scaling_min_freq" 2>/dev/null)
    max=$(cat "$d/scaling_max_freq" 2>/dev/null)
    cur=$(cat "$d/scaling_cur_freq" 2>/dev/null)
    say "$p: gov=$gov cur=${cur}Hz min=${min}Hz max=${max}Hz"
done
say ""
say "Per-core:"
for c in $(seq 0 7); do
    online=$(cat /sys/devices/system/cpu/cpu${c}/online 2>/dev/null || echo "1")
    freq=$(cat /sys/devices/system/cpu/cpu${c}/cpufreq/scaling_cur_freq 2>/dev/null || echo "0")
    say "  cpu${c}: online=$online freq=${freq}Hz"
done

hdr "GPU"
for d in /sys/class/devfreq/*kgsl* /sys/class/devfreq/*gpu* /sys/class/devfreq/*3d*; do
    [ -f "$d/cur_freq" ] || continue
    say "devfreq: $d"
    say "  cur=$(cat $d/cur_freq 2>/dev/null)Hz min=$(cat $d/min_freq 2>/dev/null)Hz max=$(cat $d/max_freq 2>/dev/null)Hz"
    say "  governor=$(cat $d/governor 2>/dev/null)"
    break
done
KGSL=/sys/class/kgsl/kgsl-3d0
[ -d "$KGSL" ] && {
    say "KGSL: pwrlevel=$(cat $KGSL/pwrlevel 2>/dev/null) thermal_pwrlevel=$(cat $KGSL/thermal_pwrlevel 2>/dev/null)"
    say "  idle_timer=$(cat $KGSL/idle_timer 2>/dev/null)"
}

hdr "THERMAL"
say "sconfig: $(cat /sys/class/thermal/thermal_message/sconfig 2>/dev/null)"
say "boost:   $(cat /sys/class/thermal/thermal_message/boost 2>/dev/null)"
say ""
say "Thermal zones (active):"
for tz in /sys/class/thermal/thermal_zone*/; do
    temp=$(cat "${tz}temp" 2>/dev/null); [ -z "$temp" ] || [ "$temp" -eq 0 ] 2>/dev/null && continue
    type=$(cat "${tz}type" 2>/dev/null)
    say "  ${tz##*/}: $type = ${temp}mC"
done | head -30

hdr "MEMORY"
say "$(cat /proc/meminfo | grep -E 'MemTotal|MemAvailable|SwapTotal|SwapFree')"
say "swappiness: $(cat /proc/sys/vm/swappiness 2>/dev/null)"
say "dirty_ratio: $(cat /proc/sys/vm/dirty_ratio 2>/dev/null)"
say "zram: $(cat /sys/block/zram0/disksize 2>/dev/null | awk '{printf "%.1f GB", $1/1073741824}')"
say "zram algo: $(cat /sys/block/zram0/comp_algorithm 2>/dev/null)"
say "$(cat /proc/swaps 2>/dev/null)"

hdr "I/O"
for q in /sys/block/*/queue/scheduler; do
    dev="${q%/queue/scheduler}"; dev="${dev##*/}"
    case "$dev" in ram*|loop*|zram*) continue ;; esac
    say "$dev: $(cat $q 2>/dev/null) | read_ahead=$(cat ${q%scheduler}read_ahead_kb 2>/dev/null)KB"
done

hdr "NETWORK"
say "tcp_algo: $(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)"
say "Available: $(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)"
ip link show 2>/dev/null | grep -E "^[0-9]+:" | while read line; do say "  $line"; done

hdr "GARNETFORGE CONFIG"
[ -f "$CONFIG" ] && cat "$CONFIG" >> "$OUT" || say "config.prop not found"

hdr "NODES.PROP SUMMARY"
[ -f "$NODES" ] && say "$(wc -l < "$NODES") nodes detected" || say "nodes.prop not found"

hdr "RECENT SERVICE LOGS"
[ -f "$LOG" ] && tail -50 "$LOG" >> "$OUT" || say "No log file"

hdr "KERNEL MESSAGES (last 30)"
dmesg 2>/dev/null | grep -iE "garnet|kgsl|thermal|cpufreq|oom" | tail -30 >> "$OUT"

hdr "BATTERY"
for f in capacity status temp voltage_now current_now; do
    v=$(cat /sys/class/power_supply/battery/$f 2>/dev/null)
    [ -n "$v" ] && say "$f: $v"
done

say ""
say "Report complete. $(wc -l < "$OUT") lines."
