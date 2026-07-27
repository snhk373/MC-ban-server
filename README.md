# PunishSystem README.md（适配 1.12.2 Paper）
> 日志：`[PunishSystem] Loading PunishSystem v1.5`，监狱jail世界、处罚GUI、PVP传送功能
仓库地址：https://github.com/snhk373/MC-ban-server/
```markdown
# PunishSystem
适用于 Minecraft 1.12.2 Spigot/Paper 处罚综合插件
版本：v1.5

## ✨ 功能列表
1. 玩家监狱系统（Jail）
    - 将玩家传送至 jail 世界进行监禁处罚
    - 支持定时监禁 / 永久监禁
    - 监禁期间禁止破坏、聊天、移动逃离
2. 处罚系统
    - 临时封禁、永久封禁、禁言、警告、踢出
    - 完整处罚记录查询
3. PVP地图传送GUI
4. 管理员快捷处罚指令
5. YAML本地数据存储（无需数据库）

## ⚠️ 启动警告说明
日志警告：`世界文件夹不存在：jail`
解决方案二选一：
1. **创建 jail 世界**
    使用 /mv create jail normal 创建监狱世界，重启插件；
2. **关闭监狱功能**
    在 config.yml 找到 jail-enabled: true 修改为 false

## 📜 可用指令
| 指令 | 说明 | 权限节点 |
|------|------|---------|
| /punish | 打开处罚GUI | punishsystem.use |
| /jail <玩家> [时长] | 监禁玩家 | punishsystem.jail |
| /unjail <玩家> | 解除监禁 | punishsystem.jail |
| /warn <玩家> [理由] | 警告玩家 | punishsystem.warn |
| /mute <玩家> [时长] [理由] | 临时禁言 | punishsystem.mute |
| /unmute <玩家> | 解除禁言 | punishsystem.mute |
| /tempban <玩家> [时长] [理由] | 临时封禁 | punishsystem.ban |
| /ban <玩家> [理由] | 永久封禁 | punishsystem.ban |
| /unban <玩家> | 解封玩家 | punishsystem.ban |
| /punishhistory <玩家> | 查看处罚记录 | punishsystem.history |
| /punishreload | 重载配置文件 | punishsystem.reload |

## 📂 文件结构
```
plugins/PunishSystem/
├─ config.yml        # 主配置（消息、时长、监狱开关）
├─ jaildata.yml      # 正在监禁玩家数据
└─ punishdata.yml    # 全部处罚历史记录
```

## 🛠️ 安装步骤
1. 将 PunishSystem.jar 放入 plugins 文件夹
2. 启动服务器自动生成配置文件
3. 如需监狱功能：使用多世界插件创建 `jail` 世界
4. 修改 config.yml 自定义处罚时长、提示消息
5. 重启服务器生效

## ❗ 常见问题
1. `世界文件夹不存在：jail`
    - 不需要监狱：关闭 jail-enabled
    - 需要监狱：Multiverse-Core 创建 jail 世界
2. 监禁玩家可以逃出
    - 在 jail 世界配置世界边界，禁止传送、使用末影珍珠
3. 处罚记录丢失
    - 关闭服务器请使用 `stop`，不要强行关闭服务端，防止yml写入失败

## 📌 兼容性
- 服务端：Paper/Spigot 1.12.2
- Java：Java 8
- 依赖（可选）：Multiverse-Core（多世界，用于jail监狱世界）

## License
GPL-3.0
```
