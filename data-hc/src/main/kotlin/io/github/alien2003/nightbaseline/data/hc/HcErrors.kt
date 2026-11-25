package io.github.alien2003.nightbaseline.data.hc

import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Health Connect failure, classified. Messages name the operation and the exception type only: the client's own
 * messages are dropped (and the cause is not attached), so nothing read from Health Connect can reach a log.
 */
sealed class HcError(
    message: String,
) : Exception(message) {
    /** Health Connect is not installed, not supported or must be updated. */
    class Unavailable(
        operation: String,
    ) : HcError("$operation: Health Connect is unavailable")

    /**
     * A permission is missing. [background] marks a read refused because the app is not in the foreground and
     * READ_HEALTH_DATA_IN_BACKGROUND is not granted.
     */
    class PermissionMissing(
        /** The missing permissions, when known. */
        val permissions: Set<String>,
        /** Refused for lack of the background-read permission. */
        val background: Boolean = false,
    ) : HcError(
            "permission missing: ${permissions.sorted().joinToString().ifEmpty { "unknown" }}${if (background) " (background)" else ""}",
        )

    /** The rate limit was hit (or a sync blocks reads): retry later. */
    class RateLimited(
        operation: String,
    ) : HcError("$operation: rate limited")

    /** Anything else; [retryable] for IPC / IO errors that may pass later. */
    class Other(
        operation: String,
        /** Simple class name of the original exception. */
        val errorType: String,
        /** Worth retrying later. */
        val retryable: Boolean,
    ) : HcError("$operation: $errorType")
}

/**
 * Pure mapping of client exceptions to [HcError] (adapted from PulseStages `HealthExceptionMapper`, verified for
 * connect-client 1.1.0):
 * - a platform `HealthConnectException` (API 34+, the cause of an [IllegalStateException]) with code 7 (rate limit)
 *   or 8 (sync in progress) → [HcError.RateLimited], 9 → [HcError.Unavailable], 5 → [HcError.PermissionMissing];
 * - [SecurityException] → [HcError.PermissionMissing];
 * - [UnsupportedOperationException] → [HcError.Unavailable];
 * - other [IllegalStateException] → [HcError.Other] (retryable) when Health Connect is available, else
 *   [HcError.Unavailable] (`getOrCreate` throws "Service not available");
 * - [RemoteException] / [IOException] → [HcError.Other] (retryable; on the Android 9–13 APK a rate limit arrives as a
 *   RemoteException);
 * - anything else → [HcError.Other] (not retryable).
 */
object HcErrorMapper {
    private const val MAX_CAUSE_DEPTH = 8

    /** Maps [error] thrown by [operation]. */
    fun map(
        operation: String,
        error: Throwable,
        platformErrorCode: (Throwable) -> Int? = PlatformErrorCodes::of,
        isAvailable: () -> Boolean = { true },
    ): HcError {
        if (error is HcError) return error
        val code = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).firstNotNullOfOrNull(platformErrorCode)
        val type = error.javaClass.simpleName
        return when {
            code == PlatformErrorCodes.RATE_LIMIT_EXCEEDED || code == PlatformErrorCodes.DATA_SYNC_IN_PROGRESS -> {
                HcError.RateLimited(
                    operation,
                )
            }

            code == PlatformErrorCodes.UNSUPPORTED_OPERATION -> {
                HcError.Unavailable(operation)
            }

            code == PlatformErrorCodes.SECURITY || error is SecurityException -> {
                HcError.PermissionMissing(emptySet())
            }

            error is UnsupportedOperationException -> {
                HcError.Unavailable(operation)
            }

            error is IllegalStateException -> {
                if (isAvailable()) {
                    HcError.Other(
                        operation,
                        type,
                        retryable = true,
                    )
                } else {
                    HcError.Unavailable(operation)
                }
            }

            error is RemoteException || error is IOException -> {
                HcError.Other(operation, type, retryable = true)
            }

            else -> {
                HcError.Other(operation, type, retryable = false)
            }
        }
    }
}

// The single place where client exceptions are classified, so the catch is deliberately generic.

/** Runs [block], converting every exception except cancellation into an [HcError] with [mapper]. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> mapHcErrors(
    mapper: (Throwable) -> HcError,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw mapper(e)
    }

/**
 * Error codes of the platform `android.health.connect.HealthConnectException` (API 34+), copied so that no platform
 * field is touched on older devices.
 */
object PlatformErrorCodes {
    /** `ERROR_SECURITY`. */
    const val SECURITY: Int = 5

    /** `ERROR_RATE_LIMIT_EXCEEDED`. */
    const val RATE_LIMIT_EXCEEDED: Int = 7

    /** `ERROR_DATA_SYNC_IN_PROGRESS`. */
    const val DATA_SYNC_IN_PROGRESS: Int = 8

    /** `ERROR_UNSUPPORTED_OPERATION`. */
    const val UNSUPPORTED_OPERATION: Int = 9

    /** The error code of [error] if it is a platform `HealthConnectException`, otherwise null (always null below API 34). */
    fun of(error: Throwable): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Api34.errorCode(error) else null

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        fun errorCode(error: Throwable): Int? = (error as? android.health.connect.HealthConnectException)?.errorCode
    }
}

/** One page of a paged read. */
data class Page<T>(
    /** Items of the page. */
    val items: List<T>,
    /** Token of the next page; null or empty on the last page. */
    val nextToken: String?,
)

/**
 * Reads every page from [fetch] (called with null first, then with each returned token). Stops on a null **or empty**
 * token: the Android 9–13 APK returns `""` on the last page, and sending it back throws. [maxPages] guards against a
 * provider that keeps returning tokens.
 */
suspend fun <T> readAllPages(
    maxPages: Int = DEFAULT_MAX_PAGES,
    fetch: suspend (pageToken: String?) -> Page<T>,
): List<T> {
    val out = ArrayList<T>()
    var token: String? = null
    var pages = 0
    do {
        val page = fetch(token)
        out += page.items
        token = page.nextToken
        pages++
    } while (!token.isNullOrEmpty() && pages < maxPages)
    return out
}

/** Upper bound of pages per read (5000 records each). */
const val DEFAULT_MAX_PAGES: Int = 1_000
