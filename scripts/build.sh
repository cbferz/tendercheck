#!/usr/bin/env bash
# Полная сборка TenderCheck: тесты, продакшен-бандл Kotlin/JS и копирование готового сайта в ./dist
# Требования: JDK 17+ и доступ в интернет при первой сборке (Gradle скачает Kotlin, Node.js и Yarn).
set -euo pipefail

# Работаем из корня проекта независимо от того, откуда запущен скрипт
cd "$(dirname "$0")/.."

echo "==> Сборка и тесты"
./gradlew build jsBrowserDistribution

# В Kotlin 2.0 дистрибутив лежит в build/dist/js/productionExecutable
SRC="build/dist/js/productionExecutable"
if [ ! -f "$SRC/index.html" ]; then
  echo "Дистрибутив не найден в $SRC, ищу в build/dist..." >&2
  INDEX="$(find build/dist -name index.html -print -quit 2>/dev/null || true)"
  if [ -z "$INDEX" ]; then
    echo "Ошибка: index.html не найден в build/dist" >&2
    exit 1
  fi
  SRC="$(dirname "$INDEX")"
fi

echo "==> Копирование $SRC -> dist/"
rm -rf dist
mkdir -p dist
cp -R "$SRC"/. dist/

echo
echo "Готово. Статический сайт лежит в папке dist/:"
ls -1 dist
echo
echo "Проверить локально:  cd dist && python3 -m http.server 8080  (затем http://localhost:8080)"
