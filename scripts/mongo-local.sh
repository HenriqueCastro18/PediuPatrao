#!/usr/bin/env bash
set -e

VERSAO="7.0.14"
BUILD="mongodb-linux-x86_64-ubuntu2204-${VERSAO}"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$RAIZ/.mongo"
BIN="$DESTINO/$BUILD/bin/mongod"
DADOS="$DESTINO/data"

mkdir -p "$DESTINO" "$DADOS"

if [ ! -f "$BIN" ]; then
    echo "Baixando MongoDB $VERSAO..."
    wget -q "https://fastdl.mongodb.org/linux/${BUILD}.tgz" -O "$DESTINO/mongo.tgz"
    tar xzf "$DESTINO/mongo.tgz" -C "$DESTINO"
    rm -f "$DESTINO/mongo.tgz"
fi

echo "Iniciando MongoDB em mongodb://localhost:27017 (Ctrl+C para parar)"
exec "$BIN" --dbpath "$DADOS" --port 27017 --bind_ip 127.0.0.1
