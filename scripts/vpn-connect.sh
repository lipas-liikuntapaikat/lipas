#!/bin/bash
# vpn-connect.sh — Connect to JYU VPN with robust cleanup on any exit
set -euo pipefail

# Snapshot network state before connecting
snapshot_network() {
  ORIG_GW=$(route -n get default 2>/dev/null | awk '/gateway:/{print $2}')
  ORIG_SERVICE=$(networksetup -listnetworkserviceorder \
    | grep -B1 "$(route -n get default 2>/dev/null | awk '/interface:/{print $2}')" \
    | head -1 | sed 's/^([0-9]*) //')
  echo "Pre-VPN state: gateway=$ORIG_GW service=$ORIG_SERVICE"
}

# Restore network to pre-VPN state
restore_network() {
  echo ""
  echo "Restoring network..."

  # 1. Restore default route
  if [ -n "${ORIG_GW:-}" ]; then
    sudo route -n delete default 2>/dev/null || true
    sudo route -n add default "$ORIG_GW" 2>/dev/null || true
    echo "  Default route -> $ORIG_GW"
  fi

  # 2. Remove any VPN host route to the gateway
  VPN_GW_IP=$(dig +short vpn.jyu.fi 2>/dev/null | head -1)
  if [ -n "$VPN_GW_IP" ]; then
    sudo route -n delete -host "$VPN_GW_IP" 2>/dev/null || true
  fi

  # 3. Clear DNS overrides
  if [ -n "${ORIG_SERVICE:-}" ]; then
    sudo networksetup -setdnsservers "$ORIG_SERVICE" Empty 2>/dev/null || true
    echo "  DNS reset on $ORIG_SERVICE"
  fi

  # 4. Remove stale scutil DNS/IPv4 entries for utun interfaces
  for iface in $(ifconfig -l | tr ' ' '\n' | grep utun); do
    sudo scutil <<-SCUTIL 2>/dev/null || true
		open
		remove State:/Network/Service/${iface}/DNS
		remove State:/Network/Service/${iface}/IPv4
		close
	SCUTIL
  done

  # 5. Restore /etc/resolv.conf from vpnc backup if it exists
  for f in /var/run/vpnc/resolv.conf-backup.*; do
    if [ -f "$f" ]; then
      sudo cp "$f" /etc/resolv.conf
      sudo rm -f "$f"
      echo "  Restored /etc/resolv.conf from backup"
      break
    fi
  done

  # 6. Clean up vpnc state files
  sudo rm -f /var/run/vpnc/defaultroute.* 2>/dev/null || true
  sudo rm -f /var/run/vpnc/defaultroute_ipv6.* 2>/dev/null || true

  # 7. Flush DNS cache
  sudo dscacheutil -flushcache 2>/dev/null || true
  sudo killall -HUP mDNSResponder 2>/dev/null || true

  echo "Network restored."
}

cleanup() {
  # Send SIGINT (not SIGTERM!) to openconnect for graceful disconnect
  if [ -n "${OC_PID:-}" ] && sudo kill -0 "$OC_PID" 2>/dev/null; then
    echo ""
    echo "Sending SIGINT to openconnect (pid $OC_PID) for graceful teardown..."
    sudo kill -INT "$OC_PID" 2>/dev/null || true
    # Wait up to 5 seconds for graceful shutdown
    for i in $(seq 1 10); do
      if ! sudo kill -0 "$OC_PID" 2>/dev/null; then
        echo "OpenConnect exited gracefully."
        return
      fi
      sleep 0.5
    done
    # Force kill if it didn't exit
    echo "OpenConnect didn't exit in time, forcing..."
    sudo kill -9 "$OC_PID" 2>/dev/null || true
    wait "$OC_PID" 2>/dev/null || true
  fi

  # Safety net: restore network even if openconnect cleanup failed
  restore_network
}

# --- Main ---

USER=${1:?Usage: vpn-connect.sh <username>}

snapshot_network

PW=$(security find-generic-password -s jyu-vpn -a "$USER" -w)

# Trap all exit signals — translate to SIGINT for openconnect
trap cleanup EXIT

echo "$PW
push
" | sudo openconnect --protocol=anyconnect \
  --user="$USER" --usergroup=appdevel \
  --no-xmlpost --passwd-on-stdin vpn.jyu.fi &
OC_PID=$!

echo -e "\n\033[1;33m>>> Verify push in Duo Mobile after POST https://vpn.jyu.fi/+webvpn+/index.html <<<\033[0m\n"

# Wait for openconnect — if it exits on its own (server disconnect, timeout),
# the EXIT trap will still fire and run cleanup
wait "$OC_PID" 2>/dev/null || true
