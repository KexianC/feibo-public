#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE="certbot/certbot:latest"
LETSENCRYPT_DIR="/etc/letsencrypt"
WEBROOT="/var/lib/feibo-acme"
NGINX="/www/server/nginx/sbin/nginx"

for cert_name in dd.hzmwmt.com manage.hzmwmt.com; do
  /usr/bin/docker run --rm \
    -v "$LETSENCRYPT_DIR:/etc/letsencrypt" \
    -v "$WEBROOT:/var/lib/feibo-acme" \
    "$IMAGE" renew --quiet --cert-name "$cert_name"
done

"$NGINX" -t
"$NGINX" -s reload
