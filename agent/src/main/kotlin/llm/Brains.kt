package org.besomontro.llm

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import java.io.FileInputStream
import java.util.Properties

enum class Brains {
    LmStudioLocal,
    GeminiFlash,
    GeminiPro,
    Kimi
}

fun Brains.client(): LLMClient {
    return when(this) {
        Brains.LmStudioLocal -> {
            OpenAILLMClient(
                apiKey = "lm-studio", // Dummy key for local instance
                settings = OpenAIClientSettings(
                    baseUrl = org.besomontro.config.LocalProperties["lmstudio.api.url"] 
                        ?: error("Missing 'lmstudio.api.url' in local.properties")
                )
            )
        }
        Brains.Kimi -> {
             // Example configuration for OpenRouter
            OpenRouterLLMClient(
                apiKey = org.besomontro.config.LocalProperties["openrouter.api.key"] 
                    ?: error("Missing 'openrouter.api.key' in local.properties")
            )
        }
        Brains.GeminiPro,
        Brains.GeminiFlash -> {
            GoogleLLMClient(
                apiKey = org.besomontro.config.LocalProperties["gemini.api.key"] 
                    ?: error("Missing 'gemini.api.key' in local.properties")
            )
        }
    }
}

fun Brains.model(): LLModel {
    return when(this) {
        Brains.LmStudioLocal -> OpenAIModels.Chat.O3Mini
        Brains.GeminiFlash -> GoogleModels.Gemini2_5Flash
        Brains.GeminiPro -> GoogleModels.Gemini3_Pro_Preview
        Brains.Kimi -> LLModel(
            provider = LLMProvider.OpenRouter,
            id = "moonshotai/kimi-k2.5",  // OpenRouter's model ID for Kimi
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Schema.JSON.Basic
            ),
            contextLength = 250_000L,
            maxOutputTokens = 200_000L
        )
    }
}