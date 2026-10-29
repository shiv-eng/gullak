package com.gullak.core.model.result

/** The outcome of an operation that can fail in an expected way. Exceptions are for bugs only. */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    data object Network : AppError

    data object Timeout : AppError

    data class Http(val code: Int) : AppError

    /** The response arrived but could not be parsed or did not match the expected schema. */
    data class InvalidResponse(val detail: String) : AppError

    data class InvalidPlanInput(val reason: PlanInputError) : AppError

    /** A mocked Paytm gateway refused the request. */
    data class Gateway(val detail: String) : AppError

    data class NotFound(val what: String) : AppError

    /** The goal has no unposted debit left to simulate. */
    data object NothingDue : AppError

    /** The demo clock cannot move backwards. */
    data object DateNotInFuture : AppError
}

enum class PlanInputError {
    TARGET_NOT_POSITIVE,
    TARGET_DATE_NOT_IN_FUTURE,
    START_AFTER_TARGET,

    /** The chosen frequency is not one of the plan's options (or none was chosen for a recurring plan). */
    FREQUENCY_NOT_AVAILABLE,
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

/** Unwraps a success; use `getOrElse { return it }` to propagate a failure from the calling function. */
inline fun <T> AppResult<T>.getOrElse(onFailure: (AppResult.Failure) -> T): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> onFailure(this)
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
