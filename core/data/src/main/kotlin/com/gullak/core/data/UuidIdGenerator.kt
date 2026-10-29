package com.gullak.core.data

import com.gullak.core.domain.gateway.IdGenerator
import java.util.UUID
import javax.inject.Inject

internal class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
