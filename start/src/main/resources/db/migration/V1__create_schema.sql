CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id            SERIAL PRIMARY KEY,
    code          TEXT NOT NULL,          -- '45/2019/QH14'
    title         TEXT NOT NULL,          -- 'Bộ luật Lao động'
    issued_date   DATE,
    effective_date DATE,
    amended_by    JSONB                   -- [{"code":"71/2025/QH15","effective_date":"2026-01-01"}]
);

CREATE TABLE chapters (
    id            SERIAL PRIMARY KEY,
    document_id   INT REFERENCES documents(id),
    chapter_no    TEXT NOT NULL,          -- 'I'..'XVII'
    title         TEXT NOT NULL
);

CREATE TABLE sections (                    -- Mục (nullable, không phải chương nào cũng có)
    id            SERIAL PRIMARY KEY,
    chapter_id    INT REFERENCES chapters(id),
    section_no    TEXT NOT NULL,          -- '1', '2'...
    title         TEXT NOT NULL
);

CREATE TABLE articles (                    -- Điều
    id            SERIAL PRIMARY KEY,
    document_id   INT REFERENCES documents(id),
    chapter_id    INT REFERENCES chapters(id),
    section_id    INT REFERENCES sections(id) NULL,
    dieu_no       INT NOT NULL,           -- 1..220
    title         TEXT NOT NULL,
    full_text     TEXT NOT NULL,
    effective_date DATE NULL,             -- override nếu Điều có hiệu lực riêng (vd Điều 11,28,29 sửa đổi)
    source_law    TEXT DEFAULT 'BLLD'     -- 'BLLD' | 'Luật BHXH' | 'BLTTDS' (cho Điều 219 lồng luật)
);

CREATE TABLE chunks (
    id            SERIAL PRIMARY KEY,
    article_id    INT REFERENCES articles(id),
    chunk_type    TEXT NOT NULL,          -- 'full_dieu' | 'khoan_group'
    khoan_range   TEXT NULL,              -- '1-2', '3-5'...
    content       TEXT NOT NULL,          -- text đã có prefix ngữ cảnh cha
    cross_refs    INT[] DEFAULT '{}',     -- danh sách dieu_no được tham chiếu
    token_count   INT,
    embedding     VECTOR(1024)
);

CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON chunks USING GIN (cross_refs);