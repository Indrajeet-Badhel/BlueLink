"""Compile with Android Studio's matching Kotlin and Compose compilers.

Used only by the explicitly documented offline fallback, never a substitute for
the standard pinned Gradle compiler verification.
"""
import argparse
import subprocess
import zipfile
import shutil
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--jdk',required=True)
p.add_argument('--kotlinc',required=True)
p.add_argument('--sdk',required=True)
a=p.parse_args()
root=Path(__file__).resolve().parent.parent
cache=root/'qa'/'offline-deps'
cache.mkdir(parents=True,exist_ok=True)
cp=[Path(a.sdk)/'platforms'/'android-37.0'/'android.jar']
for i,line in enumerate((root/'qa'/'runtime-artifacts.txt').read_text().splitlines()):
 f=Path(line)
 if f.suffix=='.aar':
  with zipfile.ZipFile(f) as z:
   for name in z.namelist():
    if name=='classes.jar' or (name.startswith('libs/') and name.endswith('.jar')):
     out=cache/f'{i}-{Path(name).name}'
     out.write_bytes(z.read(name));cp.append(out)
 elif f.suffix=='.jar': cp.append(f)
def compile_module(name, output, sources, classpath, compose=False):
 if output.exists():
  assert output.resolve().is_relative_to(root.resolve()) and output.name in ('classes','main')
  shutil.rmtree(output)
 output.mkdir(parents=True,exist_ok=True)
 args=['-no-stdlib','-no-reflect','-jvm-target','17','-module-name',name,
       '-classpath',';'.join(map(str,classpath)),'-d',str(output)]
 if compose: args+=['-Xplugin='+str(Path(a.kotlinc)/'lib'/'compose-compiler-plugin.jar')]
 args += [str(f) for f in sources]
 argfile=root/'qa'/f'offline-compiler-{name}-args.txt'
 argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8')
 cmd=[str(Path(a.jdk)/'bin'/'java.exe'),'-Xmx2048m','-cp',str(Path(a.kotlinc)/'lib'/'*'),
      'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)]
 code=subprocess.call(cmd,cwd=root)
 if code: raise SystemExit(code)
protocol_output=root/'protocol'/'build'/'classes'/'kotlin'/'main'
compile_module('bluelink_protocol',protocol_output,(root/'protocol'/'src'/'main'/'kotlin').rglob('*.kt'),cp)
compile_module('bluelink_app',root/'app'/'build'/'intermediates'/'built_in_kotlinc'/'debug'/'compileDebugKotlin'/'classes',
               (root/'app'/'src'/'main'/'java').rglob('*.kt'),cp+[protocol_output],True)
