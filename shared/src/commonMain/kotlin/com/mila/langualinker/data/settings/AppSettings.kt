package com.mila.langualinker.data.settings

data class AppSettings(
    val requestRetention: Float = 0.9f,
    val maximumInterval: Int = 36500,
    val theme: AppTheme = AppTheme.System,
    val llmApiKey: String = "",
    val llmApiProvider: LlmApiProvider = LlmApiProvider.OpenAI,
)

enum class AppTheme { System, Light, Dark }

enum class LlmApiProvider { OpenAI, Gemini, Custom }
