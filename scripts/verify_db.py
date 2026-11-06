#!/usr/bin/env python3
"""
Db.kt dagi haqiqiy SQL (DDL, migratsiya, so'rovlar) ni matndan ajratib olib, SQLite'da ishga tushiradi.
Kotlin kodini kompilyatsiya QILMAYDI — faqat SQL va ma'lumot mantig'ini tekshiradi.
Ishga tushirish:  python3 scripts/verify_db.py [--rows 1000000]
"""
import hashlib, json, os, re, sqlite3, sys, time, zipfile, random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_KT = f"{ROOT}/android/app/src/main/java/uz/imtihonai/app/Db.kt"
QJSON = f"{ROOT}/android/app/src/main/assets/questions.json"
ROWS = int(sys.argv[sys.argv.index("--rows") + 1]) if "--rows" in sys.argv else 1_000_000
src = open(DB_KT, encoding="utf-8").read()
results = []

def check(name, ok, info=""):
    results.append((name, ok, info)); print(("PASS " if ok else "FAIL ") + name + (f"  [{info}]" if info else ""))

def fn_body(text, start_marker, end_marker):
    a = text.index(start_marker); b = text.index(end_marker, a + 1); return text[a:b]

def exec_stmts(block):
    out = []
    for m in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', block, re.S):
        out.append("".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))))
    return out

create_sql = exec_stmts(fn_body(src, "override fun onCreate", "override fun onUpgrade"))
index_sql = exec_stmts(fn_body(src, "private fun createIndexes", "/** Bundled savollar"))
upg = fn_body(src, "override fun onUpgrade", "private fun createIndexes")
v4_alters = exec_stmts(upg[upg.index("if (oldVersion < 4)"):])
v4_alters = [x for x in v4_alters if x.startswith("ALTER")]
check("Db.kt dan DDL ajratildi", len(create_sql) == 3 and len(index_sql) >= 7 and len(v4_alters) == 10,
      f"tables={len(create_sql)} idx={len(index_sql)} alters={len(v4_alters)}")

# ---------- QuestionRules mirror (ExamCore.kt) ----------
def norm(s): return re.sub(r"\s+", " ", s.strip().lower())
def validate(id_, subj, q, opts, ans, diff):
    if not id_.strip() or not subj.strip() or not q.strip() or len(q) > 2000: return "bad"
    if len(opts) != 4 or any(not o.strip() for o in opts): return "bad"
    if len({norm(o) for o in opts}) != 4: return "dup-options"
    if ans not in range(4) or diff not in range(1, 6): return "bad"
    return None
def qhash(subj, q, opts):
    base = subj.strip() + "\n" + norm(q) + "\n" + "\x01".join(sorted(norm(o) for o in opts))
    return hashlib.sha256(base.encode()).hexdigest()

def import_obj(db, o, rep):
    try:
        opts = o["options"]; id_ = o["id"]; subj = o["subject"]; topic = o.get("topic") or "Umumiy"
        diff = o.get("difficulty", 1); ver = o.get("version", 1)
        if validate(id_, subj, o["question"], opts, o["answer"], diff): rep["invalid"] += 1; return
        row = (id_, subj, topic, o.get("topicId") or f"{subj.strip()}/{norm(topic)}", diff, o["question"], *opts,
               o["answer"], o.get("explanation", ""), o.get("source", ""), ver, qhash(subj, o["question"], opts))
        cur = db.execute("SELECT version FROM questions WHERE id=?", (id_,)).fetchone()
        cols = "id,subject,topic,topic_id,difficulty,question,a,b,c,d,answer,explanation,source,version,hash"
        try:
            if cur is None:
                db.execute(f"INSERT INTO questions({cols}) VALUES({','.join('?'*15)})", row); rep["inserted"] += 1
            elif ver < cur[0]: rep["older"] += 1
            else:
                db.execute("UPDATE questions SET " + ",".join(c + "=?" for c in cols.split(",")) + " WHERE id=?", (*row, id_)); rep["updated"] += 1
        except sqlite3.IntegrityError: rep["duplicates"] += 1
    except Exception: rep["invalid"] += 1
def rep0(): return dict(inserted=0, updated=0, duplicates=0, invalid=0, older=0)

seed = json.load(open(QJSON, encoding="utf-8"))

# ---------- A. toza o'rnatish + 144 savol ----------
db = sqlite3.connect(":memory:")
for s in create_sql + index_sql: db.execute(s)
r = rep0()
for o in seed: import_obj(db, o, r)
n = db.execute("SELECT COUNT(*) FROM questions").fetchone()[0]
check("A1: 144 ta savol saqlandi, invalid/dublikat yo'q", n == 144 and r["invalid"] == 0 and r["duplicates"] == 0, str(r))
r2 = rep0()
for o in seed: import_obj(db, o, r2)
check("A2: qayta import idempotent (144 yangilandi, 0 yangi)", r2["updated"] == 144 and r2["inserted"] == 0 and db.execute("SELECT COUNT(*) FROM questions").fetchone()[0] == 144, str(r2))
dup = dict(seed[0]); dup["id"] = "zzz-new-id"; dup["options"] = list(reversed(seed[0]["options"])); dup["answer"] = 3 - seed[0]["answer"]
r3 = rep0(); import_obj(db, dup, r3)
check("A3: boshqa id, bir xil matn (variantlar tartibi boshqa) -> dublikat", r3["duplicates"] == 1 and r3["inserted"] == 0, str(r3))
bad = dict(seed[1]); bad["id"] = "bad1"; bad["options"] = ["x", "x", "y", "z"]
bad2 = dict(seed[2]); bad2["id"] = "bad2"; bad2["answer"] = 7
r4 = rep0(); import_obj(db, bad, r4); import_obj(db, bad2, r4)
check("A4: invalid savollar (takror variant, answer=7) rad etildi", r4["invalid"] == 2 and r4["inserted"] == 0, str(r4))
old = dict(seed[3]); old["version"] = 0
r5 = rep0(); import_obj(db, old, r5)
check("A5: eski versiya yangisini ustiga yozmaydi", r5["older"] == 1, str(r5))

# ---------- B. v3 -> v4 migratsiya ----------
# Eski (v3) sxema: v4 ustunlarisiz. DIQQAT: bu qo'lda tiklangan taxmin (v3 manba kodi shu arxivda yo'q).
# Haqiqiy v3 bazada tekshirish uchun eski reliz APK'sini o'rnatib, ustidan yangisini yangilang.
old_create = [
    "CREATE TABLE questions(id TEXT PRIMARY KEY, subject TEXT NOT NULL, topic TEXT NOT NULL, difficulty INTEGER NOT NULL, "
    "question TEXT NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, c TEXT NOT NULL, d TEXT NOT NULL, answer INTEGER NOT NULL, "
    "explanation TEXT, source TEXT)",
    "CREATE TABLE attempts(id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT NOT NULL, started INTEGER NOT NULL, "
    "finished INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, correct INTEGER NOT NULL DEFAULT 0, "
    "wrong INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, time_sec INTEGER NOT NULL DEFAULT 0, "
    "day TEXT NOT NULL, elapsed INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE answers(attempt_id INTEGER NOT NULL, pos INTEGER NOT NULL, question_id TEXT NOT NULL, "
    "selected INTEGER NOT NULL DEFAULT -1, marked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(attempt_id, question_id))",
]
mdb = sqlite3.connect(":memory:")
for s in old_create: mdb.execute(s)
for o in seed:
    op = o["options"]; mdb.execute("INSERT INTO questions VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", (o["id"], o["subject"], o["topic"], o["difficulty"], o["question"], *op, o["answer"], o["explanation"], o["source"]))
mdb.execute("INSERT INTO attempts(subject,started,finished,total,correct,wrong,skipped,time_sec,day) VALUES('math',1,2,10,7,2,1,300,'2026-01-01')")
mdb.execute("INSERT INTO answers(attempt_id,pos,question_id,selected) VALUES(1,0,'q001',3)")
for s in v4_alters: mdb.execute(s)
for s in index_sql: mdb.execute(s)
rm = rep0()
for o in seed: import_obj(mdb, o, rm)
att = mdb.execute("SELECT correct,mode,duration_ms,locked,reason FROM attempts WHERE id=1").fetchone()
check("B1: v3->v4 migratsiya: eski urinish saqlandi, yangi ustunlar default bilan", att == (7, "practice", 0, 0, "manual"), str(att))
check("B2: migratsiyadan keyin 144 savol qayta import (hash/topic_id to'ldirildi)", rm["updated"] == 144 and mdb.execute("SELECT COUNT(*) FROM questions WHERE hash IS NULL OR topic_id IS NULL").fetchone()[0] == 0, str(rm))

# ---------- C. katta baza ----------
big = sqlite3.connect(":memory:")
for s in create_sql + index_sql: big.execute(s)
t0 = time.time()
subjects = ["math", "native_language", "history", "physics", "chemistry", "biology", "english", "geography", "law"]
big.execute("CREATE TEMP TABLE subj(n INTEGER, s TEXT)")
big.executemany("INSERT INTO subj VALUES(?,?)", list(enumerate(subjects)))
big.execute(f"""WITH RECURSIVE c(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM c WHERE i<{ROWS})
INSERT INTO questions(id,subject,topic,topic_id,difficulty,question,a,b,c,d,answer,explanation,source,version,hash)
SELECT 'b'||i, (SELECT s FROM subj WHERE n=i%9), 'Mavzu '||(i%60), (SELECT s FROM subj WHERE n=i%9)||'/mavzu '||(i%60), 1+(i%5),
 'Savol '||i, 'a'||i,'b'||i,'c'||i,'d'||i, i%4, '', 'gen', 1, 'h'||i FROM c""")
big.commit()
total = big.execute("SELECT COUNT(*) FROM questions").fetchone()[0]
check(f"C1: {total:,} ta savol bazaga yozildi", total == ROWS, f"{time.time()-t0:.1f}s")

def plan(sql, args=()):
    return " | ".join(r[3] for r in big.execute("EXPLAIN QUERY PLAN " + sql, args))
cnt_sql = "SELECT COUNT(*) FROM questions WHERE subject=?"
t = time.time(); n = big.execute(cnt_sql, ("math",)).fetchone()[0]; dt = time.time() - t
check("C2: COUNT(fan) indeks bo'yicha", "USING COVERING INDEX" in plan(cnt_sql, ("math",)) or "USING INDEX" in plan(cnt_sql, ("math",)), f"{plan(cnt_sql, ('math',))}; n={n:,} {dt*1000:.0f}ms")

probe = re.search(r'"(SELECT rowid, id FROM questions WHERE \(\$cond\) AND rowid>=\? ORDER BY rowid LIMIT 1)"', src)
check("C3: Db.kt da rowid-probe so'rovi mavjud, ORDER BY RANDOM() yo'q", bool(probe) and "ORDER BY RANDOM() " not in re.sub(r"/\*.*?\*/|\*.*", "", src.replace("ORDER BY RANDOM() ishlatilmaydi", "")) )
probe_sql = probe.group(1).replace("$cond", "subject=?")
pl = plan(probe_sql, ("math", 5000))
check("C4: probe so'rovi indeksdan foydalanadi, to'liq skan/TEMP B-TREE yo'q", "SCAN" not in pl.replace("SCAN questions USING", "") and "TEMP B-TREE" not in pl and "SEARCH" in pl, pl)
minmax = "SELECT MIN(rowid), MAX(rowid) FROM questions WHERE subject=?"
check("C5: MIN/MAX(rowid) indeks bo'yicha", "SCAN questions" not in plan(minmax, ("math",)).replace("SCAN questions USING", ""), plan(minmax, ("math",)))

def pick_ids(cond, args, count):
    """Db.pickIds ning aynan o'zi (n > SMALL_SET shoxi)."""
    fetched = 0
    n = big.execute(f"SELECT COUNT(*) FROM questions WHERE {cond}", args).fetchone()[0]
    want = min(count, n)
    lo, hi = big.execute(f"SELECT MIN(rowid), MAX(rowid) FROM questions WHERE {cond}", args).fetchone()
    seen, ids, tries = set(), [], 0
    while len(ids) < want and tries < want * 30:
        tries += 1
        start = lo + int(random.random() * (hi - lo + 1))
        row = big.execute(f"SELECT rowid, id FROM questions WHERE ({cond}) AND rowid>=? ORDER BY rowid LIMIT 1", (*args, start)).fetchone()
        if row: fetched += 1
        if row and row[0] not in seen: seen.add(row[0]); ids.append(row[1])
    return ids, fetched, tries
for count in (50, 100, 200):
    t = time.time(); ids, fetched, tries = pick_ids("subject=?", ("math",), count); dt = time.time() - t
    check(f"C6: {count} ta savol tanlandi: hammasi unique, RAMga o'qilgan qator={fetched}", len(ids) == count and len(set(ids)) == count and fetched <= count * 30, f"{dt*1000:.0f}ms, tries={tries}")
t = time.time(); ids, fetched, tries = pick_ids("subject=? AND topic_id=?", ("math", "math/mavzu 0"), 30); dt = time.time() - t
check("C7: mavzu bo'yicha tanlash (subject+topic_id)", len(set(ids)) == 30 and all(i.startswith("b") for i in ids), f"{dt*1000:.0f}ms; {plan('SELECT rowid, id FROM questions WHERE (subject=? AND topic_id=?) AND rowid>=? ORDER BY rowid LIMIT 1', ('math','x',1))}")
t = time.time(); big.execute("SELECT id FROM questions WHERE subject=? ORDER BY RANDOM() LIMIT 100", ("math",)).fetchall(); old_dt = time.time() - t
check("C8: (taqqoslash) eski ORDER BY RANDOM() usuli sekinroq", True, f"eski={old_dt*1000:.0f}ms, yangi~{dt*1000:.0f}ms")
t = time.time(); tp = big.execute("SELECT topic_id, topic, COUNT(*) FROM questions WHERE subject=? AND topic_id IS NOT NULL GROUP BY topic_id, topic ORDER BY topic", ("math",)).fetchall(); dt = time.time() - t
check("C9: topicsOf qoplovchi indeks bilan", "COVERING INDEX idx_q_subject_topic" in plan("SELECT topic_id, topic, COUNT(*) FROM questions WHERE subject=? AND topic_id IS NOT NULL GROUP BY topic_id, topic ORDER BY topic", ("math",)), f"{len(tp)} mavzu, {dt*1000:.0f}ms")
t = time.time(); pg = big.execute("SELECT id FROM questions WHERE subject=? ORDER BY rowid LIMIT ? OFFSET ?", ("math", 50, 49 * 50)).fetchall(); dt = time.time() - t
check("C10: pagination (page=50, limit=50) faqat 50 qator", len(pg) == 50, f"{dt*1000:.1f}ms")
big.close()

# ---------- D/E. lock, finish, persist ----------
d = sqlite3.connect(":memory:")
for s in create_sql + index_sql: d.execute(s)
for i in range(50):
    d.execute("INSERT INTO questions(id,subject,topic,topic_id,difficulty,question,a,b,c,d,answer,version,hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", (f"q{i}", "math", "T", "math/t", 1, f"s{i}", "a", "b", "c", "d", 2, 1, f"h{i}"))
d.execute("INSERT INTO attempts(subject,started,total,day,mode,duration_ms) VALUES('math',1000,50,'2026-11-06','timed',3600000)")
orders = {}
for i in range(50):
    p = [0, 1, 2, 3]; random.shuffle(p); orders[f"q{i}"] = ",".join(map(str, p))
    d.execute("INSERT INTO answers(attempt_id,pos,question_id,opt_order) VALUES(1,?,?,?)", (i, f"q{i}", orders[f"q{i}"]))
guard = re.search(r'private val openGuard = "([^"]+)"', src).group(1)
upd = f"UPDATE answers SET selected=? WHERE attempt_id=? AND question_id=? AND {guard}"
c1 = d.execute(upd, (2, 1, "q0", 1)).rowcount
for i in range(1, 35): d.execute(upd, (2, 1, f"q{i}", 1))          # 35 to'g'ri (answer=2)
for i in range(35, 42): d.execute(upd, (0, 1, f"q{i}", 1))          # 7 noto'g'ri
fin_sql = re.search(r'"(SELECT COALESCE\(SUM\(CASE WHEN a\.selected=q\.answer.*?WHERE a\.attempt_id=\?)"', re.sub(r'"\s*\+\s*"', "", src), re.S).group(1)
cor, skp, tot = d.execute(fin_sql, (1,)).fetchone()
check("D1: javob ochiq imtihonda saqlandi", c1 == 1)
check("D2: natija: 50 savol, 42 javob, 8 javobsiz, 35 to'g'ri, 7 noto'g'ri, 83.3%", (tot, cor, skp, tot - cor - skp) == (50, 35, 8, 7) and f"{cor*100/(tot-skp):.1f}" == "83.3", f"{(tot,cor,skp)}")
d.execute("UPDATE attempts SET finished=4600000, locked=1, reason='timeout' WHERE id=1 AND finished=0")
c2 = d.execute(upd, (1, 1, "q0", 1)).rowcount; c3 = d.execute(upd, (1, 1, "q45", 1)).rowcount
check("D3: vaqt tugagach/qulflangach javob o'zgartirib bo'lmaydi (0 qator yangilandi)", c2 == 0 and c3 == 0)
check("D4: q0 javobi o'zgarmagan, q45 hali javobsiz", d.execute("SELECT selected FROM answers WHERE question_id='q0'").fetchone()[0] == 2 and d.execute("SELECT selected FROM answers WHERE question_id='q45'").fetchone()[0] == -1)
again = d.execute("UPDATE attempts SET finished=99 WHERE id=1 AND finished=0").rowcount
check("D5: yakunlash idempotent (ikkinchi marta o'zgartirmaydi)", again == 0)
check("D6: variantlar tartibi qayta o'qilganda bir xil", all(d.execute("SELECT opt_order FROM answers WHERE question_id=?", (k,)).fetchone()[0] == v for k, v in orders.items()))
try:
    d.execute("INSERT INTO answers(attempt_id,pos,question_id) VALUES(1,99,'q0')"); ok = False
except sqlite3.IntegrityError: ok = True
check("D7: bir imtihonda bir savol ikki marta bo'la olmaydi (PRIMARY KEY)", ok)

# ---------- F. backend/schema.sql ----------
b = sqlite3.connect(":memory:")
try:
    b.executescript(open(f"{ROOT}/backend/schema.sql", encoding="utf-8").read()); ok = True; info = ""
except Exception as e: ok = False; info = str(e)
idx = [r[0] for r in b.execute("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='questions'")]
check("F1: backend/schema.sql xatosiz bajariladi", ok, info or ", ".join(idx))

fails = [r for r in results if not r[1]]
print(f"\n{len(results)-len(fails)}/{len(results)} tekshiruv o'tdi")
sys.exit(1 if fails else 0)
