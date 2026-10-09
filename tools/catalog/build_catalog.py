"""
Builds the medication catalog the app searches when adding a medication, and the chemistry its
pages show. Run from the repo root:

    python tools/catalog/build_catalog.py

Sources, all public domain or CC0, fetched here at build time so the app never goes online:
- openFDA's NDC directory: US products with their strengths, dose forms, and Rx or OTC status
- RxNorm's prescribable subset: which brand names are real brands rather than store labels
- Wikidata: international nonproprietary names (paracetamol for acetaminophen)
- Wikidata again: each ingredient's formula, mass and SMILES, keyed by PubChem id; RDKit draws the
  SMILES in 2D (pip install rdkit)
- PubChem: IUPAC names, when it isn't throttling (a burst of lookups got this machine blocked for over
  half an hour, so it's asked only in batches and skipped while it says 429)

Downloads are cached under build/catalog, so a rerun only fetches what's missing. Writes
app/src/main/assets/catalog/medications.gzjsonl and chemistry.gzjsonl: gzipped JSON lines, not
named .gz, since Android's packaging strips that ending from assets
"""
import gzip, io, json, os, re, sys, time, urllib.parse, urllib.request, zipfile
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(ROOT, 'build', 'catalog')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')
AGENT = 'PanaceaCatalogBuild/1.0 (offline medication tracker; build-time download)'
os.makedirs(CACHE, exist_ok=True)
os.makedirs(OUT, exist_ok=True)


def fetch(url, data=None, tries=8):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers={'User-Agent': AGENT})
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            # PubChem answers 404 for a name it doesn't know, and 400 for one it can't parse
            if e.code in (400, 404):
                return None
            if i == tries - 1:
                raise
            # Throttled: hammering on only keeps the block in place, so wait it out properly
            if e.code in (429, 503):
                print(f'PubChem says {e.code}; waiting {(i + 1) * 2} min', flush=True)
                time.sleep(120 * (i + 1))
                continue
        except Exception:
            if i == tries - 1:
                raise
        # PubChem's "server busy" passes in a few seconds to a minute
        time.sleep(min(60, 3 * 2 ** i))


def cached(name, url):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print('downloading', name)
        open(path, 'wb').write(fetch(url))
    return path


# ---------------------------------------------------------------- sources

def load_ndc():
    path = cached('ndc.zip', 'https://download.open.fda.gov/drug/ndc/drug-ndc-0001-of-0001.json.zip')
    z = zipfile.ZipFile(path)
    return json.loads(z.read(z.namelist()[0]))['results']


def load_rxnorm():
    path = cached('rxnorm_prescribe.json', 'https://rxnav.nlm.nih.gov/REST/Prescribe/allconcepts.json?tty=IN+BN')
    concepts = json.load(open(path, encoding='utf-8'))['minConceptGroup']['minConcept']
    return ({c['name'].lower(): c['name'] for c in concepts if c['tty'] == 'IN'},
            {c['name'].lower(): c['name'] for c in concepts if c['tty'] == 'BN'})


def load_inn():
    query = '''SELECT ?item ?inn ?label ?cid ?unii WHERE { ?item wdt:P2275 ?inn . FILTER(LANG(?inn)="en")
        OPTIONAL { ?item wdt:P662 ?cid } OPTIONAL { ?item wdt:P652 ?unii }
        OPTIONAL { ?item rdfs:label ?label FILTER(LANG(?label)="en") } }'''
    path = os.path.join(CACHE, 'inn2.json')
    if not os.path.exists(path):
        print('downloading inn.json')
        url = 'https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': query, 'format': 'json'})
        open(path, 'wb').write(fetch(url))
    rows = json.load(open(path, encoding='utf-8'))['results']['bindings']
    out = {}
    for r in rows:
        inn, label = r['inn']['value'], r.get('label', {}).get('value', '')
        e = out.setdefault(r['item']['value'], {'inn': inn, 'names': set(), 'cid': None, 'unii': None})
        # Plain names only: "(RS)-hydroxyzine" and "17α-estradiol" are chemists' labels, not what anyone searches
        e['names'] |= {n.lower() for n in (inn, label) if re.fullmatch(r"[A-Za-z][A-Za-z '-]+", n)}
        if 'unii' in r:
            e['unii'] = r['unii']['value']
        if 'cid' in r and not e['cid']:
            e['cid'] = int(r['cid']['value'])
    return list(out.values())


def load_unii_cids():
    """FDA ingredient codes (UNII) to PubChem ids, from Wikidata in one query rather than a PubChem lookup per name"""
    path = os.path.join(CACHE, 'unii.json')
    if not os.path.exists(path):
        print('downloading unii.json')
        query = 'SELECT ?unii ?cid WHERE { ?item wdt:P652 ?unii ; wdt:P662 ?cid . }'
        open(path, 'wb').write(fetch('https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': query, 'format': 'json'})))
    rows = json.load(open(path, encoding='utf-8'))['results']['bindings']
    out = {}
    for r in rows:
        # A few Wikidata entries hold a name where the id belongs
        if r['cid']['value'].isdigit():
            out.setdefault(r['unii']['value'], int(r['cid']['value']))
    return out


# ---------------------------------------------------------------- products

ROUTES = {'ORAL', 'SUBLINGUAL', 'BUCCAL', 'NASAL', 'RESPIRATORY (INHALATION)', 'TRANSDERMAL', 'SUBCUTANEOUS',
          'INTRAMUSCULAR', 'INTRAVENOUS', 'OPHTHALMIC', 'RECTAL', 'TOPICAL', 'AURICULAR (OTIC)', 'VAGINAL', 'CUTANEOUS'}
SKIP_FORMS = ('KIT', 'GAS', 'SOAP', 'SHAMPOO', 'CLOTH', 'SWAB', 'STICK', 'DENTIFRICE', 'MOUTHWASH', 'RINSE',
              'SPONGE', 'DRESSING', 'TAMPON', 'BAR', 'LIPSTICK', 'POWDER, METERED', 'IMPLANT', 'INSERT')

# Salt and ester words dropped to find the active moiety's name: "sertraline hydrochloride" is sertraline
SALTS = r'''\b(hydrochloride|dihydrochloride|hcl|hydrobromide|sodium|disodium|potassium|calcium|magnesium salt|
    maleate|succinate|tartrate|bitartrate|citrate|sulfate|sulphate|mesylate|besylate|bromide|chloride|acetate|
    fumarate|phosphate|monohydrate|dihydrate|trihydrate|hemihydrate|anhydrous|hyclate|tosylate|lactate|malate|
    gluconate|napsylate|pamoate|propionate|valerate|dipropionate|furoate|xinafoate|bromide|stearate|hydrate|
    hemifumarate|benzoate|oxalate|camsylate|edisylate|esylate|ethanolate|monosodium|trometamol|tromethamine|
    medoxomil|axetil|pivoxil|cilexetil|mofetil|decanoate|enanthate|cypionate|undecanoate|palmitate|saccharate|
    aspartate|adipate|monohydrochloride|dimesylate|meglumine|dimeglumine)\b'''


def base_name(name):
    n = re.sub(SALTS, ' ', name.lower(), flags=re.X)
    n = re.sub(r'\(.*?\)', ' ', n)
    return re.sub(r'\s+', ' ', n).strip(' ,')


def med_type(form, routes):
    f, r = form.upper(), set(routes)
    if r & {'SUBLINGUAL', 'BUCCAL'}:
        return 'SUBLINGUAL'
    if 'TRANSDERMAL' in r or 'PATCH' in f:
        return 'PATCH'
    if 'RESPIRATORY (INHALATION)' in r or 'INHAL' in f:
        return 'INHALER'
    if 'INJECT' in f or r & {'SUBCUTANEOUS', 'INTRAMUSCULAR', 'INTRAVENOUS'}:
        return 'IV_INJECTION'
    if 'SUPPOSITORY' in f:
        return 'SUPPOSITORY'
    if 'DROP' in f or r & {'OPHTHALMIC', 'AURICULAR (OTIC)'}:
        return 'DROPS'
    if r & {'TOPICAL', 'CUTANEOUS', 'VAGINAL'} or any(w in f for w in ('CREAM', 'OINTMENT', 'GEL', 'LOTION')):
        return 'TOPICAL'
    if 'GUMM' in f:
        return 'EDIBLE'
    if 'CAPSULE' in f:
        return 'ORAL_CAPSULE'
    if 'TABLET' in f or 'CAPLET' in f or 'PILL' in f:
        return 'ORAL_TABLET'
    if 'POWDER' in f or 'GRANULE' in f:
        return 'POWDER'
    if any(w in f for w in ('SOLUTION', 'SUSPENSION', 'SYRUP', 'ELIXIR', 'LIQUID', 'EMULSION', 'CONCENTRATE')):
        return 'LIQUID_SYRUP'
    return 'OTHER'


def strength(s):
    """'50 mg/1' -> (50, 'mg', None); '100 mg/5mL' -> (100, 'mg', '5 mL'); None when it isn't a plain amount"""
    m = re.match(r'^\s*([\d.]+)\s*([a-zA-Zµ%\[\]]+)\s*/\s*([\d.]*)\s*([a-zA-Z]*)\s*$', s or '')
    if not m:
        return None
    amount, unit, per_n, per_u = float(m.group(1)), m.group(2), m.group(3), m.group(4)
    unit = {'ug': 'mcg', 'mcg': 'mcg', 'g': 'g', 'mg': 'mg', 'mL': 'mL', 'meq': 'mEq', 'mEq': 'mEq', '[iU]': 'IU', 'iU': 'IU',
            '[USP\'U]': 'units', 'U': 'units'}.get(unit, unit)
    if unit not in ('mg', 'mcg', 'g', 'mL', 'mEq', 'IU', 'units', '%'):
        return None
    per = None if per_n in ('', '1') and per_u in ('', '1') else f'{per_n} {per_u}'.strip()
    if per in ('1 1',):
        per = None
    return amount, unit, per


def title(name):
    small = {'and', 'of', 'with', 'in', 'for'}
    words = name.lower().split()
    return ' '.join(w if (i and w in small) else w[:1].upper() + w[1:] for i, w in enumerate(words))


def build_entries(ndc, rx_in, rx_bn):
    products = []
    for p in ndc:
        if p.get('product_type') not in ('HUMAN OTC DRUG', 'HUMAN PRESCRIPTION DRUG'):
            continue
        cat = p.get('marketing_category') or ''
        if 'HOMEOPATHIC' in cat or 'BULK' in cat:
            continue
        routes = p.get('route') or []
        form = p.get('dosage_form') or ''
        if not routes or not set(routes) <= ROUTES or any(s in form.upper() for s in SKIP_FORMS):
            continue
        otc = p['product_type'] == 'HUMAN OTC DRUG'
        # Sunscreens, sanitizers and store-brand creams: OTC on the skin is not something people log
        if otc and set(routes) & {'TOPICAL', 'CUTANEOUS'}:
            continue
        actives = []
        for a in p.get('active_ingredients') or []:
            st = strength(a.get('strength'))
            if st is None:
                break
            actives.append((base_name(a['name']), st))
        else:
            if actives:
                # The label's own order puts the main drug first: "oxycodone and acetaminophen"
                generic = base_name(p.get('generic_name') or '')
                actives.sort(key=lambda a: generic.find(a[0]) if a[0] in generic else 999)
                # Adderall lists one drug as four salts; once the salt words are gone they're two, so merge
                merged = {}
                for n, st in actives:
                    if n in merged and merged[n][1] == st[1] and merged[n][2] == st[2]:
                        merged[n] = (merged[n][0] + st[0], st[1], st[2])
                    elif n not in merged:
                        merged[n] = st
                actives = list(merged.items())
                uniis = (p.get('openfda') or {}).get('unii') or []
                products.append({'brand': (p.get('brand_name_base') or p.get('brand_name') or '').strip(), 'actives': actives,
                                 'type': med_type(form, routes), 'otc': otc,
                                 # Only a single active's code can be told apart; with several the list isn't in label order
                                 'unii': uniis[0] if len(actives) == 1 and len(uniis) == 1 else None})

    # Generic entries: every single-active moiety, named as RxNorm names it where it can
    by_generic = defaultdict(list)
    for p in products:
        if len(p['actives']) == 1:
            by_generic[p['actives'][0][0]].append(p)
    by_brand = defaultdict(list)
    for p in products:
        b = p['brand'].lower()
        if b in rx_bn and b != p['actives'][0][0]:
            by_brand[b].append(p)

    def forms(ps):
        seen = Counter()
        for p in ps:
            (a0, (amt, unit, per)) = p['actives'][0]
            others = tuple((n, s[0], s[1]) for n, s in p['actives'][1:])
            seen[(p['type'], 'OTC' if p['otc'] else 'Prescribed', amt, unit, per, others)] += 1
        # The most common forms first; six is plenty to pick from and keeps the asset small
        out = []
        for (t, c, amt, unit, per, others), n in seen.most_common(6):
            f = {'type': t, 'category': c, 'dose': amt, 'unit': unit}
            if per:
                f['per'] = per
            if others:
                f['others'] = [{'name': title(n), 'amount': a, 'unit': u} for n, a, u in others]
            out.append(f)
        return out

    entries = []
    for g, ps in by_generic.items():
        if len(ps) < 1 or len(g) < 3:
            continue
        name = rx_in.get(g, g)
        entries.append({'kind': 'generic', 'name': title(name) if name == name.lower() else name,
                        'ingredients': [g], 'forms': forms(ps), 'n': len(ps)})
    for b, ps in by_brand.items():
        # A brand's main drug; what else a form holds is listed with that form
        main = Counter(p['actives'][0][0] for p in ps).most_common(1)[0][0]
        entries.append({'kind': 'brand', 'name': rx_bn[b], 'ingredients': [main], 'forms': forms(ps), 'n': len(ps)})
    unii = {}
    for p in products:
        if p['unii']:
            unii.setdefault(p['actives'][0][0], p['unii'])
    return entries, unii


# ---------------------------------------------------------------- beyond the FDA's list

# Supplements and recreational substances, which no drug label covers; [name, category, type, dose, unit, pubchem name]
EXTRAS = [
    ['Caffeine', 'OTC', 'ORAL_TABLET', 200, 'mg', 'caffeine'],
    ['Nicotine', 'Recreational', 'OTHER', 4, 'mg', 'nicotine'],
    ['Alcohol', 'Recreational', 'LIQUID_SYRUP', 14, 'g', 'ethanol'],
    ['Cannabis (THC)', 'Recreational', 'OTHER', 10, 'mg', 'dronabinol'],
    ['CBD', 'OTC', 'LIQUID_SYRUP', 25, 'mg', 'cannabidiol'],
    ['Psilocybin', 'Recreational', 'OTHER', 1, 'g', 'psilocybin'],
    ['MDMA', 'Recreational', 'ORAL_CAPSULE', 100, 'mg', 'midomafetamine'],
    ['LSD', 'Recreational', 'OTHER', 100, 'mcg', 'lysergide'],
    ['Kratom', 'Recreational', 'POWDER', 2, 'g', 'mitragynine'],
    ['Kava', 'OTC', 'LIQUID_SYRUP', 250, 'mg', 'kavain'],
    ['Melatonin', 'OTC', 'EDIBLE', 3, 'mg', 'melatonin'],
    ['Vitamin D3', 'OTC', 'ORAL_CAPSULE', 1000, 'IU', 'cholecalciferol'],
    ['Vitamin B12', 'OTC', 'ORAL_TABLET', 1000, 'mcg', 'cyanocobalamin'],
    ['Vitamin C', 'OTC', 'ORAL_TABLET', 500, 'mg', 'ascorbic acid'],
    ['Magnesium Glycinate', 'OTC', 'ORAL_CAPSULE', 200, 'mg', 'magnesium glycinate'],
    ['Magnesium Citrate', 'OTC', 'ORAL_TABLET', 200, 'mg', 'magnesium citrate'],
    ['Zinc', 'OTC', 'ORAL_TABLET', 25, 'mg', 'zinc gluconate'],
    ['Iron', 'OTC', 'ORAL_TABLET', 65, 'mg', 'ferrous sulfate'],
    ['Omega-3 Fish Oil', 'OTC', 'ORAL_CAPSULE', 1000, 'mg', 'eicosapentaenoic acid'],
    ['Creatine', 'OTC', 'POWDER', 5, 'g', 'creatine'],
    ['L-Theanine', 'OTC', 'ORAL_CAPSULE', 200, 'mg', 'theanine'],
    ['5-HTP', 'OTC', 'ORAL_CAPSULE', 100, 'mg', 'oxitriptan'],
    ['Ashwagandha', 'OTC', 'ORAL_CAPSULE', 600, 'mg', 'withaferin a'],
    ['Biotin', 'OTC', 'ORAL_TABLET', 5000, 'mcg', 'biotin'],
    ['Folic Acid', 'OTC', 'ORAL_TABLET', 400, 'mcg', 'folic acid'],
    ['Turmeric (Curcumin)', 'OTC', 'ORAL_CAPSULE', 500, 'mg', 'curcumin'],
    ['Glucosamine', 'OTC', 'ORAL_TABLET', 1500, 'mg', 'glucosamine'],
    ['Probiotic', 'OTC', 'ORAL_CAPSULE', 0, '', None],
    ['Electrolytes', 'OTC', 'POWDER', 0, '', None],
    ['Ketamine', 'Recreational', 'POWDER', 50, 'mg', 'ketamine'],
    ['Cocaine', 'Recreational', 'POWDER', 50, 'mg', 'cocaine'],
    ['Amphetamine', 'Recreational', 'POWDER', 20, 'mg', 'amphetamine'],
    ['Nitrous Oxide', 'Recreational', 'OTHER', 8, 'g', 'nitrous oxide'],
    ['GHB', 'Recreational', 'LIQUID_SYRUP', 1, 'g', 'sodium oxybate'],
    ['DMT', 'Recreational', 'OTHER', 30, 'mg', 'dimethyltryptamine'],
    ['Mescaline', 'Recreational', 'OTHER', 200, 'mg', 'mescaline'],
]


def extras():
    out = []
    for name, cat, typ, dose, unit, pc in EXTRAS:
        f = {'type': typ, 'category': cat, 'dose': dose, 'unit': unit}
        out.append({'kind': 'other', 'name': name, 'ingredients': [pc] if pc else [], 'forms': [f] if dose else [{'type': typ, 'category': cat}], 'n': 0})
    return out


# ---------------------------------------------------------------- chemistry

def sparql(query):
    body = fetch('https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': query, 'format': 'json'}))
    return json.loads(body)['results']['bindings']


def cids_by_name(names):
    """PubChem ids for names Wikidata labels exactly, in batches; drug labels there are lower case"""
    path = os.path.join(CACHE, 'cids_wd.json')
    found = json.load(open(path)) if os.path.exists(path) else {}
    todo = [n for n in names if n not in found]
    for i in range(0, len(todo), 150):
        batch = todo[i:i + 150]
        values = ' '.join(json.dumps(n) + '@en' for n in batch)
        rows = sparql(f'SELECT ?label ?cid WHERE {{ VALUES ?label {{ {values} }} ?item rdfs:label ?label ; wdt:P662 ?cid . }}')
        for n in batch:
            found.setdefault(n, None)
        for r in rows:
            if r['cid']['value'].isdigit() and not found.get(r['label']['value']):
                found[r['label']['value']] = int(r['cid']['value'])
        json.dump(found, open(path, 'w'))
    return found


def subscript_free(formula):
    return formula.translate(str.maketrans('₀₁₂₃₄₅₆₇₈₉', '0123456789')).replace(' ', '')


def chemistry(cids):
    """
    Formula, mass, name and structure for each PubChem id. Wikidata holds the first three and the
    SMILES, keyed by the same id, without PubChem's throttling; RDKit lays the SMILES out in 2D
    """
    from rdkit import Chem
    from rdkit.Chem import AllChem, Descriptors, rdMolDescriptors
    path = os.path.join(CACHE, 'chem_wd.json')
    chem = json.load(open(path)) if os.path.exists(path) else {}
    todo = [c for c in cids if str(c) not in chem]
    for i in range(0, len(todo), 150):
        batch = todo[i:i + 150]
        values = ' '.join(json.dumps(str(c)) for c in batch)
        rows = sparql(f"""SELECT ?cid ?label ?formula ?mass ?smiles ?iso WHERE {{ VALUES ?cid {{ {values} }} ?item wdt:P662 ?cid .
            OPTIONAL {{ ?item rdfs:label ?label FILTER(LANG(?label) = "en") }} OPTIONAL {{ ?item wdt:P274 ?formula }}
            OPTIONAL {{ ?item wdt:P2067 ?mass }} OPTIONAL {{ ?item wdt:P233 ?smiles }} OPTIONAL {{ ?item wdt:P2017 ?iso }} }}""")
        for r in rows:
            c = r['cid']['value']
            e = chem.setdefault(c, {})
            for key, field in (('title', 'label'), ('formula', 'formula'), ('mass', 'mass'), ('smiles', 'smiles'), ('iso', 'iso')):
                if field in r and key not in e:
                    e[key] = r[field]['value']
        for c in batch:
            chem.setdefault(str(c), {})
        json.dump(chem, open(path, 'w'))
        print(f'chemistry {min(i + 150, len(todo))}/{len(todo)}', flush=True)

    out = {}
    for c, e in chem.items():
        smiles = e.get('smiles') or e.get('iso')
        mol = Chem.MolFromSmiles(smiles) if smiles else None
        formula = subscript_free(e['formula']) if e.get('formula') else None
        if mol is not None:
            formula = formula or rdMolDescriptors.CalcMolFormula(mol)
        if not formula:
            continue
        rec = {'title': title((e.get('title') or '').strip()), 'formula': formula, 'iupac': None}
        # Molar mass from the atoms' average weights; Wikidata's mass is the monoisotopic one, a little lower
        rec['weight'] = round(Descriptors.MolWt(mol), 2) if mol is not None else 0
        if mol is not None:
            Chem.Kekulize(mol, clearAromaticFlags=True)
            AllChem.Compute2DCoords(mol)
            conf = mol.GetConformer()
            # RDKit draws bonds 1.5 long, PubChem 1; the app scales to whatever comes, but keep them alike
            rec['atoms'] = [[a.GetAtomicNum(), round(conf.GetAtomPosition(a.GetIdx()).x / 1.5, 3),
                             round(conf.GetAtomPosition(a.GetIdx()).y / 1.5, 3), a.GetTotalNumHs()] for a in mol.GetAtoms()]
            rec['bonds'] = [[b.GetBeginAtomIdx(), b.GetEndAtomIdx(), int(b.GetBondTypeAsDouble())] for b in mol.GetBonds()]
        out[c] = rec
    return out


def iupac_names(chem):
    """PubChem's systematic names, a hundred ids a request; skipped, not waited for, while PubChem throttles"""
    path = os.path.join(CACHE, 'iupac.json')
    names = json.load(open(path)) if os.path.exists(path) else {}
    todo = [c for c in chem if c not in names]
    for i in range(0, len(todo), 100):
        ids = ','.join(todo[i:i + 100])
        try:
            req = urllib.request.Request(f'https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/{ids}/property/IUPACName/JSON',
                                         headers={'User-Agent': AGENT})
            with urllib.request.urlopen(req, timeout=60) as r:
                for p in json.loads(r.read())['PropertyTable']['Properties']:
                    names[str(p['CID'])] = p.get('IUPACName')
        except Exception as e:
            print('no IUPAC names this run:', e)
            break
        json.dump(names, open(path, 'w'))
        time.sleep(1.0)
    for c, rec in chem.items():
        rec['iupac'] = names.get(c)


# ---------------------------------------------------------------- main

def main():
    ndc = load_ndc()
    rx_in, rx_bn = load_rxnorm()
    inn = load_inn()
    entries, unii = build_entries(ndc, rx_in, rx_bn)
    entries += extras()
    unii_cids = load_unii_cids()

    # International names as aliases of the US generic, and as their own name when there's no US one
    by_name, by_unii = {}, {}
    for e in inn:
        for n in e['names']:
            by_name[n] = e
        if e['unii']:
            by_unii[e['unii']] = e
    known = {i: unii_cids[u] for i, u in unii.items() if u in unii_cids}
    for e in entries:
        if e['kind'] != 'generic':
            continue
        # By the FDA's ingredient code first: acetaminophen and paracetamol share no name, but share one
        w = by_unii.get(unii.get(e['ingredients'][0])) or by_name.get(e['name'].lower()) or by_name.get(e['ingredients'][0])
        if w:
            e['aliases'] = sorted(n for n in w['names'] if n != e['name'].lower())
            if w['cid']:
                known[e['ingredients'][0]] = w['cid']

    ingredients = sorted({i for e in entries for i in e['ingredients']})
    print(len(entries), 'entries,', len(ingredients), 'ingredients')
    by_label = cids_by_name([i for i in ingredients if i not in known])
    cid = {i: known.get(i) or by_label.get(i) for i in ingredients}
    print(sum(1 for c in cid.values() if c), 'with a PubChem id')
    chem = chemistry(sorted({c for c in cid.values() if c}))
    iupac_names(chem)

    with gzip.open(os.path.join(OUT, 'medications.gzjsonl'), 'wt', encoding='utf-8') as f:
        for e in sorted(entries, key=lambda e: (-e['n'], e['name'])):
            e['cids'] = [cid.get(i) or 0 for i in e['ingredients']]
            e['ingredients'] = [title(i) for i in e['ingredients']]
            del e['n']
            f.write(json.dumps(e, separators=(',', ':')) + '\n')
    used = {c for e in entries for c in e['cids'] if c}
    with gzip.open(os.path.join(OUT, 'chemistry.gzjsonl'), 'wt', encoding='utf-8') as f:
        for c in sorted(used):
            v = chem.get(str(c))
            if v:
                f.write(json.dumps({'cid': c, **v}, separators=(',', ':')) + '\n')
    for name in ('medications.gzjsonl', 'chemistry.gzjsonl'):
        print(name, os.path.getsize(os.path.join(OUT, name)) // 1024, 'KB')


if __name__ == '__main__':
    main()
