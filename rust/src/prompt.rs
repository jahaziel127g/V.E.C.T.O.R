use serde_json::Value;
use std::collections::VecDeque;

use crate::planner::{Intent, ResponseMode, mode_for_intent};

pub fn build_prompt(
    question: &str,
    intent: Intent,
    wiki_context: Option<&str>,
    history: &VecDeque<(String, String)>,
) -> Vec<Value> {
    let mut messages: Vec<Value> = Vec::new();

    let mode = mode_for_intent(intent);
    let system = build_system_prompt(intent, mode, wiki_context);
    messages.push(serde_json::json!({"role": "system", "content": system}));

    for (q, a) in history.iter() {
        messages.push(serde_json::json!({"role": "user", "content": q}));
        messages.push(serde_json::json!({"role": "assistant", "content": a}));
    }

    let user_content = build_user_prompt(intent, question);
    messages.push(serde_json::json!({"role": "user", "content": user_content}));

    messages
}

fn build_system_prompt(intent: Intent, mode: ResponseMode, wiki_context: Option<&str>) -> String {
    let base = "You are V.E.C.T.O.R, a knowledgeable AI assistant.\n\nCore rules:\n- Answer the user directly and confidently.\n- Do not add code unless requested.\n- Use tables, lists, and headings to structure information when helpful.\n- Provide thorough, detailed answers. Use the Response Mode below as a guide.\n- If you need to reason step-by-step before answering, wrap that internal reasoning inside <think>...</think> tags. Only the final answer should appear outside those tags.";

    let style_hint = match intent {
        Intent::Greeting => "\nRespond with a friendly greeting.",
        Intent::Factual => "\nGive an accurate, detailed fact.",
        Intent::Explanation => "\nExplain step by step with examples.",
        Intent::Debugging => "\nIdentify the likely cause and suggest a practical fix.",
        Intent::General => "\nProvide a thorough answer.",
    };

    let mode_instruction = match mode {
        ResponseMode::Quick => "\n\nResponse Mode: Quick\n- Direct answer with sufficient detail.\n- Use examples and structure when helpful.",
        ResponseMode::Study => "\n\nResponse Mode: Study\n- Structured explanation with examples.\n- Step-by-step reasoning.\n- Break down complex ideas.",
        ResponseMode::Debug => "\n\nResponse Mode: Debug\n- Identify the issue.\n- Explain the root cause.\n- Propose fixes step by step.",
    };

    let rag_instruction = "\n\nRetrieved Knowledge:\nWhen I provide a 'Reference:' section below, use it as your primary source.\n- Do not repeat the reference verbatim. Summarize in your own words.\n- If the reference does not answer the question, use your general knowledge.\n- Never claim the reference supports something it does not.";

    let stt_warning = "\n\nThe user's input may come from speech-to-text transcription and may contain typos, missing punctuation, or phonetic errors. Interpret the intended meaning rather than the exact text.";

    let mut parts: Vec<String> = Vec::new();
    parts.push(format!("{}{}{}{}{}", base, style_hint, mode_instruction, rag_instruction, stt_warning));

    if let Some(ctx) = wiki_context {
        let trimmed = trim_wiki_context(ctx);
        if !trimmed.is_empty() {
            parts.push(format!("Reference:\n{}", trimmed));
        }
    }

    parts.join("\n\n")
}

fn build_user_prompt(intent: Intent, question: &str) -> String {
    match intent {
        Intent::Greeting => question.to_string(),
        Intent::Factual => format!("Question: {}", question),
        Intent::Explanation => format!("Explain: {}", question),
        Intent::Debugging => format!("Debug: {}", question),
        Intent::General => question.to_string(),
    }
}

fn trim_wiki_context(ctx: &str) -> String {
    if ctx.len() <= 800 {
        return ctx.to_string();
    }
    let truncated = &ctx[..800];
    if let Some(pos) = truncated.rfind(". ") {
        if pos > 400 {
            return truncated[..=pos].to_string();
        }
    }
    truncated.to_string()
}
