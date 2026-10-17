# Docker PostgreSQL 轻量备份

默认匹配当前 docker-compose：

- database service: `database`
- container: `build_postgres`
- database: `mydb`
- user: `myuser`

创建逻辑备份：

```bash
./ops/docker/backup-postgres.sh
```

备份默认保存在 macOS 宿主机的 `~/Backups/suji-finance`（如果脚本在具备 Docker socket
访问能力的宿主环境运行）。保留最近 10 份，并生成 SHA-256。

恢复前建议先停止 backend 写入：

```bash
./ops/docker/restore-postgres.sh /path/to/mydb_YYYYMMDD_HHMMSS.dump
```

Time Machine 继续作为主要整机灾备；这里的 dump 用于误删数据、数据库结构损坏等场景的快速恢复。
