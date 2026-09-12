package com.studylens.ai

// Defines the lifecycle of the LLM Engine sitting in RAM
sealed class EngineState {
    data object Uninitialized : EngineState()
    data object LoadingIntoMemory : EngineState()
    data object Ready : EngineState()
    data class Error(val message: String) : EngineState()
}

// Emitted token-by-token during inference
sealed class GenerationState {
    data object Idle : GenerationState()
    data object Generating : GenerationState()
    data class Partial(val token: String) : GenerationState()
    data class Done(val fullResponse: String) : GenerationState()
    data class Error(val message: String) : GenerationState()
}
