package com.mila.langualinker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform