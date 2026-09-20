import { Router } from 'express';
import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
import db, { nowStr, withTransaction } from '../db/database.ts';
import { hashPassword } from '../utils/helpers.ts';
import { authenticate, signToken } from '../middleware/auth.ts';
import type { UserRow, ResetTokenRow } from '../types.ts';

const router = Router();

// 对不存在的用户也执行一次同开销的 bcrypt 比较，抹平登录响应时序差异
const DUMMY_HASH = bcrypt.hashSync(crypto.randomBytes(16).toString('hex'), 10);

/** 登录 */
router.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ message: '请输入用户名和密码' });
  }
  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(String(username).trim()) as UserRow | undefined;
  if (!user) {
    bcrypt.compareSync(String(password), DUMMY_HASH);
    return res.status(401).json({ message: '用户名或密码错误' });
  }
  if (!bcrypt.compareSync(String(password), user.password_hash)) {
    return res.status(401).json({ message: '用户名或密码错误' });
  }
  const info = { id: user.id, username: user.username, name: user.name, role: user.role };
  res.json({ token: signToken(info, user.token_version), user: info });
});

/** 当前登录用户信息 */
router.get('/me', authenticate, (req, res) => {
  const user = db.prepare('SELECT id, username, name, role, created_at FROM users WHERE id = ?').get(req.user.id) as
    Omit<UserRow, 'password_hash'> | undefined;
  if (!user) return res.status(401).json({ message: '用户不存在' });
  res.json({ user });
});

/** 修改自己的密码 */
router.put('/password', authenticate, (req, res) => {
  const { oldPassword, newPassword } = req.body || {};
  if (!oldPassword || !newPassword) {
    return res.status(400).json({ message: '请输入原密码和新密码' });
  }
  if (String(newPassword).length < 6) {
    return res.status(400).json({ message: '新密码至少 6 位' });
  }
  const row = db.prepare('SELECT password_hash FROM users WHERE id = ?').get(req.user.id) as
    { password_hash: string } | undefined;
  if (!row || !bcrypt.compareSync(String(oldPassword), row.password_hash)) {
    return res.status(400).json({ message: '原密码错误' });
  }
  db.prepare('UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?').run(
    hashPassword(newPassword),
    req.user.id,
  );
  res.json({ message: '密码修改成功' });
});

/** 使用管理员发放的一次性找回码重置密码 */
router.post('/password/reset', (req, res) => {
  const { username, resetCode, newPassword } = req.body || {};
  if (!username || !resetCode || !newPassword) {
    return res.status(400).json({ message: '请填写用户名、找回码和新密码' });
  }
  if (String(newPassword).length < 6) {
    return res.status(400).json({ message: '新密码至少 6 位' });
  }

  const user = db.prepare('SELECT id FROM users WHERE username = ?').get(String(username).trim()) as
    { id: number } | undefined;
  const tokenHash = crypto.createHash('sha256').update(String(resetCode).trim().toUpperCase()).digest('hex');
  const token = user
    ? (db
        .prepare(
          `SELECT id FROM password_reset_tokens
           WHERE user_id = ? AND token_hash = ? AND used_at IS NULL AND expires_at > ?
           ORDER BY id DESC LIMIT 1`,
        )
        .get(user.id, tokenHash, nowStr()) as ResetTokenRow | undefined)
    : undefined;
  if (!user || !token) {
    return res.status(400).json({ message: '找回码无效或已过期' });
  }

  // 改密码、提升凭证版本号与作废找回码必须同时生效，避免作废失败时找回码被重复使用
  withTransaction(() => {
    db.prepare('UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?').run(
      hashPassword(newPassword),
      user.id,
    );
    db.prepare('UPDATE password_reset_tokens SET used_at = ? WHERE id = ?').run(nowStr(), token.id);
  });
  res.json({ message: '密码重置成功，请使用新密码登录' });
});

export default router;
