package com.cruisepass.verify

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cruisepass.verify.ui.AppLanguage
import com.cruisepass.verify.ui.ScanScreen
import com.cruisepass.verify.ui.theme.CruisePassVerifyTheme

class MainActivity : ComponentActivity() {
    var onWedgeScan: ((String) -> Unit)? = null
    private val wedge = StringBuilder()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val repository = (application as PortApp).repository
        setContent {
            CruisePassVerifyTheme {
                ScanScreen(repository)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && takeWedgeKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun takeWedgeKey(event: KeyEvent): Boolean {
        val listener = onWedgeScan ?: return false
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            val code = wedge.toString()
            wedge.setLength(0)
            if (code.isNotEmpty()) listener(code)
            return true
        }
        val character = event.unicodeChar
        if (character > 0 && event.repeatCount == 0) {
            wedge.append(character.toChar())
            return true
        }
        return false
    }
}
