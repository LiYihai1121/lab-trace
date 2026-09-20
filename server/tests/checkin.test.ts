import { describe, it, expect, beforeAll } from 'vitest';
import crypto from 'node:crypto';
import request from 'supertest';
import db, { nowStr, fmtDate } from '../src/db/database.ts';
import { signToken } from '../src/middleware/auth.ts';
import { makeApp } from './helpers.ts';
import type { UserRow } from '../src/types.ts';

const app = makeApp();

const FAKE_HASH = `bcrypt$${crypto.randomBytes(12).toString('hex')}`;

function insertUser(): number {
  const result = db
    .prepare('INSERT INTO users (username, password_hash, name, role, created_at) VALUES (?, ?, ?, ?, ?)')
    .run(`stu_chk_${crypto.randomBytes(4).toString('hex')}`, FAKE_HASH, '测试用户', 'student', nowStr());
  return Number(result.lastInsertRowid);
}

function insertCode(ttlMs: number): string {
  const code = crypto.randomBytes(4).toString('hex').toUpperCase();
  db.prepare('INSERT INTO checkin_codes (code, expires_at, created_at) VALUES (?, ?, ?)').run(
    code,
    fmtDate(new Date(Date.now() + ttlMs)),
    nowStr(),
  );
  return code;
}

describe('checkin routes', () => {
  let token: string;
  let userId: number;

  beforeAll(() => {
    userId = insertUser();
    const user = db.prepare('SELECT * FROM users WHERE id = ?').all(userId)[0] as UserRow;
    token = signToken(user, user.token_version);
  });

  it('checks in with a valid code and checks out', async () => {
    const code = insertCode(5 * 60 * 1000);

    const resIn = await request(app).post('/api/checkin/in').set('Authorization', `Bearer ${token}`).send({ code });
    expect(resIn.status).toBe(200);
    expect(resIn.body.message).toMatch(/签到成功/);
    expect(resIn.body.record.status).toBe('checked_in');

    const resOut = await request(app).post('/api/checkin/out').set('Authorization', `Bearer ${token}`).send({});
    expect(resOut.status).toBe(200);
    expect(resOut.body.message).toMatch(/签退成功/);
    expect(resOut.body.record.status).toBe('completed');
    expect(resOut.body.record.duration_minutes).toBeGreaterThanOrEqual(0);
  });

  it('rejects invalid and expired codes', async () => {
    const expired = insertCode(-60 * 1000);
    const missing = await request(app)
      .post('/api/checkin/in')
      .set('Authorization', `Bearer ${token}`)
      .send({ code: crypto.randomBytes(3).toString('hex').toUpperCase() });
    expect(missing.status).toBe(400);

    const expiredRes = await request(app)
      .post('/api/checkin/in')
      .set('Authorization', `Bearer ${token}`)
      .send({ code: expired });
    expect(expiredRes.status).toBe(400);

    const empty = await request(app)
      .post('/api/checkin/in')
      .set('Authorization', `Bearer ${token}`)
      .send({ code: '  ' });
    expect(empty.status).toBe(400);
  });

  it('blocks a second check-in while one is active', async () => {
    const first = insertCode(5 * 60 * 1000);
    const second = insertCode(5 * 60 * 1000);

    expect(
      (await request(app).post('/api/checkin/in').set('Authorization', `Bearer ${token}`).send({ code: first })).status,
    ).toBe(200);

    const again = await request(app)
      .post('/api/checkin/in')
      .set('Authorization', `Bearer ${token}`)
      .send({ code: second });
    expect(again.status).toBe(400);
    expect(again.body.message).toMatch(/已处于签到状态/);

    await request(app).post('/api/checkin/out').set('Authorization', `Bearer ${token}`).send({});
  });

  it('rejects checkout without an active session', async () => {
    const res = await request(app).post('/api/checkin/out').set('Authorization', `Bearer ${token}`).send({});
    expect(res.status).toBe(400);
  });

  it('requires authentication', async () => {
    expect((await request(app).post('/api/checkin/in').send({})).status).toBe(401);
    expect((await request(app).post('/api/checkin/out').send({})).status).toBe(401);
  });
});
