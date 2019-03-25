FROM alpine

RUN apk add --update apache2-utils \
&& rm -rf /var/cache/apk/*

ENTRYPOINT ["htpasswd", "-mbn"]
