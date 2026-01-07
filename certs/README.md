# SSL Certificate Management

This folder is placeholder for SSL-certificates. Place real (prod) or
self-signed (dev, test) certificate and private key into this folder with names:

* server.key
* server.crt

Files in this folder will be mounted into nginx `proxy` container and
nginx is configured to use the certs.

**NEVER PUSH CERTIFICATES INTO VERSION CONTROL**. This should be
handled by `.gitignore` in project root.

## Generating Self-Signed Certificate (Development)

```bash
openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
    -subj "/C=FI/ST=Dev/L=Local/O=LIPAS/CN=localhost" \
    -keyout certs/server.key -out certs/server.crt
```

## Certificate Structure

Our SSL implementation uses a certificate chain stored in the file `server.crt`. This file contains multiple certificate blocks:

1. **Server Certificate** - The first certificate block is our domain certificate for `*.liikuntapaikat.fi` (also covers `*.lipas.fi`)
   - Renewed annually
   - This certificate authenticates our domains

2. **Intermediate Certificate(s)** - One or more CA certificates that establish trust
   - These chain our server certificate to a trusted root CA
   - May change when JYU switches certificate providers

**Note:** The CA chain can change between renewals. Check the issuer of the new certificate to determine if you need a new intermediate.

## Renewing Production Certificates

JYU IT-support renews the certificates when they're about to expire and usually they place them on the lipas-prod.cc.jyu.fi server's `/root` folder. Typically only the "leaf level" certificate is renewed (yearly). Intermediate certificates usually have longer life span.

### Annual Certificate Renewal Process

1. **Certificate Delivery**
   - JYU IT delivers the new certificate to `/root/` on the production server
   - IT department sends an email notification when the certificate is available
   - **Important:** The email may mention a new CA file if the certificate provider changed

2. **Certificate Inspection**
   - Inspect the new certificate:
     ```bash
     openssl x509 -in /root/NEW_CERT.crt -noout -subject -issuer -dates
     ```
   - Inspect the current certificate chain:
     ```bash
     awk -v cmd='openssl x509 -noout -subject -issuer' '/BEGIN/{close(cmd)};{print | cmd}' < server.crt
     ```
   - **Check if the issuer changed** - compare the new cert's issuer with the current intermediate's subject

3. **Determine Renewal Type**

   **Same CA (issuer matches current intermediate):**
   - You can reuse the existing intermediate certificate
   - Extract it: `awk 'BEGIN {c=0;} /BEGIN CERT/{c++} c==2' server.crt > intermediate.crt`

   **New CA (issuer is different):**
   - JYU IT should provide a new intermediate/CA file (check `/etc/httpd/conf/` or ask IT)
   - You must use the new CA file, not the old intermediate

4. **Certificate Replacement**
   ```bash
   # Backup current certificate
   cp server.crt backups/server.crt.backup_$(date +%Y%m%d)

   # Build new chain (leaf cert first, then intermediate(s))
   cat /root/NEW_CERT.crt > server.crt.new
   cat INTERMEDIATE.crt >> server.crt.new

   # Verify chain links correctly (each issuer should match next subject)
   awk -v cmd='openssl x509 -noout -subject -issuer' '/BEGIN/{close(cmd)};{print | cmd}' < server.crt.new

   # Replace
   mv server.crt.new server.crt
   ```

5. **Service Restart**
   ```bash
   cd /var/lipas && docker compose restart proxy
   ```

6. **Verification**
   ```bash
   # Test locally
   echo | openssl s_client -connect localhost:443 -servername lipas.fi 2>/dev/null | openssl x509 -noout -dates

   # Verify in browser (may need hard refresh: Ctrl+Shift+R)
   ```
   - Check https://lipas.fi certificate details
   - Confirm the new expiration date is displayed

### Important Notes

- **REPLACE, DON'T APPEND** - Always replace the first certificate block; do not add the new certificate to the end of the file
- **MAINTAIN THE ORDER** - The server certificate must be first, followed by the intermediate certificate
- **BACKUP FIRST** - Always create a backup before making changes
- **PRIVATE KEY** - The private key (`server.key`) typically remains the same across renewals

The bundle (server.crt) can be built with simple concatenation. Chunks (individual certificates in a bundle) are annotated by:

```text
-----BEGIN CERTIFICATE-----
... certificate contents ....
-----END CERTIFICATE-----
```

### Troubleshooting

If SSL issues occur after renewal:
1. Check certificate chain order (leaf cert must be first)
2. Verify issuer/subject chain links correctly (each cert's issuer = next cert's subject)
3. Ensure the server certificate is not expired
4. Confirm NGINX has been restarted (`docker compose restart proxy`)
5. Browser still shows old cert? Hard refresh (Ctrl+Shift+R) or try incognito
6. If needed, restore from backup: `cp backups/server.crt.backup_YYYYMMDD server.crt`
