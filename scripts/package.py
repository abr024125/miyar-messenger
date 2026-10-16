#!/usr/bin/env python3
"""Create a portable Xcode source bundle, excluding build products and signing files."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

root = Path(__file__).resolve().parent.parent
output = root.parent / 'MiyarMessenger-iOS.zip'
excluded = {'.git', '.build', 'build', '.swiftpm', 'DerivedData', 'xcuserdata', '__pycache__'}
files = [file for file in sorted(root.rglob('*')) if file.is_file()
         and not excluded.intersection(file.relative_to(root).parts)
         and file.suffix not in {'.p12', '.mobileprovision', '.xcuserstate', '.pyc'}
         and file.name != '.DS_Store']
with ZipFile(output, 'w', ZIP_DEFLATED, compresslevel=9) as archive:
    for file in files:
        archive.write(file, Path('MiyarMessenger') / file.relative_to(root))
with ZipFile(output) as archive:
    assert archive.testzip() is None
print(f'PASS: {len(files)} project files packaged; {output.stat().st_size} bytes. Source project, not an IPA.')
