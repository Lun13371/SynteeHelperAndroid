package ru.syntee.helper.navigation

import kotlinx.serialization.Serializable

/** Type-safe маршруты (Navigation Compose + kotlinx.serialization). */

@Serializable
data object LoginRoute

@Serializable
data object ChatListRoute

@Serializable
data class ChatRoute(val chatId: String)

@Serializable
data object SettingsRoute
