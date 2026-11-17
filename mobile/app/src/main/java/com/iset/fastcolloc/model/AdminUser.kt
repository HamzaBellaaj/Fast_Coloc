package com.iset.fastcolloc.model

data class AdminUser(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val accountRole: String,
    val collocationRole: String,
    val isBlocked: Boolean
) {
    val fullName: String get() = "$firstName $lastName".trim()
}
