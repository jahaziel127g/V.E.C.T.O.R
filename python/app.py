#!/usr/bin/env python3
"""
V.E.C.T.O.R - Python/Flask Version
Fast, simple, reliable
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
import subprocess
import json
import re
from functools import lru_cache
import time

app = Flask(__name__)
CORS(app)

OLLAMA_URL = "http://localhost:11434"
MODEL = "gemma3:1b-it-qat"
ZIM_PATH = "/home/jahazielo/Downloads/wikipedia_en_simple_all_nopic_2026-02.zim"

@lru_cache(maxsize=1000)
def search_wikipedia(query):
    """Search Wikipedia ZIM file"""
    try:
        # Search for article
        result = subprocess.run(
            ["zimsearch", ZIM_PATH, query],
            capture_output=True, text=True, timeout=10
        )
        if not result.stdout.strip():
            return None
            
        # Extract article path
        article_path = None
        for line in result.stdout.split('\n'):
            line = line.strip()
            if line.startswith('article:'):
                article_path = line[8:].strip()
                break
        
        if not article_path:
            return None
            
        # Extract article name
        article_name = article_path.split('/')[-1]
        
        # Dump content
        dump = subprocess.run(
            ["zimdump", ZIM_PATH, article_name],
            capture_output=True, text=True, timeout=15
        )
        
        # Extract content
        content = []
        in_content = False
        for line in dump.stdout.split('\n'):
            if line.startswith('Content:'):
                in_content = True
                continue
            if in_content and line.strip():
                content.append(line)
        
        article = ' '.join(content)
        if len(article) < 50:
            return None
            
        # Truncate
        max_len = 1500
        if len(article) > max_len:
            truncated = article[:max_len]
            last_period = truncated.rfind('.')
            if last_period > max_len // 2:
                return truncated[:last_period + 1]
            return truncated
        
        return article
        
    except Exception as e:
        print(f"Wikipedia search error: {e}")
        return None

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy", "service": "V.E.C.T.O.R Python"})

@app.route('/api/ask', methods=['POST'])
def ask():
    start = time.time()
    data = request.get_json()
    question = data.get('question', '').strip()
    
    if not question:
        return jsonify({"error": "Invalid question"}), 400
    
    # Check cache (simple in-memory)
    cache_key = question.lower()
    
    # Search Wikipedia
    wiki_context = None
    if len(question) > 15:
        wiki_context = search_wikipedia(question)
    
    # Build prompt
    if wiki_context:
        prompt = f"Context: {wiki_context}\n\nQuestion: {question}\nAnswer:"
    else:
        prompt = f"Question: {question}\nAnswer:"
    
    # Call Ollama
    try:
        resp = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": MODEL,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "temperature": 0.7,
                    "top_p": 0.9,
                    "num_predict": 150,
                    "num_ctx": 256
                }
            },
            timeout=60
        )
        
        if resp.status_code == 200:
            answer = resp.json().get('response', 'Error')
        else:
            answer = "Error: Ollama returned status {resp.status_code}"
            
    except Exception as e:
        answer = f"Error: {str(e)}"
    
    processing_time = int((time.time() - start) * 1000)
    source = "wikipedia + local model" if wiki_context else "local model"
    
    return jsonify({
        "answer": answer,
        "model": MODEL,
        "source": source,
        "complexity": "simple",
        "processing_time_ms": processing_time
    })

if __name__ == '__main__':
    print("V.E.C.T.O.R Python starting on http://localhost:8080")
    app.run(host='localhost', port=8080, debug=False)
