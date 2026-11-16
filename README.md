# Atmosphere
Spatial music player (landscape + AR passthrough, file import, haptics, bounce animations).
GitHub Actions builds the APK automatically on every push.

## Publish from Termux
    pkg update -y && pkg install -y git unzip
    termux-setup-storage
    cd ~/storage/downloads
    unzip Atmosphere-repo.zip -d Atmosphere && cd Atmosphere
    git init -b main
    git config user.name "YOUR_NAME"
    git config user.email "YOUR_EMAIL"
    git add . && git commit -m "Atmosphere initial release"
    git remote add origin https://github.com/YOUR_USER/Atmosphere.git
    git push -u origin main
(Create an empty repo named Atmosphere on github.com first. When asked for a password, paste a Personal Access Token with "repo" + "workflow" scope.)

## Get the APK
GitHub repo > Actions > latest "Build Atmosphere APK" run > Artifacts > Atmosphere-APK.
For a Release page: `git tag v1.0.0 && git push origin v1.0.0` — the APK is attached automatically.
Install it on your phone (allow "install unknown apps").
