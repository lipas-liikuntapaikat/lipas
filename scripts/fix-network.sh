#!/bin/bash
# fix-network.sh — Restore network after OpenConnect crash/ungraceful death
#
# Usage: sudo bash scripts/fix-network.sh
#   or:  bb fix-network
set -euo pipefail

echo "Diagnosing network state..."
echo ""

# Detect active network service and interface
IFACE=$(route -n get default 2>/dev/null | awk '/interface:/{print $2}' || true)
if [ -n "$IFACE" ]; then
  SERVICE=$(networksetup -listnetworkserviceorder \
    | grep -B1 "$IFACE" | head -1 | sed 's/^([0-9]*) //')
  GW=$(route -n get default 2>/dev/null | awk '/gateway:/{print $2}' || true)
  echo "Current default: interface=$IFACE service=$SERVICE gateway=$GW"
else
  echo "No default route found. Will attempt full reset."
  SERVICE="Wi-Fi"
fi

echo ""

# 1. Kill any lingering openconnect processes
if pgrep -x openconnect >/dev/null 2>&1; then
  echo "Killing lingering openconnect processes..."
  sudo killall -9 openconnect 2>/dev/null || true
  sleep 1
fi

# 2. Bring down stale utun interfaces
echo "Checking utun interfaces..."
for iface in $(ifconfig -l | tr ' ' '\n' | grep utun); do
  if ifconfig "$iface" 2>/dev/null | grep -q "inet "; then
    echo "  Bringing down $iface"
    sudo ifconfig "$iface" down 2>/dev/null || true
  fi
done

# 3. Restore default route from vpnc state file or auto-detect
if ls /var/run/vpnc/defaultroute.* 1>/dev/null 2>&1; then
  SAVED_GW=$(cat /var/run/vpnc/defaultroute.* 2>/dev/null | head -1)
  echo "Found saved gateway: $SAVED_GW"
  sudo route -n delete default 2>/dev/null || true
  sudo route -n add default "$SAVED_GW"
  sudo rm -f /var/run/vpnc/defaultroute.*
  sudo rm -f /var/run/vpnc/defaultroute_ipv6.*
  echo "  Default route restored -> $SAVED_GW"
else
  echo "No saved gateway found in /var/run/vpnc/"
  echo "  If you have no connectivity, run:"
  echo "    sudo route -n delete default; sudo route -n add default <your-router-ip>"
fi

# 4. Remove VPN host route
VPN_GW_IP=$(dig +short vpn.jyu.fi 2>/dev/null | head -1)
if [ -n "$VPN_GW_IP" ]; then
  sudo route -n delete -host "$VPN_GW_IP" 2>/dev/null || true
  echo "  Removed host route to $VPN_GW_IP"
fi

# 5. Clear scutil DNS/IPv4 entries for all utun interfaces
echo "Clearing scutil VPN entries..."
for iface in $(ifconfig -l | tr ' ' '\n' | grep utun); do
  sudo scutil <<-SCUTIL 2>/dev/null || true
		open
		remove State:/Network/Service/${iface}/DNS
		remove State:/Network/Service/${iface}/IPv4
		close
	SCUTIL
done

# 6. Reset DNS on active network service
echo "Resetting DNS on $SERVICE..."
sudo networksetup -setdnsservers "$SERVICE" Empty 2>/dev/null || true

# 7. Restore /etc/resolv.conf from backup
for f in /var/run/vpnc/resolv.conf-backup.*; do
  if [ -f "$f" ]; then
    sudo cp "$f" /etc/resolv.conf
    sudo rm -f "$f"
    echo "  Restored /etc/resolv.conf from backup"
    break
  fi
done

# 8. Clean up remaining vpnc state files
sudo rm -f /var/run/vpnc/defaultroute.* 2>/dev/null || true
sudo rm -f /var/run/vpnc/defaultroute_ipv6.* 2>/dev/null || true
sudo rm -f /var/run/vpnc/resolv.conf-backup.* 2>/dev/null || true

# 9. Flush DNS cache
echo "Flushing DNS cache..."
sudo dscacheutil -flushcache 2>/dev/null || true
sudo killall -HUP mDNSResponder 2>/dev/null || true

# 10. Verify connectivity
echo ""
echo "Testing connectivity..."
if ping -c 1 -W 2 1.1.1.1 >/dev/null 2>&1; then
  echo "  IP connectivity: OK"
else
  echo "  IP connectivity: FAILED"
  echo "  Try: sudo networksetup -setv4off $SERVICE && sudo networksetup -setdhcp $SERVICE"
fi

if ping -c 1 -W 2 google.com >/dev/null 2>&1; then
  echo "  DNS resolution:  OK"
else
  echo "  DNS resolution:  FAILED (IP may work — DNS still broken)"
fi

echo ""
echo "Done. If still broken, the nuclear option is:"
echo "  sudo networksetup -setv4off \"$SERVICE\" && sudo networksetup -setdhcp \"$SERVICE\""
