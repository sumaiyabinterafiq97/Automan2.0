plugins {
    kotlin("multiplatform") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
}

group = "com.automan"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Task to copy Kotlin/JS dependencies to dist directory
tasks.named("jsBrowserDevelopmentWebpack") {
    doLast {
        val sourceDir = file("build/js/packages/automan-car-purchase/kotlin")
        val targetDir = file("build/dist/js/developmentExecutable")
        
        if (sourceDir.exists()) {
            targetDir.mkdirs()
            
            // Copy all Kotlin/JS dependency files
            val filesToCopy = listOf(
                "kotlin-kotlin-stdlib.js",
                "88b0986a7186d029-atomicfu-js-ir.js",
                "kotlin-kotlinx-atomicfu-runtime-js-ir.js",
                "kotlinx-serialization-kotlinx-serialization-core.js",
                "kotlinx.coroutines-kotlinx-coroutines-core-js-ir.js",
                "kotlinx-serialization-kotlinx-serialization-json.js",
                "kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.js"
            )
            
            filesToCopy.forEach { fileName ->
                val sourceFile = sourceDir.resolve(fileName)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(targetDir.resolve(fileName), overwrite = true)
                    println("✅ Copied $fileName")
                }
            }
            
            // Copy index.html and static assets
            val resourcesDir = file("src/jsMain/resources")
            listOf("index.html", "invoice-history-pdf-btn.jpeg").forEach { name ->
                val f = resourcesDir.resolve(name)
                if (f.exists()) {
                    f.copyTo(targetDir.resolve(name), overwrite = true)
                    println("✅ Copied $name")
                }
            }
        }
    }
}

// Ensure production build gets latest index.html and styles.css (for Docker / version updates)
tasks.named("jsBrowserProductionWebpack") {
    doLast {
        val targetDir = file("build/dist/js/productionExecutable")
        val resourcesDir = file("src/jsMain/resources")
        if (resourcesDir.exists()) {
            targetDir.mkdirs()
            listOf("index.html", "styles.css", "rixo-price-mapping.js", "booking-mapping.js", "booking-mapping-modal.js", "invoice-history-pdf-btn.jpeg").forEach { name ->
                val f = resourcesDir.resolve(name)
                if (f.exists()) {
                    f.copyTo(targetDir.resolve(name), overwrite = true)
                    println("✅ Copied $name to productionExecutable")
                }
            }
        }
    }
}

tasks.register("prepareVercelPublic") {
    dependsOn("jsBrowserProductionWebpack")
    doLast {
        val sourceDir = file("build/dist/js/productionExecutable")
        val automanDir = file("public/automan")
        val publicDir = file("public")
        automanDir.mkdirs()
        publicDir.mkdirs()
        val automanFiles = listOf(
            "index.html",
            "styles.css",
            "automan-car-purchase.js",
            "rixo-price-mapping.js",
            "booking-mapping.js",
            "booking-mapping-modal.js",
            "invoice-history-pdf-btn.jpeg",
        )
        if (sourceDir.exists()) {
            automanFiles.forEach { name ->
                val f = sourceDir.resolve(name)
                if (f.exists()) {
                    val dest = automanDir.resolve(name)
                    if (name == "index.html") {
                        dest.writeText(
                            f.readText().replace(
                                """<base href="/">""",
                                """<base href="/automan/">"""
                            )
                        )
                    } else {
                        f.copyTo(dest, overwrite = true)
                    }
                }
            }
            val imagesDir = sourceDir.resolve("images")
            if (imagesDir.exists()) {
                val targetImages = automanDir.resolve("images")
                targetImages.mkdirs()
                imagesDir.listFiles()?.forEach { img ->
                    if (img.isFile) {
                        img.copyTo(targetImages.resolve(img.name), overwrite = true)
                    }
                }
            }
            println("✅ Copied Automan build to public/automan/")
        }
        val homepageDir = file("homepage")
        homepageDir.resolve("index.html").copyTo(publicDir.resolve("index.html"), overwrite = true)
        homepageDir.resolve("home.css").copyTo(publicDir.resolve("home.css"), overwrite = true)
        println("✅ Copied homepage to public/")
    }
}
