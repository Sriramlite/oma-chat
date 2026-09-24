package com.oma.chat.presentation.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object ResetPassword : Screen("reset_password?token={token}") {
        fun createRoute(token: String) = "reset_password?token=$token"
    }
    object Home : Screen("home")
    object Chat : Screen("chat/{chatId}/{chatName}") {
        fun createRoute(chatId: String, chatName: String) = "chat/$chatId/$chatName"
    }
    object CreateGroup : Screen("create_group")
    object GroupInfo : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object Call : Screen("call")
    object Profile : Screen("profile")
    object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }
    object Settings : Screen("settings")
    object Privacy : Screen("privacy")
    object BlockedUsers : Screen("blocked_users")
    object Diagnostics : Screen("diagnostics")
}
