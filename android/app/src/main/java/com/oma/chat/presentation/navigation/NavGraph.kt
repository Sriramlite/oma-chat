package com.oma.chat.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oma.chat.domain.model.CallState
import com.oma.chat.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.oma.chat.presentation.auth.login.LoginScreen
import com.oma.chat.presentation.auth.signup.SignupScreen
import com.oma.chat.presentation.call.CallScreen
import com.oma.chat.presentation.call.CallViewModel
import com.oma.chat.presentation.call.IncomingCallDialog
import com.oma.chat.presentation.chat.ChatScreen
import com.oma.chat.presentation.group.CreateGroupScreen
import com.oma.chat.presentation.group.GroupInfoScreen
import com.oma.chat.presentation.home.ConversationListScreen

import com.oma.chat.presentation.profile.ProfileScreen
import com.oma.chat.presentation.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String,
    callViewModel: CallViewModel = hiltViewModel()
) {
    val callState by callViewModel.callState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToSignup = {
                        navController.navigate(Screen.Signup.route)
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                )
            }

            composable(Screen.Signup.route) {
                SignupScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Signup.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Home.route) {
                ConversationListScreen(
                    onNavigateToChat = { chatId, chatName ->
                        navController.navigate(Screen.Chat.createRoute(chatId, chatName))
                    },
                    onNavigateToCreateGroup = {
                        navController.navigate(Screen.CreateGroup.route)
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToDiagnostics = {
                        navController.navigate(Screen.Diagnostics.route)
                    },
                    onNavigateToPrivacy = {
                        navController.navigate(Screen.Privacy.route)
                    },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Privacy.route) {
                com.oma.chat.presentation.settings.privacy.PrivacyScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToBlockedUsers = {
                        navController.navigate(Screen.BlockedUsers.route)
                    }
                )
            }

            composable(Screen.BlockedUsers.route) {
                com.oma.chat.presentation.settings.privacy.BlockedUsersScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Diagnostics.route) {
                com.oma.chat.presentation.diagnostics.DiagnosticsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("chatName") { type = NavType.StringType }
                )
            ) {
                ChatScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToGroupInfo = { groupId ->
                        navController.navigate(Screen.GroupInfo.createRoute(groupId))
                    },
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId))
                    },
                    onStartCall = { targetId, targetName, targetAvatar, callType ->
                        callViewModel.startCall(targetId, targetName, targetAvatar, callType)
                        navController.navigate(Screen.Call.route)
                    }
                )
            }

            composable(
                route = Screen.UserProfile.route,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) {
                com.oma.chat.presentation.profile.UserProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onStartCall = { targetId, targetName, targetAvatar, callType ->
                        callViewModel.startCall(targetId, targetName, targetAvatar, callType)
                        navController.navigate(Screen.Call.route)
                    }
                )
            }

            composable(Screen.CreateGroup.route) {
                CreateGroupScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onGroupCreated = { group ->
                        navController.navigate(Screen.Chat.createRoute(group.id, group.name)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.GroupInfo.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) {
                GroupInfoScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onGroupLeft = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            }

            composable(Screen.Call.route) {
                CallScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    viewModel = callViewModel
                )
            }
        }

        // Global incoming call banner / dialog
        val currentCallState = callState
        val context = androidx.compose.ui.platform.LocalContext.current
        val incomingPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val incoming = callState as? CallState.IncomingRinging ?: return@rememberLauncherForActivityResult
            val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
            if (audioGranted) {
                callViewModel.acceptCall(
                    incoming.callerId,
                    incoming.sdp,
                    incoming.callType
                )
                navController.navigate(Screen.Call.route)
            } else {
                android.widget.Toast.makeText(context, "Microphone permission is required to answer call", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        if (currentCallState is CallState.IncomingRinging) {
            IncomingCallDialog(
                incomingState = currentCallState,
                onAccept = {
                    val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val isVideo = currentCallState.callType == com.oma.chat.domain.model.CallType.VIDEO

                    if (hasAudio && (!isVideo || hasCamera)) {
                        callViewModel.acceptCall(
                            currentCallState.callerId,
                            currentCallState.sdp,
                            currentCallState.callType
                        )
                        navController.navigate(Screen.Call.route)
                    } else {
                        val perms = if (isVideo) {
                            arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)
                        } else {
                            arrayOf(android.Manifest.permission.RECORD_AUDIO)
                        }
                        incomingPermissionLauncher.launch(perms)
                    }
                },
                onDecline = {
                    callViewModel.rejectCall(currentCallState.callerId)
                }
            )
        }
    }
}
