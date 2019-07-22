Set-up Instructions
===================

1. Clone the repository
2. Run `git submodule update --init` to populate `lib/prism`
3. Run `make` in `lib/prism/prism` to compile prism

Users:

Run `./gradlew distZip` to build and package the tool.
The package can be found under `build/distributions/`

Developers:

1. Import project in IntelliJ, enable gradle auto-import
2. Run `./gradlew compileJava` to create the prism.jar and run the annotation processor
