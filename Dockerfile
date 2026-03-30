FROM babashka/babashka:latest

WORKDIR /app

COPY bb.edn ./
COPY src/ src/
COPY resources/ resources/

# Pre-download the SQLite pod during build
RUN bb -e '(require (quote [babashka.pods :as pods])) (pods/load-pod (quote org.babashka/go-sqlite3) "0.1.0")'

ENV PORT=3001
ENV DB_PATH=/data/blzr.db

EXPOSE 3001

CMD ["bb", "-m", "server"]
