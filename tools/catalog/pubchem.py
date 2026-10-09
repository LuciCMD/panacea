"""
A client for PubChem's PUG REST that keeps to its usage policy
(https://pubchem.ncbi.nlm.nih.gov/docs/programmatic-access#section=Request-Volume-Limitations):
no more than five requests a second or 400 a minute, one at a time, and slower still whenever the
X-Throttling-Control header it sends back says PubChem is getting busy. A 429 or 503 is waited out,
for longer each time, rather than retried straight away, since hammering on only keeps a block in place
"""
import json, re, sys, time, urllib.error, urllib.parse, urllib.request

BASE = 'https://pubchem.ncbi.nlm.nih.gov/rest/pug'

# Seconds between requests for the worst status in the header; at Black PubChem still serves small
# requests but turns big ones away, so batches shrink too (see properties)
PAUSE = {'green': 1.0, 'yellow': 4.0, 'red': 8.0, 'black': 10.0}


class Refused(Exception):
    pass


class PubChem:
    def __init__(self, agent, log=print):
        self.agent = agent
        self.log = log
        self.pause = PAUSE['green']
        self.last = 0.0
        self.minute = []

    def _wait_turn(self):
        now = time.monotonic()
        # Under 400 a minute with room to spare
        self.minute = [t for t in self.minute if now - t < 60]
        if len(self.minute) >= 300:
            time.sleep(60 - (now - self.minute[0]) + 0.5)
        delay = self.last + self.pause - time.monotonic()
        if delay > 0:
            time.sleep(delay)

    def _read_throttle(self, headers):
        """'Request Count status: Green (2%), Request Time status: Yellow (55%), Service status: Black (120%)'"""
        text = (headers.get('X-Throttling-Control') or '').lower()
        found = re.findall(r'status:\s*(green|yellow|red|black)', text)
        if found:
            worst = max(found, key=lambda c: list(PAUSE).index(c))
            pause = PAUSE[worst]
            if pause != self.pause:
                self.log(f'PubChem is {worst}; one request every {pause:g} s')
            self.pause = pause

    def get(self, path, max_wait_hours=3.0, patient=True):
        """The body of BASE/path, or None for a name or id PubChem doesn't have; raises Refused at once unless [patient]"""
        url = f'{BASE}/{path}'
        waited = 0.0
        backoff = 60.0
        while True:
            self._wait_turn()
            self.last = time.monotonic()
            self.minute.append(self.last)
            try:
                req = urllib.request.Request(url, headers={'User-Agent': self.agent})
                with urllib.request.urlopen(req, timeout=120) as r:
                    self._read_throttle(r.headers)
                    return r.read()
            except urllib.error.HTTPError as e:
                self._read_throttle(e.headers or {})
                if e.code in (400, 404):
                    return None
                if e.code not in (429, 500, 502, 503, 504):
                    raise
                if not patient:
                    raise Refused(e.code)
                retry_after = (e.headers or {}).get('Retry-After')
                wait = max(backoff, float(retry_after) if retry_after and retry_after.isdigit() else 0)
            except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
                wait = backoff
                self.log(f'PubChem unreachable ({e}); trying again in {wait / 60:g} min')
            if waited + wait > max_wait_hours * 3600:
                raise RuntimeError(f'PubChem kept refusing for {max_wait_hours:g} h: {url}')
            self.log(f'PubChem asked to wait; trying again in {wait / 60:g} min')
            time.sleep(wait)
            waited += wait
            backoff = min(backoff * 2, 15 * 60)

    def cid_for_name(self, name):
        body = self.get('compound/name/' + urllib.parse.quote(name, safe='') + '/cids/JSON')
        return json.loads(body)['IdentifierList']['CID'][0] if body else None

    def properties(self, cids, fields='MolecularFormula,MolecularWeight,IUPACName,Title,SMILES', size=50):
        """
        By id, [size] at a time to begin with; a refused batch is tried again at half the size, down
        to five, and only then waited out
        """
        out = {}
        cids = list(cids)
        i = 0
        while i < len(cids):
            batch = cids[i:i + size]
            ids = ','.join(map(str, batch))
            try:
                body = self.get(f'compound/cid/{ids}/property/{fields}/JSON', patient=size <= 5)
            except Refused:
                size = max(5, size // 2)
                self.log(f'PubChem turned away a batch; asking for {size} at a time')
                time.sleep(30)
                continue
            for p in (json.loads(body)['PropertyTable']['Properties'] if body else []):
                out[p['CID']] = p
            i += len(batch)
        return out
