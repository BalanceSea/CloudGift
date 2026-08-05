# CloudGift

CloudGift `1.6.0` 是面向 **Spigot 1.20.1 / Java 17** 的礼包插件。它支持权限礼包、精确小时冷却、跨零点刷新、累计领取次数、命令与完整物品奖励、游戏内 GUI 编辑、PlaceholderAPI，以及 SQLite / MySQL / MariaDB 存储。

> 跨零点刷新指按配置时区判断自然日：启用后，只要经过下一次 00:00 就能再次领取；关闭后才按实际经过的小时数计算。

## ✨ 主要功能

- 为每个礼包单独设置显示名称、权限、冷却和累计领取次数。
- `reset-at-midnight: true` 时跨过下一自然日 00:00 即刷新，并忽略小时冷却。
- `reset-at-midnight: false` 时按 `cooldown-hours` 精确计算等待时间。
- 奖励支持控制台命令和保留名称、Lore、附魔等数据的完整物品。
- 游戏内 GUI 可新建、编辑、删除礼包，并批量投放最多 45 项物品奖励。
- SQLite 适合单服；MySQL / MariaDB 可让多个子服共享领取记录。
- 数据库条件更新和唯一键共同防止跨服并发重复领取。
- PlaceholderAPI 可显示能否领取、下次领取时间、已用次数、上限和剩余次数。
- 玩家数据和领取操作异步访问数据库，避免阻塞服务器主线程。

## 📦 运行环境

| 项目 | 要求 | 说明 |
| --- | --- | --- |
| Minecraft | `1.20.1` | 当前编译与测试目标 |
| 服务端 | Spigot `1.20.1` | 使用 Spigot 公共 API，不引用 Paper 专属类 |
| Java | `17` 或更新版本 | 编译字节码目标为 Java 17 |
| PlaceholderAPI | `2.12.3`，可选 | 缺失时仅关闭 PAPI 变量和奖励中的外部变量解析 |

插件没有必须手动安装的前置插件。以下第三方库由服务端的 Library Loader 下载，不会打包进 CloudGift JAR：

| 运行库 | 版本 | 用途 |
| --- | --- | --- |
| Adventure MiniMessage | `4.17.0` | 解析消息和礼包显示名称 |
| Adventure Legacy Serializer | `4.17.0` | 把 MiniMessage 转为 Spigot 可显示的颜色文本 |
| HikariCP | `7.1.0` | 数据库连接池 |
| MySQL Connector/J | `9.7.0` | MySQL / MariaDB 驱动 |
| SQLite JDBC | `3.53.2.0` | SQLite 驱动 |

首次启动需要能访问 Maven Central。依赖下载成功后由服务端缓存。

## 🚀 安装

1. 使用 Java 17 启动 Spigot 1.20.1。
2. 将 `CloudGift-1.6.0.jar` 放入服务端的 `plugins/` 目录。
3. 如需变量功能，将 PlaceholderAPI 放入 `plugins/`。
4. 启动一次服务器，等待 Library Loader 下载运行库。
5. 修改 `plugins/CloudGift/config.yml`、`messages.yml` 和 `gifts/` 下的礼包文件。
6. 数据库连接设置修改后重启；其他支持热重载的设置使用 `/cloudgift reload`。

首次安装会生成：

```text
plugins/CloudGift/
├── config.yml
├── messages.yml
├── items.yml
├── data.db
└── gifts/
    ├── novice.yml
    └── monthly.yml
```

`data.db` 只在 SQLite 模式使用。插件也兼容旧版根目录 `gifts.yml` / `gifts.yaml`。

## 🎮 命令

| 命令 | 权限 | 默认授权 | 说明 |
| --- | --- | --- | --- |
| `/gift <礼包ID>` | `cloudgift.command.gift` | 所有人 | 领取礼包 |
| `/cloudgift claim <礼包ID>` | `cloudgift.command.gift` | 所有人 | 领取礼包的另一种写法 |
| `/cloudgift help` | 无 | 所有人 | 查看当前执行者可用的命令 |
| `/cloudgift list` | `cloudgift.command.list` | OP | 查看已载入礼包 |
| `/cloudgift add <物品ID>` | `cloudgift.command.add` | OP | 保存主手完整物品 |
| `/cloudgift remove <在线玩家名或UUID> <礼包ID>` | `cloudgift.command.remove` | OP | 删除该玩家的礼包领取记录 |
| `/cloudgift reload` | `cloudgift.command.reload` | OP | 重载消息、物品、礼包和非连接类主配置 |
| `/cloudgift menu` | `cloudgift.command.menu` | OP | 打开礼包编辑 GUI |

别名：

- `/gift`：`/libao`、`/cgift`
- `/cloudgift`：`/cloudgifts`
- `menu`：`gui`、`editor`
- `add`：`saveitem`
- `remove`：`reset`

## 🔐 权限

| 权限 | 默认值 | 用途 |
| --- | --- | --- |
| `cloudgift.admin` | OP | 包含 reload、menu、add、remove、list 管理权限 |
| `cloudgift.command.gift` | 所有人 | 使用领取命令 |
| `cloudgift.command.reload` | OP | 重载配置 |
| `cloudgift.command.menu` | OP | 打开礼包编辑器 |
| `cloudgift.command.add` | OP | 保存主手物品 |
| `cloudgift.command.remove` | OP | 删除领取记录 |
| `cloudgift.command.list` | OP | 查看礼包列表 |

每个礼包还可以设置独立权限，例如 `cloudgift.gift.monthly`。空字符串表示不额外限制。

## ⚙️ 礼包配置

礼包文件位于 `plugins/CloudGift/gifts/`，可按功能拆分为任意 `.yml` 或 `.yaml` 文件。所有文件都使用 `gifts:` 顶层结构：

```yaml
gifts:
  monthly:
    display-name: '<gold>月卡礼包'
    permission: cloudgift.gift.monthly
    # false 时从上次成功领取起精确等待 24 小时。
    cooldown-hours: 24
    # true 时忽略 cooldown-hours，进入下一自然日即可领取。
    reset-at-midnight: true
    # 0 表示不限累计领取次数。
    max-claims: 0
    rewards:
      - type: command
        command: 'give %player% diamond 3'
      - type: item
        item: monthly_sword
        amount: 1
```

礼包 ID 只能包含小写字母、数字、下划线和连字符，最长 128 个字符。目录内礼包优先于根目录旧文件；重复 ID 保留先载入的定义。

### 刷新方式

| 配置 | 行为 |
| --- | --- |
| `reset-at-midnight: true` | 按 `time.zone-id` 计算下一自然日 00:00；忽略 `cooldown-hours` |
| `reset-at-midnight: false` | 从上次成功领取时刻起，精确等待 `cooldown-hours` |

例如玩家在 23:50 领取：

- 开启跨零点刷新时，次日 00:00 即可再次领取。
- 关闭跨零点刷新且冷却为 24 小时时，次日 23:50 才能再次领取。

### 命令奖励变量

- `%player%`：玩家名称
- `%uuid%`：玩家 UUID
- `%gift%`：礼包 ID
- 安装 PlaceholderAPI 后还会解析其他 PAPI 变量

命令以控制台身份执行，开头的 `/` 会自动移除。

### 物品奖励

手持目标物品执行：

```text
/cloudgift add monthly_sword
```

物品会完整保存到 `items.yml`。背包放不下的奖励会掉落在玩家脚下。

也可以在 `/cloudgift menu` 的奖励列表中左键“添加物品奖励”，把物品放进前 45 格。保存、取消、直接关闭、退出服务器或插件停用时，投入的原物品都会返还；背包溢出部分会掉落在管理员脚下。

## 🗄️ 数据库

### SQLite 单服

默认配置即可使用：

```yaml
storage:
  type: sqlite
  table-prefix: cloudgift_
```

数据保存到 `plugins/CloudGift/data.db`。不要让多台服务器共享同一个 SQLite 文件。

### MySQL / MariaDB 群组服

```yaml
storage:
  type: mysql
  table-prefix: cloudgift_
  mysql:
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: cloudgift
    password: change_me
    parameters: useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

所有子服需要使用同一数据库、表前缀、礼包定义和 `time.zone-id`。插件通过数据库条件更新与唯一键保证同一玩家跨服同时领取时只成功一次。

`storage.*` 修改后必须重启服务器。切换存储类型不会自动迁移旧数据，请先备份并自行迁移 `<table-prefix>claims` 表。

## PlaceholderAPI

把 `<礼包ID>` 替换为实际 ID：

| 变量 | 返回值 |
| --- | --- |
| `%cloudgift_can_<礼包ID>%` | 可领取返回 `yes`，其他状态返回 `no` |
| `%cloudgift_next_<礼包ID>%` | 可领取文本、状态文本或下次领取时间 |
| `%cloudgift_used_<礼包ID>%` | 已领取次数 |
| `%cloudgift_limit_<礼包ID>%` | 次数上限；不限返回 `∞` |
| `%cloudgift_remaining_<礼包ID>%` | 剩余次数；不限返回 `∞` |

示例：

```text
%cloudgift_can_monthly%
%cloudgift_next_monthly%
%cloudgift_remaining_monthly%
```

玩家数据仍在异步载入时，`can` 返回 `no`，`next` 返回 `数据加载中`。

## 🔄 从 1.5.0 升级

1. 停服并备份 `plugins/CloudGift/` 与数据库。
2. 将旧 JAR 替换为 `CloudGift-1.6.0.jar`。
3. 使用 Java 17 或更新版本启动 Spigot 1.20.1。
4. 首次启动会额外下载 Adventure MiniMessage 与 Legacy Serializer。
5. 原有礼包、物品和领取记录格式保持兼容，无需修改 `reset-at-midnight` 或迁移数据库。

本次版本从 Paper 1.21.11 / Java 21 迁移到 Spigot 1.20.1 / Java 17。不要继续在 Minecraft 1.21 服务端使用此构建。

## ❓ 常见问题

### 为什么经过 00:00 仍然不能领取？

确认礼包设置了 `reset-at-midnight: true`，并检查 `config.yml` 的 `time.zone-id`。关闭该选项时会按小时精确计算。

### 修改 MySQL 配置后为什么没有生效？

数据库连接池只在插件启动时创建。修改 `storage.*` 后需要完整重启，`/cloudgift reload` 不会重建连接池。

### 奖励失败后为什么仍显示已经领取？

领取资格会先原子写入数据库，再回到主线程发奖。这样可以防止跨服重复领取。管理员应根据控制台日志检查失败命令或缺失物品，并在确认后使用 `remove` 重置记录。

### 没装 PlaceholderAPI 能用吗？

可以。领取、GUI、命令奖励内置变量和数据库功能都可用；只有 CloudGift 的 PAPI 变量及奖励中的其他 PAPI 变量不可用。

## 🧰 构建

使用 JDK 17 和 Maven：

```text
mvn clean package
```

构建产物：`target/CloudGift-1.6.0.jar`。

Spigot API、PlaceholderAPI 和全部运行库使用 `provided`，最终 JAR 不包含这些依赖。运行库坐标统一声明在 `plugin.yml` 的 `libraries` 中。

## 联系方式

- 作者：`MoutainSeaL`
- QQ：`3643203568`
- QQ 群：`342097496`
