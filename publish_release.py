import os
import sys
import json
import subprocess
import requests

def get_github_token():
    cred_input = "protocol=https\nhost=github.com\n"
    res = subprocess.run(["git", "credential", "fill"], input=cred_input, text=True, capture_output=True)
    for line in res.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    return None

def main():
    token = get_github_token()
    if not token:
        print("[ERROR] Could not retrieve GitHub token from git credential manager.")
        sys.exit(1)

    apk_path = os.path.abspath("app/build/outputs/apk/release/app-release.apk")
    if not os.path.exists(apk_path):
        print(f"[ERROR] APK file not found at {apk_path}")
        sys.exit(1)

    apk_size = os.path.getsize(apk_path)
    print(f"[INFO] Found release APK: {apk_path} ({apk_size / (1024*1024):.2f} MB)")

    proxies = {
        "http": "http://127.0.0.1:2080",
        "https": "http://127.0.0.1:2080"
    }

    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "CastoReleaseBot"
    }

    repo = "mogeol/casto"
    tag = "v1.1.0"
    release_name = "Casto v1.1.0"
    release_notes = """## 🌟 Casto v1.1.0 Release

A modern, high-performance ADB Remote & Low-Latency Screen Mirroring client for Android TV & Android devices.

### 🚀 What's New in v1.1.0:
- **📺 Apps Auto-Refresh & Pull-to-Refresh**: TV apps list automatically updates upon opening the tab and supports pull-down-to-refresh gestures.
- **📁 Custom Local Download Folder (SAF)**: Choose any local directory (Downloads, Documents, SD Card) using Android Storage Access Framework with persistent permissions.
- **❤️ Top Bar Support Shortcut**: Added quick-access heart button in top bar for project support and donations.
- **ℹ️ Independent About & Support Dialogs**: Clean separation of app information, open-source credits, and donation methods (Alipay & PayPal).
- **⚡ Video Decoder Stutter Fix**: Optimized frame rendering loop and 30 FPS cap for smooth scrcpy streaming on TV boxes.
- **🌐 Multi-Language Support**: Full UI localization in English, Simplified Chinese, Traditional Chinese, Spanish, Japanese, Russian, German, and French.

### 📦 Assets:
Download the signed APK below (`Casto-v1.1.0.apk`) and install it directly on your Android phone or tablet.
"""

    # Check if release already exists
    r = requests.get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers, proxies=proxies)
    if r.status_code == 200:
        release_data = r.json()
        release_id = release_data["id"]
        print(f"[INFO] Release {tag} already exists (ID: {release_id}). Updating...")
        update_payload = {
            "name": release_name,
            "body": release_notes,
            "draft": False,
            "prerelease": False
        }
        r_update = requests.patch(f"https://api.github.com/repos/{repo}/releases/{release_id}", json=update_payload, headers=headers, proxies=proxies)
        r_update.raise_for_status()
    else:
        print(f"[INFO] Creating new release for tag {tag}...")
        create_payload = {
            "tag_name": tag,
            "target_commitish": "main",
            "name": release_name,
            "body": release_notes,
            "draft": False,
            "prerelease": False
        }
        r_create = requests.post(f"https://api.github.com/repos/{repo}/releases", json=create_payload, headers=headers, proxies=proxies)
        r_create.raise_for_status()
        release_data = r_create.json()
        release_id = release_data["id"]
        print(f"[SUCCESS] Release created (ID: {release_id})")

    # Check existing assets and remove old Casto-v1.1.0.apk if present
    r_assets = requests.get(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", headers=headers, proxies=proxies)
    if r_assets.status_code == 200:
        for asset in r_assets.json():
            if asset["name"] == "Casto-v1.1.0.apk":
                print(f"[INFO] Deleting existing asset {asset['name']} (ID: {asset['id']})...")
                requests.delete(f"https://api.github.com/repos/{repo}/releases/assets/{asset['id']}", headers=headers, proxies=proxies)

    # Upload APK asset
    asset_name = "Casto-v1.1.0.apk"
    upload_url = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={asset_name}"
    upload_headers = {
        "Authorization": f"token {token}",
        "Content-Type": "application/vnd.android.package-archive",
        "User-Agent": "CastoReleaseBot"
    }

    print(f"[INFO] Uploading {asset_name} ({apk_size / (1024*1024):.2f} MB) to GitHub Releases...")
    with open(apk_path, "rb") as f:
        r_upload = requests.post(upload_url, headers=upload_headers, data=f, proxies=proxies)
        r_upload.raise_for_status()

    upload_data = r_upload.json()
    download_url = upload_data.get("browser_download_url")
    print(f"[SUCCESS] APK successfully uploaded!")
    print(f"[SUCCESS] Direct Download URL: {download_url}")
    print(f"[SUCCESS] Release Page: https://github.com/{repo}/releases/tag/{tag}")

if __name__ == "__main__":
    main()
