package stapel.probe.room

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Notes kept in an in-memory Room database on the bundled SQLite driver.
 *
 * Room types stay internal: the public surface is this class only.
 */
public class ProbeNotes {
    private val db: ProbeDatabase = probeDatabaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .build()

    /** Stores [text] under [id]; fails on a duplicate id. */
    public suspend fun add(id: Long, text: String) {
        db.notes().insert(ProbeNoteRow(id, text))
    }

    /** Every stored text, ordered by id. */
    public suspend fun texts(): List<String> = db.notes().all().map { it.text }

    /** Closes the database. */
    public fun close() {
        db.close()
    }
}

/** Platform builder: Room's builder entry points are platform API (the documented KMP pattern). */
internal expect fun probeDatabaseBuilder(): RoomDatabase.Builder<ProbeDatabase>
