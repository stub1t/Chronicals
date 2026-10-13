# ── Build stage ──────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app

RUN apk add --no-cache python3 make g++

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build
RUN npm prune --production

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM node:22-alpine AS runner
WORKDIR /app

COPY --from=builder /app/build ./build
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/migrations ./migrations
COPY --from=builder /app/package.json ./package.json

RUN mkdir -p /data /tmp && chown -R node:node /app /data /tmp

ENV DATABASE_PATH=/data/chronicals.db
ENV NODE_ENV=production
ENV PORT=3000
# Allow backup uploads (POST /api/backup) up to the 50 MB limit
ENV BODY_SIZE_LIMIT=50M

USER node

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/api/health || exit 1

CMD ["node", "--max-old-space-size=200", "build"]
