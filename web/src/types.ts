/** 与后端 API 对应的数据行类型（供各视图复用）；源定义在 server/src/types.ts，保持字段一致 */

export interface CheckinRecordRow {
  id: number;
  checkin_time: string;
  checkout_time: string | null;
  duration_minutes: number | null;
  status: 'checked_in' | 'completed';
  /** /records/all 联表返回的字段 */
  name?: string;
  username?: string;
}

export interface UserRow {
  id: number;
  username: string;
  name: string;
  role: 'student' | 'admin';
  created_at: string;
}
