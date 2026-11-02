import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "ink.x2.logkit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

mavenPublishing {
    coordinates("ink.x2", "logkit", "1.0.0")
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = SourcesJar.Sources(),
            javadocJar = JavadocJar.Empty(),
        ),
    )
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("LogKit")
        description.set("Kotlin Android logging with UTF-8 message splitting and local Logcat collection.")
        inceptionYear.set("2026")
        url.set("https://github.com/x2ink/LogKit")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("x2ink")
                name.set("x2ink")
                url.set("https://github.com/x2ink")
            }
        }
        scm {
            url.set("https://github.com/x2ink/LogKit")
            connection.set("scm:git:https://github.com/x2ink/LogKit.git")
            developerConnection.set("scm:git:ssh://git@github.com/x2ink/LogKit.git")
        }
    }
}

val prepareLicenseResources = tasks.register<Copy>("prepareLicenseResources") {
    from(rootProject.file("LICENSE"))
    rename("LICENSE", "LogKit-LICENSE.txt")
    into(layout.buildDirectory.dir("generated/licenseResources/META-INF"))
}

android.sourceSets.getByName("main").resources.srcDir(layout.buildDirectory.dir("generated/licenseResources").get().asFile)
tasks.named("preBuild") {
    dependsOn(prepareLicenseResources)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
