# Сторонние компоненты

Код игры распространяется по лицензии MIT (см. `LICENSE`). Ниже перечислено всё чужое, что используется в игре.

## Графика, звуки и музыка

Все файлы в `assets/` - общественное достояние (CC0 1.0), указывать авторство не обязательно, но мы указываем.
Подробности и ссылки - в [`assets/CREDITS.md`](assets/CREDITS.md).

| Что | Автор | Лицензия |
|---|---|---|
| Tiny Town, Tiny Dungeon (тайлы и фигурки) | Kenney (kenney.nl) | CC0 1.0 |
| 50 RPG sound effects, 85 Short music jingles | Kenney (kenney.nl) | CC0 1.0 |
| Heartfelt Battle (музыка боя) | request, OpenGameArt | CC0 1.0 |
| Medieval: The Bard's Tale (музыка меню) | RandomMind, OpenGameArt | CC0 1.0 |

Процедурные тайлы (вода, горы, дома, замки), волк, огонёк и кристалл нарисованы кодом в `js/assets.js` и `android/app/.../Assets.kt`.

## Веб-версия

Внешних библиотек нет: чистый JavaScript и Canvas.

## Android-версия

| Библиотека | Лицензия |
|---|---|
| [libGDX](https://libgdx.com) 1.14.2: gdx, gdx-backend-android, gdx-freetype | Apache License 2.0 |
| [FreeType](https://freetype.org) (входит в нативные библиотеки gdx-freetype) | FreeType License (FTL) |
| [Kotlin](https://kotlinlang.org) стандартная библиотека | Apache License 2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) 1.11.0 | Apache License 2.0 |
| [Google Play services Nearby](https://developers.google.com/nearby/connections/overview) 19.5.1 | Android Software Development Kit License |
| Gradle Wrapper (только для сборки) | Apache License 2.0 |

Части этой программы защищены авторским правом © The FreeType Project (www.freetype.org). Все права защищены.
*Portions of this software are copyright © The FreeType Project (www.freetype.org). All rights reserved.*

Полный текст Apache License 2.0 лежит в `android/app/src/main/licenses/APACHE-2.0.txt` и входит в APK
(в игре: «Как играть» → «Об игре и лицензиях»).

Шрифт в Android-версии системный (Roboto, Noto Sans Symbols), в APK он не входит.
