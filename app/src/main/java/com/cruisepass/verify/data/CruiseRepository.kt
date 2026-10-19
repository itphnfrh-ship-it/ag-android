package com.cruisepass.verify.data

import android.content.Context
import com.cruisepass.verify.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

class CruiseRepository(
    context: Context,
    private val api: CruiseApi = CruiseApi(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN),
    private val database: CruiseDatabase = CruiseDatabase(context.applicationContext),
) {
    private val photos = File(context.applicationContext.filesDir, "cruise-photos")
    private val mutex = Mutex()

    suspend fun refresh(): Sync = withContext(Dispatchers.IO) {
        mutex.withLock {
            val staging = File(photos, "incoming.tmp")
            var swapped = false
            val live = File(photos, "current")
            val previous = File(photos, "previous")
            try {
                val board = api.board()
                if (board.people.isEmpty()) throw IOException("empty")
                staging.deleteRecursively()
                staging.mkdirs()
                val gate = Semaphore(4)
                coroutineScope {
                    board.people.mapNotNull { it.photoId }.distinct().map { photoId ->
                        async {
                            gate.withPermit {
                                File(staging, photoId).writeBytes(api.photo(photoId))
                            }
                        }
                    }.awaitAll()
                }
                val people = board.people.map { person ->
                    val photoPath = person.photoId?.let { File(live, it).absolutePath }
                    person.toStored(photoPath)
                }
                previous.deleteRecursively()
                if (live.exists() && !live.renameTo(previous)) throw IllegalStateException("photos")
                if (!staging.renameTo(live)) {
                    previous.renameTo(live)
                    throw IllegalStateException("photos")
                }
                swapped = true
                try {
                    database.replace(
                        board.id,
                        board.ship,
                        board.id,
                        Instant.now().toString(),
                        null,
                        people,
                    )
                } catch (error: Exception) {
                    val failed = File(photos, "failed")
                    failed.deleteRecursively()
                    live.renameTo(failed)
                    if (previous.exists()) previous.renameTo(live)
                    failed.deleteRecursively()
                    throw error
                }
                previous.deleteRecursively()
                photos.listFiles()?.forEach { child ->
                    if (child.name != live.name) child.deleteRecursively()
                }
                Sync(Refresh.UPDATED, online = true)
            } catch (error: Exception) {
                if (!swapped) staging.deleteRecursively()
                if (error is CancellationException) throw error
                val refresh = if (database.currentCruise() == null) Refresh.UNAVAILABLE else Refresh.KEPT_LOCAL
                Sync(refresh, online = false)
            }
        }
    }

    suspend fun lookup(rawCardId: String): Lookup = withContext(Dispatchers.IO) {
        val cardId = scannedCardId(rawCardId)
        val cruise = database.currentCruise() ?: return@withContext Lookup.NoCruise
        val person = database.find(cruise.id, cardId)
        if (person == null) Lookup.Missing(cardId, cruise) else Lookup.Found(person, cruise)
    }

    suspend fun search(rawQuery: String): ManualSearch = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        val cruise = database.currentCruise() ?: return@withContext ManualSearch.NoCruise
        val people = database.findManual(cruise.id, query)
        if (people.isEmpty()) ManualSearch.None(query) else ManualSearch.Matches(people, cruise)
    }

    suspend fun currentCruise(): LoadedCruise? = withContext(Dispatchers.IO) {
        database.currentCruise()
    }

    suspend fun record(cruise: LoadedCruise, person: Person) = withContext(Dispatchers.IO) {
        database.insertEvent(cruise, person)
    }

    suspend fun events(cruiseId: String): List<LogEvent> = withContext(Dispatchers.IO) {
        database.events(cruiseId)
    }

    suspend fun logCruises(): List<LogCruise> = withContext(Dispatchers.IO) {
        database.logCruises()
    }

    private fun RemotePerson.toStored(photoPath: String?) = Person(
        cardId = cardId,
        type = type,
        lastName = lastName,
        firstName = firstName,
        rank = rank,
        documentNumber = documentNumber,
        issuingCountry = issuingCountry,
        nationality = nationality,
        dateOfBirth = dateOfBirth,
        sex = sex,
        expirationDate = expirationDate,
        cabin = cabin,
        photoPath = photoPath,
        listNo = listNo,
        documentIssueDate = documentIssueDate,
    )
}

enum class Refresh {
    UPDATED,
    KEPT_LOCAL,
    UNAVAILABLE,
}

data class Sync(
    val refresh: Refresh,
    val online: Boolean,
)
