#!/usr/bin/env python3
"""Check actual merged test manifests and APK metadata before publishing."""
import json
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
EXPECTED = 'app.orchard.tv.topshelf'
DISABLED = ('BootReceiver', 'ConfigReceiver', 'HomeGuardService', 'RevivalListener', 'RevivalSyncService', 'RevivalAccountService', 'AerialDream')
BLOCKED = {'WRITE_SECURE_SETTINGS', 'SYSTEM_ALERT_WINDOW', 'WRITE_SETTINGS', 'RECEIVE_BOOT_COMPLETED', 'PACKAGE_USAGE_STATS'}
certificate = subprocess.check_output(['keytool', '-exportcert', '-alias', 'androiddebugkey',
    '-keystore', 'local/topshelf-test.keystore', '-storepass', 'android'])
expected_fingerprint = hashlib.sha256(certificate).hexdigest()
apksigner = str(Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/apksigner')
for variant in ('standard', 'guard'):
    metadata = json.loads(Path(f'app/build/outputs/apk/{variant}/release/output-metadata.json').read_text())
    assert metadata['applicationId'] == EXPECTED, metadata
    assert metadata['elements'][0]['versionCode'] == int(sys.argv[1]), metadata
    manifests = list(Path('app/build/intermediates/merged_manifests').glob(f'{variant}Release/**/AndroidManifest.xml'))
    assert manifests, f'No merged manifest for {variant}'
    root = ET.parse(manifests[0]).getroot()
    assert root.get('package') == EXPECTED
    categories = [x.get(ANDROID + 'name', '') for x in root.iter('category')]
    assert not any('HOME' in category for category in categories), categories
    components = {x.get(ANDROID + 'name', '').split('.')[-1]: x for tag in ('service', 'receiver') for x in root.iter(tag)}
    for component in DISABLED:
        assert component in components and components[component].get(ANDROID + 'enabled') == 'false', component
    permissions = {x.get(ANDROID + 'name', '').split('.')[-1] for x in root.iter('uses-permission')}
    assert not (permissions & BLOCKED), permissions & BLOCKED
    print(f'{variant}: isolated package, no HOME, takeover components disabled, no takeover permissions')

    apk = f'app/build/outputs/apk/{variant}/release/app-{variant}-release.apk'
    signature = subprocess.check_output([apksigner, 'verify', '--verbose', '--print-certs', apk], text=True)
    fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
    assert [x.lower() for x in fingerprints] == [expected_fingerprint], fingerprints
    print(f'{variant}: APK signature verified; fixed test certificate {expected_fingerprint}')
