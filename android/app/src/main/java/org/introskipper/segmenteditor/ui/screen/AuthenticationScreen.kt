package org.introskipper.segmenteditor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.introskipper.segmenteditor.R
import org.introskipper.segmenteditor.ui.component.ErrorMessage
import org.introskipper.segmenteditor.ui.component.LoadingIndicator
import org.introskipper.segmenteditor.ui.component.PrimaryButton
import org.introskipper.segmenteditor.ui.component.TextInputField
import org.introskipper.segmenteditor.ui.navigation.Screen
import org.introskipper.segmenteditor.ui.viewmodel.AuthMethod
import org.introskipper.segmenteditor.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    navController: NavHostController,
    viewModel: AuthViewModel
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            navController.navigate(Screen.ConnectionSuccess.route) {
                popUpTo(Screen.ConnectionWizard.route) { inclusive = true }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.authentication)) },
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
                text = stringResource(R.string.auth_sign_in),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Auth method selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.authMethod == AuthMethod.API_KEY,
                    onClick = { viewModel.setAuthMethod(AuthMethod.API_KEY) },
                    label = { Text(stringResource(R.string.auth_api_key)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.authMethod == AuthMethod.USERNAME_PASSWORD,
                    onClick = { viewModel.setAuthMethod(AuthMethod.USERNAME_PASSWORD) },
                    label = { Text(stringResource(R.string.auth_username_password)) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (state.authMethod) {
                AuthMethod.API_KEY -> {
                    TextInputField(
                        value = state.apiKey,
                        onValueChange = { viewModel.onApiKeyChange(it) },
                        label = stringResource(R.string.auth_api_key),
                        placeholder = stringResource(R.string.auth_api_key_placeholder),
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.authenticate()
                            }
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = state.error != null
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "You can generate an API key from your Jellyfin dashboard under User Settings > API Keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                AuthMethod.USERNAME_PASSWORD -> {
                    TextInputField(
                        value = state.username,
                        onValueChange = { viewModel.onUsernameChange(it) },
                        label = stringResource(R.string.auth_username),
                        placeholder = stringResource(R.string.auth_username_placeholder),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        isError = state.error != null
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextInputField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = stringResource(R.string.auth_password),
                        placeholder = stringResource(R.string.auth_password_placeholder),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) stringResource(R.string.auth_password_hide) else stringResource(R.string.auth_password_show)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.authenticate()
                            }
                        ),
                        isError = state.error != null
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (state.error != null) {
                ErrorMessage(
                    message = state.error!!,
                    onRetry = { viewModel.authenticate() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (state.isLoading) {
                LoadingIndicator(message = "Authenticating...")
            } else {
                PrimaryButton(
                    text = stringResource(R.string.auth_sign_in_button),
                    onClick = { viewModel.authenticate() },
                    enabled = when (state.authMethod) {
                        AuthMethod.API_KEY -> state.apiKey.isNotBlank()
                        AuthMethod.USERNAME_PASSWORD -> state.username.isNotBlank() && state.password.isNotBlank()
                    }
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
