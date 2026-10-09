#!/usr/bin/env python3
"""Exercise deployed Alpha 14 boundaries without a user's GitHub credentials."""
import json
import secrets
import re
import time
import urllib.error
import urllib.request

ORIGIN = 'https://apkdrop.rawinstinctai.de'
def request(path, data=None, headers=None):
    req=urllib.request.Request(ORIGIN+path, data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json','User-Agent':'APKDrop-Companion/0.1.0-alpha.14',**(headers or {})})
    try:
        response=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as error:
        response=error
    payload=response.read()
    return response.status, json.loads(payload) if 'application/json' in response.headers.get('Content-Type','') else None

status,catalog=request('/api/discover')
assert status==200 and isinstance(catalog.get('apps'),list), 'Public Discover is unavailable'
for app in catalog['apps']:
    assert all(field in app for field in ('packageName','versionCode','signers')), 'Missing public radar identity hints'
assert request('/api/companion/radar?offset=0')[0]==401, 'Radar must require device authorization'
poll,device=secrets.token_hex(32),secrets.token_hex(32)
assert request('/api/companion/pair',{'pollSecret':poll,'deviceSecret':device},{'Origin':'https://invalid.example'})[0]==403
status,pair=request('/api/companion/pair',{'pollSecret':poll,'deviceSecret':device})
assert status==200 and isinstance(pair,dict), 'Pairing endpoint is unavailable'
assert isinstance(pair.get('id'),str) and re.fullmatch(r'[a-f0-9]{32}',pair['id']), 'Invalid Android pairing ID'
assert type(pair.get('expires')) is int and time.time()<pair['expires']<=time.time()+630, 'Invalid pairing expiry'
assert pair.get('verificationUrl')==ORIGIN+'/companion/connect?code='+pair['id']
assert device not in json.dumps(pair) and poll not in json.dumps(pair)
status,state=request('/api/companion/pair/status',{'id':pair['id'],'pollSecret':poll})
assert status==200 and state['connected'] is False
assert request('/api/companion/pair/approve',{'id':pair['id'],'confirm':True},{'Origin':ORIGIN})[0]==401
assert request('/api/companion/radar?offset=0',headers={'Authorization':'Bearer '+device})[0]==401
print('LIVE ALPHA14 BACKEND VERIFIED: Discover identity hints, scoped radar, pairing and browser authorization boundaries.')
