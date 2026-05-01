#!/bin/bash
# Self-contained runner for V.E.C.T.O.R with llama.cpp

# Set inference backend to llama.cpp
export VECTOR_INFERENCE_BACKEND=llama.cpp

# Set paths
export VECTOR_LLAMA_CLI_PATH=$(pwd)/target/bin/llama-cli
export VECTOR_MODEL_SIMPLE_MODEL_PATH=$(pwd)/models/gemma-3-1b-it.q4_K_M.gguf
export VECTOR_MODEL_COMPLEX_MODEL_PATH=$(pwd)/models/deepseek-r1-1.5b.q4_K_M.gguf

# Run the application
java -jar target/vector-1.0.0.jar