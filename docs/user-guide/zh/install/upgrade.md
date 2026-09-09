---
description: 有数据时只导入新镜像，不覆盖库和正在使用的配置
---

# 升级（不覆盖数据）

升级 = **导入新版本镜像并切换 API / Worker / 网页**。  
**不会**清空数据目录，也 **不会**用新包里的默认密码覆盖正在使用的 `config.env`。

## 步骤

1. 把新包解压到 **新目录**（不要覆盖旧目录）。也可在当前安装目录覆盖解压后再升级；**不要删数据目录**。
2. 进入解压目录后执行：

```bash
sudo ./upgrade_zh.sh    # 中文提示
# 或
sudo ./upgrade_en.sh    # 英文提示
```

3. 脚本会：
   - 读取 `/etc/asset-management/install-root` 里正在用的 `config.env`
   - 比较版本：只允许 **新版本 ≥ 已安装版本**（禁止降级）
   - 同版本再跑：只重新导入镜像
   - `docker load` 新镜像，只重建应用容器
   - Postgres / 文档 / Redis / Mosquitto 数据卷保持原样
   - 库结构由 API 启动时 **Flyway 迁移**（加表、加列），不是拿空库替换

{% hint style="success" %}
资产、信标、网关、用户、告警规则、MQTT 连接都会保留。
{% endhint %}

## 找不到旧配置时

若数据目录已有库，但没有 `install-root` / 旧 `config.env`：把 **旧安装目录里的 `config.env`** 复制到新包目录后再升级。不要用新包默认密码去连旧库。

## 降级

脚本会拒绝把高版本换成低版本。需要回退请用备份 [恢复](ops.md)。
