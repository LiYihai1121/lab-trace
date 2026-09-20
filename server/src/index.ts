import express from 'express';
import type { ErrorRequestHandler } from 'express';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import path from 'node:path';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import './db/database.ts';
import { environment, appVersion } from './config/environment.ts';
import { startDailyBackup } from './utils/backup.ts';
import authRoutes from './routes/auth.ts';
import userRoutes from './routes/users.ts';
import qrcodeRoutes from './routes/qrcode.ts';
import checkinRoutes from './routes/checkin.ts';
import recordRoutes from './routes/records.ts';
import statsRoutes from './routes/stats.ts';

const app = express();
// 默认端口按环境区分：开发/生产 3000，测试 3100；PORT 显式设置时优先
const PORT = Number(process.env.PORT) || environment.port;

// 反向代理部署时设置 TRUST_PROXY=true（或写跳数），限流按真实客户端 IP 计数
if (process.env.TRUST_PROXY) {
  app.set('trust proxy', process.env.TRUST_PROXY === 'true' ? 1 : process.env.TRUST_PROXY);
}

// 敏感接口限流：防暴力破解密码、找回码与签到码
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: '尝试次数过多，请 15 分钟后再试' },
});
const resetLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: '尝试次数过多，请 15 分钟后再试' },
});
const checkinLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: '操作过于频繁，请稍后再试' },
});

const allowedOrigins: string[] | true = process.env.CORS_ORIGIN
  ? process.env.CORS_ORIGIN.split(',')
      .map((origin) => origin.trim())
      .filter(Boolean)
  : true;
app.use(cors({ origin: allowedOrigins }));
app.use(express.json({ limit: '32kb' }));

// 限流挂载在 cors/json 之后，避免预检请求被计数或拦截
app.use('/api/auth/login', loginLimiter);
app.use('/api/auth/password/reset', resetLimiter);
app.use('/api/checkin/in', checkinLimiter);

app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'lab-trace',
    environment: environment.name,
    environmentLabel: environment.label,
    version: appVersion,
    timestamp: new Date().toISOString(),
  });
});

app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/qrcode', qrcodeRoutes);
app.use('/api/checkin', checkinRoutes);
app.use('/api/records', recordRoutes);
app.use('/api/stats', statsRoutes);

// 容器化部署时同源托管前端构建产物；本地开发无 dist 时自动跳过
const webDistDir = path.resolve(fileURLToPath(new URL('../..', import.meta.url)), 'web', 'dist');
if (existsSync(path.join(webDistDir, 'index.html'))) {
  app.use(express.static(webDistDir));
  app.use((req, res, next) => {
    if (req.method !== 'GET' || req.path.startsWith('/api')) return next();
    res.sendFile(path.join(webDistDir, 'index.html'));
  });
}

app.use((req, res) => res.status(404).json({ message: '接口不存在' }));

const errorHandler: ErrorRequestHandler = (err, req, res, _next) => {
  console.error(err);
  if (err.type === 'entity.parse.failed' || err.status === 400) {
    return res.status(400).json({ message: '请求格式错误' });
  }
  res.status(500).json({ message: '服务器内部错误' });
};
app.use(errorHandler);

const server = app.listen(PORT, () => {
  console.info(`LabTrace 电子实验室签到系统后端已启动: http://localhost:${PORT}`);
  const corsDesc = Array.isArray(allowedOrigins) ? allowedOrigins.join(', ') : '任意来源（未设置 CORS_ORIGIN）';
  const dbDesc = process.env.DB_PATH || `server/data/${environment.dbFile}`;
  console.info(
    `[环境] ${environment.label}（NODE_ENV=${environment.name}）| 版本 v${appVersion} | 数据库=${dbDesc} | CORS=${corsDesc}`,
  );
  startDailyBackup();
});

// 端口被占用（如 --watch 重启时旧实例尚未退出）时给出明确原因而非裸堆栈
server.on('error', (err) => {
  const errno = err as NodeJS.ErrnoException;
  console.error(
    `[启动失败] ${errno.code === 'EADDRINUSE' ? `端口 ${PORT} 已被占用，可能有旧实例尚未退出` : errno.message}`,
  );
  process.exit(1);
});

function shutdown(signal: string): void {
  console.info(`[shutdown] 收到 ${signal}，正在停止服务...`);
  // keep-alive 连接与调试器等待会挂住退出流程，导致 --watch 重启时端口无法释放：
  // 立即断开全部连接并限时兜底退出
  server.closeAllConnections();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500).unref();
}

process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));

// 捕获未处理的异常与拒绝，记录后优雅退出
process.on('uncaughtException', (err) => {
  console.error('[uncaughtException]', err);
  process.exit(1);
});
process.on('unhandledRejection', (reason) => {
  console.error('[unhandledRejection]', reason);
  process.exit(1);
});
