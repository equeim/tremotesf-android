allprojects {
    repositories {
        jcenter()
        google()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
