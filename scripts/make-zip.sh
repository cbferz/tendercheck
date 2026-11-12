#!/usr/bin/env bash
# Упаковывает репозиторий в tendercheck-pro.zip (рядом с папкой проекта).
# Служебные каталоги Gradle и IDE в архив не попадают; готовый dist/ включается, если он есть.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PARENT_DIR="$(dirname "$PROJECT_DIR")"
NAME="$(basename "$PROJECT_DIR")"

cd "$PARENT_DIR"
rm -f tendercheck-pro.zip
zip -r tendercheck-pro.zip "$NAME" \
  -x "$NAME/.gradle/*" "$NAME/.kotlin/*" "$NAME/build/*" "$NAME/.idea/*" "$NAME/*.iml" "$NAME/local.properties"

echo "Архив создан: $PARENT_DIR/tendercheck-pro.zip"
