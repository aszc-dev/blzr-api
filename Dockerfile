FROM babashka/babashka:latest

WORKDIR /app

COPY bb.edn ./
COPY src/ src/
COPY resources/ resources/

ENV PORT=3001
ENV DB_PATH=/data/blzr-data.edn

EXPOSE 3001

CMD ["bb", "-m", "server"]
