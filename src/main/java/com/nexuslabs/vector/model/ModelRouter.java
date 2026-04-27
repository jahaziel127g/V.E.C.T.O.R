package com.nexuslabs.vector.model;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.intelligence.QueryComplexity;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {

    private final String simpleModel;
    private final String complexModel;
    private final String fallbackModel;

    public ModelRouter(AppConfig config) {
        this.simpleModel = config.getModel().getSimpleModel();
        this.complexModel = config.getModel().getComplexModel();
        this.fallbackModel = simpleModel;
    }

    public String getModelForComplexity(QueryComplexity complexity) {
        if (complexity == QueryComplexity.COMPLEX) {
            return complexModel;
        }
        return simpleModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }
}