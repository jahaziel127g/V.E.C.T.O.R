use crate::state::AppState;

#[derive(Clone, Copy, PartialEq)]
pub enum Intent {
    Greeting,
    Factual,
    Explanation,
    Debugging,
    General,
}

#[derive(Clone, Copy, PartialEq)]
pub enum ResponseMode {
    Quick,
    Study,
    Debug,
}

pub fn mode_for_intent(intent: Intent) -> ResponseMode {
    match intent {
        Intent::Greeting | Intent::Factual | Intent::General => ResponseMode::Quick,
        Intent::Explanation => ResponseMode::Study,
        Intent::Debugging => ResponseMode::Debug,
    }
}

pub struct RequestPlan {
    pub cached_answer: Option<String>,
    pub intent: Intent,
    pub wiki_from_cache: Option<String>,
    pub needs_wiki_search: bool,
    pub needs_model: bool,
}

fn classify_intent(query: &str) -> Intent {
    let lower = query.trim().to_lowercase();

    // Short greetings — respond briefly, no follow-up
    {
        let trimmed = lower.trim();
        if trimmed.len() <= 12 {
            let greetings = [
                "hi", "hello", "hey", "greetings", "good morning", "good evening",
                "good afternoon", "sup", "yo", "howdy", "what's up", "hi there",
                "hello there", "hey there", "morning", "evening",
            ];
            if greetings.contains(&trimmed) {
                return Intent::Greeting;
            }
        }
    }

    let fact_keywords = [
        "what is", "define", "meaning of", "definition",
        "what are", "what does", "who is", "when did",
        "where is", "how many", "how much", "capital of",
    ];
    if fact_keywords.iter().any(|k| lower.starts_with(k)) {
        return Intent::Factual;
    }

    let explain_keywords = [
        "explain", "how does", "how do", "describe",
        "why is", "why does", "how it works", "purpose of",
        "difference between", "compare",
    ];
    if explain_keywords.iter().any(|k| lower.starts_with(k)) {
        return Intent::Explanation;
    }

    let debug_keywords = [
        "fix", "error", "bug", "debug", "not working",
        "problem", "issue", "crash", "fail", "broken",
        "help me fix", "why won't", "doesn't work",
    ];
    if debug_keywords.iter().any(|k| lower.contains(k)) {
        return Intent::Debugging;
    }

    Intent::General
}

pub fn plan_request(query: &str, state: &AppState) -> RequestPlan {
    let query = query.trim();

    if let Some(cached) = state.cache.get_answer(query) {
        return RequestPlan {
            cached_answer: Some(cached),
            intent: Intent::General,
            wiki_from_cache: None,
            needs_wiki_search: false,
            needs_model: false,
        };
    }

    let intent = classify_intent(query);
    let wiki_from_cache = state.cache.get_wiki(query);
    let needs_wiki_search = wiki_from_cache.is_none() && query.len() >= 15 && intent != Intent::Greeting;

    let needs_model = match intent {
        Intent::Greeting => true,
        Intent::Factual => !needs_wiki_search && wiki_from_cache.is_none(),
        Intent::Explanation => true,
        Intent::Debugging => true,
        Intent::General => true,
    };

    RequestPlan {
        cached_answer: None,
        intent,
        wiki_from_cache,
        needs_wiki_search,
        needs_model,
    }
}
