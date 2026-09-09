---
description: MQTT Integration → Message monitor
---

# Message monitor

Path: **MQTT Integration → Message monitor**.

Topic, QoS, parse result, redacted payload preview — to see whether the gateway publishes and the worker consumes.

The page shows the **last ~500 messages (about 1 hour)**. Successfully parsed frames update beacons/gateways and are **not stored**. Parse failures stay in the database for a few days. After a worker or Redis restart the list is empty until new frames arrive.
