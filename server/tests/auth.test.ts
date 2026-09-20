import { describe, it, expect, beforeAll } from 'vitest';
import crypto from 'node:crypto';
import request from 'supertest';
import db, { fmtDate } from '../src/db/database.ts';
import { makeApp } from './helpers.ts';

const app = makeApp();

// 测试口令全部随机生成，避免在源码中出现硬编码凭据
const INIT_ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const CHANGED_ADMIN_PASSWORD = crypto.randomBytes(8).toString('hex');
const STU_PASSWORD = crypto.randomBytes(8).toString('hex');
const STU_RESET_PASSWORD = crypto.randomBytes(8).toString('hex');

describe('auth', () => {
  let adminToken: string;

  beforeAll(async () => {
    const res = await request(app).post('/api/auth/login').send({ username: 'admin', password: INIT_ADMIN_PASSWORD });
    expect(res.status).toBe(200);
    adminToken = res.body.token;
  });

  it('rejects bad credentials and missing fields', async () => {
    const bad = await request(app)
      .post('/api/auth/login')
      .send({ username: 'admin', password: crypto.randomBytes(8).toString('hex') });
    expect(bad.status).toBe(401);
    const missing = await request(app).post('/api/auth/login').send({});
    expect(missing.status).toBe(400);
  });

  it('returns current user via /me and requires bearer token', async () => {
    const res = await request(app).get('/api/auth/me').set('Authorization', `Bearer ${adminToken}`);
    expect(res.status).toBe(200);
    expect(res.body.user.username).toBe('admin');
    expect(res.body.user).not.toHaveProperty('password_hash');
    expect((await request(app).get('/api/auth/me')).status).toBe(401);
  });

  it('changes own password, re-login works, validates input', async () => {
    const wrongOld = await request(app)
      .put('/api/auth/password')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ oldPassword: crypto.randomBytes(8).toString('hex'), newPassword: crypto.randomBytes(8).toString('hex') });
    expect(wrongOld.status).toBe(400);

    const shortNew = await request(app)
      .put('/api/auth/password')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ oldPassword: INIT_ADMIN_PASSWORD, newPassword: '123' });
    expect(shortNew.status).toBe(400);

    const change = await request(app)
      .put('/api/auth/password')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ oldPassword: INIT_ADMIN_PASSWORD, newPassword: CHANGED_ADMIN_PASSWORD });
    expect(change.status).toBe(200);

    const relogin = await request(app)
      .post('/api/auth/login')
      .send({ username: 'admin', password: CHANGED_ADMIN_PASSWORD });
    expect(relogin.status).toBe(200);
    adminToken = relogin.body.token;

    // 改回初始密码，避免影响后续用例；改回后 token_version 又 +1，需重新登录
    await request(app)
      .put('/api/auth/password')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ oldPassword: CHANGED_ADMIN_PASSWORD, newPassword: INIT_ADMIN_PASSWORD });
    const relogin2 = await request(app)
      .post('/api/auth/login')
      .send({ username: 'admin', password: INIT_ADMIN_PASSWORD });
    adminToken = relogin2.body.token;
  });

  it('resets password with one-time code and blocks reuse', async () => {
    const created = await request(app)
      .post('/api/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ username: 'stu_reset', password: STU_PASSWORD, name: '重置同学', role: 'student' });
    expect(created.status).toBe(200);
    const loginToken = (
      await request(app).post('/api/auth/login').send({ username: 'stu_reset', password: STU_PASSWORD })
    ).body.token;

    const issued = await request(app)
      .post(`/api/users/${created.body.id}/password-reset-token`)
      .set('Authorization', `Bearer ${adminToken}`);
    expect(issued.status).toBe(200);
    expect(issued.body.code).toMatch(/^[0-9A-F]{12}$/);

    const reset = await request(app)
      .post('/api/auth/password/reset')
      .send({ username: 'stu_reset', resetCode: issued.body.code, newPassword: STU_RESET_PASSWORD });
    expect(reset.status).toBe(200);

    // 重置密码后旧 token 立即失效（token_version 提升），新密码可登录
    const relogin = await request(app)
      .post('/api/auth/login')
      .send({ username: 'stu_reset', password: STU_RESET_PASSWORD });
    expect(relogin.status).toBe(200);
    const oldToken = loginToken;
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${oldToken}`)).status).toBe(401);

    const reuse = await request(app)
      .post('/api/auth/password/reset')
      .send({ username: 'stu_reset', resetCode: issued.body.code, newPassword: crypto.randomBytes(8).toString('hex') });
    expect(reuse.status).toBe(400);
  });

  it('rejects expired reset code', async () => {
    const user = db.prepare("SELECT id FROM users WHERE username = 'stu_reset'").all()[0] as { id: number };
    const code = crypto.randomBytes(6).toString('hex').toUpperCase();
    const tokenHash = crypto.createHash('sha256').update(code).digest('hex');
    db.prepare(
      'INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)',
    ).run(user.id, tokenHash, fmtDate(new Date(Date.now() - 60_000)), fmtDate(new Date()));

    const res = await request(app)
      .post('/api/auth/password/reset')
      .send({ username: 'stu_reset', resetCode: code, newPassword: crypto.randomBytes(8).toString('hex') });
    expect(res.status).toBe(400);
  });

  it('invalidates old tokens after role change or account deletion', async () => {
    const auth = { Authorization: `Bearer ${adminToken}` };
    // 降级：被降级管理员的旧 token 不再持有管理权限
    const admin2Pwd = crypto.randomBytes(8).toString('hex');
    const created = await request(app)
      .post('/api/users')
      .set(auth)
      .send({ username: 'stu_demote', password: admin2Pwd, name: '降级同学', role: 'admin' });
    const admin2Id = created.body.id;
    const admin2Token = (
      await request(app).post('/api/auth/login').send({ username: 'stu_demote', password: admin2Pwd })
    ).body.token;
    expect((await request(app).get('/api/users').set('Authorization', `Bearer ${admin2Token}`)).status).toBe(200);
    await request(app).put(`/api/users/${admin2Id}`).set(auth).send({ role: 'student' });
    expect((await request(app).get('/api/users').set('Authorization', `Bearer ${admin2Token}`)).status).toBe(403);

    // 删除：被删账号的旧 token 立即失效
    const stuPwd = crypto.randomBytes(8).toString('hex');
    const stu = await request(app)
      .post('/api/users')
      .set(auth)
      .send({ username: 'stu_del', password: stuPwd, name: '删除同学', role: 'student' });
    const stuToken = (await request(app).post('/api/auth/login').send({ username: 'stu_del', password: stuPwd })).body
      .token;
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${stuToken}`)).status).toBe(200);
    await request(app).delete(`/api/users/${stu.body.id}`).set(auth);
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${stuToken}`)).status).toBe(401);
  });

  it('invalidates old tokens after password change', async () => {
    // 创建一个学生并登录
    const pwd = crypto.randomBytes(8).toString('hex');
    const newPwd = crypto.randomBytes(8).toString('hex');
    const created = await request(app)
      .post('/api/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ username: 'stu_tv', password: pwd, name: '版本同学', role: 'student' });
    expect(created.status).toBe(200);
    const login = await request(app).post('/api/auth/login').send({ username: 'stu_tv', password: pwd });
    const oldToken = login.body.token;
    expect(oldToken).toBeTruthy();

    // 用旧 token 访问正常
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${oldToken}`)).status).toBe(200);

    // 修改密码后旧 token 立即失效
    const change = await request(app)
      .put('/api/auth/password')
      .set('Authorization', `Bearer ${oldToken}`)
      .send({ oldPassword: pwd, newPassword: newPwd });
    expect(change.status).toBe(200);
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${oldToken}`)).status).toBe(401);

    // 新密码登录获得的新 token 可用
    const relogin = await request(app).post('/api/auth/login').send({ username: 'stu_tv', password: newPwd });
    expect(relogin.status).toBe(200);
    expect(
      (await request(app).get('/api/auth/me').set('Authorization', `Bearer ${relogin.body.token}`)).status,
    ).toBe(200);
  });

  it('invalidates old tokens after admin resets password', async () => {
    const pwd = crypto.randomBytes(8).toString('hex');
    const adminResetPwd = crypto.randomBytes(8).toString('hex');
    const created = await request(app)
      .post('/api/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ username: 'stu_admin_reset', password: pwd, name: '被重置同学', role: 'student' });
    const login = await request(app).post('/api/auth/login').send({ username: 'stu_admin_reset', password: pwd });
    const oldToken = login.body.token;

    // 管理员重置密码
    const reset = await request(app)
      .put(`/api/users/${created.body.id}`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ password: adminResetPwd });
    expect(reset.status).toBe(200);

    // 旧 token 失效
    expect((await request(app).get('/api/auth/me').set('Authorization', `Bearer ${oldToken}`)).status).toBe(401);
  });
});
