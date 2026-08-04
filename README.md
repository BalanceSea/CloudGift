# CloudGift

CloudGift 1.3.0 是面向 Paper 1.21.11（Java 21）的礼包插件。它支持权限礼包、自定义领取冷却、累计领取次数、GUI 直接添加物品、控制台命令奖励、PlaceholderAPI，以及适合群组服的 MySQL/MariaDB 共享存储。

## 安装

1. 将 `CloudGift-1.3.0.jar` 放入服务端的 `plugins` 目录。
2. 如需变量功能，安装 PlaceholderAPI。
3. 启动一次服务端，修改 `plugins/CloudGift/config.yml`、`messages.yml` 和礼包文件。
4. 执行 `/cloudgift reload` 重载礼包、物品、消息、时间格式等配置。数据库连接设置变更后需要重启服务端。

CloudGift 使用服务端的 Library Loader 在首次启动时从 Maven Central 下载 HikariCP、MySQL Connector/J 和 SQLite JDBC，不把数据库驱动打包进插件 JAR。首次启动必须能够访问 Maven Central，下载后的库由服务端缓存。

Paper 1.21.11 使用 Java 21 或更新版本运行。本项目编译目标固定为 Java 21。

## 玩家与管理命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/gift <礼包ID>` | `cloudgift.command.gift` | 领取礼包 |
| `/cloudgift claim <礼包ID>` | `cloudgift.command.gift` | 领取礼包的另一种写法 |
| `/cloudgift help` | 无 | 查看当前 sender 可用的命令 |
| `/cloudgift list` | `cloudgift.command.list` | 查看已载入的礼包 |
| `/cloudgift add <物品ID>` | `cloudgift.command.add` | 保存主手物品（含名称、附魔和组件等） |
| `/cloudgift remove <在线玩家名或UUID> <礼包ID>` | `cloudgift.command.remove` | 清除领取记录 |
| `/cloudgift reload` | `cloudgift.command.reload` | 重载配置 |
| `/cloudgift menu` | `cloudgift.command.menu` | 打开礼包编辑 GUI |

旧命令仍可使用：`saveitem` 是 `add` 的别名，`reset` 是 `remove` 的别名，`gui` 和 `editor` 是 `menu` 的别名。`cloudgift.admin` 通过子权限继承全部管理命令权限。

每个礼包可单独填写权限，例如 `cloudgift.gift.monthly`。使用 LuckPerms 等权限插件，在玩家购买月卡后授予该权限即可：

```text
/lp user 玩家名 permission set cloudgift.gift.monthly true
```

领取成功、无权限、冷却中和重置成功等提示里的 `<gift>` 会显示礼包的 `display-name`，而不是内部礼包 ID。礼包 ID 仍用于命令、权限判断、PAPI 和数据库记录。

## 礼包配置

新安装默认会在 `plugins/CloudGift/gifts/` 生成 `novice.yml` 和 `monthly.yml`。礼包也可继续写在旧版的 `plugins/CloudGift/gifts.yml`，或拆分到该目录下任意层级的多个 `.yml` 或 `.yaml` 文件。插件启动时会自动创建 `gifts` 目录，修改文件后执行 `/cloudgift reload` 即可生效。

目录内的文件优先于旧版根目录文件，适合逐步把礼包从一个大文件迁移到多个文件。每个文件使用相同的 `gifts:` 顶层结构；所有文件中的礼包 ID 必须唯一，只能使用小写字母、数字、下划线和连字符。通过 `/cloudgift menu` 新建的礼包会自动保存为 `gifts/<礼包ID>.yml`，已有礼包会写回它原本所在的文件。

```yaml
gifts:
  monthly:
    display-name: '<gold>月卡礼包'
    permission: cloudgift.gift.monthly
    # 支持小数。冷却从该玩家本次成功占用领取资格的时刻开始计算。
    cooldown-hours: 24
    # 每位玩家累计可领取的总次数；0 表示不限次数。
    max-claims: 0
    rewards:
      - type: command
        command: 'money give %player% 1000'
      - type: command
        command: 'say %player% 领取了 %gift%'
      - type: item
        item: monthly_sword
        # 不填写 amount 时使用保存物品原本的数量；填写后以这里为准。
        amount: 1
```

控制台命令支持 `%player%`、`%uuid%`、`%gift%`，安装 PlaceholderAPI 后也会解析该玩家的其他 PAPI 变量。物品栏放不下的物品会掉落在玩家脚下。

先把物品拿在主手，再执行：

```text
/cloudgift add monthly_sword
```

物品会保存至 `items.yml`，礼包奖励通过物品 ID 引用它。

也可以执行 `/cloudgift menu` 打开礼包编辑器，在“奖励列表”中直接添加背包物品：

- 把物品拿到光标后，点击任意空奖励格或“添加物品奖励”按钮。
- Shift 点击背包物品，可直接复制整组物品为奖励。
- 空奖励格支持数字键、副手交换键和单槽拖入。

GUI 只复制物品模板，不会扣除管理员背包中的原物品。物品名称、Lore、附魔和组件会完整写入 `items.yml`，插件会自动生成 `__cloudgift_gui_` 开头的内部物品 ID。空手点击“添加物品奖励”仍可输入已有物品 ID。

## PlaceholderAPI 变量

把 `<礼包ID>` 换成实际 ID，例如 `monthly`：

| 变量 | 返回值 |
|---|---|
| `%cloudgift_can_<礼包ID>%` | 当前可以领取返回 `yes`，否则返回 `no` |
| `%cloudgift_next_<礼包ID>%` | 下次领取时间；已可领取时返回 `可领取` |

示例：

```text
%cloudgift_can_monthly%
%cloudgift_next_monthly%
```

玩家刚进服、数据仍在异步载入时，`can` 安全地返回 `no`，`next` 返回 `数据加载中`；载入通常在很短时间内完成。以上文字可在 `config.yml` 的 `placeholder` 节点修改。

时间格式位于 `config.yml`：

```yaml
time:
  pattern: yyyy年MM月dd日 HH:mm:ss
  zone-id: Asia/Shanghai
```

`pattern` 使用 Java [`DateTimeFormatter`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html) 规则。

## 群组服数据库

所有子服的 `config.yml` 使用同一个 MySQL/MariaDB 数据库：

```yaml
storage:
  type: mysql
  table-prefix: cloudgift_
  mysql:
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: cloudgift
    password: strong_password
    parameters: useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

数据表会自动创建。领取资格使用数据库条件更新与唯一键进行原子竞争，因此同一玩家从不同子服同时发起领取时只有一个请求能够成功。每台子服的礼包文件也应保持一致；建议通过部署脚本同步这些文件。

默认 `sqlite` 只适合单服，不能把同一个 SQLite 文件给多台服务端共享。

## 自定义消息

所有提示位于 `messages.yml`，使用 MiniMessage 格式，例如 `<green>`、`<red>`、`<#66ccff>`。可用占位内容已在默认文件中给出。

## 构建

```text
mvn clean package
```

构建产物位于 `target/CloudGift-1.3.0.jar`。这是不包含数据库驱动的轻量 JAR；运行依赖声明在 `plugin.yml` 的 `libraries` 节点中。

## 联系方式

- 作者：`MoutainSeaL`
- QQ：`3643203568`
- QQ 群：`342097496`
