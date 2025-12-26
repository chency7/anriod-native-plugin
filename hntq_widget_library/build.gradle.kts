plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hntq.destop.widget.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// 自动打包插件任务
tasks.register("packageUniPlugin") {
    group = "uniapp"
    description = "Package the library as a UniApp native plugin"
    
    // 依赖 release 构建
    dependsOn("assembleRelease")
    
    doLast {
        // 定义路径
        val pluginId = "HNTQ-Widget" // 必须与 package.json 中的 id 一致
        val buildDir = layout.buildDirectory.get().asFile
        val outputDir = File(rootProject.rootDir, "dist/nativeplugins/$pluginId")
        val aarFile = File(buildDir, "outputs/aar/hntq_widget_library-release.aar")
        val packageJson = file("package.json")
        
        println("Packaging UniApp Plugin to: $outputDir")
        
        // 1. 清理旧文件
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()
        
        // 2. 复制 package.json
        if (packageJson.exists()) {
            packageJson.copyTo(File(outputDir, "package.json"), overwrite = true)
            println("✓ Copied package.json")
        } else {
            println("Error: package.json not found in ${project.projectDir}")
        }
        
        // 3. 复制 AAR
        if (aarFile.exists()) {
            val libsDir = File(outputDir, "android/libs")
            libsDir.mkdirs()
            aarFile.copyTo(File(libsDir, "${project.name}.aar"), overwrite = true)
            println("✓ Copied AAR to android/libs/")
        } else {
            println("Error: Release AAR not found. Make sure assembleRelease ran successfully.")
        }
        
        println("\nPlugin packaged successfully!")
        println("Output: ${outputDir.absolutePath}")
    }
}
