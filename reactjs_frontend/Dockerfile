# ── Stage 1: Build ────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Cache deps layer
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

# Build
COPY . .
RUN npm run build

# ── Stage 2: Serve ────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
