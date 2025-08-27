#!/bin/bash

# Корень проекта (поменяй при необходимости)
ROOT="src/main/java"

OLD="ru/hollowhorizon/hc"
NEW="ru/hollowhorizon/hollowengine"

OLD_PATH="$ROOT/$(echo $OLD | tr '.' '/')"
NEW_PATH="$ROOT/$(echo $NEW | tr '.' '/')"

# Создаём новую директорию
mkdir -p "$NEW_PATH"

# Переносим файлы (.java и .kt)
find "$OLD_PATH" -type f \( -name "*.java" -o -name "*.kt" \) -exec bash -c '
    for file; do
        relpath="${file#'"$OLD_PATH"'}"
        newfile="'"$NEW_PATH"'$relpath"

        mkdir -p "$(dirname "$newfile")"
        mv "$file" "$newfile"

        # Обновляем package и импорты внутри файла
        sed -i "s|'"$OLD"'|'"$NEW"'|g" "$newfile"
    done
' bash {} +

# Удаляем пустые старые папки
find "$OLD_PATH" -type d -empty -delete
