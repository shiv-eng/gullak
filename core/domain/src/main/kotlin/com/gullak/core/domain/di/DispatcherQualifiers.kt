package com.gullak.core.domain.di

import javax.inject.Qualifier

/** Dispatcher for blocking I/O (database, network). Injected so tests can substitute a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/** Dispatcher for CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/** A process-lifetime scope for work that must outlive any screen (e.g. keeping the demo clock in sync). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
