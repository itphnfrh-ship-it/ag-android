package com.cruisepass.verify.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CruiseDatabase(context: Context) : SQLiteOpenHelper(context, "cruises.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cruise (
                id TEXT PRIMARY KEY,
                ship TEXT NOT NULL,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                current_port TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE person (
                cruise_id TEXT NOT NULL,
                card_id TEXT NOT NULL,
                type TEXT NOT NULL,
                last_name TEXT NOT NULL,
                first_name TEXT NOT NULL,
                cabin TEXT,
                rank TEXT,
                document_number TEXT,
                issuing_country TEXT,
                nationality TEXT,
                date_of_birth TEXT,
                sex TEXT,
                expiration_date TEXT,
                photo_file TEXT,
                list_no INTEGER,
                document_issue_date TEXT,
                PRIMARY KEY (cruise_id, card_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE app_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createEventLog(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE cruise ADD COLUMN current_port TEXT")
        if (oldVersion < 3) db.execSQL("ALTER TABLE person ADD COLUMN cabin TEXT")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE person ADD COLUMN list_no INTEGER")
            db.execSQL("ALTER TABLE person ADD COLUMN document_issue_date TEXT")
        }
        if (oldVersion < 5) createEventLog(db)
    }

    private fun createEventLog(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE event_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cruise_id TEXT NOT NULL,
                ship TEXT NOT NULL,
                at_millis INTEGER NOT NULL,
                card_id TEXT NOT NULL,
                last_name TEXT NOT NULL,
                first_name TEXT NOT NULL,
                cabin TEXT,
                type TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX event_log_cruise_time ON event_log (cruise_id, at_millis)")
    }

    fun replace(
        cruiseId: String,
        ship: String,
        name: String,
        version: String,
        currentPort: String?,
        people: List<Person>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("person", null, null)
            db.delete("cruise", null, null)
            db.insert("cruise", null, contentValuesOf(
                "id" to cruiseId,
                "ship" to ship,
                "name" to name,
                "version" to version,
                "current_port" to currentPort,
            ))
            people.forEach { person ->
                val values = contentValuesOf(
                    "cruise_id" to cruiseId,
                    "card_id" to person.cardId,
                    "type" to person.type,
                    "last_name" to person.lastName,
                    "first_name" to person.firstName,
                    "cabin" to person.cabin,
                    "rank" to person.rank,
                    "document_number" to person.documentNumber,
                    "issuing_country" to person.issuingCountry,
                    "nationality" to person.nationality,
                    "date_of_birth" to person.dateOfBirth,
                    "sex" to person.sex,
                    "expiration_date" to person.expirationDate,
                    "photo_file" to person.photoPath,
                    "document_issue_date" to person.documentIssueDate,
                )
                if (person.listNo == null) values.putNull("list_no") else values.put("list_no", person.listNo)
                db.insert("person", null, values)
            }
            db.insertWithOnConflict(
                "app_state",
                null,
                contentValuesOf("key" to CURRENT_CRUISE, "value" to cruiseId),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun currentCruise(): LoadedCruise? {
        val cruiseId = readableDatabase.query(
            "app_state",
            arrayOf("value"),
            "key = ?",
            arrayOf(CURRENT_CRUISE),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        val header = readableDatabase.query(
            "cruise",
            arrayOf("id", "ship", "name", "current_port", "version"),
            "id = ?",
            arrayOf(cruiseId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            LoadedCruise(
                id = cursor.getString(0),
                ship = cursor.getString(1),
                name = cursor.getString(2),
                currentPort = cursor.nullable(3),
                paxCount = 0,
                crewCount = 0,
                updatedAt = cursor.getString(4),
            )
        }
        val counts = readableDatabase.rawQuery(
            "SELECT SUM(type = 'pax'), SUM(type = 'crew') FROM person WHERE cruise_id = ?",
            arrayOf(cruiseId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) 0 to 0 else cursor.getInt(0) to cursor.getInt(1)
        }
        return header.copy(paxCount = counts.first, crewCount = counts.second)
    }

    fun find(cruiseId: String, cardId: String): Person? {
        val needle = scannedCardId(cardId)
        if (needle.isEmpty()) return null
        return readableDatabase.query(
            "person",
            personColumns,
            """
            cruise_id = ? AND (
                card_id = ?
                OR (substr(card_id, 1, 1) = '%' AND substr(card_id, 2) = ?)
            )
            """.trimIndent(),
            arrayOf(cruiseId, needle, needle),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toPerson() else null }
    }

    fun findManual(cruiseId: String, query: String): List<Person> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return readableDatabase.query(
            "person",
            personColumns,
            "cruise_id = ?",
            arrayOf(cruiseId),
            null,
            null,
            "last_name, first_name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val person = cursor.toPerson()
                    if (person.matchesManual(needle)) add(person)
                }
            }
        }
    }

    private val personColumns = arrayOf(
        "card_id",
        "type",
        "last_name",
        "first_name",
        "cabin",
        "rank",
        "document_number",
        "issuing_country",
        "nationality",
        "date_of_birth",
        "sex",
        "expiration_date",
        "photo_file",
        "list_no",
        "document_issue_date",
    )

    private fun android.database.Cursor.toPerson() = Person(
        cardId = getString(0),
        type = getString(1),
        lastName = getString(2),
        firstName = getString(3),
        cabin = nullable(4),
        rank = nullable(5),
        documentNumber = nullable(6),
        issuingCountry = nullable(7),
        nationality = nullable(8),
        dateOfBirth = nullable(9),
        sex = nullable(10),
        expirationDate = nullable(11),
        photoPath = nullable(12),
        listNo = if (isNull(13)) null else getInt(13),
        documentIssueDate = nullable(14),
    )

    fun insertEvent(cruise: LoadedCruise, person: Person) {
        val values = contentValuesOf(
            "cruise_id" to cruise.id,
            "ship" to cruise.ship,
            "card_id" to person.cardId,
            "last_name" to person.lastName,
            "first_name" to person.firstName,
            "cabin" to person.cabin,
            "type" to person.type,
        )
        values.put("at_millis", System.currentTimeMillis())
        writableDatabase.insert("event_log", null, values)
    }

    fun events(cruiseId: String): List<LogEvent> = readableDatabase.query(
        "event_log",
        arrayOf("id", "at_millis", "card_id", "last_name", "first_name", "cabin", "type"),
        "cruise_id = ?",
        arrayOf(cruiseId),
        null,
        null,
        "at_millis DESC, id DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LogEvent(
                        id = cursor.getLong(0),
                        atMillis = cursor.getLong(1),
                        cardId = cursor.getString(2),
                        lastName = cursor.getString(3),
                        firstName = cursor.getString(4),
                        cabin = cursor.nullable(5),
                        type = cursor.getString(6),
                    ),
                )
            }
        }
    }

    fun logCruises(): List<LogCruise> = readableDatabase.rawQuery(
        """
        SELECT cruise_id, ship, MAX(at_millis) FROM event_log
        GROUP BY cruise_id
        ORDER BY MAX(at_millis) DESC
        """.trimIndent(),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(LogCruise(id = cursor.getString(0), ship = cursor.getString(1)))
            }
        }
    }

    private fun Person.matchesManual(needle: String): Boolean {
        if (lastName.equals(needle, ignoreCase = true)) return true
        if (cabin?.equals(needle, ignoreCase = true) == true) return true
        if (documentNumber?.equals(needle, ignoreCase = true) == true) return true
        val document = documentNumber ?: return false
        return document.split(Regex("\\s+")).any { it.equals(needle, ignoreCase = true) }
    }

    private fun android.database.Cursor.nullable(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun contentValuesOf(vararg pairs: Pair<String, String?>): ContentValues =
        ContentValues().apply {
            pairs.forEach { (key, value) ->
                if (value == null) putNull(key) else put(key, value)
            }
        }

    companion object {
        private const val CURRENT_CRUISE = "current_cruise"
    }
}
