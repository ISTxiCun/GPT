#!/usr/bin/env python3
"""Local-only, assertion-based acceptance for the isolated observability Compose."""
import concurrent.futures
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ['docker', 'compose', '-p', 'backend-base-demo', '-f',
           str(ROOT / 'compose.distributed-demo.yaml'), '-f', str(ROOT / 'compose.observability-demo.yaml')]
CLIENT = urllib.request.build_opener(urllib.request.ProxyHandler({}))
RESULTS = {}


def compose(*args):
    return subprocess.check_output(COMPOSE + list(args), cwd=ROOT, stderr=subprocess.STDOUT, text=True)


def request(port, path, token=None, headers=None, method='GET', timeout=8):
    h = dict(headers or {})
    if token:
        h['Authorization'] = 'Bearer ' + token
    start = time.monotonic()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', headers=h, method=method)
    try:
        response = CLIENT.open(req, timeout=timeout)
    except urllib.error.HTTPError as ex:
        response = ex
    with response:
        body = response.read().decode()
        return response.status, body, time.monotonic() - start


def data(port, path, **kw):
    status, body, _ = request(port, path, **kw)
    assert status == 200, (port, path, status, body[:200])
    return json.loads(body)['data']


def eventually(action, timeout=60):
    end = time.monotonic() + timeout
    while True:
        try:
            return action()
        except (AssertionError, OSError, KeyError, IndexError):
            if time.monotonic() >= end:
                raise
            time.sleep(1)


def ready(port):
    status, _, _ = request(port, '/actuator/health/readiness')
    assert status == 200, (port, status)


def trace(trace_id):
    traces = data(16686, '/api/traces/' + trace_id)
    assert traces and traces[0]['spans'], 'Trace has not reached Jaeger'
    return traces[0]


def series(port):
    status, body, _ = request(port, '/actuator/prometheus')
    assert status == 200
    return {line.rsplit(' ', 1)[0] for line in body.splitlines()
            if line.startswith(('http_server_requests_seconds', 'distributed_'))}, body


def traffic(tokens, count):
    def invoke(i):
        port = (18080, 28080)[i % 2]
        status, body, elapsed = request(port, '/api/demo/observability/' + str(i), tokens[i % 2])
        assert status == 200, (status, body[:100])
        return elapsed
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        elapsed = list(pool.map(invoke, range(count)))
    return {'requests': count, 'max_seconds': round(max(elapsed), 3),
            'p95_seconds': round(sorted(elapsed)[int(len(elapsed) * .95)], 3)}


def main():
    if not os.environ.get('DEMO_ACCESS_KEY') or not os.environ.get('REDIS_PASSWORD'):
        raise SystemExit('Set DEMO_ACCESS_KEY and REDIS_PASSWORD for the local demo')
    for port in (18081, 28081):
        eventually(lambda: ready(port))
    def scraped_targets():
        targets = data(9090, '/api/v1/targets')['activeTargets']
        assert len(targets) == 2 and all(t['health'] == 'up' for t in targets)
        return targets
    eventually(scraped_targets)
    tokens = [data(port, '/api/public/demo/login', method='POST',
                   headers={'X-Demo-Key': os.environ['DEMO_ACCESS_KEY']})['token'] for port in (18080, 28080)]
    # The first instance's session and cache are usable on the second instance.
    first = data(18080, '/api/demo/cache', token=tokens[0])
    second = data(28080, '/api/demo/cache', token=tokens[0])
    assert first['loadedBy'] == second['loadedBy'] and first['instance'] != second['instance']
    RESULTS['shared_session_and_cache'] = 'passed'
    trace_ids, request_ids = [], set()
    for i in range(20):
        parent = secrets.token_hex(16)
        port = (18080, 28080)[i % 2]
        task = data(port, '/api/demo/observability-task', token=tokens[i % 2], headers={
            'traceparent': f'00-{parent}-0123456789abcdef-01',
            'baggage': 'credential=SENSITIVE-BAGGAGE-MARKER', 'tracestate': 'vendor=SENSITIVE-STATE-MARKER'})
        assert task['http'] == task['task'], task
        assert task['http']['traceId'] == parent
        assert task['http']['requestId'] not in request_ids
        request_ids.add(task['http']['requestId'])
        trace_ids.append(parent)
    def linked_trace():
        value = trace(trace_ids[-1])
        http_spans = [s for s in value['spans'] if s['operationName'].startswith('http ')]
        redis_spans = [s for s in value['spans'] if any(t['key'].startswith('db.') for t in s['tags'])]
        assert http_spans and redis_spans, 'Missing HTTP/Redis spans'
        parents = {s['spanID'] for s in http_spans}
        assert any(r['spanID'] in parents for s in redis_spans for r in s.get('references', [])), 'Redis span disconnected from HTTP'
        return value
    spans = eventually(linked_trace)
    serialized = json.dumps(spans)
    assert not any(secret in serialized for secret in tokens + ['SENSITIVE-BAGGAGE-MARKER', 'SENSITIVE-STATE-MARKER'])
    RESULTS['w3c_redis_task_and_context_isolation'] = {'requests': 20, 'jaeger_spans': len(spans['spans'])}
    for port in (18080, 28080):
        response = data(port, '/api/demo/observability/invalid', token=tokens[0], headers={'traceparent': 'invalid'})
        assert len(response['traceId']) == 32 and response['traceId'] not in trace_ids
    traffic(tokens, 40)
    before = [series(port)[0] for port in (18081, 28081)]
    RESULTS['dynamic_ids'] = traffic(tokens, 400)
    after = [series(port) for port in (18081, 28081)]
    for i, (labels, raw) in enumerate(after):
        assert labels == before[i], 'Dynamic IDs increased metric series'
        assert 'uri="/api/demo/observability/{id}"' in raw
        assert not any(secret in raw for secret in tokens + trace_ids)
    RESULTS['dynamic_ids']['series_stable'] = True
    # Stop both collection mechanisms. Business requests must remain bounded.
    try:
        compose('stop', 'collector', 'prometheus')
        RESULTS['collection_outage'] = traffic(tokens, 1600)
        assert RESULTS['collection_outage']['max_seconds'] < 8
    finally:
        compose('start', 'collector', 'prometheus')
    parent = secrets.token_hex(16)
    def recovered_trace():
        data(18080, '/api/demo/observability/recovered', token=tokens[0],
             headers={'traceparent': f'00-{parent}-0123456789abcdef-01'})
        return trace(parent)
    eventually(recovered_trace)
    RESULTS['trace_export_recovery'] = 'passed'
    # Shared authentication Redis is required even if an admission rule allows OPEN.
    try:
        compose('stop', '-t', '2', 'redis')
        for port in (18081, 28081):
            assert request(port, '/actuator/health/liveness')[0] == 200
            assert request(port, '/actuator/health/readiness')[0] == 503
        assert request(18080, '/api/demo/me', tokens[0])[0] >= 400
    finally:
        compose('start', 'redis')
    for port in (18081, 28081):
        eventually(lambda: ready(port))
    RESULTS['redis_fault_and_recovery'] = 'passed'
    started = time.monotonic()
    try:
        compose('stop', '-t', '40', 'app-a')
        assert time.monotonic() - started < 40
        ready(28081)
        assert data(28080, '/api/demo/me', token=tokens[1])['instance'] == 'b'
    finally:
        compose('start', 'app-a')
    eventually(lambda: ready(18081))
    RESULTS['termination_and_surviving_instance'] = 'passed'
    logs = compose('logs', '--no-color', 'app-a', 'app-b')
    assert not any(secret in logs for secret in tokens + ['SENSITIVE-BAGGAGE-MARKER', 'SENSITIVE-STATE-MARKER'])
    for parent in trace_ids:
        assert re.search(r'requestId=[\w-]+ traceId=' + parent + r' spanId=[a-f0-9]+', logs), 'Missing log correlation'
    RESULTS['log_correlation_and_secret_redaction'] = 'passed'


if __name__ == '__main__':
    destination = ROOT / 'target-demo/fault-results/acceptance.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        main()
    except Exception as ex:
        RESULTS['failure'] = str(ex)
        raise
    finally:
        destination.write_text(json.dumps(RESULTS, indent=2) + '\n')
        print(json.dumps(RESULTS, indent=2))
