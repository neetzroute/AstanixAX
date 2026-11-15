# 🛡️ Astanix-AX (Anti-Xray Inspector)

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-brightgreen.svg?style=for-the-badge&logo=minecraft" alt="Minecraft Version" />
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk" alt="Java Version" />
  <img src="https://img.shields.io/badge/Platform-Paper%20%2F%20Purpur-blue.svg?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge" alt="License" />
</p>

**Astanix-AX** — это высокопроизводительный серверный Paper-плагин для административного расследования нечестной добычи ресурсов (X-Ray, ESP, FreeCam, Baritone). 

Плагин не блокирует и не подменяет руды в генерации на лету (в отличие от ресурсоёмкого Engine Mode 2), а ведёт **пространственно-кластерный учёт добычи** ценных руд в локальную базу данных SQLite и предоставляет администрации удобный визуальный инструментарий с **персональной сетевой подсветкой блоков через пакеты протокола**.

---

## ✨ Ключевые возможности

* ⛏️ **Умный трекинг ценных руд:** отслеживание алмазов (`DIAMOND_ORE`, `DEEPSLATE_DIAMOND_ORE`), древних обломков (`ANCIENT_DEBRIS`) и изумрудов (`EMERALD_ORE`, `DEEPSLATE_EMERALD_ORE`).
* 🧩 **Автоматическая кластеризация жил (Spatial Clustering):** близко расположенные блоки (в радиусе 3.5 блоков) автоматически объединяются в единую «кладку» (кластер) с динамическим пересчётом геометрического центра.
* 📦 **Двухуровневый интерактивный Chest GUI:**
  * **Меню игроков:** ранжирование добытчиков по общему количеству выкопанных ценностей, пагинация, статистика на головах игроков.
  * **Меню кладок игрока:** фильтрация по типу руды и статусу проверки, просмотр точных координат и времени добычи.
* 👁️ **Пакетная сетевая подсветка блоков (Zero World Damage):**
  * При клике на кладку администратор телепортируется к ней в режиме спектатора.
  * Блоки кладки **визуально подменяются на золотые блоки (`GOLD_BLOCK`) исключительно для проверяющего администратора** через сетевые пакеты `ClientboundBlockUpdatePacket` (`Player#sendBlockChange`).
  * Для остальных игроков и в самом мире сервера блоки остаются нетронутыми.
  * Автоматический и безопасный сброс подсветки при телепортации, смене мира, выходе с сервера или ручной команде.
* 🔖 **Персистентная отметка «Чисто» (Mark as Clean):**
  * Возможность пометить кластер как безопасный/проверенный (ПКМ в инвентаре или кликом в чате).
  * Статус сохраняется в базе данных между перезагрузками сервера.
* ⚡ **100% TPS-Safe & Асинхронность:**
  * Все операции с БД (запись, поиск, агрегация статистики) выполняются в фоновых потоках с пулом соединений HikariCP (SQLite в режиме WAL).
  * Основной поток сервера никогда не зависает при сканировании и выборках.
* 🎨 **Современный визуальный стиль:** полная поддержка Adventure API и форматирования компонентов MiniMessage.

---

## 📂 Структура модулей (`dev.astanix.axray`)
- `command` — обработчик административных команд `/ax` и автодополнение аргументов.
- `config` — загрузка настроек и сообщений плагина из `config.yml`.
- `gui` — модульная система инвентарей на базе `InventoryHolder` с защитой от перемещения предметов.
- `highlight` — управление сессиями администраторов, отправка клиентских пакетов блоков и их откат.
- `model` — предметные модели: `OreType`, `OreBlock`, `OreCluster`, `PlayerOreStats`.
- `storage` — пул соединений HikariCP, инициализация таблиц/индексов и асинхронный репозиторий.
- `tracker` — перехват `BlockBreakEvent` и алгоритм пространственной группировки блоков.
- `util` — фабрики `ItemBuilder` (головы, чары, Lore) и утилиты MiniMessage.

---

## 🎮 Команды и права

| Команда | Алиасы | Описание | Право доступа |
|---|---|---|---|
| `/astanixax` | `/ax`, `/axray` | Открыть главное меню со списком игроков | `astanix.axray.admin` |
| `/ax inspect <игрок>` | — | Открыть список всех кладок конкретного игрока | `astanix.axray.admin` |
| `/ax markclean <id>` | — | Пометить кладку проверенной по её ID | `astanix.axray.admin` |
| `/ax clear` | — | Сбросить подсветку блоков и выйти из инспекции | `astanix.axray.admin` |
| `/ax reload` | — | Перезагрузить файл конфигурации `config.yml` | `astanix.axray.admin` |

> По умолчанию право `astanix.axray.admin` выдано операторам сервера (`OP`).

---

## ⚙️ Конфигурация (`config.yml`)

```yaml
# Настройки базы данных
database:
  file: "axray_data.db"
  maximum-pool-size: 10
  minimum-idle: 2
  idle-timeout-ms: 300000
  connection-timeout-ms: 10000

# Параметры трекера и кластеризации
tracker:
  ignore-creative: true          # Игнорировать добычу в творческом режиме
  ignore-silk-touch: false       # Учитывать руды даже при добыче шелковым касанием
  monitored-worlds: []           # Пустой список = мониторинг во всех мирах
  cluster-radius: 3.5            # Радиус объединения руд в одну кладку (в блоках)
  cluster-time-window-minutes: 15 # Окно времени группировки рудной жилы

# Параметры инспекции
inspection:
  highlight-material: "GOLD_BLOCK" # Блок, отправляемый пакетом админу
  spectator-on-teleport: true     # Переключать админа в спектатора при телепортации
  save-previous-state: true       # Восстанавливать режим и позицию при выходе
  auto-clear-minutes: 30

# Сообщения (MiniMessage с поддержкой цветов, градиентов и кликабельности)
messages:
  prefix: "<gradient:#00D2FF:#3A7BD5><bold>[Astanix-AX]</bold></gradient> "
  no-permission: "<red>У вас нет прав для выполнения этой команды!</red>"
  teleported-to-cluster: "<green>Телепортация к кладке <white>#{id}</white> игрока <yellow>{player}</yellow>. Блоки подсвечены золотом!</green>"
  teleport-hint: "<gray>Для сброса подсветки введите <click:run_command:'/ax clear'><yellow><u>/ax clear</u></yellow></click>.</gray>"
  highlight-cleared: "<gray>Подсветка блоков успешно снята, оригинальный вид восстановлен.</gray>"
  cluster-clean-marked: "<green>Кладка <white>#{id}</white> помечена как <bold>ЧИСТАЯ</bold> ✔</green>"
  cluster-unclean-marked: "<yellow>С кладки <white>#{id}</white> снята отметка чистоты ⚠</yellow>"
  player-not-found: "<red>Игрок не найден или не имеет записей о добыче.</red>"
  config-reloaded: "<green>Конфигурация успешно перезагружена!</green>"
```

---

## 🔨 Сборка из исходников

### Требования
* **Java:** JDK 21+
* **Система сборки:** Maven 3.9+ или Gradle 8+

### Сборка через Maven:
```bash
mvn clean package
```
Готовый затенённый плагин со всеми зависимостями: `target/astanix-ax-1.0.0.jar`.

### Сборка через Gradle:
```bash
./gradlew build
```
Готовый файл плагина: `build/libs/AstanixAX-1.0.0.jar`.

---

## 📄 Лицензия

Проект разработан для платформы **Astanix** и распространяется под лицензией [MIT](LICENSE).
