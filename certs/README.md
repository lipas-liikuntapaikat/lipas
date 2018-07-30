# SSL Certificates

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
