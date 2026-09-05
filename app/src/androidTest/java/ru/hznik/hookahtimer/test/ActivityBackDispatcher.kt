package ru.hznik.hookahtimer.test

import androidx.activity.OnBackPressedDispatcherOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

fun dispatchActivityBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .singleOrNull()
            ?: error("Exactly one resumed Activity is required to dispatch Back")
        val backOwner = activity as? OnBackPressedDispatcherOwner
            ?: error("The resumed Activity does not own an OnBackPressedDispatcher")
        backOwner.onBackPressedDispatcher.onBackPressed()
    }
    instrumentation.waitForIdleSync()
}
