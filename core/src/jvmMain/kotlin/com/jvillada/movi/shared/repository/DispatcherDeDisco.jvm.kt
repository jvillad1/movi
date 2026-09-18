package com.jvillada.movi.shared.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun dispatcherDeDisco(): CoroutineDispatcher = Dispatchers.IO
