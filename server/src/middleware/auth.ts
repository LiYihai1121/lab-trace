import jwt from 'jsonwebtoken';
import type { Request, Response, NextFunction } from 'express';
import type { SignOptions, JwtPayload } from 'jsonwebtoken';
import db from '../db/database.ts';
import type { AuthUser } from '../types.ts';

// 生产环境必须显式配置 JWT_SECRET，防止使用公开默认值签发可伪造的 token
if (process.env.NODE_ENV === 'production' && !process.env.JWT_SECRET) {
  throw new Error('生产环境必须设置 JWT_SECRET 环境变量');
}
const JWT_SECRET = process.env.JWT_SECRET || 'lab-trace-dev-secret-change-in-production';
const TOKEN_TTL: SignOptions['expiresIn'] = '24h';

/** 签发 JWT：payload 为用户基本信息 + 凭证版本号（改密后旧 token 立即失效） */
export function signToken(user: AuthUser, tokenVersion: number): string {
  return jwt.sign(
    { id: user.id, username: user.username, name: user.name, role: user.role, tv: tokenVersion },
    JWT_SECRET,
    { expiresIn: TOKEN_TTL },
  );
}

/**
 * 登录校验中间件：验证 token 后实时读取用户，使角色调整、账号删除、密码修改即时生效
 * （tv 与库中 token_version 不一致即视为旧凭证）
 */
export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const header = req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) {
    res.status(401).json({ message: '未登录' });
    return;
  }
  let payload: JwtPayload;
  try {
    payload = jwt.verify(header.slice(7), JWT_SECRET) as JwtPayload;
  } catch {
    res.status(401).json({ message: '登录已过期，请重新登录' });
    return;
  }
  const user = db.prepare('SELECT id, username, name, role, token_version FROM users WHERE id = ?').all(payload.id)[0] as
    (AuthUser & { token_version: number }) | undefined;
  if (!user) {
    res.status(401).json({ message: '账号不存在或已被删除' });
    return;
  }
  // 凭证版本号不一致：密码已被修改/重置，要求重新登录
  if (typeof payload.tv !== 'number' || payload.tv !== user.token_version) {
    res.status(401).json({ message: '凭证已失效，请重新登录' });
    return;
  }
  req.user = { id: user.id, username: user.username, name: user.name, role: user.role };
  next();
}

/** 管理员权限中间件（需在 authenticate 之后使用） */
export function requireAdmin(req: Request, res: Response, next: NextFunction): void {
  if (req.user?.role !== 'admin') {
    res.status(403).json({ message: '需要管理员权限' });
    return;
  }
  next();
}
