# 变更日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 约定，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Fixed

- 修复 Docker 容器内时区默认为 UTC 导致签到/签退时间与「今日」统计日期边界偏移 8 小时的问题：`docker-compose.yml` 为 test/prod 服务注入 `TZ`（默认 `Asia/Shanghai`，可用环境变量覆盖）；`server/.env.production.example` 补充 TZ 说明

### Added

- **凭证版本号（token_version）**：修改或重置密码后，此前签发的 JWT 立即失效，防止旧 token 继续访问
- **每日数据库备份**：服务启动时及每 24 小时自动将 SQLite 快照备份至 `server/data/backups/`，保留最近 10 份

### Changed

- 项目更名为 **LabTrace**：GitHub 仓库 `lab-checkin` → `lab-trace`，双包更名 `lab-trace-server` / `lab-trace-web`，镜像路径迁移至 `ghcr.io/liyihai1121/lab-trace`，界面品牌（Logo/kicker/标题）与全部文档同步更新；SQLite 默认数据文件名保持 `lab-checkin.db` 不变，避免存量数据迁移

### Added

- 镜像发布流水线 `release.yml`：push `v*` tag 或手动触发时构建镜像并推送 GHCR（`ghcr.io/liyihai1121/lab-trace`，tag 号 + latest），CI 内完成容器冒烟验收（`/api/health` 的 environment/version 与 tag 一致性断言）

## [1.1.0] - 2026-09-06

### Added

- 工程化交付升级：ESLint/Prettier 双包质量门禁、Git 提交钩子（pre-commit 门禁与 Conventional Commits 校验）、三环境 Docker Compose 交付编排（test/prod profile、健康检查、独立数据卷）
- CI 流水线升级：类型检查 + Lint + 测试 + 生产/测试双构建 + 版本一致性检查
- VSCode 全栈开发环境配置：扩展推荐、三环境调试（后端/前端/浏览器/Vitest）与质量门禁任务
- 模块化 skill 体系（后端/前端/测试/安全/交付）与业界选型参考文档

### Changed

- 新增 `.gitattributes`：仓库统一 LF，与 Prettier、.editorconfig 对齐

### Removed

- 删除三环境改造后失效的 `web/.env.example`（前端 mode 环境文件已入库）

## [1.0.0] - 2026-09-05

### Added

- 三大环境（开发 / 测试 / 生产）配置体系：按 `NODE_ENV` 区分端口、数据库与界面标识，页面标题后缀、环境徽标、`/api/health` 环境字段、启动日志与构建产物目录（`dist` / `dist-test`）全链路可区分
- 全量 TypeScript 化：后端经 Node 原生类型剥离直接运行，前端 `vue-tsc` 类型检查，双包 strict 模式
- 单容器部署支持：后端同源托管前端构建产物，提供 Dockerfile 与部署文档

### Changed

- 运行时要求提升至 Node.js >= 22.18（`node:sqlite` 与原生类型剥离）
- 环境变量模板拆分为 `server/.env.development`、`server/.env.test`（入库、无密钥）与 `server/.env.production.example`（生产模板）

## [0.x] - 2026-09-04

### Added

- 实验室签到签退、动态二维码、统计看板、记录管理和用户管理功能。
- 企业级版本号、提交、发布、变更日志与回滚规范。
