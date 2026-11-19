package stapel.cache.room

import androidx.room3.Room
import androidx.room3.RoomDatabase

internal actual fun cacheDatabaseBuilder(path: String): RoomDatabase.Builder<CacheDatabase> =
    Room.databaseBuilder<CacheDatabase>(name = path)
