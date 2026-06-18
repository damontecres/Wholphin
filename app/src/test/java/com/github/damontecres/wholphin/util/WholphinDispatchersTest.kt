package com.github.damontecres.wholphin.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
fun WholphinDispatchers.configure(testDispatcher: TestDispatcher) {
    Dispatchers.setMain(testDispatcher)
    IO = testDispatcher
    Default = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
fun WholphinDispatchers.reset() {
    Dispatchers.resetMain()
    IO = kotlinx.coroutines.Dispatchers.IO
    Default = kotlinx.coroutines.Dispatchers.Default
}
