---
description: MQTT 接入 → 连接管理
---

# 连接管理

路径：**MQTT 接入 → 连接管理**。

管理 Broker 地址、账号、环境。生产环境可配置主备。离线包会创建名为 **本机 Broker** 的项目连接，URI 形如 `mqtt://<PUBLIC_HOST>:1883`。

可测试连接。密码加密存储，编辑页不回填明文。网页 **不直接** 连 Broker，由 Worker 订阅、由 API 入队下行。
