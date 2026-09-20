import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import bcrypt from 'bcryptjs';
import { environment } from '../config/environment.ts';

// node:sqlite 通过 require 加载：部分打包/测试工具链尚不能静态解析该内置模块
const require = createRequire(import.meta.url);
const { DatabaseSync } = require('node:sqlite') as typeof import('node:sqlite');
import type { StatementSync } from 'node:sqlite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, '..', '..', 'data');
fs.mkdirSync(dataDir, { recursive: true });

// 查询结果按调用点的 `as` 断言取形状；此处统一放宽为 any 以承载任意行结构
type UntypedStatement = Omit<StatementSync, 'get' | 'all'> & {
  get(...params: any[]): any;
  all(...params: any[]): any[];
};

/** 含 serialize 的完整数据库类型（备份用）；prepare 保持宽松签名 */
export type BackupableDatabase = Omit<InstanceType<typeof DatabaseSync>, 'prepare' | 'serialize'> & {
  prepare(sql: string): UntypedStatement;
  serialize(): Uint8Array;
};

// 默认数据库文件按环境区分（测试环境独立建库），DB_PATH 显式设置时优先
const db = new DatabaseSync(process.env.DB_PATH || path.join(dataDir, environment.dbFile)) as BackupableDatabase;

// 建表与索引：幂等执行，重复启动不破坏已有数据
const SCHEMA_STATEMENTS = [
  'PRAGMA journal_mode = WAL',

  `CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    name          TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('student','admin')),
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT NOT NULL
  )`,

  `CREATE TABLE IF NOT EXISTS checkin_codes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT UNIQUE NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
  )`,

  `CREATE TABLE IF NOT EXISTS checkin_records (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL REFERENCES users(id),
    checkin_time     TEXT NOT NULL,
    checkout_time    TEXT,
    duration_minutes INTEGER,
    status           TEXT NOT NULL DEFAULT 'checked_in' CHECK (status IN ('checked_in','completed')),
    code_id          INTEGER
  )`,

  `CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id),
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used_at    TEXT,
    created_at TEXT NOT NULL
  )`,

  'CREATE INDEX IF NOT EXISTS idx_records_user      ON checkin_records(user_id)',
  'CREATE INDEX IF NOT EXISTS idx_records_status    ON checkin_records(status)',
  'CREATE INDEX IF NOT EXISTS idx_records_time      ON checkin_records(checkin_time)',
  'CREATE INDEX IF NOT EXISTS idx_reset_tokens_hash ON password_reset_tokens(token_hash)',
];
for (const sql of SCHEMA_STATEMENTS) {
  db.prepare(sql).run();
}

// 外键约束需每个连接单独开启
db.prepare('PRAGMA foreign_keys = ON').run();

// 轻量迁移：为既有库补充 token_version 列（建表语句只对新库生效）
const userColumns = (db.prepare('PRAGMA table_info(users)').all() as Array<{ name: string }>).map((c) => c.name);
if (!userColumns.includes('token_version')) {
  db.prepare('ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0').run();
}

// 同一用户同时只允许一条进行中的签到记录（并发签到在数据库层兜底）；
// 若历史数据已存在重复进行中记录，先收敛为已结束再建唯一索引
const UNIQUE_ACTIVE_INDEX_SQL =
  "CREATE UNIQUE INDEX IF NOT EXISTS idx_records_user_active ON checkin_records(user_id) WHERE status = 'checked_in'";
try {
  db.prepare(UNIQUE_ACTIVE_INDEX_SQL).run();
} catch {
  db.prepare(
    "UPDATE checkin_records SET status = 'completed', checkout_time = checkin_time, duration_minutes = 0 WHERE status = 'checked_in' AND id NOT IN (SELECT MAX(id) FROM checkin_records WHERE status = 'checked_in' GROUP BY user_id)",
  ).run();
  db.prepare(UNIQUE_ACTIVE_INDEX_SQL).run();
}
db.prepare('CREATE INDEX IF NOT EXISTS idx_reset_tokens_user ON password_reset_tokens(user_id)').run();
db.prepare('CREATE INDEX IF NOT EXISTS idx_codes_expires ON checkin_codes(expires_at)').run();
db.prepare('CREATE INDEX IF NOT EXISTS idx_records_code ON checkin_records(code_id)').run();

/** 本地时间字符串 YYYY-MM-DD HH:mm:ss */
export function nowStr(): string {
  return fmtDate(new Date());
}

export function fmtDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** 在事务中执行 fn；fn 抛错时回滚并原样抛出 */
export function withTransaction<T>(fn: () => T): T {
  db.prepare('BEGIN IMMEDIATE').run();
  try {
    const result = fn();
    db.prepare('COMMIT').run();
    return result;
  } catch (err) {
    db.prepare('ROLLBACK').run();
    throw err;
  }
}

/** 首次启动时创建默认管理员（可通过环境变量控制） */
const userCount = (db.prepare('SELECT COUNT(*) AS c FROM users').get() as { c: number }).c;
if (userCount === 0 && process.env.CREATE_DEFAULT_ADMIN !== 'false') {
  const initPassword = process.env.ADMIN_PASSWORD || 'admin123';
  db.prepare('INSERT INTO users (username, password_hash, name, role, created_at) VALUES (?, ?, ?, ?, ?)').run(
    'admin',
    bcrypt.hashSync(initPassword, 10),
    '系统管理员',
    'admin',
    nowStr(),
  );
  console.info(
    '[init] 已创建默认管理员账号：admin。请尽快修改初始密码（若需要自定义，请设置 ADMIN_PASSWORD 环境变量或将 CREATE_DEFAULT_ADMIN=false 禁用默认创建）',
  );
} else if (userCount === 0) {
  console.warn('[init] 未创建默认管理员（CREATE_DEFAULT_ADMIN=false）。请通过管理员脚本或迁移添加管理员账号。');
}

export default db;
