package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.NexusShell
import com.example.ui.theme.GamerHubTheme
import com.example.ui.theme.NexusObsidianBg
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.viewmodel.NexusViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NexusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val credentialManager = CredentialManager.create(this)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var autoSignInDone by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                attemptAutoSignIn(
                    context = this@MainActivity,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        autoSignInDone = true
                        viewModel.onAuthCompleted()
                    },
                    onUnauthenticated = {
                        autoSignInDone = true
                    },
                    scope = scope
                )
            }

            GamerHubTheme(darkTheme = isDarkTheme) {
                Crossfade(
                    targetState = Triple(autoSignInDone, uiState.authInitialized, uiState.currentUid != null),
                    label = "root_gate"
                ) { (autoDone, initialized, isSignedIn) ->
                    when {
                        !autoDone || !initialized -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NexusObsidianBg),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NexusPurpleGlow)
                        }

                        !isSignedIn -> AuthScreen(
                            credentialManager = credentialManager,
                            onAuthenticated = { tag, avatarId ->
                                viewModel.onAuthCompleted(tag, avatarId)
                            }
                        )

                        else -> NexusShell(
                            viewModel = viewModel,
                            onSignOut = {
                                viewModel.leaveVoiceRoom()
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
