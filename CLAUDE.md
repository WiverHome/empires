# Empires - правила работы

Пошаговая тактика в духе Ancient Empires 2. Две версии с общими правилами и ресурсами (`assets/`):
- веб: Vanilla JS + Canvas без сборки: `index.html`, `style.css`, `js/*.js` (обычные `<script src>` с общей глобальной областью, порядок задаёт index.html);
- Android: нативное приложение Kotlin + libGDX (без WebView) в `android/`.
Это отдельный проект, к Embermarch отношения не имеет.

Обе версии поддерживаются: любую правку правил, данных, кампании или интерфейса делать в обеих. Веб - эталон, Kotlin повторяет его структуру и имена функций один к одному.

## Карта кода
- `js/data.js`: BAL (числа баланса), DIFFICULTY, TERRAIN, MAP_LEGEND, UNIT_DEFS, BUY_LIST, TEAMS, TILE_SPRITES
- `js/rules.js`: состояние партии и все правила без DOM (движение, бой, яд, ауры, захват, разрушение и отстройка деревень, найм, смена хода, туман войны visibleTiles). Действия возвращают списки событий для анимации
- `js/ai.js`: ИИ (`aiNextAction` возвращает одно действие, `aiApply` применяет без анимации), AI_TUNE
- `js/campaign.js`: SPEAKERS, MISSIONS (карты, юниты, диалоги, triggers, check), evaluateOutcome, spawnNear
- `js/skirmish.js`: SKIRMISH_MAPS (симметричные карты), CUSTOM_MAPS (свои карты из редактора), skirmishMission по id вида `sk:<карта>:<золото>:<стороны h/a/n>[:f]` (f - туман войны), skirmishOutcome
- `js/editor.js`: редактор карт для схватки (openMapEditor), карты хранятся в SAVE.data.maps
- `js/achievements.js`: статистика партии (st.stats), звёзды миссий, ACHIEVEMENTS
- `js/sound.js`: готовые звуки и музыка из `assets/audio` (CC0)
- `js/assets.js`: листы Kenney, процедурные тайлы, спрайты юнитов с обводкой цвета стороны
- `js/render.js`: холст, камера, масштаб под экран (fitTs), подсветка, анимации
- `js/game.js`: контроллер партии: ввод, меню действий, найм, диалоги, ход ИИ, триггеры, HUD
- `js/main.js`: SAVE (localStorage), титул, список миссий, правила, ввод мыши/тача/клавиатуры, boot
- `rules/ai/campaign/skirmish/data/achievements` не трогают DOM: их грузят `tools/sim.js` и `tools/check-maps.js`
- Повторы: в `st.rinit` сид и параметры создания, в `st.log` каждое действие с сидом перед ним (`s`); ИИ тоже тратит случайность, поэтому сид пишется для каждого действия
- `android/logic`: порт data/rules/ai/campaign/skirmish/achievements (пакет `empires.logic`), `Sim.kt` = `tools/sim.js`, `StateJson.kt` - сохранение состояния
- `android/app`: `Assets.kt` (= assets.js на Pixmap), `GameScreen.kt` (= game.js + render.js, async через корутины), `TitleScreen.kt`, `SkirmishSetup.kt`, `MapEditor.kt`, `Save.kt`, `Sfx.kt`, `Ui.kt` (стили из style.css), `Net.kt` (лобби и сессия: обмен действиями журнала) + `Nearby.kt` (Nearby Connections) + `Lan.kt` (одна сеть Wi-Fi по TCP, `DualLink` держит оба канала)

## Ограничения
- Главная цель - телефон в вертикальном положении, один экран без прокрутки. Карты не шире 11 и не выше 15 клеток (проверяет `tools/check-maps.js`). Окна на узком экране должны помещаться без прокрутки.
- Тексты для игрока на русском.
- Баланс и числа - в константах (BAL, AI_TUNE, UNIT_DEFS).
- Формат сохранения (`empires.save.v1`) менять обратно совместимо.
- Версия одна на обе версии игры: `GAME_VERSION` в `js/data.js` и `versionName`/`versionCode` в `android/app/build.gradle.kts`; при релизе менять вместе.
- Лицензии: код - MIT (`LICENSE`), ресурсы - CC0 (`assets/CREDITS.md`), сторонние библиотеки - `THIRD_PARTY_NOTICES.md` и `android/app/src/main/licenses/` (входит в APK). Новую зависимость или ресурс - сразу вписать туда.
- Графика, звуки и музыка только CC0, авторы в `assets/CREDITS.md`. Шрифт в Android - системный (не входит в APK).
- Правила в двух версиях должны совпадать до бита: случайность только через `rand(st)`, порядок обхода как в JS (LinkedHashMap), JS `Math.round` = `floor(x + 0.5)`.

## Как проверять
- Синтаксис: `for f in js/*.js tools/*.js; do node --check "$f"; done`
- Карты: `node tools/check-maps.js`
- ИИ против ИИ: `node tools/sim.js --runs 10` (без зависаний; поражения синих нормальны, за них тоже играет ИИ)
- Игра: `node tools/serve.js` и http://localhost:5173 (в превью конфигурация `empires` из `.claude/launch.json`)
- Повторы: `node tools/sim.js --runs 2 --replay` (в конце `REPLAY: all match`)
- Совпадение версий: `diff <(node tools/sim.js --runs 3) <(cd android && ./gradlew :logic:run --args="--runs 3" -q)` - пусто
- Android: `cd android && ./gradlew :logic:test :app:assembleDebug`
- Сеть на эмуляторах (Nearby там не работает): два эмулятора `-read-only`, `adb -s A forward tcp:47123 tcp:47123`, `adb -s B reverse tcp:47123 tcp:47123`, гость вводит адрес `127.0.0.1:47123`
- Android-подводные камни: регулярки с `(?U)` не поддерживаются; пустые слои Scene2D должны быть `Touchable.childrenOnly`; в системном Roboto нет ★▶↶ - их подмешивает `Ui.mergeSymbols`

## Агенты и экономия токенов
Агенты в `.claude/agents/`: `scout` (haiku, поиск на чтение), `checker` (haiku, прогон проверок), `coder` (sonnet, правка по готовой спецификации).
- Мелкие правки и точечный поиск (1-3 вызова) делать самому: запуск агента стоит дороже.
- Широкий поиск по многим файлам - `scout`; проверки после правок с длинным выводом (`sim.js`) - `checker`.
- `coder` - только для независимых, чётко описанных кусков; несколько таких можно запускать параллельно. Проектирование и итоговую проверку оставлять основной сессии.
- В задаче агенту передавать нужный контекст (файлы, строки, решения), чтобы он не перечитывал проект.
