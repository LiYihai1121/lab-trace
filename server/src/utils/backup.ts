import fs from 'node:fs';
import path from 'node:path';
import db from '../db/database.ts';
import type { BackupableDatabase } from '../db/database.ts';

/** 备份文件名保留数量（超出后删除最旧的） */
const RETAIN_COUNT = 10;

/**
 * 将当前数据库 serialize 为一致性的快照文件（WAL 模式下已包含已提交事务）。
 * 每日调用一次；失败仅记录日志，不影响服务运行。
 */
export function backupDatabase(): void {
  try {
    const dbPath = process.env.DB_PATH || path.join(process.cwd(), 'data', 'lab-checkin.db');
    if (dbPath === ':memory:') return; // 内存库无备份意义

    const dir = path.join(path.dirname(dbPath), 'backups');
    fs.mkdirSync(dir, { recursive: true });

    const stamp = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    const target = path.join(dir, `lab-trace-${stamp}.db`);
    const buf = (db as BackupableDatabase).serialize();
    fs.writeFileSync(target, Buffer.from(buf));

    // 保留最近 N 份，防止磁盘无限增长
    const files = fs
      .readdirSync(dir)
      .filter((f) => f.startsWith('lab-trace-') && f.endsWith('.db'))
      .sort()
      .reverse();
    for (const old of files.slice(RETAIN_COUNT)) {
      fs.unlinkSync(path.join(dir, old));
    }
    console.info(`[backup] 数据库已备份至 ${target}（保留最近 ${RETAIN_COUNT} 份）`);
  } catch (err) {
    console.error('[backup] 数据库备份失败', err);
  }
}

/** 启动每日备份：立即执行一次，之后每 24 小时执行 */
export function startDailyBackup(): void {
  backupDatabase();
  setInterval(backupDatabase, 24 * 60 * 60 * 1000).unref();
}