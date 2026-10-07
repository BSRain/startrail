from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / 'outputs'
shutil.copy2(root / 'app/build/outputs/apk/debug/app-debug.apk', out / 'startrail-4.3-debug.apk')
for source, dest in [('README.md', 'README-使用说明.md'), ('VALIDATION.md', 'VALIDATION.md'), ('CHANGELOG.md', 'CHANGELOG-4.3.md'), ('RELEASE.md', '发布与安装-4.3.md')]:
    shutil.copy2(root / source, out / dest)
for name in ['startrail-4.3-events.png','startrail-4.3-event-detail.png']:
    shutil.copy2(root / 'work' / name, out / name)
shutil.copy2(root / 'app/build/outputs/apk/release/app-release-unsigned.apk', out / 'startrail-4.3-release-unsigned.apk')
files = set()
for name in ['README.md','RELEASE.md','VERSION_CONTROL.md','signing.properties.example','ROADMAP.md','VALIDATION.md','CHANGELOG.md','THIRD-PARTY-NOTICES.txt','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','app/build.gradle','app/proguard-rules.pro']:
    if (root / name).is_file(): files.add(root / name)
for directory in ['gradle', 'app/src', 'tasks']:
    files.update(p for p in (root / directory).rglob('*') if p.is_file())
for pattern in ['package-v43.py','*-sources.md','events43-sources.md','meteor43-sources.md','events-planner-green-v43.txt','planner-lunar-red-v43.txt','planner-separation-red-v43.txt','events-boundary-red-v43.txt','signing-config-guard-v41.txt','small-screen-v43.txt','focused-v43.txt','focused-v43-attempt1.txt','build-tests-v43.log','build-v43.log','deepsky-reference.csv','performance-v36.md','motion-v37.md','performance-v35-baseline.txt','performance-v36-optimized.txt','instrumentation-v43.txt','jvm-v43.txt','signature-v43.txt','astro-tests/*.java','orientation-tests/*.java','solar-tests/*.java','solar-tests/*.json','fetch-solar-reference.py','generate-solar-tests.py']:
    files.update(p for p in (root / 'work').glob(pattern) if p.is_file())
archive = out / 'startrail-4.3-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files): z.write(p,p.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any('keystore' in name or name.endswith(('.apk','.pem','.jks')) or name.endswith(('local.properties','signing.properties')) for name in z.namelist())
    assert 'app/src/main/java/com/startrail/app/ConstellationArtwork.java' in z.namelist()
    assert 'work/galaxy-sources.md' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/astro/EquatorialTransform.java' in z.namelist()
    assert 'app/src/test/java/com/startrail/app/astro/TimeAxisTest.java' in z.namelist()
    assert 'work/performance-v36.md' in z.namelist()
    assert 'work/weather-sources.md' in z.namelist()
    assert 'RELEASE.md' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/EventCalendarDialog.java' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/astro/CelestialEvents.java' in z.namelist()
    assert 'tasks/plan.md' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/JournalTransferController.java' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/WeatherPlanDialog.java' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/QWeatherCredentials.java' in z.namelist()
    assert 'app/src/main/java/com/startrail/app/astro/WeatherPlanner.java' in z.namelist()
    for name in ['app/src/main/java/com/startrail/app/astro/ObservingEvents.java','app/src/main/java/com/startrail/app/ObservingListStore.java','app/src/main/java/com/startrail/app/AltitudeChartView.java','app/src/test/java/com/startrail/app/astro/ObservingEventsTest.java']:
        assert name in z.namelist()
names = ['startrail-4.3-release-unsigned.apk','startrail-4.3-debug.apk','startrail-4.3-source.zip','startrail-4.3-events.png','startrail-4.3-event-detail.png']
checks = []
for name in names:
    p=out/name
    checks.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+name)
    print(name,p.stat().st_size)
(out/'SHA256SUMS-4.3.txt').write_text('\n'.join(checks)+'\n',encoding='utf-8')
print('Archive integrity and private-key exclusion: OK')
