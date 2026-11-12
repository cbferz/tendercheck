// Сборка TenderCheck: Kotlin Multiplatform, единственная цель — Kotlin/JS (браузер).
// Модели, логика оценки, контент и хранилище лежат в commonMain и не зависят от DOM,
// слой интерфейса (kotlinx-html + DOM) — в jsMain.

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "pro.tendercheck"
version = "1.0.0"

repositories {
    mavenCentral()
}

// --- Контент заявок -----------------------------------------------------------------
// Единый источник правды — человекочитаемый файл data/scenarios.json.
// На этапе сборки он встраивается в Kotlin-константу, поэтому игра работает офлайн
// (в том числе при открытии index.html без веб-сервера) и не делает сетевых запросов.
val scenariosJson = layout.projectDirectory.file("data/scenarios.json")
val generatedScenariosDir = layout.buildDirectory.dir("generated/scenarios/kotlin")

val generateScenarios by tasks.registering {
    group = "build"
    description = "Встраивает data/scenarios.json в исходники Kotlin (ScenariosJson.kt)."
    inputs.file(scenariosJson)
    outputs.dir(generatedScenariosDir)

    doLast {
        val text = scenariosJson.asFile.readText(Charsets.UTF_8)
        // В сырой строке Kotlin недопустимы тройные кавычки и символ «$».
        if (text.contains("\"\"\"") || text.contains("$")) {
            throw GradleException("data/scenarios.json не должен содержать символы \"\"\" и \$")
        }
        val outDir = generatedScenariosDir.get().asFile.resolve("pro/tendercheck/data")
        outDir.mkdirs()
        outDir.resolve("ScenariosJson.kt").writeText(
            "package pro.tendercheck.data\n\n" +
                "/** Содержимое data/scenarios.json. Файл сгенерирован Gradle-задачей generateScenarios. */\n" +
                "internal const val SCENARIOS_JSON: String = \"\"\"\n" + text + "\"\"\"\n",
            Charsets.UTF_8,
        )
    }
}

kotlin {
    js {
        browser {
            // Имя итогового бандла совпадает с тегом <script> в index.html
            commonWebpackConfig {
                outputFileName = "tendercheck.js"
            }
            // Тесты логики запускаются в Node.js (nodejs() ниже), Karma и Chrome не нужны
            testTask {
                enabled = false
            }
        }
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateScenarios)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-html:0.11.0")
        }
    }
}
