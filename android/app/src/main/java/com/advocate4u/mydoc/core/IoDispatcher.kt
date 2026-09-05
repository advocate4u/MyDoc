package com.advocate4u.mydoc.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface IoDispatcher { val io: CoroutineDispatcher }

object AppDispatchers : IoDispatcher { override val io: CoroutineDispatcher = Dispatchers.IO }
