package io.github.fush1ft.summarised.lab

import android.app.Application
import io.github.fush1ft.summarised.lab.log.EventLog
import io.github.fush1ft.summarised.lab.log.Results

class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        Results.init(this)
        EventLog.add("APP", "process started")
        io.github.fush1ft.summarised.lab.bench.BenchRunner.recoverCrashedRun()
    }
}
