#!/bin/sh
# Ishlatish: scripts/set-contact-email.sh sizning@email.uz
set -e
[ -n "$1" ] || { echo "Email kiriting: $0 sizning@email.uz"; exit 1; }
cd "$(dirname "$0")/.."
sed -i "s/CONTACT_EMAIL_HERE/$1/g" docs/privacy.html
sed -i "s/CONTACT_EMAIL_HERE/$1/g" PLAY_CONSOLE_LISTING.md
echo "Tayyor: $1"
