plugins {
    // Kotlin для :app встроен в AGP 9, отдельный плагин kotlin.android не нужен
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}

// Версии зависимостей в одном месте
extra["gdxVersion"] = "1.14.2"
