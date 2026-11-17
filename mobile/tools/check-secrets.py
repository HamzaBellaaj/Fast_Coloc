"""Compare configured server secrets with Android sources and decompressed APK; print no values."""
from pathlib import Path
from zipfile import ZipFile

workspace = Path(__file__).resolve().parents[2]
configuration = workspace / "backend/.env.local"
secrets = []
for line in configuration.read_text(encoding="utf-8-sig").splitlines():
    if "=" not in line or line.lstrip().startswith("#"):
        continue
    key, value = line.split("=", 1)
    value = value.strip()
    if ("PASSWORD" in key or "SECRET_KEY" in key) and len(value) >= 8:
        secrets.extend([value.encode("utf-8"), value.encode("utf-16le")])
if not secrets:
    raise SystemExit("No configured secret available for comparison.")

def check(name, data):
    if any(value in data for value in secrets):
        raise SystemExit("Configured secret detected in: " + name)

count = 0
for root in [workspace / "mobile/app/src", workspace / "mobile/gradle"]:
    for file in root.rglob("*"):
        if file.is_file() and file.suffix in [".kt", ".xml", ".toml", ".kts", ".properties"]:
            check(str(file.relative_to(workspace)), file.read_bytes())
            count += 1
apk = workspace / "mobile/app/build/outputs/apk/debug/app-debug.apk"
with ZipFile(apk) as archive:
    for entry in archive.infolist():
        if not entry.is_dir():
            check("APK:" + entry.filename, archive.read(entry))
            count += 1
print(f"PASS: {count} source/APK entries checked; no configured server secret found.")
