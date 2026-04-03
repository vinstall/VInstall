package com.vinstall.alwiz.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object InstallEvents {

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String?) : Result()
    }

    private val _results = MutableSharedFlow<Result>(replay = 1, extraBufferCapacity = 1)

    fun emit(result: Result) {
        DebugLog.d("InstallEvents", "emit: $result")
        _results.tryEmit(result)
    }

    fun reset() {
        _results.resetReplayCache()
    }

    suspend fun awaitResult(timeoutMs: Long = 300_000L): Result? =
        withTimeoutOrNull(timeoutMs) {
            _results.first()
        }
}
