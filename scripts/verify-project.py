#!/usr/bin/env python3
"""Validate the standalone project structure; this does not compile Swift."""
import json
import plistlib
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def read_project(path):
    pattern = r'/\*[\s\S]*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|[{}()=;,]|[^\s{}()=;,]+'
    tokens = [m.group() for m in re.finditer(pattern, path.read_text()) if not m.group().startswith(('//', '/*'))]
    position = 0

    def take(expected=None):
        nonlocal position
        value = tokens[position]
        position += 1
        if expected is not None and value != expected:
            raise ValueError(f'Expected {expected}, found {value}')
        return value

    def value():
        token = take()
        if token == '{':
            result = {}
            while tokens[position] != '}':
                key = take()
                key = json.loads(key) if key.startswith('"') else key
                take('=')
                if key in result:
                    raise ValueError(f'Duplicate project key: {key}')
                result[key] = value()
                take(';')
            take('}')
            return result
        if token == '(':
            result = []
            while tokens[position] != ')':
                result.append(value())
                if tokens[position] == ',':
                    take(',')
                elif tokens[position] != ')':
                    raise ValueError('Missing array separator')
            take(')')
            return result
        if token.startswith('"'):
            return json.loads(token)
        return int(token) if token.isdigit() else token

    result = value()
    if position != len(tokens):
        raise ValueError('Unexpected project data')
    return result


def verify():
    project = read_project(ROOT / 'MiyarMessenger.xcodeproj/project.pbxproj')
    objects = project['objects']
    native = objects[project['rootObject']]
    assert native['isa'] == 'PBXProject'
    targets = [objects[identifier] for identifier in native['targets']]
    assert len(targets) == 1 and targets[0]['name'] == 'MiyarMessenger'
    resolved = {}

    def walk(identifier, parent):
        obj = objects[identifier]
        if obj['isa'] == 'PBXGroup':
            directory = parent / obj.get('path', '')
            for child in obj.get('children', []):
                walk(child, directory)
        elif obj['isa'] == 'PBXFileReference' and obj.get('sourceTree') != 'BUILT_PRODUCTS_DIR':
            file = parent / obj['path']
            assert file.exists(), f'Missing project file: {file}'
            resolved[identifier] = file.resolve()

    walk(native['mainGroup'], ROOT)
    for obj in objects.values():
        for key in ['fileRef', 'buildConfigurationList', 'productReference', 'mainGroup', 'productRefGroup']:
            if key in obj:
                assert obj[key] in objects, f'Missing object {key}: {obj[key]}'
        for key in ['children', 'buildPhases', 'buildConfigurations', 'targets', 'dependencies', 'files']:
            for identifier in obj.get(key, []):
                assert identifier in objects, f'Missing {key}: {identifier}'

    phases = [objects[identifier] for identifier in targets[0]['buildPhases']]
    sources = [resolved[objects[identifier]['fileRef']] for phase in phases if phase['isa'] == 'PBXSourcesBuildPhase' for identifier in phase['files']]
    assert len(sources) == len(set(sources)), 'Duplicate Swift build entries'
    assert set(sources) == {p.resolve() for p in (ROOT / 'MiyarMessenger').rglob('*.swift')}, 'Swift sources missing from build phase'
    resources = [resolved[objects[identifier]['fileRef']] for phase in phases if phase['isa'] == 'PBXResourcesBuildPhase' for identifier in phase['files']]
    info = plistlib.loads((ROOT / 'MiyarMessenger/Resources/Info.plist').read_bytes())
    assert info['CFBundleIdentifier'] == '$(PRODUCT_BUNDLE_IDENTIFIER)'
    assert all(info.get(key) for key in ['NSCameraUsageDescription', 'NSMicrophoneUsageDescription', 'NSPhotoLibraryUsageDescription'])
    for font in info['UIAppFonts']:
        assert (ROOT / 'MiyarMessenger/Resources' / font).resolve() in resources
    privacy = ROOT / 'MiyarMessenger/Resources/PrivacyInfo.xcprivacy'
    assert privacy.resolve() in resources
    assert plistlib.loads(privacy.read_bytes())['NSPrivacyTracking'] is False
    assert (ROOT / 'MiyarMessenger/Resources/Info.plist').resolve() not in resources
    for file in (ROOT / 'MiyarMessenger/Resources/Assets.xcassets').rglob('Contents.json'):
        asset = json.loads(file.read_text())
        for entry in asset.get('images', []):
            if 'filename' in entry:
                assert (file.parent / entry['filename']).is_file()
    icon = (ROOT / 'MiyarMessenger/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png').read_bytes()
    assert icon[:8] == b'\x89PNG\r\n\x1a\n'
    width, height, depth, color = struct.unpack('>IIBB', icon[16:26])
    assert (width, height, depth, color) == (1024, 1024, 8, 2), 'App icon must be opaque RGB 1024px'
    scheme = ET.parse(ROOT / 'MiyarMessenger.xcodeproj/xcshareddata/xcschemes/MiyarMessenger.xcscheme')
    for reference in scheme.findall('.//BuildableReference'):
        assert objects[reference.attrib['BlueprintIdentifier']]['isa'] == 'PBXNativeTarget'
    for name in ['inbox', 'thread', 'contacts', 'login']:
        data = json.loads((ROOT / 'Tests/CoreTests/Fixtures' / (name + '.json')).read_text())
        assert isinstance(data, dict)
    print(f'PASS: project graph, {len(sources)} Swift build inputs, resources, plists, scheme, icon and 4 API fixtures. Swift compilation requires Xcode.')


if __name__ == '__main__':
    try:
        verify()
    except (AssertionError, KeyError, ValueError, IndexError, OSError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        sys.exit(1)
