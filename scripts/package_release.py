from pathlib import Path
import hashlib
import zipfile

root=Path(__file__).resolve().parent.parent
dist=root/'dist'
dist.mkdir(exist_ok=True)
apk=dist/'BlueLink_2.0.apk'
assert apk.is_file()
sha=hashlib.sha256(apk.read_bytes()).hexdigest().upper()
(dist/'SHA256SUMS.txt').write_text(f'{sha}  BlueLink_2.0.apk\n',encoding='utf-8')
paths=[]
for name in ['README.md','.gitignore','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat']:
 paths.append(root/name)
for folder in ['app/src','protocol/src','gradle/wrapper','scripts']:
 paths += [p for p in (root/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
paths += [root/'app/build.gradle.kts',root/'protocol/build.gradle.kts']
paths += list((root/'docs').glob('*.md')) + list((root/'docs').glob('*.txt'))
paths += [root/name for name in ['protocol-tests-v2.log','build-v2.log','app-compile-v2.log','adb-status-v2.log','lint-v2.log','apk-metadata-v2.log','apk-alignment-v2.log'] if (root/name).is_file()]
paths += [apk,dist/'SHA256SUMS.txt']
archive=dist/'BlueLink_2.0_Project.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for path in sorted(set(paths)):
  z.write(path,'BlueLink_2.0/'+path.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 assert hashlib.sha256(z.read('BlueLink_2.0/dist/BlueLink_2.0.apk')).hexdigest().upper()==sha
 assert not any('.keystore' in n or 'local.properties' in n or '/.gradle' in n for n in z.namelist())
 print(f'Verified {len(z.namelist())} archive entries')
print(archive)
print('APK SHA-256',sha)
