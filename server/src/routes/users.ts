import { Router } from 'express';
import crypto from 'node:crypto';
import db, { nowStr, fmtDate, withTransaction } from '../db/database.ts';
import { hashPassword, likePattern, parsePagination, isUniqueConstraintError } from '../utils/helpers.ts';
import { authenticate, requireAdmin } from '../middleware/auth.ts';
import type { UserRow, UserPublicRow } from '../types.ts';

const router = Router();
router.use(authenticate, requireAdmin);

/** 用户列表（分页 + 关键字搜索用户名/姓名） */
router.get('/', (req, res) => {
  const { page, pageSize } = parsePagination(req.query);
  const kw = likePattern(req.query.keyword);

  const totalRow = db
    .prepare("SELECT COUNT(*) AS c FROM users WHERE username LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\'")
    .all(kw, kw)[0] as { c: number };
  const list = db
    .prepare(
      `SELECT id, username, name, role, created_at FROM users
       WHERE username LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\'
       ORDER BY id DESC LIMIT ? OFFSET ?`,
    )
    .all(kw, kw, pageSize, (page - 1) * pageSize) as UserPublicRow[];

  res.json({ list, total: totalRow.c, page, pageSize });
});

/** 新增用户 */
router.post('/', (req, res) => {
  const { username, password, name, role } = req.body || {};
  if (!username || !/^[A-Za-z0-9_]{2,20}$/.test(String(username))) {
    return res.status(400).json({ message: '用户名需为 2-20 位字母、数字或下划线' });
  }
  if (!password || String(password).length < 6) {
    return res.status(400).json({ message: '密码至少 6 位' });
  }
  if (!name || !String(name).trim()) {
    return res.status(400).json({ message: '请填写姓名' });
  }
  if (!['student', 'admin'].includes(role)) {
    return res.status(400).json({ message: '角色无效' });
  }
  try {
    const result = db
      .prepare('INSERT INTO users (username, password_hash, name, role, created_at) VALUES (?, ?, ?, ?, ?)')
      .run(String(username), hashPassword(password), String(name).trim(), role, nowStr());
    res.json({ id: Number(result.lastInsertRowid) });
  } catch (err) {
    // 并发创建撞用户名唯一约束时返回业务错误而非 500
    if (isUniqueConstraintError(err)) {
      return res.status(400).json({ message: '用户名已存在' });
    }
    throw err;
  }
});

/** 生成一次性密码找回码（管理员交给用户使用） */
router.post('/:id/password-reset-token', (req, res) => {
  const id = Number(req.params.id);
  const user = db.prepare('SELECT id FROM users WHERE id = ?').all(id)[0] as { id: number } | undefined;
  if (!user) return res.status(404).json({ message: '用户不存在' });

  const code = crypto.randomBytes(6).toString('hex').toUpperCase();
  const tokenHash = crypto.createHash('sha256').update(code).digest('hex');
  const expiresAtStr = fmtDate(new Date(Date.now() + 15 * 60 * 1000));
  // 作废旧码与发放新码必须同时生效
  withTransaction(() => {
    db.prepare('UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL').run(nowStr(), id);
    db.prepare(
      'INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)',
    ).run(id, tokenHash, expiresAtStr, nowStr());
  });
  res.json({ code, expiresAt: expiresAtStr });
});

/** 编辑用户（姓名/角色，可顺带重置密码） */
router.put('/:id', (req, res) => {
  const id = Number(req.params.id);
  const user = db.prepare('SELECT * FROM users WHERE id = ?').all(id)[0] as UserRow | undefined;
  if (!user) return res.status(404).json({ message: '用户不存在' });

  const name = String(req.body?.name ?? user.name).trim() || user.name;
  const role = req.body?.role ?? user.role;
  if (!['student', 'admin'].includes(role)) {
    return res.status(400).json({ message: '角色无效' });
  }
  // 防止把最后一个管理员降级
  if (user.role === 'admin' && role !== 'admin') {
    const adminsRow = db.prepare("SELECT COUNT(*) AS c FROM users WHERE role = 'admin'").get() as { c: number };
    if (adminsRow.c <= 1) return res.status(400).json({ message: '系统至少需要保留一名管理员' });
  }

  let sql = 'UPDATE users SET name = ?, role = ?';
  const params: (string | number)[] = [name, role];
  if (req.body?.password) {
    if (String(req.body.password).length < 6) {
      return res.status(400).json({ message: '密码至少 6 位' });
    }
    sql += ', password_hash = ?, token_version = token_version + 1';
    params.push(hashPassword(req.body.password));
  }
  sql += ' WHERE id = ?';
  params.push(id);
  db.prepare(sql).run(...params);
  res.json({ message: '保存成功' });
});

/** 删除用户（不允许删除自己） */
router.delete('/:id', (req, res) => {
  const id = Number(req.params.id);
  if (id === req.user.id) {
    return res.status(400).json({ message: '不能删除当前登录账号' });
  }
  const user = db.prepare('SELECT id FROM users WHERE id = ?').all(id)[0] as { id: number } | undefined;
  if (!user) return res.status(404).json({ message: '用户不存在' });
  withTransaction(() => {
    db.prepare('DELETE FROM password_reset_tokens WHERE user_id = ?').run(id);
    db.prepare('DELETE FROM checkin_records WHERE user_id = ?').run(id);
    db.prepare('DELETE FROM users WHERE id = ?').run(id);
  });
  res.json({ message: '删除成功' });
});

export default router;
