# nginx + ngx_brotli dynamic modules.
#
# The official nginx image ships no brotli support and nginx.org provides no
# prebuilt package for it, so we compile google/ngx_brotli as dynamic modules
# in a throwaway build stage and copy the two .so files into an otherwise
# stock image. Both stages use the same base tag, so the modules are always
# built against the exact nginx version they get loaded into (--with-compat
# guarantees binary compatibility).
#
# nginx.conf loads the modules; the brotli/brotli_static directives live in
# the proxy*.conf server blocks. Rebuild with: docker compose build proxy
# (or proxy-dev / proxy-local — all three share this image).

FROM nginx:stable AS builder

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates cmake gcc git libc6-dev libpcre2-dev make wget zlib1g-dev \
 && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --recurse-submodules --shallow-submodules \
      https://github.com/google/ngx_brotli /usr/src/ngx_brotli

# Prebuild the bundled brotli library (static) for the module to link against
RUN cd /usr/src/ngx_brotli/deps/brotli \
 && mkdir out && cd out \
 && cmake -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF .. \
 && cmake --build . --config Release --target brotlienc

# NGINX_VERSION comes from the base image, so the module source always
# matches the binary that will load it
RUN wget -q "https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz" -O- \
      | tar -xz -C /usr/src \
 && cd "/usr/src/nginx-${NGINX_VERSION}" \
 && ./configure --with-compat --add-dynamic-module=/usr/src/ngx_brotli \
 && make modules

FROM nginx:stable

COPY --from=builder /usr/src/nginx-*/objs/ngx_http_brotli_filter_module.so \
                    /usr/src/nginx-*/objs/ngx_http_brotli_static_module.so \
                    /usr/lib/nginx/modules/
