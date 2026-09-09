# 客户离线安装包

给全新 Ubuntu 22.04 / 24.04 x86_64 机器用，不在客户现场编译。

## 打包装

在开发机（需 Docker、JDK 25、Maven，打包装时需要能拉镜像和 Docker .deb）：

```bash
bash deploy/offline/pack-offline.sh
```

产物（已 gitignore，不要提交；发布时上传到 GitHub Releases）：

`deploy/offline/dist/asset-management-<版本>-linux-amd64.tar.gz`

可选跳过步骤：

```bash
SKIP_JAVA=1 SKIP_WEB=1 bash deploy/offline/pack-offline.sh   # 只重打镜像和 .deb
SKIP_DEBS=1 bash deploy/offline/pack-offline.sh              # 不重新下载 Docker .deb
```

客户侧步骤见包内 `README_zh.txt` / `README_en.txt`：解压到新目录 → `sudo ./install_zh.sh` 或 `sudo ./install_en.sh`。已有数据只能走 `upgrade_zh.sh` / `upgrade_en.sh`，只导入新镜像，不覆盖数据目录和正在使用的 `config.env`。

## 包内脚本（不要改客户流程）

| 脚本 | 作用 |
|------|------|
| `install_zh.sh` / `install_en.sh` | 空机器安装。询问管理员账号密码，未设置则拒绝；若已有 Postgres 数据则拒绝 |
| `upgrade_zh.sh` / `upgrade_en.sh` | 导入新镜像并切换 API/Worker/Web，不覆盖 `$DATA_DIR` 和旧 `config.env` |
| `reset_admin_zh.sh` / `reset_admin_en.sh` | 重置网页管理员密码（用户名不变） |
| `uninstall.sh` | 停服务，保留 `$DATA_DIR` |
| `uninstall.sh --purge` | 连数据目录一起删 |
| `backup.sh` / `restore.sh` | 库 + 文档 |

数据只出现在 `$DATA_DIR`（默认 `/var/lib/asset-management`），由 `config.env` 配置。
