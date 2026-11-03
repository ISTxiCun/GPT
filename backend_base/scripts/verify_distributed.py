#!/usr/bin/env python3
"""Local demo verification using only the Python standard library. Never prints tokens or keys."""
import argparse
import concurrent.futures
import json
import os
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8090')
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    secret = os.environ.get('DEMO_ACCESS_KEY')
    if not secret:
        parser.error('Export DEMO_ACCESS_KEY with the value configured in the demo app')
    # Disable environment proxying for this local verification client.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(path, method='GET', token=None, key=None, demo_key=None):
        headers = {}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if key:
            headers['Idempotency-Key'] = key
        if demo_key:
            headers['X-Demo-Key'] = demo_key
        req = urllib.request.Request(base + path, headers=headers, method=method)
        try:
            response = opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            try:
                body = json.loads(raw)
            except json.JSONDecodeError:
                body = {}
            return response.status, body, response.headers

    token = None
    for attempt in range(40):
        try:
            status, login, _ = request('/a/api/public/demo/login', 'POST', demo_key=secret)
            if status == 403:
                raise RuntimeError('Demo access key rejected')
            if status == 200:
                token = login['data']['token']
                break
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(1)
    assert token, 'Instance A did not become ready'
    try:
        # Also allow instance B to finish startup without creating extra sessions.
        for attempt in range(40):
            status, body, _ = request('/b/api/demo/me', token=token)
            if status == 200:
                break
            time.sleep(1)
        assert status == 200 and body['data']['user'] == login['data']['user']
        assert login['data']['instance'] == 'a' and body['data']['instance'] == 'b'
        print('PASS: login created on A is accepted by B')

        for index in range(5):
            status, _, _ = request(('/a' if index % 2 == 0 else '/b') + '/api/demo/rate', token=token)
            assert status == 200, 'Unexpected early rate rejection'
        status, body, headers = request('/b/api/demo/rate', token=token)
        assert status == 429 and body['code'] == 'RATE_LIMITED' and int(headers['Retry-After']) > 0
        print('PASS: A and B share one rate budget')

        key = str(uuid.uuid4())
        barrier = threading.Barrier(2)

        def submit(instance):
            barrier.wait(timeout=5)
            return request('/' + instance + '/api/demo/submit', 'POST', token=token, key=key)[0]

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            statuses = list(pool.map(submit, ['a', 'b']))
        assert sorted(statuses) == [200, 409], 'Exactly one duplicate submission must pass'
        print('PASS: concurrent duplicate submissions admit exactly one request')

        resource = str(uuid.uuid4())
        barrier = threading.Barrier(2)

        def lock(instance):
            barrier.wait(timeout=5)
            return request('/' + instance + '/api/demo/lock?' + urllib.parse.urlencode(
                {'resource': resource, 'holdMillis': 1000}), 'POST', token=token)[0]

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            statuses = list(pool.map(lock, ['a', 'b']))
        assert sorted(statuses) == [200, 409], 'One lock holder and one lock timeout expected'
        status, _, _ = request('/b/api/demo/lock?' + urllib.parse.urlencode(
            {'resource': resource, 'holdMillis': 0}), 'POST', token=token)
        assert status == 200, 'Lock must be released after callback'
        print('PASS: cross-instance lock contention and release')

        assert request('/a/api/demo/logout', 'POST', token=token)[0] == 200
        assert request('/b/api/demo/me', token=token)[0] == 401
        print('PASS: logout on A invalidates the session on B')
    finally:
        if token:
            request('/a/api/demo/logout', 'POST', token=token)


if __name__ == '__main__':
    main()
