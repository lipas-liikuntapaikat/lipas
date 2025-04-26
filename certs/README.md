# SSL Certificate Management

This folder is placeholder for SSL-certificates. Place real (prod) or
self-signed (dev, test) certificate and private key into this folder with names:

* server.key
* server.crt

Files in this folder will be mounted into nginx `proxy` container and
nginx is configured to use the certs.

**NEVER PUSH CERTIFICATES INTO VERSION CONTROL**. This should be
handled by `.gitignore` in project root.

Instructions for generating self-signed ssl-certificate can be found
[here](https://devcenter.heroku.com/articles/ssl-certificate-self).

## Certificate Structure

Our SSL implementation uses a certificate chain stored in the file `server.crt`. This file contains two certificate blocks:

1. **Server Certificate** - The first certificate block is our domain certificate for `*.liikuntapaikat.fi` (also covers `*.lipas.fi`)
   - Issued by: GEANT OV RSA CA 4
   - Renewed annually
   - This certificate authenticates our domains

2. **Intermediate Certificate** - The second certificate block is the CA certificate
   - Subject: GEANT OV RSA CA 4
   - Issuer: USERTrust RSA Certification Authority
   - Long-term validity (expires May 1, 2033)
   - This certificate establishes trust between our server certificate and root CAs

## Renewing Production Certificates

JYU IT-support renews the certificates when they're about to expire and usually they place them on the lipas-prod.cc.jyu.fi server's `/root` folder. Typically only the "leaf level" certificate is renewed (yearly). Intermediate certificates usually have longer life span.

### Annual Certificate Renewal Process

1. **Certificate Delivery**
   - JYU IT delivers the new certificate to `/root/server_[YEAR].crt` on our production server
   - IT department sends an email notification when the certificate is available

2. **Certificate Inspection**
   - Verify the new certificate before installation:
     ```bash
     awk -v cmd='openssl x509 -noout -subject -issuer -dates' '/BEGIN/{close(cmd)};{print | cmd}' < /root/server_[YEAR].crt
     ```
   - Or examine the full details:
     ```bash
     openssl x509 -in /root/server_[YEAR].crt -text
     ```
   - Confirm it's for `*.liikuntapaikat.fi` (should also include `*.lipas.fi`) and check the validity dates

3. **Certificate Replacement**
   - Create a backup of the current certificate:
     ```bash
     cp server.crt server.crt.backup_$(date +%Y%m%d)
     ```

   - Extract the intermediate certificate from the current file:
     ```bash
     awk 'BEGIN {c=0;} /BEGIN CERT/{c++} c==2' server.crt > intermediate.crt
     ```

   - Create the new certificate file with proper chain order:
     ```bash
     cat /root/server_[YEAR].crt > new_server.crt
     cat intermediate.crt >> new_server.crt
     ```

   - Replace the current certificate file:
     ```bash
     mv new_server.crt server.crt
     ```

4. **Service Restart**
   - Restart NGINX to apply the new certificate:
     ```bash
     docker compose restart proxy
     ```

5. **Verification**
   - Verify the website is accessible via HTTPS
   - Check the certificate details in a browser
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
1. Check certificate chain order in the file
2. Verify there are exactly two certificate blocks
3. Ensure the server certificate is not expired
4. Confirm NGINX has been restarted
5. If needed, restore from backup and retry the process
