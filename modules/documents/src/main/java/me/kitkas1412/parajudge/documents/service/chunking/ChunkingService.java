package me.kitkas1412.parajudge.documents.service.chunking;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.entity.Section;
import me.kitkas1412.parajudge.documents.service.parser.LegalTextPatterns;
import me.kitkas1412.parajudge.documents.service.parser.model.Amendment;
import me.kitkas1412.parajudge.documents.service.parser.model.AmendmentItem;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedClause;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Cuts an {@link Article} into the retrievable units stored in {@code chunks}.
 *
 * <p>Two rules shape the result. A chunk is cut at a numbering boundary and never
 * inside one, because half a legal clause answers nothing; and every chunk repeats
 * where it came from, because "Người lao động có các quyền sau đây" retrieved on its
 * own cannot be told from the dozen other articles that open the same way. So the
 * text that gets embedded is the Khoản prefixed with its statute, chapter, section
 * and Điều.
 *
 * <p>A Điều that fits whole becomes one {@code full_dieu} chunk. A longer one becomes
 * consecutive {@code khoan_group} chunks, each naming the Khoản it covers in
 * {@code khoan_range}.
 */
public class ChunkingService {

    /** The two values {@code chunks.chunk_type} is documented to take. */
    public static final String FULL_DIEU = "full_dieu";
    public static final String KHOAN_GROUP = "khoan_group";

    /**
     * A real token count now that {@link TokenEstimator} has been calibrated, not a
     * guess with slack in it. Kept below 512 so the corpus stays portable to models
     * with that ceiling, and set so chunks come out the size they were when retrieval
     * was first checked against this corpus — the 1.6-per-syllable estimate that
     * produced them was over-counting by about a fifth.
     */
    public static final int DEFAULT_MAX_TOKENS = 400;

    /**
     * A short lead-in ("Người sử dụng lao động có các nghĩa vụ sau đây:") is what makes
     * the Khoản under it readable, so it is repeated into every chunk of the article.
     * A long one is not a lead-in but substance, and only belongs to the first.
     */
    private static final int LEAD_REPEAT_MAX_TOKENS = 60;

    private static final String CONTEXT_SEPARATOR = " — ";

    private final int maxTokens;
    private final TokenEstimator tokens;

    public ChunkingService() {
        this(DEFAULT_MAX_TOKENS, new TokenEstimator());
    }

    public ChunkingService(int maxTokens) {
        this(maxTokens, new TokenEstimator());
    }

    public ChunkingService(int maxTokens, TokenEstimator tokens) {
        this.maxTokens = maxTokens;
        this.tokens = tokens;
    }

    /**
     * Chunks an ordinary article. The chunks register themselves with {@code article},
     * so they are saved by the same cascade that saves it.
     */
    public List<Chunk> chunk(Article article, ParsedArticle parsed) {
        String prefix = contextPrefix(article);
        int budget = maxTokens - tokens.estimate(prefix);

        String lead = String.join("\n", parsed.leadText());
        List<ParsedClause> clauses = parsed.clauses();
        if (!parsed.amendments().isEmpty()) {
            clauses = amendmentClauses(parsed.amendments());
        } else if (clauses.isEmpty()) {
            Body recovered = recover(lines(lead.isBlank() ? article.getFullText() : lead));
            lead = recovered.lead();
            clauses = recovered.clauses();
        }
        return assemble(article, prefix, lead, units(clauses, budget));
    }

    /**
     * Điều 219's own Khoản, which the amendment parser holds rather than
     * {@link ParsedArticle#clauses()}. Only the instructions go in — "sửa đổi, bổ sung
     * Điều 54 như sau" — because the statute text quoted after each one is stored as an
     * article in its own right, and chunking it here would index it a second time under
     * the wrong law.
     */
    private List<ParsedClause> amendmentClauses(List<Amendment> amendments) {
        List<ParsedClause> clauses = new ArrayList<>();
        for (Amendment amendment : amendments) {
            List<ParsedPoint> points = amendment.items().stream()
                    .map(item -> new ParsedPoint(item.diemNo(), 0, item.instruction()))
                    .toList();
            clauses.add(new ParsedClause(amendment.khoanNo(), 0, amendment.instruction(), points));
        }
        return clauses;
    }

    /**
     * Chunks an article Điều 219 quotes out of another statute. Only its raw lines
     * survive the amendment parse, so the Khoản are recovered from the text — the
     * heading is dropped because {@link #contextPrefix} already states it.
     */
    public List<Chunk> chunk(Article article, AmendmentItem item) {
        String prefix = contextPrefix(article);
        Body body = recover(item.quotedText());
        return assemble(article, prefix, body.lead(),
                units(body.clauses(), maxTokens - tokens.estimate(prefix)));
    }

    private List<Chunk> assemble(Article article, String prefix, String lead, List<Unit> units) {
        if (units.isEmpty()) {
            String body = lead.isBlank() ? article.getFullText() : lead;
            return List.of(create(article, FULL_DIEU, null, prefix, body));
        }

        String whole = body(lead, units);
        if (tokens.estimate(prefix) + tokens.estimate(whole) <= maxTokens) {
            return List.of(create(article, FULL_DIEU, null, prefix, whole));
        }
        return split(article, prefix, lead, units);
    }

    /**
     * Fills a chunk with as many whole units as the budget allows. One that is over
     * budget on its own still goes out whole — an over-long chunk costs recall on that
     * one clause, a clause cut mid-sentence costs correctness everywhere it is quoted.
     */
    private List<Chunk> split(Article article, String prefix, String lead, List<Unit> units) {
        boolean repeatLead = !lead.isBlank() && tokens.estimate(lead) <= LEAD_REPEAT_MAX_TOKENS;
        int prefixCost = tokens.estimate(prefix);
        int leadCost = tokens.estimate(lead);

        List<Chunk> chunks = new ArrayList<>();
        List<Unit> group = new ArrayList<>();
        boolean firstGroup = true;
        int used = 0;
        for (Unit unit : units) {
            int cost = tokens.estimate(unit.text());
            int overhead = prefixCost + (firstGroup || repeatLead ? leadCost : 0);
            if (!group.isEmpty() && overhead + used + cost > maxTokens) {
                chunks.add(toChunk(article, prefix, lead, group, firstGroup, repeatLead));
                firstGroup = false;
                group = new ArrayList<>();
                used = 0;
            }
            group.add(unit);
            used += cost;
        }
        chunks.add(toChunk(article, prefix, lead, group, firstGroup, repeatLead));
        return chunks;
    }

    private Chunk toChunk(Article article, String prefix, String lead, List<Unit> group,
                          boolean firstGroup, boolean repeatLead) {
        String head = firstGroup || repeatLead ? lead : "";
        String from = group.get(0).no();
        String to = group.get(group.size() - 1).no();
        return create(article, KHOAN_GROUP, from.equals(to) ? from : from + "-" + to,
                prefix, body(head, group));
    }

    private Chunk create(Article article, String type, String khoanRange, String prefix, String body) {
        String content = prefix + "\n" + body;
        return new Chunk(article, type, khoanRange, content,
                crossRefs(content, article.getDieuNo()), tokens.estimate(content));
    }

    /**
     * Rebuilds a Khoản with the number the parser stripped, and its Điểm under it.
     * A Khoản too big to embed is handed back as several units of the same number,
     * cut at Điểm boundaries and each repeating the sentence that introduces them —
     * "a) …" on its own is unreadable without the "sau đây:" it hangs from.
     */
    private List<Unit> units(List<ParsedClause> clauses, int budget) {
        List<Unit> units = new ArrayList<>();
        for (ParsedClause clause : clauses) {
            String head = clause.no() + ". " + clause.text();
            if (clause.points().isEmpty()) {
                units.add(new Unit(clause.no(), head));
                continue;
            }
            List<String> points = clause.points().stream()
                    .map(p -> p.no() + ") " + p.text())
                    .toList();
            if (tokens.estimate(head) + points.stream().mapToInt(tokens::estimate).sum() <= budget) {
                units.add(new Unit(clause.no(), join(head, points)));
                continue;
            }
            units.addAll(splitAtPoints(clause.no(), head, points, budget));
        }
        return units;
    }

    private List<Unit> splitAtPoints(String no, String head, List<String> points, int budget) {
        List<Unit> units = new ArrayList<>();
        List<String> group = new ArrayList<>();
        int used = tokens.estimate(head);
        for (String point : points) {
            int cost = tokens.estimate(point);
            if (!group.isEmpty() && used + cost > budget) {
                units.add(new Unit(no, join(head, group)));
                group = new ArrayList<>();
                used = tokens.estimate(head);
            }
            group.add(point);
            used += cost;
        }
        units.add(new Unit(no, join(head, group)));
        return units;
    }

    /**
     * Recovers the Khoản from text the structural parser did not break down — quoted
     * statutes and Điều 219. Điểm are folded into the Khoản they belong to; there is
     * no numbering to validate here, the text is known to be one article already.
     */
    private Body recover(List<String> lines) {
        List<String> lead = new ArrayList<>();
        List<ParsedClause> clauses = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip().replace("“", "").replace("”", "");
            if (line.isBlank() || (clauses.isEmpty() && lead.isEmpty() && isHeading(line))) {
                continue;
            }
            Matcher khoan = LegalTextPatterns.KHOAN.matcher(line);
            if (khoan.matches()) {
                clauses.add(new ParsedClause(khoan.group(1), 0, khoan.group(2), List.of()));
            } else if (clauses.isEmpty()) {
                lead.add(line);
            } else {
                ParsedClause open = clauses.remove(clauses.size() - 1);
                clauses.add(new ParsedClause(open.no(), 0, open.text() + "\n" + line, List.of()));
            }
        }
        return new Body(String.join("\n", lead), clauses);
    }

    /**
     * Where this text sits, so the chunk carries its own context into the index. An
     * article quoted from another statute takes that statute's name and drops the
     * chapter it happens to be printed under, which belongs to the amending code.
     */
    String contextPrefix(Article article) {
        StringBuilder prefix = new StringBuilder();
        boolean own = article.getSourceLaw() == null
                || article.getSourceLaw().equals(article.getDocument().getCode());
        if (own) {
            prefix.append(article.getDocument().getTitle());
            Chapter chapter = article.getChapter();
            prefix.append(CONTEXT_SEPARATOR).append("Chương ").append(chapter.getChapterNo());
            append(prefix, chapter.getTitle());
            Section section = article.getSection();
            if (section != null) {
                prefix.append(CONTEXT_SEPARATOR).append("Mục ").append(section.getSectionNo());
                append(prefix, section.getTitle());
            }
        } else {
            prefix.append(article.getSourceLaw());
        }
        prefix.append(CONTEXT_SEPARATOR).append("Điều ").append(article.getDieuNo());
        if (article.getTitle() != null && !article.getTitle().isBlank()) {
            prefix.append(". ").append(article.getTitle());
        }
        return prefix.toString();
    }

    private void append(StringBuilder prefix, String title) {
        if (title != null && !title.isBlank()) {
            prefix.append(": ").append(title);
        }
    }

    /** The Điều this chunk points at, so a retrieval hit can pull in what it depends on. */
    private Integer[] crossRefs(String content, Integer own) {
        Set<Integer> refs = new LinkedHashSet<>();
        Matcher matcher = LegalTextPatterns.ARTICLE_REFERENCE.matcher(content);
        while (matcher.find()) {
            int no = Integer.parseInt(matcher.group(1));
            if (!Integer.valueOf(no).equals(own)) {
                refs.add(no);
            }
        }
        return refs.stream().sorted(Comparator.naturalOrder()).toArray(Integer[]::new);
    }

    private String body(String lead, List<Unit> units) {
        return join(lead == null ? "" : lead.strip(), units.stream().map(Unit::text).toList());
    }

    private String join(String head, List<String> parts) {
        StringBuilder joined = new StringBuilder(head);
        for (String part : parts) {
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(part);
        }
        return joined.toString();
    }

    private List<String> lines(String text) {
        return text == null ? List.of() : List.of(text.split("\n"));
    }

    private boolean isHeading(String line) {
        return LegalTextPatterns.DIEU.matcher(line).matches();
    }

    /** One Khoản, numbered as it reads on the page ({@code "1"}, {@code "1a"}). */
    private record Unit(String no, String text) {
    }

    /** An article's text split into what comes before the first Khoản, and the Khoản. */
    private record Body(String lead, List<ParsedClause> clauses) {
    }
}
