package stapel.probe.room

import androidx.room3.Room
import androidx.room3.RoomDatabase

internal actual fun probeDatabaseBuilder(): RoomDatabase.Builder<ProbeDatabase> =
    Room.inMemoryDatabaseBuilder<ProbeDatabase>()
