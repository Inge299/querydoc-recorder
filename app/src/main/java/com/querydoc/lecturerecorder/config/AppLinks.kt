package com.querydoc.lecturerecorder.config

object AppLinks {
    /** Store listing for the release package (the dev build shares this link on purpose). */
    const val RUSTORE = "https://www.rustore.ru/catalog/app/com.querydoc.lecturerecorder"

    fun groupJoin(baseUrl: String, inviteCode: String): String =
        "${baseUrl.trimEnd('/')}/join/${inviteCode.trim().uppercase()}"
}
