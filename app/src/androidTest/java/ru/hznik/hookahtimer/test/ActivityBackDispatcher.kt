package ru.hznik.hookahtimer.test

import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

fun dispatchActivityBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<ComponentActivity>()
            .single()
        activity.onBackPressedDispatcher.onBackPressed()
    }
}
