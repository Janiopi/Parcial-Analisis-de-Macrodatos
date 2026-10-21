#!/usr/bin/env bash
# Run on master with the matching Hadoop profile active.
# Usage: benchmark.sh single|cluster HDFS_INPUT HDFS_OUTPUT_ROOT [REPEATS] [SSH_WORKER...]
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:?single or cluster}
input=${2:?HDFS input path}
output_root=${3:?HDFS output root}
repeats=${4:-3}
workers=("${@:5}")
case "$mode" in
  single) expected_conf=/opt/hadoop/conf-single ;;
  cluster|multi) mode=cluster; expected_conf=/opt/hadoop/etc/hadoop ;;
  *) echo 'mode must be single or cluster' >&2; exit 2 ;;
esac
if [[ ${HADOOP_CONF_DIR:-} != "$expected_conf" ]]; then
  echo "Select the matching master profile: export HADOOP_CONF_DIR=$expected_conf" >&2
  exit 2
fi
if [[ ! "$repeats" =~ ^[1-9][0-9]*$ ]]; then
  echo 'repeats must be a positive integer' >&2
  exit 2
fi
if [[ "$mode" == single && ${#workers[@]} -ne 0 || "$mode" == cluster && ${#workers[@]} -ne 2 ]]; then
  echo 'single takes no workers; cluster requires worker1 and worker2' >&2
  exit 2
fi
if [[ -f dist/cc531.jar ]]; then
  jar=dist/cc531.jar
elif [[ -f build/cc531.jar ]]; then
  jar=build/cc531.jar
else
  echo 'cc531.jar missing: copy it to dist/ or run scripts/build.sh' >&2
  exit 2
fi
for worker in "${workers[@]}"; do
  ssh -o BatchMode=yes -o ConnectTimeout=5 "$worker" true
done
samplers=()
stop_samples() {
  for sampler in "${samplers[@]}"; do kill "$sampler" 2>/dev/null || true; done
  for sampler in "${samplers[@]}"; do wait "$sampler" 2>/dev/null || true; done
  samplers=()
}
trap stop_samples EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "bench/$mode"
metrics="bench/$mode/timing.tsv"
if [[ -e "$metrics" ]]; then
  echo "Previous measurements exist in $metrics; use a fresh local campaign directory" >&2
  exit 2
fi
printf 'mode\tquery\trepetition\tseconds\toutput\n' > "$metrics"
for query in duration interruption dbscan mlp-class mlp-reg; do
  warmup="$output_root/$mode/$query/warmup"
  warmup_log="bench/$mode/${query}-warmup.log"
  echo "$mode $query: calentamiento sin cronometrar"
  if ! hadoop jar "$jar" pe.edu.cc531.Main "$query" "$input" "$warmup" > "$warmup_log" 2>&1; then
    tail -n 30 "$warmup_log" >&2
    exit 1
  fi
  for ((run=1;run<=repeats;run++)); do
    dest="$output_root/$mode/$query/run-$run"
    log="bench/$mode/${query}-${run}.log"
    sample="bench/$mode/${query}-${run}-node.tsv"
    bash scripts/sample-node.sh "$sample" & samplers+=("$!")
    for worker in "${workers[@]}"; do
      worker_name=${worker%%@*}
      bash scripts/sample-node.sh "bench/$mode/${query}-${run}-${worker_name}.tsv" "$worker" & samplers+=("$!")
    done
    start=$(date +%s.%N)
    if hadoop jar "$jar" pe.edu.cc531.Main "$query" "$input" "$dest" > "$log" 2>&1; then
      end=$(date +%s.%N)
      stop_samples
      seconds=$(awk -v a="$start" -v b="$end" 'BEGIN{printf "%.3f",b-a}')
      printf '%s\t%s\t%d\t%s\t%s\n' "$mode" "$query" "$run" "$seconds" "$dest" >> "$metrics"
      echo "$mode $query $run: ${seconds}s"
    else
      stop_samples
      tail -n 30 "$log" >&2
      exit 1
    fi
  done
done
