package com.applycopilot.matching

import com.applycopilot.db.Db
import com.applycopilot.db.Jobs
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Serves [Autocomplete] options. They change only when boards sync, so they're rebuilt at most once per [ttl]. */
class AutocompleteService(private val db: Db, private val ttl: Duration = Duration.ofMinutes(10)) {
    private val cache = AtomicReference<Pair<Instant, AutocompleteOptions>?>(null)

    suspend fun options(): AutocompleteOptions {
        cache.get()?.let { (at, options) -> if (at.isAfter(Instant.now().minus(ttl))) return options }
        val options = Autocomplete.options(db.tx { c -> Jobs.openTitlesAndLocations(c) })
        cache.set(Instant.now() to options)
        return options
    }

    /** Call after a sync so new jobs show up in the counts straight away. */
    fun invalidate() = cache.set(null)
}
