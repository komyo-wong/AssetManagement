---
description: MQTT Integration → Connections
---

# Connections

Path: **MQTT Integration → Connections**.

Broker URI, credentials, environment; production can use primary/standby. The pack creates **Local broker**, e.g. `mqtt://<PUBLIC_HOST>:1883`. Connection test is available. Secrets are stored encrypted and not echoed back. The browser does not speak MQTT; the worker subscribes and the API enqueues downlink.
