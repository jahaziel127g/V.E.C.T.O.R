use moka::sync::Cache;

fn cache_key(input: &str) -> String {
    let normalized: String = input
        .chars()
        .filter(|c| !c.is_whitespace() || *c == ' ')
        .flat_map(|c| c.to_lowercase())
        .collect();
    let cleaned: String = normalized
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    blake3::hash(cleaned.as_bytes()).to_hex().to_string()
}

pub struct AppCache {
    pub answer: Cache<String, String>,
    pub wiki: Cache<String, String>,
    pub stt: Cache<String, String>,
}

impl AppCache {
    pub fn new(max_size: u64) -> Self {
        let stt_max = max_size.min(5000);
        AppCache {
            answer: Cache::builder()
                .max_capacity(max_size)
                .build(),
            wiki: Cache::builder()
                .max_capacity(max_size.max(1000) / 2)
                .build(),
            stt: Cache::builder()
                .max_capacity(stt_max)
                .build(),
        }
    }

    fn key(input: &str) -> String {
        cache_key(input)
    }

    pub fn get_answer(&self, question: &str) -> Option<String> {
        self.answer.get(&Self::key(question))
    }

    pub fn insert_answer(&self, question: &str, answer: String) {
        self.answer.insert(Self::key(question), answer);
    }

    pub fn get_wiki(&self, question: &str) -> Option<String> {
        self.wiki.get(&Self::key(question))
    }

    pub fn insert_wiki(&self, question: &str, content: String) {
        self.wiki.insert(Self::key(question), content);
    }

    pub fn get_stt(&self, hash: &str) -> Option<String> {
        self.stt.get(hash)
    }

    pub fn insert_stt(&self, hash: &str, text: String) {
        self.stt.insert(hash.to_string(), text);
    }

    pub fn stats(&self) -> (u64, u64, u64) {
        (
            self.answer.entry_count(),
            self.wiki.entry_count(),
            self.stt.entry_count(),
        )
    }
}
