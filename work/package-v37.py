from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / 'outputs'
shutil.copy2(root / 'app/build/outputs/apk/debug/app-debug.apk', out / 'startrail-3.7-debug.apk')
for source, dest in [('README.md', 'README-使用说明.md'), ('VALIDATION.md', 'VALIDATION.md'), ('CHANGELOG.md', 'CHANGELOG-3.7.md')]:
    shutil.copy2(root / source, out / dest)
for name in ['startrail-3.7-deepsky.png','startrail-3.7-catalog.png','startrail-3.7-below-horizon.png']:
    shutil.copy2(root / 'work' / name, out / name)
files = set()
for name in ['README.md','ROADMAP.md','VALIDATION.md','CHANGELOG.md','THIRD-PARTY-NOTICES.txt','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','app/build.gradle','app/proguard-rules.pro']:
    if (root / name).is_file(): files.add(root / name)
for directory in ['gradle', 'app/src']:
    files.update(p for p in (root / directory).rglob('*') if p.is_file())
for pattern in ['*-sources.md','deepsky-reference.csv','performance-v36.md','motion-v37.md','performance-v35-baseline.txt','performance-v36-optimized.txt','instrumentation-v37.txt','jvm-v37.txt','astro-tests/*.java','orientation-tests/*.java','solar-tests/*.java','solar-tests/*.json','fetch-solar-reference.py','generate-solar-tests.py']:
    files.update(p for p in (root / 'work').glob(pattern) if p.is_file())
archive = out / 'startrail-3.7-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files): z.write(p,p.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any('keystore' in name or name.endswith('.apk') for name in z.namelist())
    assert 'app/src/main/java/com/startrail/app/ConstellationArtwork.java' in z.namelist()
    assert 'work/galaxy-sources.md' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/astro/EquatorialTransform.java' in z.namelist()
    assert 'app/src/test/java/com/startrail/app/astro/TimeAxisTest.java' in z.namelist()
    assert 'work/performance-v36.md' in z.namelist()
names = ['startrail-3.7-debug.apk','startrail-3.7-source.zip','startrail-3.7-deepsky.png','startrail-3.7-catalog.png','startrail-3.7-below-horizon.png']
checks = []
for name in names:
    p=out/name
    checks.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+name)
    print(name,p.stat().st_size)
(out/'SHA256SUMS-3.7.txt').write_text('\n'.join(checks)+'\n',encoding='utf-8')
print('Archive integrity and private-key exclusion: OK')
