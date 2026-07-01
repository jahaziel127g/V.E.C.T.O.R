use std::process::Command;

const WIKI_MIN_QUERY_LEN: usize = 15;
const WIKI_MIN_CONTENT: usize = 50;
const WIKI_MAX_CONTENT: usize = 1200;

/// Common question words to strip before ZIM search.
const STOP_WORDS: &[&str] = &[
    "what", "is", "are", "the", "a", "an", "do", "does", "did",
    "how", "why", "where", "when", "who", "which",
    "define", "meaning", "tell", "me", "about",
    "explain", "describe", "please", "can", "you",
    "give", "some", "information",
];

/// Extract meaningful keywords from a question, sorted by length descending.
fn extract_keywords(query: &str) -> Vec<String> {
    let lower = query.to_lowercase();
    let mut words: Vec<String> = lower
        .split_whitespace()
        .filter(|w| w.len() > 2 && !STOP_WORDS.contains(w))
        .map(|w| w.trim_matches(|c: char| !c.is_alphanumeric()).to_string())
        .filter(|w| !w.is_empty())
        .collect();
    words.sort_by_key(|b| std::cmp::Reverse(b.len()));
    words.dedup();
    words
}

#[allow(dead_code)]
struct SearchResult {
    id: u64,
    score: u32,
    title: String,
}

fn parse_search_results(output: &str) -> Vec<SearchResult> {
    let mut results = Vec::new();
    let mut prev_article: Option<u64> = None;

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        if let Some(rest) = trimmed.strip_prefix("article ") {
            let id: u64 = if let Some(end) = rest.find(|c: char| !c.is_ascii_digit()) {
                rest[..end].parse().ok()
            } else {
                rest.parse().ok()
            }.unwrap_or(0);
            if id > 0 {
                prev_article = Some(id);
            }
        } else if let Some(rest) = trimmed.strip_prefix("score ") {
            let score_end = rest.find(|c: char| !c.is_ascii_digit()).unwrap_or(rest.len());
            let score: u32 = rest[..score_end].parse().unwrap_or(0);

            if let Some(id) = prev_article {
                let title = if let Some(pos) = trimmed.find(':') {
                    trimmed[pos + 1..].trim().to_string()
                } else {
                    String::new()
                };
                if score > 0 && !title.is_empty() {
                    results.push(SearchResult { id, score, title });
                }
                prev_article = None;
            }
        }
    }
    results
}

fn pick_best_article(query: &str, results: &[SearchResult]) -> Option<u64> {
    if results.is_empty() {
        return None;
    }

    let q_words: Vec<String> = query
        .to_lowercase()
        .split_whitespace()
        .map(|w| w.trim_matches(|c: char| !c.is_alphanumeric()).to_string())
        .filter(|w| w.len() > 2)
        .collect();

    // Pass 1: prefer article whose title (lowercased) matches a query keyword exactly
    for r in results {
        let title_lower = r.title.to_lowercase().trim().to_string();
        if q_words.contains(&title_lower) {
            return Some(r.id);
        }
    }

    // Pass 2: prefer article whose title contains a query keyword
    for r in results {
        let title_lower = r.title.to_lowercase();
        if q_words.iter().any(|w| title_lower.contains(w.as_str())) {
            return Some(r.id);
        }
    }

    // Pass 3: skip parenthetical disambiguation unless query has parens
    let q_has_parens = query.contains('(') || query.contains('（');
    for r in results {
        if q_has_parens || !r.title.contains('(') {
            return Some(r.id);
        }
    }

    // Last resort: first result
    results.first().map(|r| r.id)
}

fn parse_search_result(query: &str, output: &str) -> Option<u64> {
    let results = parse_search_results(output);
    pick_best_article(query, &results)
}

fn strip_html(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    let mut in_script: u32 = 0;
    let mut prev_space = false;
    let bytes = raw.as_bytes();
    let len = bytes.len();
    let mut i = 0;

    while i < len {
        let b = bytes[i];

        if in_script > 0 {
            // Look for </script> or </style>
            if b == b'<' && i + 8 < len {
                let tag = &raw[i..i + 9].to_lowercase();
                if tag == "</script>" || tag.starts_with("</script") {
                    i += 9;
                    while i < len && bytes[i] != b'>' {
                        i += 1;
                    }
                    i += 1;
                    in_script -= 1;
                    continue;
                }
                if tag == "</style>" || tag.starts_with("</style") {
                    i += 9;
                    while i < len && bytes[i] != b'>' {
                        i += 1;
                    }
                    i += 1;
                    in_script -= 1;
                    continue;
                }
            }
            i += 1;
            continue;
        }

        if b == b'<' {
            i += 1;
            // Check if it's a script or style tag
            let remaining = &raw[i..].to_lowercase();
            if remaining.starts_with("script") || remaining.starts_with("style") {
                in_script = 1;
                // Skip to >
                while i < len && bytes[i] != b'>' {
                    i += 1;
                }
                i += 1;
                continue;
            }
            // Skip tag contents
            while i < len && bytes[i] != b'>' {
                i += 1;
            }
            if i < len {
                i += 1;
            }
            continue;
        }

        if b == b'&' {
            let end = raw[i..].find(';').map(|p| i + p + 1).unwrap_or(len);
            let entity = &raw[i..end];
            let decoded = match entity {
                "&amp;" => "&",
                "&lt;" => "<",
                "&gt;" => ">",
                "&quot;" => "\"",
                "&apos;" => "'",
                "&#39;" => "'",
                "&#10;" => "\n",
                "&#13;" => "",
                _ => entity,
            };
            for ch in decoded.chars() {
                if ch.is_control() && ch != '\n' {
                    continue;
                }
                if ch.is_whitespace() {
                    if !prev_space {
                        out.push(' ');
                        prev_space = true;
                    }
                } else {
                    out.push(ch);
                    prev_space = false;
                }
            }
            i = end;
            continue;
        }

        if b < 32 && b != b'\n' && b != b'\r' {
            i += 1;
            continue;
        }

        if b.is_ascii_whitespace() {
            if !prev_space {
                out.push(' ');
                prev_space = true;
            }
            i += 1;
            continue;
        }

        prev_space = false;
        out.push(b as char);
        i += 1;
    }

    out.trim().to_string()
}

pub async fn search_wikipedia(
    query: &str,
    zim_path: &str,
    req_id: &str,
) -> Option<String> {
    if query.len() < WIKI_MIN_QUERY_LEN {
        return None;
    }

    // Fallback search: try full query first, then extracted keywords, then longest keyword
    let search_terms = {
        let kw = extract_keywords(query);
        let longest = kw.first().cloned().unwrap_or_else(|| query.to_string());
        vec![
            query.to_string(),
            if kw.is_empty() { query.to_string() } else { kw.join(" ") },
            longest,
        ]
    };

    let q_for_parse = query.to_string();
    let mut article_id: Option<u64> = None;

    for term in &search_terms {
        if term.len() < 3 {
            continue;
        }
        log::info!("[{}] wiki: search term='{}'", req_id, term);

        let z = zim_path.to_string();
        let t = term.clone();
        let search_output = tokio::task::spawn_blocking(move || {
            Command::new("zimsearch")
                .args([&z, &t])
                .output()
        })
        .await
        .ok()?
        .ok()?;

        let raw = String::from_utf8_lossy(&search_output.stdout);
        article_id = parse_search_result(&q_for_parse, &raw);
        if article_id.is_some() {
            break;
        }
    }
    let article_id = article_id?;

    log::info!("[{}] wiki: best article id={}", req_id, article_id);

    // Step 2: dump article content using the correct subcommand
    let z2 = zim_path.to_string();
    let dump_output = tokio::task::spawn_blocking(move || {
        Command::new("zimdump")
            .args(["show", "--idx", &article_id.to_string(), &z2])
            .output()
    })
    .await
    .ok()?
    .ok()?;

    let content = String::from_utf8_lossy(&dump_output.stdout);
    let text = strip_html(&content);
    let cleaned = clean_content(&text);

    if cleaned.len() < WIKI_MIN_CONTENT {
        log::warn!("[{}] wiki: content too short ({} chars)", req_id, cleaned.len());
        return None;
    }

    // Deduplicate repeated leading title (common in ZIM HTML output)
    let deduped = dedup_leading(&cleaned);

    log::info!("[{}] wiki: extracted {} chars", req_id, deduped.len());
    Some(truncate_sentence(deduped, WIKI_MAX_CONTENT))
}

fn clean_content(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut prev_space = false;

    for ch in s.chars() {
        match ch {
            '[' | ']' | '{' | '}' | '(' | ')' => continue,
            '\\' | '|' | '=' => continue,
            c if c.is_control() => continue,
            c if c.is_whitespace() => {
                if !prev_space {
                    out.push(' ');
                    prev_space = true;
                }
            }
            c => {
                out.push(c);
                prev_space = false;
            }
        }
    }

    out.trim().to_string()
}

/// Remove repeated leading words (e.g. "Gravity Gravity Gravity" → "Gravity").
fn dedup_leading(s: &str) -> String {
    let words: Vec<&str> = s.split_whitespace().collect();
    if words.len() < 3 {
        return s.to_string();
    }
    let first = words[0];
    let mut n = 1;
    for w in &words[1..] {
        if w.trim_end_matches(|c: char| !c.is_alphanumeric()) == first {
            n += 1;
        } else {
            break;
        }
    }
    if n >= 2 {
        let skip: usize = words.iter().take(n).map(|w| w.len() + 1).sum();
        let rest = if skip < s.len() { &s[skip..] } else { "" };
        format!("{} {}", first, rest.trim_start())
    } else {
        s.to_string()
    }
}

fn truncate_sentence(s: String, max: usize) -> String {
    if s.len() <= max {
        return s;
    }
    let truncated: String = s.chars().take(max).collect();
    if let Some(pos) = truncated.rfind('.') {
        if pos > max / 2 {
            return truncated[..=pos].to_string();
        }
    }
    if let Some(pos) = truncated.rfind(' ') {
        if pos > max / 2 {
            return truncated[..pos].to_string();
        }
    }
    truncated
}

/// Decision on how to use retrieved wiki context after relevance scoring.
#[derive(Debug)]
pub enum WikiDecision {
    /// High relevance — use wiki as authoritative source (standalone for factual).
    UseDirectly(String),
    /// Medium relevance — include wiki in model prompt but never standalone.
    CombineWithModel(String),
    /// Low relevance — ignore wiki entirely.
    Ignore,
}

/// Score wiki content relevance to the query.
///
/// Heuristics:
/// - Keyword overlap: fraction of query words appearing in content
/// - Phrase bonus: exact query appears as substring
/// - Title strength: query words match in first sentence
fn score_relevance(query: &str, content: &str) -> f64 {
    let query_lower = query.to_lowercase();
    let content_lower = content.to_lowercase();
    let query_words: Vec<&str> = query_lower
        .split_whitespace()
        .filter(|w| w.len() > 2)
        .collect();
    if query_words.is_empty() {
        return 0.5;
    }
    let overlap = query_words
        .iter()
        .filter(|w| content_lower.contains(*w))
        .count() as f64
        / query_words.len() as f64;
    let phrase_bonus = if content_lower.contains(&query_lower) {
        0.3
    } else {
        0.0
    };
    let first_sentence = content.split('.').next().unwrap_or("");
    let first_lower = first_sentence.to_lowercase();
    let title_strength = query_words
        .iter()
        .filter(|w| first_lower.contains(*w))
        .count() as f64
        / query_words.len() as f64
        * 0.2;
    (overlap + phrase_bonus + title_strength).min(1.0)
}

/// Decide how to use wiki context based on relevance to the query.
pub fn decide_wiki_usage(query: &str, content: &str) -> WikiDecision {
    let score = score_relevance(query, content);
    log::debug!("wiki relevance score: {:.3}", score);
    if score >= 0.5 {
        WikiDecision::UseDirectly(content.to_string())
    } else if score >= 0.25 {
        WikiDecision::CombineWithModel(content.to_string())
    } else {
        WikiDecision::Ignore
    }
}
