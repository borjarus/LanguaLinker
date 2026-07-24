plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.clikt)
        }
    }
}

// Fat JAR: ./gradlew :cliApp:fatJar
// Output: cliApp/build/libs/cliApp-all.jar
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds a self-contained executable JAR"
    archiveClassifier = "all"
    manifest {
        attributes["Main-Class"] = "com.mila.langualinker.cli.MainKt"
    }
    from(configurations.named("jvmRuntimeClasspath").get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.named<Jar>("jvmJar").get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Dev run: ./gradlew :cliApp:runCli -PcliArgs="import --input foo.apkg"
tasks.register<JavaExec>("runCli") {
    dependsOn("jvmJar")
    group = "application"
    description = "Run LanguaLinker CLI. Pass arguments via -PcliArgs=\"...\""
    classpath(configurations.named("jvmRuntimeClasspath"), tasks.named("jvmJar"))
    mainClass = "com.mila.langualinker.cli.MainKt"
    args((project.findProperty("cliArgs") as? String)?.split(" ") ?: emptyList<String>())
}
