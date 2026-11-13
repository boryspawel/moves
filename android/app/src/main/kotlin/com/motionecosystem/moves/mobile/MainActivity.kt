package com.motionecosystem.moves.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.motionecosystem.moves.mobile.app.MovesApp
import com.motionecosystem.moves.mobile.auth.OidcSession
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val session by lazy { OidcSession.get(applicationContext) }
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch { session.completeLogin(result.data) }
    }
    private val logout = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovesApp(session, onLogin = {
                lifecycleScope.launch {
                    runCatching { session.loginIntent() }.onSuccess { login.launch(it) }
                }
            }, onLogout = {
                val intent = session.logoutIntent()
                if (intent != null) runCatching { logout.launch(intent) }
            })
        }
    }
}
