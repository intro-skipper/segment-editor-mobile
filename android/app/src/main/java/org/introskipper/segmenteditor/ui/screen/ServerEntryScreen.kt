package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.ErrorMessage
import org.introskipper.segmenteditor.ui.component.LoadingIndicator
import org.introskipper.segmenteditor.ui.component.PrimaryButton
import org.introskipper.segmenteditor.ui.component.TextInputField
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.viewmodel.ConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEntryScreen(
    navController: NavHostController,
    viewModel: ConnectionViewModel
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(state.serverValidated) {
        if (state.serverValidated) {
            navController.navigate(Screen.Authentication.route) {
                popUpTo(Screen.ConnectionWizard.route) { inclusive = false }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_url)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter your Jellyfin server URL",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            TextInputField(
                value = state.serverUrl,
                onValueChange = { viewModel.onServerUrlChange(it) },
                label = stringResource(R.string.server_url),
                placeholder = stringResource(R.string.server_url_placeholder),
                leadingIcon = {
                    Icon(Icons.Default.Language, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (state.isValidUrl) {
                            viewModel.validateAndSaveServer()
                        }
                    }
                ),
                isError = state.error != null
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Example: https://jellyfin.local:8096 or http://192.168.1.100:8096",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (state.error != null) {
                ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.validateAndSaveServer() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (state.isLoading) {
                LoadingIndicator(message = "Connecting to server...")
            } else {
                PrimaryButton(
                    text = stringResource(R.string.server_connect),
                    onClick = { viewModel.validateAndSaveServer() },
                    enabled = state.isValidUrl
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
