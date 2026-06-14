#!/usr/bin/env bash
set -euo pipefail

DEPLOY_HOST="${DEPLOY_HOST:?Set DEPLOY_HOST}"
DEPLOY_USER="${DEPLOY_USER:?Set DEPLOY_USER}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/kkalscan}"

./gradlew shadowJar --no-daemon
JAR=$(ls build/libs/*-all.jar | head -1)

scp "$JAR" "${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/app.jar"
ssh "${DEPLOY_USER}@${DEPLOY_HOST}" "cd ${DEPLOY_PATH} && docker compose up -d --build"

echo "Deployed to ${DEPLOY_HOST}"
