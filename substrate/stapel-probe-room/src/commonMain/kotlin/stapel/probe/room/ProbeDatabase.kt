package stapel.probe.room

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Entity(tableName = "probe_note")
internal data class ProbeNoteRow(@PrimaryKey val id: Long, val text: String)

@Dao
internal interface ProbeNoteDao {
    @Insert
    suspend fun insert(note: ProbeNoteRow)

    @Query("SELECT * FROM probe_note ORDER BY id")
    suspend fun all(): List<ProbeNoteRow>
}

@Database(entities = [ProbeNoteRow::class], version = 1)
@ConstructedBy(ProbeDatabaseConstructor::class)
internal abstract class ProbeDatabase : RoomDatabase() {
    abstract fun notes(): ProbeNoteDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
