-- IMTIHON AI database schema (SQLite; PostgreSQL ga oson o'tadi)
PRAGMA foreign_keys = ON;

CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE,
  password_hash TEXT,
  is_guest INTEGER NOT NULL DEFAULT 0,
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin','editor','reviewer')),
  name TEXT,
  nickname TEXT UNIQUE,
  avatar TEXT,
  goal TEXT CHECK (goal IN ('otm','school','certificate','knowledge')),
  daily_goal INTEGER NOT NULL DEFAULT 20,
  language TEXT NOT NULL DEFAULT 'uz',
  show_in_leaderboard INTEGER NOT NULL DEFAULT 1,
  totp_secret TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  deleted_at TEXT
);

CREATE TABLE refresh_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE subjects (
  id TEXT PRIMARY KEY,
  slug TEXT UNIQUE NOT NULL,
  name_uz TEXT NOT NULL,
  name_ru TEXT, name_en TEXT,
  icon TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active INTEGER NOT NULL DEFAULT 1
);

-- fan -> bo'lim -> mavzu -> submavzu (bitta jadval, parent_id daraxti)
CREATE TABLE topics (
  id TEXT PRIMARY KEY,
  subject_id TEXT NOT NULL REFERENCES subjects(id),
  parent_id TEXT REFERENCES topics(id),
  level TEXT NOT NULL CHECK (level IN ('section','topic','subtopic')),
  name_uz TEXT NOT NULL,
  name_ru TEXT, name_en TEXT,
  theory_md TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_topics_subject ON topics(subject_id, parent_id);

CREATE TABLE questions (
  id TEXT PRIMARY KEY,
  subject_id TEXT NOT NULL REFERENCES subjects(id),
  topic_id TEXT REFERENCES topics(id),
  subtopic_id TEXT REFERENCES topics(id),
  difficulty INTEGER NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
  question TEXT NOT NULL,
  option_a TEXT NOT NULL, option_b TEXT NOT NULL,
  option_c TEXT NOT NULL, option_d TEXT NOT NULL,
  correct_answer TEXT NOT NULL CHECK (correct_answer IN ('A','B','C','D')),
  explanation TEXT,
  source TEXT,
  source_type TEXT CHECK (source_type IN ('original','ai','licensed','public_domain')),
  language TEXT NOT NULL DEFAULT 'uz',
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft','review','approved','published','archived')),
  flagged INTEGER NOT NULL DEFAULT 0,
  flag_reason TEXT,
  content_hash TEXT NOT NULL,          -- duplicate tekshiruvi uchun
  version INTEGER NOT NULL DEFAULT 1,
  created_by TEXT REFERENCES users(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_q_filter ON questions(subject_id, topic_id, status, difficulty);
CREATE UNIQUE INDEX idx_q_hash ON questions(content_hash) WHERE status != 'archived';

CREATE TABLE exam_configs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  config_json TEXT NOT NULL,   -- fanlar, savollar soni, vaqt, ball, qiyinlik, bloklar
  version INTEGER NOT NULL DEFAULT 1,
  is_published INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE tests (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('daily','topic','exam','custom','weak_topics')),
  exam_config_id TEXT REFERENCES exam_configs(id),
  duration_sec INTEGER,
  is_premium INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','archived')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE test_questions (
  test_id TEXT NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
  question_id TEXT NOT NULL REFERENCES questions(id),
  position INTEGER NOT NULL,
  PRIMARY KEY (test_id, question_id)
);

CREATE TABLE attempts (
  id TEXT PRIMARY KEY,
  client_uuid TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  test_id TEXT REFERENCES tests(id),
  started_at TEXT NOT NULL,
  finished_at TEXT,
  time_spent_sec INTEGER NOT NULL DEFAULT 0,
  score REAL, correct INTEGER, wrong INTEGER, skipped INTEGER,
  UNIQUE (user_id, client_uuid)         -- sync idempotent
);

CREATE TABLE attempt_answers (
  attempt_id TEXT NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
  question_id TEXT NOT NULL REFERENCES questions(id),
  selected TEXT CHECK (selected IN ('A','B','C','D')),
  is_correct INTEGER,
  marked INTEGER NOT NULL DEFAULT 0,
  time_spent_sec INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (attempt_id, question_id)
);
CREATE INDEX idx_answers_q ON attempt_answers(question_id);

CREATE TABLE topic_stats (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  topic_id TEXT NOT NULL REFERENCES topics(id),
  answered INTEGER NOT NULL DEFAULT 0,
  correct INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE daily_progress (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  questions_solved INTEGER NOT NULL DEFAULT 0,
  xp INTEGER NOT NULL DEFAULT 0,
  study_sec INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);

CREATE TABLE achievements (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code TEXT NOT NULL,
  earned_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, code)
);

CREATE TABLE ai_usage (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  action TEXT NOT NULL,          -- explain, simple, steps, similar, mistake, teach, ocr_solve
  tokens INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_ai_usage ON ai_usage(user_id, day);

CREATE TABLE question_reports (
  id TEXT PRIMARY KEY,
  question_id TEXT NOT NULL REFERENCES questions(id),
  user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','rejected')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE subscriptions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('subscription','one_time')),
  purchase_token_hash TEXT NOT NULL UNIQUE,  -- Google Play tekshiruvi serverda
  status TEXT NOT NULL CHECK (status IN ('active','expired','canceled','refunded')),
  expires_at TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE notification_settings (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  enabled INTEGER NOT NULL DEFAULT 0,   -- opt-in
  reminder_time TEXT
);

CREATE TABLE audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_id TEXT, action TEXT NOT NULL, entity TEXT, entity_id TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
  -- sezgir ma'lumot (parol, token) yozilmaydi
);

-- 1M+ savol uchun qo'shimcha indekslar (inkremental sinxronizatsiya va keyset pagination)
CREATE INDEX idx_q_pub  ON questions(status, subject_id, id);          -- published savollarni id bo'yicha sahifalash
CREATE INDEX idx_q_sync ON questions(updated_at, id);                  -- "since" bo'yicha o'zgarganlarni olish
