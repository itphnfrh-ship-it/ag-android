package com.ag.port.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ag.port.R
import com.ag.port.data.CardVerification
import com.ag.port.data.CardVerificationApi
import kotlinx.coroutines.launch
import java.io.IOException

private sealed interface VerifyUiState {
    data object Idle : VerifyUiState
    data object Loading : VerifyUiState
    data class Result(val value: CardVerification) : VerifyUiState
    data class Failed(val message: String) : VerifyUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(api: CardVerificationApi) {
    var code by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<VerifyUiState>(VerifyUiState.Idle) }
    val scope = rememberCoroutineScope()
    val enterCodeMessage = stringResource(R.string.enter_code)
    val networkError = stringResource(R.string.network_error)
    val checkFailed = stringResource(R.string.check_failed)

    fun submit() {
        val payload = code.trim()
        if (payload.isEmpty()) {
            state = VerifyUiState.Failed(enterCodeMessage)
            return
        }
        state = VerifyUiState.Loading
        scope.launch {
            state = try {
                VerifyUiState.Result(api.verify(payload))
            } catch (_: IOException) {
                VerifyUiState.Failed(networkError)
            } catch (_: RuntimeException) {
                VerifyUiState.Failed(checkFailed)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_subtitle),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.card_code_label)) },
                supportingText = { Text(stringResource(R.string.card_code_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(
                onClick = { submit() },
                enabled = state !is VerifyUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.verify))
            }
            when (val current = state) {
                VerifyUiState.Idle -> Unit
                VerifyUiState.Loading -> CircularProgressIndicator()
                is VerifyUiState.Failed -> Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                )
                is VerifyUiState.Result -> VerificationCard(current.value)
            }
        }
    }
}

@Composable
private fun VerificationCard(result: CardVerification) {
    val valid = result.status == "VALID"
    val container = if (valid) Color(0xFFE5F4EA) else Color(0xFFFDECEC)
    val accent = if (valid) Color(0xFF1B7A3A) else Color(0xFFB42318)
    val title = stringResource(if (valid) R.string.status_valid else R.string.status_invalid)
    val role = when (result.role) {
        "PASSENGER" -> stringResource(R.string.role_passenger)
        "CREW" -> stringResource(R.string.role_crew)
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, color = accent, fontWeight = FontWeight.SemiBold)
            role?.let { Text(it) }
            result.holderName?.let { Text(it, fontWeight = FontWeight.Medium) }
            result.documentNumber?.let { Text(it) }
            listOfNotNull(result.vesselName, result.voyageCode).joinToString(" · ")
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it) }
            result.reason?.let { Text(it, color = accent) }
        }
    }
}
