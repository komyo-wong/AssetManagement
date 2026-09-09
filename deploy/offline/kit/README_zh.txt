资产管理平台 离线安装包
========================

适用系统: Ubuntu 22.04 / 24.04，x86_64（amd64），全新机器即可。
不需要连接公网，不需要在客户现场编译。
英文说明见 README_en.txt。

1. 解压
-------
  tar -xzf asset-management-*-linux-amd64.tar.gz
  cd asset-management-<版本>

请解压到新目录，不要解压覆盖正在运行的旧目录。

2. 全新安装
----------
  sudo ./install_zh.sh

安装一开始会询问网页管理员用户名和密码（也可先在 config.env 填写 ROOT_USERNAME / ROOT_PASSWORD）。
不填写则停止安装，不会使用默认账号，也不会随机生成管理员密码。
PUBLIC_HOST 留空时自动探测本机第一块网卡 IP。云主机这块经常是内网地址，
请改成浏览器里要用的公网 IP 或域名，并写上 AUTH_ALLOWED_ORIGINS
（例如 http://公网IP,http://公网IP:80,http://127.0.0.1,http://localhost）。
对不上时登录会提示「跨站认证请求被拒绝」。改完 sudo ./restart.sh。
网页和接口必须同一个网址，不要另开 8080。

已有数据的机器禁止再跑安装脚本，请走第 3 步升级。

安装结束时，终端会打印网页地址、登录账号和密码，以及网关 MQTT 地址、用户名和密码。
请以该界面为准；也可事后在安装目录的 config.env 中查看。不要把密码写进对外说明书。

网页会自动出现「本机 Broker」，接入规则已预置，一般不用改。
网关 MQTT 地址一般为 mqtt://<本机IP>:1883。账号、密码和 Topic 请从安装结束界面，或网页「网关管理 → 接入配置」复制。

3. 升级（已有数据）
----------------
  把新包解压到新目录，不要覆盖旧目录。
  cd asset-management-<新版本>
  sudo ./upgrade_zh.sh

升级只会导入新镜像并切换 API / Worker / 网页。
不会覆盖数据目录，也不会用新包默认密码替换正在用的 config.env。
脚本会沿用 /etc/asset-management/install-root 指向的旧配置。
不允许降级。同版本再跑一次只重新导入镜像。

4. 日常命令
-----------
  sudo ./start.sh
  sudo ./stop.sh
  sudo ./restart.sh
  sudo ./status.sh
  sudo ./logs.sh
  sudo ./backup.sh
  sudo ./restore.sh <备份包>
  sudo ./reset_admin_zh.sh     # 忘记网页管理员密码
  sudo ./reset_admin_en.sh
  sudo ./uninstall.sh          # 保留数据
  sudo ./uninstall.sh --purge # 删除数据目录

5. 数据位置
-----------
  $DATA_DIR/postgres/
  $DATA_DIR/redis/
  $DATA_DIR/mosquitto/
  $DATA_DIR/documents/
  $DATA_DIR/backups/

默认 DATA_DIR=/var/lib/asset-management

6. 端口
-------
  80    网页（HTTP）
  443   网页（仅 ENABLE_HTTPS=true）
  1883  MQTT
  5432 / 6379 / 8080 / 8081  仅本机回环

如需 HTTPS：编辑正在使用的 config.env，ENABLE_HTTPS=true，PUBLIC_HOST=域名，
把 fullchain.pem、privkey.pem 放到当前安装目录的 certs/，再执行 sudo ./start.sh。

7. 使用说明书
-------------
解压后双击 guide/index.html 即可阅读，无需联网、无需上传。
也可执行:  ./guide/打开说明书.command
