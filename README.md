Set-up Instructions
===================

1. Clone the repository
2. Run `git submodule update --init` to populate `lib/prism`
3. Run `make` in `lib/prism/prism`
4. Import project in IntelliJ, enable gradle auto-import
5. Go to `Project structure` -> `Libraries` -> `+ New Project Library` and add `lib/prism/prism/classes` as a new library
6. Optional: Add `lib/prism/prism/src` as sources
7. Run `./gradlew compileJava` to process all annotations and generate the code

Note: Project Library has to be added whenever the project is synced with gradle!

Example Configurations
======================


 * `-m rabin3.nm --unbounded`
 * `-m wlan4.nm --const k=0,TRANS_TIME_MAX=10 --bounded 100 --heuristic PROB`
 * `-m cyclin.sm --const N=4 --bounded 100 --uniformization 30 --heuristic WEIGHTED`
 * `-m zeroconf.nm --const N=1000,K=1000,err=0.01,reset=false --unbounded`