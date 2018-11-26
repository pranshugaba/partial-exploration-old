Set-up Instructions
===================

1. Clone the repository
2. Run `git submodule update --init` to populate `lib/prism`
3. Run `make` in `lib/prism/prism`
4. Run `./gradlew compileJava` to create the prism.jar and run the annotation processor
5. Import project in IntelliJ, enable gradle auto-import

Note: `./gradlew compileJava` has to be run whenever some of the annotation processor classes is changed.

Example Configurations
======================


 * `-m rabin3.nm --unbounded`
 * `-m wlan4.nm --const k=0,TRANS_TIME_MAX=10 --bounded 100 --heuristic PROB`
 * `-m cyclin.sm --const N=4 --bounded 100 --uniformization 30 --heuristic WEIGHTED`
 * `-m zeroconf.nm --const N=1000,K=1000,err=0.01,reset=false --unbounded`