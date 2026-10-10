// Правила, ИИ, кампания и схватка без графики: порт js/rules.js, ai.js, campaign.js, skirmish.js, data.js, achievements.js.
// Проверка совпадения с вебом: ./gradlew :logic:run --args="--runs 3" должен печатать то же, что node tools/sim.js --runs 3
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    // JsonReader/JsonValue для кодов карт и сохранений (чистая Java, работает и на JVM)
    api("com.badlogicgames.gdx:gdx:${rootProject.extra["gdxVersion"]}")
    testImplementation(kotlin("test"))
}

application { mainClass.set("empires.logic.SimKt") }

tasks.test { useJUnitPlatform() }
