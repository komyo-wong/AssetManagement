#!/usr/bin/env python3
"""Local smoke for e-ink APIs. Reads ROOT_PASSWORD from env."""
import base64
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("API_BASE", "http://127.0.0.1:8080")
ORIGIN = "http://localhost:3006"
ACCOUNT = os.environ.get("ROOT_ACCOUNT", "root")
PASSWORD = os.environ.get("ROOT_PASSWORD", "")

if not PASSWORD:
    print("ROOT_PASSWORD env required", file=sys.stderr)
    sys.exit(2)


def req(method, path, token=None, data=None):
    headers = {"Content-Type": "application/json", "Origin": ORIGIN}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None if data is None else json.dumps(data).encode()
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode()
        try:
            payload = json.loads(raw)
        except Exception:
            payload = raw
        return exc.code, payload


def unwrap(payload):
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


st, login = req("POST", "/api/v1/auth/login", data={"account": ACCOUNT, "password": PASSWORD})
print("login", st)
if st != 200:
    print(login)
    sys.exit(1)
token = unwrap(login)["accessToken"]
me = unwrap(req("GET", "/api/v1/auth/me", token=token)[1])
tid = me["tenantMemberships"][0]["tenantId"]
pid = me["tenantMemberships"][0]["projects"][0]["projectId"]
scope = f"/api/v1/tenants/{tid}/projects/{pid}"
print("scope_ok")

beacons = unwrap(req("GET", scope + "/beacons", token=token)[1]) or []
assets = unwrap(req("GET", scope + "/assets", token=token)[1]) or []
print(f"beacons={len(beacons)} assets={len(assets)}")
if not beacons:
    print("NO_BEACON")
    sys.exit(0)

beacon = beacons[0]
bid = beacon["id"]
st, settings = req(
    "PUT",
    scope + f"/beacons/{bid}/eink-settings",
    token=token,
    data={"einkCapable": True, "einkPasskey": "123456", "preferredGatewayId": None},
)
print("eink_settings", st, unwrap(settings))
if st != 200 or not unwrap(settings).get("einkCapable"):
    sys.exit(1)

assets = unwrap(req("GET", scope + "/assets", token=token)[1]) or []
target = None
for asset in assets:
    fields = asset.get("fields") or {}
    if fields.get("boundBeaconId") == bid:
        target = asset
        break
if target is None:
    for asset in assets:
        if (asset.get("fields") or {}).get("boundBeaconId"):
            target = asset
            break
if target is None:
    print("SETTINGS_OK_NO_BOUND_ASSET")
    sys.exit(0)

aid = target["id"]
print("asset", target.get("code"), "einkCapable", (target.get("fields") or {}).get("einkCapable"))
bw = base64.b64encode(bytes(4000)).decode()
red = base64.b64encode(bytes(4000)).decode()
st, job = req(
    "POST",
    scope + f"/assets/{aid}/eink-jobs",
    token=token,
    data={"bw": bw, "red": red, "orient": "landscape", "templateId": "smoke", "title": "smoke-test"},
)
print("push", st)
payload = unwrap(job)
print(json.dumps(payload if isinstance(payload, dict) else {"raw": payload}, ensure_ascii=False)[:800])
if st == 200:
    jid = payload["jobId"]
    st2, got = req("GET", scope + f"/eink-jobs/{jid}", token=token)
    print("get_job", st2, unwrap(got))
    print("SMOKE_OK")
    sys.exit(0)
if st == 404:
    print("ENDPOINT_MISSING")
    sys.exit(1)
print("SMOKE_PARTIAL_OK")
sys.exit(0)
