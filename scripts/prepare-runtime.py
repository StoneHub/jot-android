#!/usr/bin/env python3
"""Fetch the pinned official GenieX AAR and use one shared QAIRT runtime.

The app's Maven dependency supplies QAIRT 2.50.0. Remove duplicate QAIRT
libraries from GenieX to avoid packaging older versions of those libraries.
"""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://repo.maven.apache.org/maven2/com/qualcomm/qti/geniex-android/0.8.0/geniex-android-0.8.0.aar'
SHA256 = '330d4d20ca27b449ba3e1b588fa2e964124e1682c01502952b483875d902dcc9'
destination = ROOT / 'build/runtime'
destination.mkdir(parents=True, exist_ok=True)
source = destination / 'geniex-original.aar'
if not source.exists() or hashlib.sha256(source.read_bytes()).hexdigest() != SHA256:
    urllib.request.urlretrieve(URL, source)
if hashlib.sha256(source.read_bytes()).hexdigest() != SHA256:
    raise SystemExit('Official GenieX archive checksum mismatch')
with zipfile.ZipFile(source) as archive, zipfile.ZipFile(destination / 'geniex-android-0.8.0.aar', 'w', zipfile.ZIP_DEFLATED) as output:
    for entry in archive.infolist():
        name = entry.filename
        if name.startswith('jni/') and (name.split('/')[-1].startswith('libQnn') or name.endswith(('libhta_hexagon_runtime_qnn.so', 'libhta_hexagon_runtime_snpe.so'))):
            continue
        output.writestr(entry, archive.read(name))
print('Pinned GenieX 0.8.0 prepared; QAIRT comes from Maven qnn-runtime:2.50.0.')
