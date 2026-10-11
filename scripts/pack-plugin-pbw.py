#!/usr/bin/env python3
"""Package a Pebble plugin project into a .pbw.

Run it with just the project directory; it works out what to do from what's there:

  * A watchapp project (has `src/c`) is built with the Pebble SDK (`pebble build`), then its plugin
    script + config page — the ones its appinfo.json names — are added, since the stock build
    webpacks pkjs into one file and bundles no other loose files.
  * A plugin-only project (no `src/c`) is packaged directly: appinfo.json is generated from
    package.json's `pebble` block and zipped with the script + config page it names. No SDK needed —
    there is no C to compile.

    pack-plugin-pbw.py <project-dir>

The pbw is written to <project-dir>/build/<name>.pbw (name from package.json).
"""

import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

# The appinfo rules the pbw is built for; absent means a legacy app.
MANIFEST_VERSION = 2

# Must match PermissionKind. Built-in permission names belong to the phone app, so whether a
# callerPermissions name is one is left to it.
PERMISSION_KINDS = {"read", "write", "exfiltrating"}
PERMISSION_NAME = re.compile(r"[A-Za-z][A-Za-z0-9]*")
# Every plugin's implicit permission (see PluginDefinedPermissions.kt).
RESERVED_PERMISSION_NAMES = {"InvokeAction"}


def permission_errors(plugin):
    """What `resolvePermissions` would reject the plugin's definitions for at load."""
    errors = []
    for d in plugin.get("definesPermissions", []):
        name = d.get("name", "")
        if not PERMISSION_NAME.fullmatch(name):
            errors.append(f"permission name '{name}' must be alphanumeric")
        if name in RESERVED_PERMISSION_NAMES:
            errors.append(f"permission name '{name}' is reserved")
        if not str(d.get("label", "")).strip():
            errors.append(f"permission '{name}' has no label")
        if d.get("kind") not in PERMISSION_KINDS:
            errors.append(f"permission '{name}' needs a kind: one of {', '.join(sorted(PERMISSION_KINDS))}")
    return errors


def check_plugin(appinfo):
    plugin = appinfo.get("plugin")
    if not isinstance(plugin, dict):
        return
    errors = permission_errors(plugin)
    if errors:
        sys.exit("bad plugin manifest:\n  " + "\n  ".join(errors))


def files_named_by(appinfo):
    """The loose files the host reads at runtime: the plugin script, and a file-based config page."""
    names = []
    plugin = appinfo.get("plugin")
    if isinstance(plugin, dict) and plugin.get("script"):
        names.append(plugin["script"])
    config = appinfo.get("configPage")
    if isinstance(config, str) and not config.startswith(("http://", "https://")):
        names.append(config)
    return names


def add_named_files(pbw, project_dir, appinfo):
    with zipfile.ZipFile(pbw) as z:
        existing = set(z.namelist())
    with zipfile.ZipFile(pbw, "a", zipfile.ZIP_DEFLATED) as z:
        for name in files_named_by(appinfo):
            if name in existing:
                continue
            path = os.path.join(project_dir, name)
            if not os.path.isfile(path):
                sys.exit(f"appinfo names '{name}' but {path} does not exist")
            z.write(path, name)
            print(f"added {name}")


def appinfo_from_package(pkg):
    """The appinfo shape `pebble build` writes, for the fields the host reads."""
    pebble = pkg.get("pebble", {})
    display = pebble.get("displayName") or pkg.get("name", "")
    appinfo = dict(pebble)
    appinfo["manifestVersion"] = MANIFEST_VERSION
    appinfo.setdefault("shortName", display)
    appinfo.setdefault("longName", display)
    appinfo.setdefault("companyName", pkg.get("author", ""))
    appinfo.setdefault("versionLabel", pkg.get("version", "1.0.0"))
    appinfo.setdefault("sdkVersion", "3")
    appinfo.setdefault("resources", {"media": []})
    appinfo.setdefault("watchapp", {"watchface": False})
    return appinfo


def build_watchapp(project_dir, pbw):
    if not shutil.which("pebble"):
        sys.exit("this project has a watchapp (src/c); install the Pebble SDK to build it")
    # waf's bundle step doesn't track appinfo.json, so delete the pbw to force a re-zip.
    if os.path.exists(pbw):
        os.remove(pbw)
    subprocess.run(["pebble", "build"], cwd=project_dir, check=True)
    with zipfile.ZipFile(pbw) as z:
        appinfo = json.loads(z.read("appinfo.json"))
        entries = [(info, z.read(info)) for info in z.infolist() if info.filename != "appinfo.json"]
    appinfo["manifestVersion"] = MANIFEST_VERSION
    check_plugin(appinfo)
    with zipfile.ZipFile(pbw, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("appinfo.json", json.dumps(appinfo, indent=2))
        for info, data in entries:
            z.writestr(info, data)
    add_named_files(pbw, project_dir, appinfo)


def create_plugin_only(project_dir, pbw, pkg):
    appinfo = appinfo_from_package(pkg)
    if not appinfo.get("uuid"):
        sys.exit("package.json pebble block needs a uuid")
    if not isinstance(appinfo.get("plugin"), dict):
        sys.exit("a plugin-only project needs a plugin in package.json's pebble block")
    check_plugin(appinfo)
    os.makedirs(os.path.dirname(pbw), exist_ok=True)
    with zipfile.ZipFile(pbw, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("appinfo.json", json.dumps(appinfo, indent=2))
    add_named_files(pbw, project_dir, appinfo)


def main(argv):
    if len(argv) != 2:
        sys.exit("usage: pack-plugin-pbw.py <project-dir>")
    project_dir = argv[1]
    with open(os.path.join(project_dir, "package.json")) as f:
        pkg = json.load(f)
    pbw = os.path.join(project_dir, "build", f"{pkg['name']}.pbw")

    if os.path.isdir(os.path.join(project_dir, "src", "c")):
        build_watchapp(project_dir, pbw)
    else:
        create_plugin_only(project_dir, pbw, pkg)
    print(f"packaged {pbw}")


if __name__ == "__main__":
    main(sys.argv)
