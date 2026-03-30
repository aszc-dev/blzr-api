FROM babashka/babashka:latest

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY bb.edn ./
COPY src/ src/
COPY resources/ resources/

# Pre-download the SQLite pod during build
RUN bb -e '(require (quote [babashka.pods :as pods])) (pods/load-pod (quote org.babashka/go-sqlite3) "0.1.0")'

ENV PORT=3001
ENV DB_PATH=/data/blzr.db

EXPOSE 3001

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD curl -f http://localhost:3001/health || exit 1

CMD ["bb", "-m", "server"]
