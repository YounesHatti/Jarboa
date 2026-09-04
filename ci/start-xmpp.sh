#!/usr/bin/env bash
set -euo pipefail
# This script runs only on disposable GitHub Actions runners.
sudo apt-get update -qq
sudo apt-get install -y prosody
sudo systemctl stop prosody
RUNTIME="$(mktemp -d /tmp/jarboa-xmpp.XXXXXX)"
mkdir -p "$RUNTIME/data" app/src/runtimeCheck/res/raw
openssl req -x509 -newkey rsa:2048 -nodes -days 2 \
  -keyout "$RUNTIME/server.key" -out "$RUNTIME/server.crt" \
  -subj '/CN=jarboa.test' -addext 'subjectAltName=DNS:jarboa.test' \
  -addext 'basicConstraints=critical,CA:TRUE'
cp "$RUNTIME/server.crt" app/src/runtimeCheck/res/raw/ci_ca.pem
cat > "$RUNTIME/prosody.cfg.lua" <<EOF
daemonize = false
pidfile = "$RUNTIME/prosody.pid"
data_path = "$RUNTIME/data"
log = { { levels = { min = "info" }; to = "console" } }
interfaces = { "0.0.0.0" }
c2s_ports = { 5222 }
s2s_ports = { }
modules_enabled = { "roster"; "saslauth"; "tls"; "disco"; "pep"; "smacks"; "ping"; }
authentication = "internal_hashed"
c2s_require_encryption = true
ssl = { key = "$RUNTIME/server.key"; certificate = "$RUNTIME/server.crt"; }
VirtualHost "jarboa.test"
EOF
sudo chown -R prosody:prosody "$RUNTIME"
sudo cp "$RUNTIME/prosody.cfg.lua" /etc/prosody/prosody.cfg.lua
sudo -u prosody prosodyctl register alice jarboa.test ci-alice-password
sudo -u prosody prosodyctl register bob jarboa.test ci-bob-password
sudo -u prosody prosody > ci/prosody.log 2>&1 &
for attempt in {1..30}; do
  if (echo > /dev/tcp/127.0.0.1/5222) 2>/dev/null; then exit 0; fi
  sleep 1
done
cat ci/prosody.log
exit 1
