#!/usr/bin/env bash
# Usage: bash scripts/sample-node.sh samples.tsv [ssh-host]; stop with Ctrl-C or SIGTERM.
set -euo pipefail
out=${1:?provide output TSV}
remote=${2:-}
host=${remote:-$(hostname)}
snapshot() {
  if [[ -n "$remote" ]]; then
    ssh -o BatchMode=yes -o ConnectTimeout=5 "$remote" "head -n 1 /proc/stat; awk '/^MemTotal:|^MemAvailable:/ {print \$2}' /proc/meminfo"
  else
    head -n 1 /proc/stat
    awk '/^MemTotal:|^MemAvailable:/ {print $2}' /proc/meminfo
  fi
}
printf 'epoch\thost\tcpu_pct\tmemory_used_mb\n' > "$out"
stats=$(snapshot)
mapfile -t lines <<< "$stats"
read -r _ user nice system idle iowait irq softirq steal _ <<< "${lines[0]}"
previous_busy=$((user+nice+system+irq+softirq))
previous_total=$((previous_busy+idle+iowait+steal))
while sleep 1; do
  stats=$(snapshot)
  mapfile -t lines <<< "$stats"
  read -r _ user nice system idle iowait irq softirq steal _ <<< "${lines[0]}"
  busy=$((user+nice+system+irq+softirq))
  total=$((busy+idle+iowait+steal))
  delta_busy=$((busy-previous_busy))
  delta_total=$((total-previous_total))
  previous_busy=$busy; previous_total=$total
  memory_total=${lines[1]}
  memory_available=${lines[2]}
  awk -v t="$(date +%s)" -v h="$host" -v b="$delta_busy" -v d="$delta_total" -v m="$((memory_total-memory_available))" \
    'BEGIN {printf "%s\t%s\t%.3f\t%.3f\n",t,h,(d>0?100*b/d:0),m/1024}' >> "$out"
done
