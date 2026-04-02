# ── Build stage ──────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app

# Build tools needed for better-sqlite3 native bindings (compile in builder, not runner)
RUN apk add --no-cache python3 make g++

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Prune dev dependencies
RUN npm prune --production

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM node:22-alpine AS runner
WORKDIR /app

# Copy only what's needed — no build tools in final image
COPY --from=builder /app/build ./build
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/migrations ./migrations
COPY --from=builder /app/package.json ./package.json

# Persistent data directory
RUN mkdir -p /data

ENV DATABASE_PATH=/data/chronicals.db
ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:3000/api/health || exit 1

CMD ["node", "--max-old-space-size=200", "build"]
