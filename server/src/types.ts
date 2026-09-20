/**
 * 全局共享类型：数据库行结构与认证后的请求用户。
 * Express 的 Request 增补（req.user）也在此统一声明。
 */

/** 登录后随 JWT 携带并由 authenticate 写入 req.user 的用户信息 */
export interface AuthUser {
  id: number;
  username: string;
  name: string;
  role: 'student' | 'admin';
}

export interface UserRow extends AuthUser {
  password_hash: string;
  /** 凭证版本号：修改/重置密码时 +1，使此前签发的 JWT 立即失效 */
  token_version: number;
  created_at: string;
}

/** 对外暴露的用户字段（不含密码哈希与凭证版本号） */
export type UserPublicRow = Omit<UserRow, 'password_hash' | 'token_version'>;

export interface CheckinCodeRow {
  id: number;
  code: string;
  expires_at: string;
  created_at: string;
}

export interface CheckinRecordRow {
  id: number;
  user_id: number;
  checkin_time: string;
  checkout_time: string | null;
  duration_minutes: number | null;
  status: 'checked_in' | 'completed';
  code_id: number | null;
}

export interface ResetTokenRow {
  id: number;
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace -- Express 类型增补的标准写法
  namespace Express {
    interface Request {
      /** authenticate 中间件验证通过后写入的当前用户 */
      user: AuthUser;
    }
  }
}
