#!/usr/bin/env python3
"""Exercise deployed Alpha 15 boundaries without a user's GitHub credentials."""
import json
import secrets
import re
import time
import urllib.error
import urllib.request

ORIGIN = 'https://apkdrop.rawinstinctai.de'
def request(path, data=None, headers=None):
    req=urllib.request.Request(ORIGIN+path, data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json','User-Agent':'APKDrop-Companion/0.1.0-alpha.15',**(headers or {})})
    try:
        response=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as error:
        response=error
    payload=response.read()
    return response.status, json.loads(payload) if 'application/json' in response.headers.get('Content-Type','') else None

status,catalog=request('/api/discover?q=&category=&page=1&sort=new')
assert status==200 and isinstance(catalog.get('apps'),list), 'Public Discover is unavailable'
assert request('/api/discover?page=1')[0]==200, 'Local matching catalog route unavailable'
assert request('/api/discover?q=&category=&page=1&sort=updated')[0]==200
for path,data in [('/api/companion/apps',None),('/api/companion/import/preview',{'repo':'rawinstinctart/apkdrop-companion'}),('/api/companion/import',{'repo':'rawinstinctart/apkdrop-companion','importConsent':True})]:
    assert request(path,data)[0]==401, 'Native private endpoints must require device authorization'
for app in catalog['apps']:
    assert all(field in app for field in ('packageName','versionCode','signers')), 'Missing public radar identity hints'
assert request('/api/companion/radar?offset=0')[0]==401, 'Radar must require device authorization'
poll,device=secrets.token_hex(32),secrets.token_hex(32)
assert request('/api/companion/pair',{'pollSecret':poll,'deviceSecret':device},{'Origin':'https://invalid.example'})[0]==403
status,pair=request('/api/companion/pair',{'pollSecret':poll,'deviceSecret':device,'privateImport':True})
assert status==200 and isinstance(pair,dict), 'Pairing endpoint is unavailable'
assert isinstance(pair.get('id'),str) and re.fullmatch(r'[a-f0-9]{32}',pair['id']), 'Invalid Android pairing ID'
assert type(pair.get('expires')) is int and time.time()<pair['expires']<=time.time()+630, 'Invalid pairing expiry'
assert pair.get('verificationUrl')==ORIGIN+'/companion/connect?code='+pair['id']
assert device not in json.dumps(pair) and poll not in json.dumps(pair)
status,state=request('/api/companion/pair/status',{'id':pair['id'],'pollSecret':poll})
assert status==200 and state['connected'] is False and state['privateImport'] is False
assert request('/api/companion/pair/approve',{'id':pair['id'],'confirm':True},{'Origin':ORIGIN})[0]==401
assert request('/api/companion/radar?offset=0',headers={'Authorization':'Bearer '+device})[0]==401
print('LIVE ALPHA15 BACKEND VERIFIED: sorted discovery, installed catalog, authenticated private imports, scoped radar and unapproved pairing boundaries.')
