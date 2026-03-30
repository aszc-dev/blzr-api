FROM babashka/babashka:latest

WORKDIR /app

COPY bb.edn ./
COPY src/ src/
COPY resources/ resources/

ENV PORT=3001
ENV DB_PATH=/data/blzr.db

EXPOSE 3001

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD curl -f http://localhost:3001/health || exit 1

CMD ["bb", "-m", "server"]
