package io.github.woxakv.koreadercompanion.core.result

import io.github.woxakv.koreadercompanion.core.error.AppError

sealed interface Try<out T> {
    data class Success<T>(val value: T) : Try<T>
    data class Failure(val error: AppError) : Try<Nothing>
}

inline fun <T, R> Try<T>.map(transform: (T) -> R): Try<R> = when (this) {
    is Try.Success -> Try.Success(transform(value))
    is Try.Failure -> this
}

inline fun <T, R> Try<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is Try.Success -> onSuccess(value)
    is Try.Failure -> onFailure(error)
}

inline fun <T> Try<T>.onSuccess(action: (T) -> Unit): Try<T> {
    if (this is Try.Success) action(value)
    return this
}

inline fun <T> Try<T>.onFailure(action: (AppError) -> Unit): Try<T> {
    if (this is Try.Failure) action(error)
    return this
}

fun <T> Try<T>.getOrNull(): T? = when (this) {
    is Try.Success -> value
    is Try.Failure -> null
}
