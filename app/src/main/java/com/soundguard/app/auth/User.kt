package com.soundguard.app.auth

data class User(
    val id: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val isGuest: Boolean
) {
    companion object {
        fun guest(): User = User(
            id = "guest",
            displayName = "Guest",
            email = null,
            photoUrl = null,
            isGuest = true
        )
    }
}
