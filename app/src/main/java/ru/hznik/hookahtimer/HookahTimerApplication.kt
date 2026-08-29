package ru.hznik.hookahtimer

import android.app.Application
import ru.hznik.hookahtimer.hall.data.HallRepository
import ru.hznik.hookahtimer.hall.data.local.HookahTimerDatabase
import ru.hznik.hookahtimer.hall.data.local.RoomHallRepository

class HookahTimerApplication : Application() {
    val database: HookahTimerDatabase by lazy {
        HookahTimerDatabase.create(this)
    }

    val hallRepository: HallRepository by lazy {
        RoomHallRepository(database)
    }
}
