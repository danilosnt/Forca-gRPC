#!/usr/bin/env bash
# Deploy do jogo da forca na VPS. Instalado em /usr/local/bin/forca-deploy.
# A chave SSH do GitHub Actions só consegue executar este script.
set -euo pipefail

REPOSITORIO="https://github.com/danilosnt/sis-dis.git"
PASTA="/opt/forca"
RAMO="main"

exec 9>/var/lock/forca-deploy.lock
flock 9

cd "$PASTA"
[ -d .git ] || git init -q
git remote remove origin 2>/dev/null || true
git remote add origin "$REPOSITORIO"

git fetch -q --depth 1 origin "$RAMO"
git reset -q --hard FETCH_HEAD
echo "Versão: $(git log -1 --format='%h %s')"

docker compose up -d --build --remove-orphans
docker image prune -f > /dev/null
docker compose ps
