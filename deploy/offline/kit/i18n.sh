# shellcheck shell=bash
# Message catalog. AM_LANG is zh or en.

i18n_text() {
  local key="${1:-}"
  if [[ "${AM_LANG:-zh}" == "en" ]]; then
    case "$key" in
      error_prefix) echo "Error:" ;;
      need_root) echo "Run as root: sudo %s %s" ;;
      only_amd64) echo "Only x86_64 / amd64 is supported" ;;
      no_os_release) echo "Cannot detect the operating system" ;;
      only_ubuntu) echo "Only Ubuntu 22.04 / 24.04 is supported" ;;
      ubuntu_codename) echo "Only Ubuntu 22.04 (jammy) or 24.04 (noble) is supported, current: %s" ;;
      missing_docker_debs_dir) echo "Install pack is missing Docker packages: %s" ;;
      installing_docker) echo ">>> Installing Docker (offline %s .deb)" ;;
      no_debs) echo "No .deb files found in %s" ;;
      docker_install_failed) echo "Docker install failed. Check iptables/deps or the dpkg -i output" ;;
      compose_plugin_missing) echo "docker compose plugin is missing; check packages/%s for docker-compose-plugin" ;;
      missing_images) echo "Missing image archive %s; use the full offline pack" ;;
      loading_images) echo ">>> Loading Docker images (this may take a few minutes)" ;;
      missing_example) echo "Missing config.env.example; the pack is incomplete" ;;
      wrote_default_config) echo ">>> Created %s from the pack template" ;;
      using_config_admin) echo ">>> Using ROOT_USERNAME / ROOT_PASSWORD from config.env" ;;
      admin_credentials_required) echo "Admin username and password are required. Set ROOT_USERNAME and ROOT_PASSWORD in config.env, or run the installer in a terminal so it can prompt you. Install will not continue without them." ;;
      prompt_admin_intro) echo ">>> Set the web admin account. Install will stop if you leave these empty." ;;
      prompt_admin_user) echo "Admin username (3–32 chars, start with a letter; letters, digits, . _ -):" ;;
      prompt_admin_password) echo "Admin password (8–64 chars; letters, digits, and ! @ % ^ * _ + = . , : / ? -):" ;;
      prompt_admin_password_again) echo "Confirm password:" ;;
      admin_username_required) echo "Admin username was empty. Install stopped." ;;
      admin_password_required) echo "Admin password was empty. Install stopped." ;;
      admin_password_mismatch) echo "Passwords do not match. Install stopped." ;;
      invalid_admin_username) echo "Invalid admin username. Use 3–32 characters, starting with a letter; only letters, digits, dot, underscore and hyphen." ;;
      invalid_admin_password) echo "Invalid admin password. Use 8–64 characters: letters, digits, and ! @ % ^ * _ + = . , : / ? - (no spaces)." ;;
      cannot_detect_host) echo "Cannot detect PUBLIC_HOST; set the machine IP or domain in config.env" ;;
      generated_mqtt_password) echo ">>> Generated MQTT_PASSWORD and wrote it to config.env (platform Worker account, not gateway access credentials)" ;;
      https_needs_certs) echo "ENABLE_HTTPS=true requires certs/fullchain.pem and certs/privkey.pem" ;;
      mosquitto_passwd) echo ">>> Writing Mosquitto password file" ;;
      mqtt_users) echo ">>> Registering gateway MQTT users on the broker" ;;
      missing_config) echo "Cannot find %s. Fresh install: sudo ./install_zh.sh or sudo ./install_en.sh" ;;
      api_logs) echo ">>> Recent API logs" ;;
      wait_api) echo ">>> Waiting for API (first-time database setup may take 1–3 minutes)" ;;
      api_unhealthy) echo "API did not become healthy in time. Run ./logs.sh api" ;;
      mqtt_env) echo ">>> MQTT bootstrap variables in the API container" ;;
      bootstrap_logs) echo ">>> bootstrap logs" ;;
      wait_mqtt) echo ">>> Checking local MQTT connection and Topic rules" ;;
      mqtt_ready) echo "    Local broker and Topic rules are ready" ;;
      mqtt_skipped) echo "Local MQTT was not written: bootstrap variables are empty. Set PUBLIC_HOST / MQTT_USERNAME / MQTT_PASSWORD in config.env, then sudo ./start.sh" ;;
      mqtt_failed) echo "Local MQTT write failed. Run ./logs.sh api and look for MQTT bootstrap failed" ;;
      mqtt_retry) echo ">>> MQTT or Topic rules not found, recreating API" ;;
      mqtt_missing) echo "Local MQTT or Topic rules were not created. Run ./logs.sh api" ;;
      start_infra) echo ">>> Starting Postgres / Redis / Mosquitto" ;;
      start_api) echo ">>> Starting API" ;;
      start_apps) echo ">>> Starting Worker and web" ;;
      install_done) echo "Install finished." ;;
      web_url) echo "  Web:        %s" ;;
      account) echo "  Account:    %s" ;;
      password) echo "  Password:   %s" ;;
      data_dir) echo "  Data dir:   %s" ;;
      mqtt_lan) echo "Gateway MQTT (LAN):" ;;
      mqtt_uri) echo "  URI:        mqtt://%s:%s" ;;
      mqtt_user) echo "  Username:   %s" ;;
      mqtt_pass) echo "  Password:   %s" ;;
      mqtt_topics) echo "  Topics:     uplink GwData / downlink SrvData (already in the platform)" ;;
      commands) echo "Commands (in this directory):" ;;
      cmd_status) echo "  sudo ./status.sh" ;;
      cmd_logs) echo "  sudo ./logs.sh" ;;
      cmd_restart) echo "  sudo ./restart.sh" ;;
      cmd_reset_admin) echo "  sudo ./reset_admin_zh.sh   # or reset_admin_en.sh" ;;
      cmd_uninstall) echo "  sudo ./uninstall.sh          # keep data" ;;
      cmd_purge) echo "  sudo ./uninstall.sh --purge  # delete %s" ;;
      use_upgrade) echo "This machine already has data. Do not run install. Unpack the new pack and run sudo ./upgrade_zh.sh or sudo ./upgrade_en.sh. Existing data and config.env will be kept." ;;
      use_install) echo "No existing install found. For a new machine run sudo ./install_zh.sh or sudo ./install_en.sh" ;;
      upgrade_need_config) echo "Data exists at %s but the previous config.env was not found. Copy the old config.env into this directory, then run upgrade again. The pack default passwords will not be used." ;;
      upgrade_downgrade) echo "Refusing to downgrade: installed %s, this pack is %s" ;;
      upgrade_same) echo ">>> Same version %s; reloading images without touching data" ;;
      upgrade_from_to) echo ">>> Upgrading %s -> %s; data directory will not be modified" ;;
      upgrade_keep_config) echo ">>> Reusing installed config.env (passwords and data path unchanged)" ;;
      upgrade_stop_old) echo ">>> Stopping the previous pack without deleting volumes" ;;
      upgrade_apps_only) echo ">>> Recreating API / Worker / web only (Postgres data is left as-is)" ;;
      upgrade_done) echo "Upgrade finished. Data directory was not overwritten: %s" ;;
      started) echo "Started." ;;
      stopped) echo "Stopped (data remains in %s)." ;;
      restarted) echo "Restarted." ;;
      uninstalled_keep) echo "Uninstalled. Data remains in %s" ;;
      uninstalled_purge_warn) echo "Will delete data directory: %s" ;;
      uninstalled_purged) echo "Uninstalled and data removed." ;;
      purge_hint) echo "To delete data as well: sudo %s --purge" ;;
      backup_db) echo ">>> Dumping database" ;;
      backup_docs) echo ">>> Archiving documents" ;;
      backup_done) echo "Backup written: %s" ;;
      restore_usage) echo "Usage: sudo ./restore.sh /var/lib/asset-management/backups/asset-management-YYYYMMDD-HHMMSS.tar.gz" ;;
      restore_missing_sql) echo "Backup is missing database.sql" ;;
      restore_stop) echo ">>> Stopping API / Worker / Web" ;;
      restore_db) echo ">>> Restoring database" ;;
      restore_docs) echo ">>> Restoring documents" ;;
      restore_start) echo ">>> Starting services" ;;
      restore_done) echo "Restore finished." ;;
      api_not_ready) echo "API is not ready" ;;
      postgres_not_ready) echo "Postgres did not become ready. Run sudo ./start.sh, then try again." ;;
      reset_admin_intro) echo ">>> Reset the web admin password. Username is unchanged. Empty input stops the script." ;;
      reset_admin_need_tty) echo "Run this in a terminal: sudo ./reset_admin_en.sh" ;;
      reset_admin_wait_db) echo ">>> Updating the admin password in the database" ;;
      reset_admin_hash_failed) echo "Could not hash the new password" ;;
      reset_admin_missing) echo "No admin account found in the database. Install the platform first." ;;
      reset_admin_done) echo "Admin password updated for account: %s. Sign in with the password you just entered." ;;
      use_install_zh_en) echo "Use: sudo ./install_zh.sh   or   sudo ./install_en.sh" ;;
      *) echo "$key" ;;
    esac
    return
  fi
  case "$key" in
    error_prefix) echo "错误:" ;;
    need_root) echo "请用 root 执行: sudo %s %s" ;;
    only_amd64) echo "只支持 x86_64 / amd64" ;;
    no_os_release) echo "无法识别操作系统" ;;
    only_ubuntu) echo "只支持 Ubuntu 22.04 / 24.04" ;;
    ubuntu_codename) echo "只支持 Ubuntu 22.04 (jammy) 或 24.04 (noble)，当前: %s" ;;
    missing_docker_debs_dir) echo "安装包缺少 Docker 软件包目录: %s" ;;
    installing_docker) echo ">>> 安装 Docker（离线 %s .deb）" ;;
    no_debs) echo "未找到 %s/*.deb" ;;
    docker_install_failed) echo "Docker 安装失败。请确认系统已有 iptables 等依赖，或查看: dpkg -i 报错" ;;
    compose_plugin_missing) echo "docker compose 插件未装上，请检查 packages/%s 是否含 docker-compose-plugin" ;;
    missing_images) echo "缺少镜像包 %s，请使用完整离线安装包" ;;
    loading_images) echo ">>> 导入 Docker 镜像（可能需要几分钟）" ;;
    missing_example) echo "缺少 config.env.example，安装包不完整" ;;
    wrote_default_config) echo ">>> 已根据模板生成 %s" ;;
    using_config_admin) echo ">>> 使用 config.env 中已填写的 ROOT_USERNAME / ROOT_PASSWORD" ;;
    admin_credentials_required) echo "必须设置网页管理员用户名和密码。请在 config.env 填写 ROOT_USERNAME 与 ROOT_PASSWORD，或在终端交互安装。未设置则不执行安装。" ;;
    prompt_admin_intro) echo ">>> 请设置网页管理员账号。留空将停止安装。" ;;
    prompt_admin_user) echo "管理员用户名（3–32 位，字母开头，可含字母数字 . _ -）：" ;;
    prompt_admin_password) echo "管理员密码（8–64 位，字母数字及 ! @ % ^ * _ + = . , : / ? -，不能有空格）：" ;;
    prompt_admin_password_again) echo "再输入一次密码：" ;;
    admin_username_required) echo "管理员用户名为空，已停止安装。" ;;
    admin_password_required) echo "管理员密码为空，已停止安装。" ;;
    admin_password_mismatch) echo "两次密码不一致，已停止安装。" ;;
    invalid_admin_username) echo "管理员用户名不合法：3–32 位，字母开头，只能含字母、数字、点、下划线和连字符。" ;;
    invalid_admin_password) echo "管理员密码不合法：8–64 位，可用字母数字和 ! @ % ^ * _ + = . , : / ? -，不能有空格。" ;;
    cannot_detect_host) echo "无法自动探测 PUBLIC_HOST，请在 config.env 中填写本机 IP 或域名" ;;
    generated_mqtt_password) echo ">>> 已生成 MQTT_PASSWORD 并写入 config.env（平台 Worker 账号，不是网关接入配置）" ;;
    https_needs_certs) echo "ENABLE_HTTPS=true 时需要 certs/fullchain.pem 和 certs/privkey.pem" ;;
    mosquitto_passwd) echo ">>> 写入 Mosquitto 密码文件" ;;
    mqtt_users) echo ">>> 把网页新建的网关账号写入 Mosquitto" ;;
    missing_config) echo "找不到 %s。全新安装请执行 sudo ./install_zh.sh 或 sudo ./install_en.sh" ;;
    api_logs) echo ">>> API 最近日志" ;;
    wait_api) echo ">>> 等待 API 就绪（首次建库可能需要 1–3 分钟）" ;;
    api_unhealthy) echo "API 未能在超时时间内变为健康，请执行 ./logs.sh api 查看原因" ;;
    mqtt_env) echo ">>> API 里的 MQTT 引导变量" ;;
    bootstrap_logs) echo ">>> bootstrap 日志" ;;
    wait_mqtt) echo ">>> 确认本机 MQTT 与 Topic 规则已写入" ;;
    mqtt_ready) echo "    本机 Broker 与 Topic 规则已就绪" ;;
    mqtt_skipped) echo "本机 MQTT 未写入：引导变量为空。请确认 config.env 里有 PUBLIC_HOST / MQTT_USERNAME / MQTT_PASSWORD 后执行 sudo ./start.sh" ;;
    mqtt_failed) echo "本机 MQTT 写入失败。请执行 ./logs.sh api 查看 MQTT bootstrap failed" ;;
    mqtt_retry) echo ">>> 未检测到本机 MQTT 或 Topic 规则，重建 API 再试" ;;
    mqtt_missing) echo "本机 MQTT 或 Topic 规则未自动写入。请执行 ./logs.sh api 查看原因" ;;
    start_infra) echo ">>> 启动 Postgres / Redis / Mosquitto" ;;
    start_api) echo ">>> 启动 API" ;;
    start_apps) echo ">>> 启动 Worker 与网页" ;;
    install_done) echo "安装完成。" ;;
    web_url) echo "  网页:     %s" ;;
    account) echo "  账号:     %s" ;;
    password) echo "  密码:     %s" ;;
    data_dir) echo "  数据目录: %s" ;;
    mqtt_lan) echo "网关 MQTT（局域网）:" ;;
    mqtt_uri) echo "  地址:     mqtt://%s:%s" ;;
    mqtt_user) echo "  用户名:   %s" ;;
    mqtt_pass) echo "  密码:     %s" ;;
    mqtt_topics) echo "  Topic:    上行 GwData / 下行 SrvData（已写入平台）" ;;
    commands) echo "常用命令（在本目录）:" ;;
    cmd_status) echo "  sudo ./status.sh" ;;
    cmd_logs) echo "  sudo ./logs.sh" ;;
    cmd_restart) echo "  sudo ./restart.sh" ;;
    cmd_reset_admin) echo "  sudo ./reset_admin_zh.sh   # 或 reset_admin_en.sh" ;;
    cmd_uninstall) echo "  sudo ./uninstall.sh          # 保留数据" ;;
    cmd_purge) echo "  sudo ./uninstall.sh --purge  # 删除 %s" ;;
    use_upgrade) echo "这台机器已有数据，不能再执行安装。请解压新版本后执行 sudo ./upgrade_zh.sh 或 sudo ./upgrade_en.sh。不会覆盖已有数据和 config.env。" ;;
    use_install) echo "未检测到已安装环境。全新机器请执行 sudo ./install_zh.sh 或 sudo ./install_en.sh" ;;
    upgrade_need_config) echo "数据目录 %s 已有库，但找不到原先的 config.env。请把旧包里的 config.env 复制到本目录后再升级。不会使用新包里的默认密码。" ;;
    upgrade_downgrade) echo "拒绝降级：已安装 %s，当前包是 %s" ;;
    upgrade_same) echo ">>> 版本相同 %s；只重新导入镜像，不改动数据" ;;
    upgrade_from_to) echo ">>> 升级 %s -> %s；不会修改数据目录" ;;
    upgrade_keep_config) echo ">>> 沿用已安装的 config.env（密码和数据路径不变）" ;;
    upgrade_stop_old) echo ">>> 停止旧包进程，不删除数据卷" ;;
    upgrade_apps_only) echo ">>> 只重建 API / Worker / 网页（Postgres 数据保持原样）" ;;
    upgrade_done) echo "升级完成。数据目录未被覆盖: %s" ;;
    started) echo "已启动。" ;;
    stopped) echo "已停止（数据保留在 %s）。" ;;
    restarted) echo "已重启。" ;;
    uninstalled_keep) echo "已卸载。数据仍保留在 %s" ;;
    uninstalled_purge_warn) echo "将删除数据目录: %s" ;;
    uninstalled_purged) echo "已卸载并清除数据。" ;;
    purge_hint) echo "如需连数据一起删除: sudo %s --purge" ;;
    backup_db) echo ">>> 导出数据库" ;;
    backup_docs) echo ">>> 打包文档" ;;
    backup_done) echo "备份已写入: %s" ;;
    restore_usage) echo "用法: sudo ./restore.sh /var/lib/asset-management/backups/asset-management-YYYYMMDD-HHMMSS.tar.gz" ;;
    restore_missing_sql) echo "备份中缺少 database.sql" ;;
    restore_stop) echo ">>> 停止 API / Worker / Web" ;;
    restore_db) echo ">>> 恢复数据库" ;;
    restore_docs) echo ">>> 恢复文档" ;;
    restore_start) echo ">>> 启动服务" ;;
    restore_done) echo "恢复完成。" ;;
    api_not_ready) echo "API 未就绪" ;;
    postgres_not_ready) echo "Postgres 未就绪。请先执行 sudo ./start.sh 后再试。" ;;
    reset_admin_intro) echo ">>> 重置网页管理员密码。用户名不变。留空则停止。" ;;
    reset_admin_need_tty) echo "请在终端执行: sudo ./reset_admin_zh.sh" ;;
    reset_admin_wait_db) echo ">>> 正在更新数据库中的管理员密码" ;;
    reset_admin_hash_failed) echo "无法生成密码哈希" ;;
    reset_admin_missing) echo "数据库中没有管理员账号。请先完成安装。" ;;
    reset_admin_done) echo "已更新管理员 %s 的密码。请用刚才输入的新密码登录。" ;;
    use_install_zh_en) echo "请使用: sudo ./install_zh.sh   或   sudo ./install_en.sh" ;;
    *) echo "$key" ;;
  esac
}

t() {
  local key="${1:-}"
  shift || true
  local fmt
  fmt="$(i18n_text "$key")"
  if [[ "$#" -gt 0 ]]; then
    # shellcheck disable=SC2059
    printf "${fmt}\n" "$@"
  else
    printf '%s\n' "$fmt"
  fi
}
