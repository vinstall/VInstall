package com.vinstall.alwiz.installer

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class SilentOnly : AbstractCoroutineContextElement(SilentOnly) {
    companion object Key : CoroutineContext.Key<SilentOnly>
}
