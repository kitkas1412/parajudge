# parajudge

Hỏi đáp trên văn bản pháp luật Việt Nam. Nạp PDF luật, cắt theo đúng cấu trúc
Chương / Mục / Điều / Khoản / Điểm, sinh vector, tìm kiếm ngữ nghĩa, rồi trả lời bằng
tiếng Việt kèm trích dẫn.

Điểm khác biệt so với một RAG thông thường nằm ở chỗ **trích dẫn không do model viết ra**.
Model chỉ được *chọn* id của những trích đoạn nó thực sự dùng; mọi trường của trích dẫn
(số Điều, khoản, tên điều, thuộc luật nào) đều dựng lại từ hàng trong cơ sở dữ liệu. Một
trích dẫn trỏ tới điều luật không tồn tại là điều không thể xảy ra, chứ không phải điều
hiếm xảy ra.

```
NẠP
  PDF ─▶ PdfPageExtractor ─▶ LegalDocumentParser ─▶ DocumentEntityMapper ─▶ Postgres
         cỡ chữ, lề,          cây Chương/Mục/         + ChunkingService      documents
         chỉ số trên          Điều/Khoản/Điểm          cắt tại ranh giới     chapters
                                                       đánh số               sections
                                                            │                articles
                                                            ▼                chunks
                                                     EmbeddingService ─ bge-m3 ─▶ vector(1024)

HỎI
  câu hỏi ─▶ bge-m3 ─▶ pgvector <=> ─▶ top-K chunk ─▶ qwen3:8b ─▶ câu trả lời + trích dẫn
                                            │                          ▲
                                            └──── id chunk ────────────┘
```

Công nghệ: Java 21 · Spring Boot 4.0.8 · Spring AI 2.0.0 · PostgreSQL 16 + pgvector ·
Flyway · PDFBox 3.0.5 · Ollama.

## Chạy thử

Cần: JDK 21, Docker, [Ollama](https://ollama.com).

```bash
# 1. Postgres. Nhớ --env-file: compose tìm .env cạnh file compose, không phải ở gốc repo.
docker compose --env-file .env -f environment/docker-compose.yml up -d postgres

# 2. Model. Ứng dụng cũng tự pull khi thiếu (pull-model-strategy: when_missing),
#    nhưng lần đầu mất vài phút nên kéo trước thì khởi động nhanh hơn.
ollama pull bge-m3      # sinh vector, 1024 chiều
ollama pull qwen3:8b    # sinh câu trả lời

# 3. Chạy
./mvnw -DskipTests install      # lần đầu, hoặc sau khi sửa modules/documents
./mvnw -pl start spring-boot:run
```

`-pl start -am` **không** dùng được: `-am` kéo cả POM gốc vào reactor và
`spring-boot:run` chạy trên nó sẽ báo *Unable to find a suitable main class*.

Nạp Bộ luật Lao động có sẵn trong classpath rồi hỏi thử:

```bash
# Parse + ghi xuống DB + sinh vector, một request (~22 giây trên Apple Silicon)
curl -X POST 'localhost:8080/api/parser/ingest/samples/boluatlaodong.pdf?replace=true'

# Tìm kiếm
curl -X POST localhost:8080/api/search \
  -H 'Content-Type: application/json' \
  -d '{"query": "nghỉ hằng năm bao nhiêu ngày", "topK": 3}'

# Hỏi đáp
curl -X POST localhost:8080/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "Người lao động được nghỉ hằng năm bao nhiêu ngày?"}'
```

Bộ test Postman đi kèm ở `docs/postman/` chạy trọn đường đi trên: 9 nhóm, 30 request,
111 assertion.

## API

| | Endpoint | Việc |
|---|---|---|
| **Parse** | `GET /api/parser/samples` | các PDF đóng gói sẵn |
| | `GET /api/parser/samples/{tên}?view=outline\|full` | parse, chưa ghi gì |
| | `POST /api/parser/parse` (multipart `file`) | parse file tải lên |
| **Nạp** | `POST /api/parser/ingest` (multipart `file`) | parse + ghi DB `?replace=` `?embed=` |
| | `POST /api/parser/ingest/samples/{tên}` | như trên, cho PDF có sẵn |
| **Vector** | `GET /api/embeddings` | đã embed bao nhiêu, còn bao nhiêu |
| | `POST /api/embeddings?all=false` | embed những chunk còn thiếu |
| **Tìm** | `GET /api/search?q=&topK=&minScore=&expandRefs=` | tra nhanh từ trình duyệt |
| | `POST /api/search` | như trên, dạng JSON |
| **Hỏi** | `POST /api/ask` | `{question, topK, minScore, expandRefs}` |

Mặc định: `topK` 5 (hỏi đáp: 6), `minScore` 0.5, `expandRefs` false.

Vài mã trạng thái có chủ ý:

- `409` từ `/ingest` — mã văn bản đã tồn tại. Lược đồ không có ràng buộc nào chặn, nên
  việc chặn nằm ở tầng dịch vụ; gọi lại với `?replace=true` để ghi đè.
- `409` từ `/api/embeddings` — model trả về vector khác 1024 chiều. Chặn *trước khi* ghi,
  vì Postgres cũng sẽ từ chối nhưng chỉ báo hai con số mà không nói model nào gây ra.
- `503` từ `/api/ask` — không gọi được model sinh câu trả lời. Cố ý **không** gộp vào
  `answered: false`: "luật không quy định" và "model không chạy" mà nhìn giống nhau thì
  người đọc sẽ tưởng luật im lặng trong khi không phải.
- `/api/ask` luôn `200` khi corpus không trả lời được. Đó là kết quả hợp lệ, không phải lỗi.

## Corpus

Số liệu đo trên `boluatlaodong.pdf` đóng gói trong repo:

| | |
|---|---|
| Trang | 85 parse được, 1 bỏ (trang 86 là bản scan có lớp OCR) |
| Chương / Mục / Điều | 17 / 24 / 222 |
| Chunk | 270 — 182 `full_dieu`, 88 `khoan_group` |
| Token mỗi chunk | nhỏ nhất 44, trung vị 223, lớn nhất 397 (trần 400, không chunk nào vượt) |
| Luật trong corpus | 3 — Bộ luật Lao động, và Luật BHXH 58/2014/QH13 + BLTTDS 92/2015/QH13 do Điều 219 dẫn nguyên văn |

222 Điều chứ không phải 219, vì Điều 219 sửa đổi các luật khác và dẫn nguyên văn 3 điều
của chúng. Ba điều đó được lưu thành hàng riêng, phân biệt bằng `articles.source_law` —
Điều 54 tồn tại ở cả hai luật, nên số Điều không đủ để định danh.

Chất lượng truy hồi đo trên chính corpus này:

| Câu hỏi | Kết quả đầu bảng | Điểm |
|---|---|---|
| nghỉ hằng năm bao nhiêu ngày | Điều 113 khoản 1-5 | 0.726 |
| điều kiện hưởng lương hưu | Điều 54 khoản 1 **của Luật BHXH** | 0.771 |
| sa thải lao động nữ mang thai | Điều 137 khoản 3-4 | 0.617 |
| làm thêm giờ tối đa | Điều 108 *Làm thêm giờ trong trường hợp đặc biệt* | 0.672 |

Câu thứ hai là câu đáng chú ý: nó trả về đúng điều của luật lồng bên trong, với
`source_law` đúng — tức là đường xử lý luật lồng chạy thông từ parser tới truy hồi.

Câu cuối cho thấy giới hạn. "Làm thêm giờ tối đa" đẩy Điều 108 lên trên Điều 107 đúng
0.0065 điểm, trong khi câu trả lời thật nằm ở khoản 2 Điều 107. Hỏi rõ hơn — "số giờ làm
thêm tối đa trong một năm" — thì Điều 107 khoản 3-5 lên đầu với 0.682. Truy hồi thuần
ngữ nghĩa trên câu hỏi cụt như vậy là chỗ mong manh; `topK` mặc định 5–6 tồn tại chính
vì thế, và ở `/api/ask` cả ba chunk đều được đưa cho model.

## Kiến trúc

```
pom.xml                 quản lý phiên bản Spring AI BOM cho cả cây
common/                 rỗng
infrastructure/         rỗng
modules/documents/      toàn bộ phần miền — parser, chunking, embedding, search, ask
start/                  bootstrap: main class, application.yaml, migration Flyway, test tích hợp
environment/            docker-compose (postgres + redis + rabbitmq)
docs/postman/           collection test đầu-cuối (nằm ngoài git)
```

Trong `modules/documents`:

| Gói | Việc |
|---|---|
| `service/parser/pdf` | PDFBox → dòng chữ kèm cỡ chữ, toạ độ, in đậm; lọc trang scan |
| `service/parser` | dòng → khối đoạn → cây Chương/Mục/Điều/Khoản/Điểm; `Article219Parser` tách luật lồng |
| `service/mapper` | cây parse → đồ thị entity; parse phần mở đầu để lấy mã và ngày hiệu lực |
| `service/chunking` | Điều → các chunk truy hồi được |
| `service/ingestion` | ghi xuống DB, chặn nạp trùng |
| `service/embedding` | lấp `chunks.embedding` |
| `service/search` | câu hỏi → chunk gần nhất |
| `service/ask` | chunk → câu trả lời có căn cứ |
| `controller` | 4 controller ứng với 4 nhóm endpoint trên |

Hai module `common` và `infrastructure` hiện đang rỗng.

## Những chỗ đáng đọc kỹ

### Cắt chunk theo cấu trúc, không theo độ dài

`ChunkingService` cắt tại ranh giới đánh số, không bao giờ cắt giữa một khoản — nửa
khoản luật thì không trả lời được gì. 400 token là **trần**, không phải đích: một Điều
vừa vặn thì thành một chunk `full_dieu` duy nhất dù chỉ 44 token. Một khoản dài quá trần
mà không tách được tại ranh giới Điểm thì vẫn đi nguyên: một chunk quá dài chỉ mất recall
ở đúng khoản đó, còn một khoản đứt giữa câu thì sai ở mọi chỗ nó được trích.

### Mỗi chunk mang theo ngữ cảnh cha

Nội dung đem đi embed luôn có tiền tố:

```
Bộ luật Lao động — Chương VII: THỜI GIỜ LÀM VIỆC… — Điều 113. Nghỉ hằng năm
1. Người lao động làm việc đủ 12 tháng…
```

Không có nó, "Người lao động có các quyền sau đây" không phân biệt được với hàng chục
điều khác mở đầu y hệt. Với điều dẫn từ luật khác, tiền tố lấy tên luật đó và bỏ chương —
chương ở đây thuộc về luật đi sửa, không thuộc về điều được dẫn.

### `TokenEstimator` là ước lượng đã đo, không phải ước lượng đoán

Hằng số ban đầu 1.6 token/âm tiết lấy từ hành vi điển hình của SentencePiece đa ngữ. Chạy
tokenizer thật của bge-m3 trên cả 270 chunk cho con số thật là **1.27** (tỉ lệ so với ước
lượng cũ: trung bình 0.795, độ lệch chuẩn 0.065, n = 270).

Hằng số và trần được sửa **cùng lúc** (1.6 → 1.27 và 500 → 400) để kết quả cắt chunk
không đổi một bit nào, trong khi cả hai con số đều trở nên trung thực.

Cột `chunks.token_count` tồn tại chính là để việc này lặp lại được: mỗi lần embed,
`EmbeddingResult` báo ước lượng cạnh số token thật của model và tỉ lệ lệch giữa chúng
(hiện là 0.982), nên đổi model là thấy ngay cần đo lại — không cần thêm phụ thuộc
tokenizer nào.

### Ba lớp chặn model bịa luật

1. **Không truy hồi được gì trên ngưỡng thì không gọi model.** Trả lời từ chunk gần nhất
   dù nó xa tít chính là cách một trợ lý pháp lý bịa ra điều luật.
2. **Model chỉ trả về id chunk.** Trích dẫn dựng từ hàng trong DB; id model bịa ra bị
   loại (kèm log cảnh báo) trước khi ra tới response.
3. **Model được phép nói không.** `answerable = false` là kết quả trung thực khi trích
   đoạn đúng chủ đề nhưng không đủ để kết luận.

Ngoài ra, `AskService` đọc tên model **từ metadata của response**, không từ cấu hình —
đổi provider mà vẫn báo tên cũ thì mọi câu trả lời đã lưu đều không kiểm chứng lại được.

### Embedding là lượt đi riêng, không nằm trong transaction nạp

Chunk được ghi trước với vector `NULL`, rồi `EmbeddingService` quay lại lấp. Lý do: việc
nạp không được phụ thuộc vào một model server đang sống, và không được giữ kết nối DB
suốt mấy phút. Dịch vụ này cố ý **không** `@Transactional` toàn cục — mỗi lô tự `saveAll`,
nên chạy nửa chừng mà hỏng thì phần đã làm vẫn giữ, và lần sau
`findWithoutEmbedding()` là điểm tiếp tục.

`POST /ingest?embed=true` (mặc định) chạy hai việc nối tiếp *ngoài* transaction nạp. Nếu
embedding hỏng, kết quả trả về `embeddingError` chứ không ném lỗi: lúc đó văn bản đã
commit rồi, báo 5xx sẽ nói rằng việc nạp thất bại trong khi nó không hề thất bại.

## Lược đồ

`start/src/main/resources/db/migration/V1__create_schema.sql`, chạy bằng Flyway lúc khởi
động; Hibernate để `ddl-auto: validate`.

```
documents ─┬─ chapters ─── sections
           └─ articles ─── chunks
```

`articles` trỏ thẳng tới `documents` (chứ không chỉ qua `chapters`) vì việc xoá phải theo
đúng thứ tự khi thay văn bản. `chunks` có hai chỉ mục: HNSW trên `embedding` với
`vector_cosine_ops`, và GIN trên `cross_refs`.

Điểm tương đồng là `1 - (embedding <=> query)`. Đẳng thức này đúng vì bge-m3 trả về vector
đơn vị — đổi sang model không chuẩn hoá thì phải sửa cả câu truy vấn.

## Kiểm thử

```bash
./mvnw test          # 104 test, cần Docker cho Testcontainers
```

Test tích hợp trong `start/` bật container `pgvector/pgvector:pg16` thật, vì phần đáng
sai nhất — mảng `INT[]`, `JSONB`, `vector(1024)`, toán tử `<=>`, thứ tự xoá — là đúng
những thứ H2 không mô phỏng được. `VectorSearchTest` dựng vector đơn vị bằng tay để kiểm
tra thứ hạng mà không cần model server.

Test cấp dịch vụ dùng `EmbeddingModel` và `ChatModel` giả, nên chạy được khi không có
Ollama.

## Hạn chế đã biết

**Dẫn chiếu không phân biệt được luật.** `chunks.cross_refs` là `INT[]`, chỉ lưu số Điều.
Điều 54 của Luật BHXH viết "khoản 1 **Điều 2 của Luật này**" và "khoản 2 **Điều 169 của
Bộ luật Lao động**" trong cùng một chunk — hai chữ số trỏ về hai luật khác nhau. Khi
`expandRefs=true`, Điều 169 ra đúng còn Điều 2 ra sai. Ảnh hưởng 3/222 điều. Vì vậy
`expandRefs` **mặc định tắt** ở `/api/ask`: một điều sai trong ngữ cảnh sẽ thành một trích
dẫn sai trong câu trả lời. Sửa được bằng cách bắt luôn phần định danh luật lúc cắt chunk
và đổi `cross_refs` sang JSONB — cần migration mới và nạp lại.

**`documents.code` chưa có UNIQUE index.** Việc chặn trùng hiện chỉ nằm ở tầng dịch vụ;
`findByCode` trả `Optional`, nên nếu có hai hàng cùng mã (do ghi thẳng vào DB) thì nó ném
`IncorrectResultSizeDataAccessException`. Nên gộp vào cùng migration với việc trên.

**PDF đóng gói thiếu Điều 220.** Phần thân văn bản dừng giữa chừng ở trang 85 (đang trong
đoạn dẫn Điều 32 BLTTDS), trang 86 là trang ký. Đây là khiếm khuyết của file nguồn chứ
không phải của parser: 219 Điều có trong file đều được parse, liên tục từ 1 đến 219, không
sót số nào. Nhưng corpus vì thế thiếu Điều 220 *Hiệu lực thi hành*.

**Redis và RabbitMQ khai báo nhưng chưa dùng.** Có trong compose và trong
`application.yaml`, chưa có code nào đụng tới.

**Corpus chỉ có một bộ luật.** Chưa có gì kiểm việc truy hồi phân biệt như thế nào khi
trong DB có nhiều văn bản độc lập.

**Kiến trúc là naive RAG.** Truy hồi một lần, nhồi vào ngữ cảnh, sinh một lần. Model không
quyết định tra cứu gì — đó là cái làm nó tái lập được, và cũng là cái làm nó không xử lý
được câu hỏi cần tra nhiều bước. Một endpoint agentic (`SearchService` và một `getArticle`
làm tool) sẽ đồng thời hoá giải lỗi dẫn chiếu ở trên, vì một agent đọc nguyên văn
"Điều 2 của Luật này" thì hiểu ngay đó là luật nào.

## Cấu hình

Biến môi trường:

| Biến | Mặc định | |
|---|---|---|
| `POSTGRES_DB` `POSTGRES_USER` `POSTGRES_PASS` | — | bắt buộc, dùng chung với compose |
| `DB_HOST` | `localhost` | |
| `OLLAMA_HOST` | `http://localhost:11434` | |
| `SPRING_PROFILES_ACTIVE` | `local` | |
| `ANTHROPIC_API_KEY` | rỗng | chỉ cần khi đổi `spring.ai.model.chat` sang `anthropic` |

`spring-ai-starter-model-anthropic` vẫn nằm trong dependency, cấu hình sẵn ở
`spring.ai.anthropic`, nhưng đang tắt. Cả hai starter đều tự cấu hình một `ChatModel`, nên
`spring.ai.model.chat` / `spring.ai.model.embedding` **bắt buộc phải chỉ rõ** — thiếu là
`ChatClient.Builder` gặp hai bean và ứng dụng không khởi động được.

`.env` ở gốc repo (cũng nằm ngoài git) giữ ba biến Postgres và hai biến RabbitMQ, dùng
chung cho compose và cho ứng dụng.

`application-local.yaml` nằm ngoài git (`.gitignore` có `*-local.*`), dùng để ghi đè cấu
hình cho máy cá nhân. Thư mục `docs/` cũng nằm ngoài git.

**Một cái bẫy:** profile `local` đang bật mặc định, và `application-local.yaml` ghi cứng
`spring.datasource.url` trỏ vào `parajudge_db`. Nghĩa là đặt `POSTGRES_DB` khi chạy sẽ
**không** có tác dụng — biến môi trường thua giá trị ghi cứng trong profile. Muốn trỏ vào
DB khác thì phải ghi đè thẳng thuộc tính đó:

```bash
./mvnw -pl start spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/ten_db_khac"
```
