package com.cruisepass.verify.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CruiseApi(
    private val baseUrl: String,
    private val token: String,
) {
    fun board(): Board {
        val json = JSONObject(get("/GetPersonsOnBoard", readTimeoutMs = 120_000))
        val people = json.getJSONArray("PERSONS")
        return Board(
            id = json.required("CRUISE"),
            ship = json.required("SHIP"),
            people = List(people.length()) { index -> people.getJSONObject(index).toPerson() },
        )
    }

    fun photo(photoId: String): ByteArray = getBytes("/photos/${encode(photoId)}", readTimeoutMs = 30_000)

    private fun JSONObject.toPerson(): RemotePerson {
        val photo = if (!has("PHOTO") || isNull("PHOTO")) null else getJSONObject("PHOTO")
        val kind = when (text("PERSON_TYPE")?.uppercase()) {
            "PASSENGER", "PAX" -> "pax"
            "CREW" -> "crew"
            else -> throw IOException("person type")
        }
        return RemotePerson(
            cardId = required("BARCODE"),
            type = kind,
            lastName = required("LAST_NAME"),
            firstName = required("FIRST_NAME"),
            rank = if (kind == "crew") text("POSITION") else null,
            documentNumber = text("PP_NUMBER"),
            issuingCountry = null,
            nationality = text("NATIONALITY"),
            dateOfBirth = text("DOB"),
            sex = text("GENDER"),
            expirationDate = text("PP_EXPIRY")?.substringBefore('T'),
            cabin = text("CABIN"),
            photoId = photo?.required("id"),
            listNo = null,
            documentIssueDate = text("PP_ISSUE")?.substringBefore('T'),
        )
    }

    private fun JSONObject.required(name: String): String =
        text(name) ?: throw IOException(name)

    private fun JSONObject.text(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return getString(name).trim().ifEmpty { null }
    }

    private fun get(path: String, readTimeoutMs: Int): String {
        val bytes = getBytes(path, readTimeoutMs)
        return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun getBytes(path: String, readTimeoutMs: Int): ByteArray {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            useCaches = false
            setRequestProperty("Auth", "Bearer $token")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) throw IOException("HTTP $code")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

data class Board(
    val id: String,
    val ship: String,
    val people: List<RemotePerson>,
)

data class RemotePerson(
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
    val photoId: String?,
    val listNo: Int?,
    val documentIssueDate: String?,
)
