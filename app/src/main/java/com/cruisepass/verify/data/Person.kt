package com.cruisepass.verify.data

data class Person(
    val cardId: String,
    val type: String,
    val lastName: String,
    val firstName: String,
    val rank: String?,
    val documentNumber: String?,
    val issuingCountry: String?,
    val nationality: String?,
    val dateOfBirth: String?,
    val sex: String?,
    val expirationDate: String?,
    val cabin: String?,
    val photoPath: String?,
    val listNo: Int?,
    val documentIssueDate: String?,
)

data class LoadedCruise(
    val id: String,
    val ship: String,
    val name: String,
    val currentPort: String?,
    val paxCount: Int,
    val crewCount: Int,
    val updatedAt: String?,
)

sealed interface Lookup {
    data object NoCruise : Lookup
    data class Found(val person: Person, val cruise: LoadedCruise) : Lookup
    data class Missing(val cardId: String, val cruise: LoadedCruise) : Lookup
}

sealed interface ManualSearch {
    data object NoCruise : ManualSearch
    data class None(val query: String) : ManualSearch
    data class Matches(val people: List<Person>, val cruise: LoadedCruise) : ManualSearch
}

data class LogEvent(
    val id: Long,
    val atMillis: Long,
    val cardId: String,
    val lastName: String,
    val firstName: String,
    val cabin: String?,
    val type: String,
)

data class LogCruise(
    val id: String,
    val ship: String,
)

fun scannedCardId(raw: String): String = raw.trim().removePrefix("%")
