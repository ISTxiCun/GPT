#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
compose=(docker compose -p backend-base-demo -f compose.distributed-demo.yaml -f compose.observability-demo.yaml)
case "${1:-}" in
  collector) target=collector ;;
  redis) target=redis ;;
  terminate) target=app-a ;;
  *) echo 'Usage: faults.sh collector|redis|terminate'; exit 2 ;;
esac
mkdir -p target-demo/fault-results
log="target-demo/fault-results/$target-$(date -u +%Y%m%dT%H%M%SZ).log"
probe() {
  for port in 18081 28081; do
    for group in liveness readiness; do
      curl --silent --show-error --max-time 8 -w ' status=%{http_code} seconds=%{time_total}\n' "http://127.0.0.1:$port/actuator/health/$group" || true
    done
  done
}
trap '"${compose[@]}" start "$target" >/dev/null' EXIT
{
  echo before; probe
  "${compose[@]}" stop -t 40 "$target"
  echo fault; probe
  "${compose[@]}" start "$target"
  echo 'Recovery may require Redis reconnect/startup; inspect subsequent probes.'
  for i in {1..10}; do probe; sleep 2; done
} 2>&1 | tee "$log"
