package com.ag.port

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ag.port.data.CardVerificationApi
import com.ag.port.ui.VerifyScreen
import com.ag.port.ui.theme.AgPortTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgPortTheme {
                VerifyScreen(api = CardVerificationApi(BuildConfig.API_BASE_URL))
            }
        }
    }
}
