"""Generate fixed offline fixtures from NASA/JPL Horizons observer ephemerides."""
from pathlib import Path
import urllib.request, urllib.parse, json, csv, datetime
out=Path(__file__).parent/'solar-tests';out.mkdir(exist_ok=True)
targets={'sun':10,'moon':301,'mercury':199,'venus':299,'mars':499,'jupiter':599,'saturn':699,'uranus':799,'neptune':899}
cases=[(key,39.9042,116.4074,'2026-09-06T12:00:00Z') for key in targets]
cases += [('moon',-33.8688,151.2093,'2026-09-06T12:00:00Z'),('moon',30.2672,-97.7431,'2024-04-08T18:36:00Z'),('sun',30.2672,-97.7431,'2024-04-08T18:36:00Z')]
fixtures=[]
for index,(name,lat,lon,iso) in enumerate(cases):
    start=datetime.datetime.fromisoformat(iso.replace('Z','+00:00'));stop=start+datetime.timedelta(minutes=1)
    parameters=dict(COMMAND=str(targets[name]),OBJ_DATA='NO',MAKE_EPHEM='YES',EPHEM_TYPE='OBSERVER',CENTER='coord@399',COORD_TYPE='GEODETIC',SITE_COORD=f'{lon},{lat},0',START_TIME=start.strftime('%Y-%m-%d %H:%M:%S'),STOP_TIME=stop.strftime('%Y-%m-%d %H:%M:%S'),STEP_SIZE='1 m',QUANTITIES='4,10,20',CSV_FORMAT='YES',APPARENT='AIRLESS',RANGE_UNITS='KM',SUPPRESS_RANGE_RATE='YES',EXTRA_PREC='YES')
    url='https://ssd.jpl.nasa.gov/api/horizons.api?'+urllib.parse.urlencode({'format':'json',**{k:"'"+v+"'" for k,v in parameters.items()}})
    cache=out/f'horizons-{index:02d}-{name}.json'
    if cache.exists():result=json.loads(cache.read_text(encoding='utf8'))
    else:
        result=json.load(urllib.request.urlopen(url,timeout=45));cache.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    text=result.get('result','')
    if '$$SOE' not in text:raise RuntimeError(result)
    row=next(csv.reader([text.split('$$SOE')[1].split('$$EOE')[0].strip().splitlines()[0]]))
    numeric=[float(v) for v in row[3:] if v.strip() and v.strip()!='n.a.']
    if len(numeric)<4:raise RuntimeError(row)
    az,alt,lit,distance=numeric[:4]
    fixtures.append(dict(id=name,latitude=lat,longitude=lon,time=iso,azimuth=az,altitude=alt,illumination=lit/100,distanceKm=distance,url=url))
    print(index,name,alt,az,flush=True)
(out/'horizons-fixtures.json').write_text(json.dumps(fixtures,indent=2),encoding='utf8')
print('Saved',len(fixtures),'independent JPL references')
