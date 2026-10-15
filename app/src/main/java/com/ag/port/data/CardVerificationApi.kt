package com.ag.port.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CardVerification(
    val status: String,
    val role: String?,
    val holderName: String?,
    val documentNumber: String?,
    val vesselName: String?,
    val voyageCode: String?,
    val reason: String?,
)

class CardVerificationException(message: String) : IOException(message)

class CardVerificationApi(
    private val baseUrl: String,
) {
    suspend fun verify(payload: String): CardVerification = withContext(Dispatchers.IO) {
        val connection = (URL("${baseUrl.trimEnd('/')}/api/cards/verifications").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        val body = JSONObject().put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(body) }

        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
            throw CardVerificationException("Сервис ответил с кодом $code")
        }

        val json = JSONObject(text)
        CardVerification(
            status = json.getString("status"),
            role = json.optionalString("role"),
            holderName = json.optionalString("holderName"),
            documentNumber = json.optionalString("documentNumber"),
            vesselName = json.optionalString("vesselName"),
            voyageCode = json.optionalString("voyageCode"),
            reason = json.optionalString("reason"),
        )
    }
}

private fun JSONObject.optionalString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)
