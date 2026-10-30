package com.gullak.core.ai

import okhttp3.Interceptor

/** Release builds never log HTTP traffic. */
@Suppress("FunctionOnlyReturningConstant")
internal fun networkLoggingInterceptor(): Interceptor? = null
