#!/usr/bin/env bash
# Полный прогон лабы: каждый коллектор замеряется в отдельном процессе java, затем стресс-тест и график.
#   ./scripts/run_all.sh                      # потоки 1 2 4 8 10, прогрев 5 с, 5 забегов по 5 с
#   THREADS="1 2 4" BENCH_ARGS="--seconds 1 --runs 3 --warmup 1" ./scripts/run_all.sh   # быстрый прогон
set -euo pipefail

cd "$(dirname "$0")/.."

THREADS=${THREADS:-"1 2 4 8 10"}
BENCH_ARGS=${BENCH_ARGS:-}
CONSISTENCY_REPEAT=${CONSISTENCY_REPEAT:-3}
OUT=${OUT:-results}

if [[ -z "${JAVA:-}" ]]; then
    if [[ -x /usr/libexec/java_home ]] && JH=$(/usr/libexec/java_home -v 25 2>/dev/null); then
        JAVA="$JH/bin/java"
    else
        JAVA=java
    fi
fi

./gradlew -q classes
CP=build/classes/java/main
mkdir -p "$OUT"

"$JAVA" -version 2>&1 | head -1 > "$OUT/env.txt"
if [[ "$(uname)" == "Darwin" ]]; then
    {
        sysctl -n machdep.cpu.brand_string
        echo "logical cpus: $(sysctl -n hw.ncpu)"
        echo "performance cores: $(sysctl -n hw.perflevel0.physicalcpu)"
        echo "efficiency cores: $(sysctl -n hw.perflevel1.physicalcpu)"
    } >> "$OUT/env.txt"
else
    { lscpu | grep -E 'Model name|^CPU\(s\)|Thread|Core'; } >> "$OUT/env.txt" || true
fi

echo "collector,threads,mops" > "$OUT/throughput.csv"
for c in single-thread empty-lock synchronized striped-lock thread-local double-buffer; do
    # shellcheck disable=SC2086
    "$JAVA" -cp "$CP" org.labs.benchmark.Benchmark "$c" $THREADS $BENCH_ARGS | tee -a "$OUT/throughput.csv"
done

echo "collector,snapshots,broken_percent,buckets_less,buckets_greater,record_calls,final_count,final_diff" > "$OUT/consistency.csv"
for c in striped-lock thread-local double-buffer double-buffer-no-recheck; do
    "$JAVA" -cp "$CP" org.labs.benchmark.ConsistencyCheck "$c" --repeat "$CONSISTENCY_REPEAT" | tee -a "$OUT/consistency.csv"
done

python3 scripts/plot.py "$OUT/throughput.csv" "$OUT/throughput.svg"
echo "Готово: $OUT/"
