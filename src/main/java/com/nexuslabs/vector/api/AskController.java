package com.nexuslabs.vector.api;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.processing.RateLimiter;
import com.nexuslabs.vector.processing.InputSanitizer;
import com.nexuslabs.vector.intelligence.QuestionClassifier;
import com.nexuslabs.vector.intelligence.QueryComplexity;
import com.nexuslabs.vector.memory.AnswerCache;
import com.nexuslabs.vector.knowledge.WikipediaService;
import com.nexuslabs.vector.context.ContextOptimizer;
import com.nexuslabs.vector.prompt.PromptBuilder;
import com.nexuslabs.vector.model.ModelRouter;
import com.nexuslabs.vector.model.ModelManager;
import com.nexuslabs.vector.inference.OllamaClient;
import com.nexuslabs.vector.inference.LlamaCppClient;
import com.nexuslabs.vector.response.ResponseProcessor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AskController {

    private final InputSanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final QuestionClassifier classifier;
    private final AnswerCache answerCache;
    private final WikipediaService wikipediaService;
    private final ContextOptimizer contextOptimizer;
    private final PromptBuilder promptBuilder;
    private final ModelRouter modelRouter;
    private final ModelManager modelManager;
    private final OllamaClient ollamaClient;
    private final LlamaCppClient llamaCppClient;
    private final ResponseProcessor responseProcessor;
    private final AppConfig config;

    public AskController(InputSanitizer sanitizer,
                         RateLimiter rateLimiter,
                         QuestionClassifier classifier,
                         AnswerCache answerCache,
                         WikipediaService wikipediaService,
                         ContextOptimizer contextOptimizer,
                         PromptBuilder promptBuilder,
                         ModelRouter modelRouter,
                         ModelManager modelManager,
                         OllamaClient ollamaClient,
                         LlamaCppClient llamaCppClient,
                         ResponseProcessor responseProcessor,
                         AppConfig config) {
        this.sanitizer = sanitizer;
        this.rateLimiter = rateLimiter;
        this.classifier = classifier;
        this.answerCache = answerCache;
        this.wikipediaService = wikipediaService;
        this.contextOptimizer = contextOptimizer;
        this.promptBuilder = promptBuilder;
        this.modelRouter = modelRouter;
        this.modelManager = modelManager;
        this.ollamaClient = ollamaClient;
        this.llamaCppClient = llamaCppClient;
        this.responseProcessor = responseProcessor;
        this.config = config;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@Valid @RequestBody AskRequest request,
                                  @RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestHeader(value = "X-Forwarded-For", required = false, defaultValue = "unknown") String forwardedIp) {
        
        long startTime = System.currentTimeMillis();
        
        String clientIp = request.getIpAddress() != null ? request.getIpAddress() : forwardedIp;
        String identifier = userId != null ? userId : clientIp;
        
        if (!rateLimiter.isAllowed(identifier)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Please try again later."));
        }
        
        String sanitizedQuestion = sanitizer.sanitize(request.getQuestion());
        if (sanitizedQuestion == null || sanitizedQuestion.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid question after sanitization"));
        }
        
        // Skip classifier - use SIMPLE always (one model strategy)
        QueryComplexity complexity = QueryComplexity.SIMPLE;
        String cacheKey = sanitizedQuestion.toLowerCase().trim();
        
        // Quick cache check first (fastest path)
        String cachedAnswer = answerCache.get(cacheKey);
        if (cachedAnswer != null) {
            AskResponse response = new AskResponse(
                cachedAnswer,
                "cached",
                "answer cache",
                complexity.name().toLowerCase()
            );
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return ResponseEntity.ok(response);
        }
        
        // Skip Wikipedia for short queries (speed optimization)
        String wikiContext = null;
        if (config.getWikipedia().isEnabled() && sanitizedQuestion.length() > 20) {
            Optional<String> wikiOpt = wikipediaService.getContextForQuery(sanitizedQuestion);
            if (wikiOpt.isPresent()) {
                wikiContext = contextOptimizer.optimize(wikiOpt.get());
            }
        }
        
        String modelName = config.getModel().getSimpleModel(); // Direct model access
        
        // Use appropriate inference backend based on configuration
        String rawResponse;
        try {
            if ("llama.cpp".equals(config.getInferenceBackend())) {
                modelManager.ensureModelLoaded(modelName);
                rawResponse = llamaCppClient.generateResponse(promptBuilder.build(sanitizedQuestion, complexity, wikiContext), false);
            } else {
                modelManager.ensureModelLoaded(modelName);
                String prompt = promptBuilder.build(sanitizedQuestion, complexity, wikiContext);
                rawResponse = ollamaClient.generate(prompt, modelName);
            }
        } catch (Exception e) {
            rawResponse = "I apologize, but I'm unable to process your request at the moment. Please try again later.";
        }
        String processedAnswer = responseProcessor.process(rawResponse);
        
        answerCache.put(cacheKey, processedAnswer);
        
        AskResponse response = new AskResponse(
            processedAnswer,
            modelName,
            wikiContext != null ? "wikipedia + local model" : "local model",
            complexity.name().toLowerCase()
        );
        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "V.E.C.T.O.R"));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        return ResponseEntity.ok(modelManager.getModelStatus());
    }
}