# Floo

There are two main components for Floo. 
* Cache Helper: this helper concurrently runs along with all the apps. This helper is created by writing our own dummy Android app which contains additional helper classes. Then, from the apk, we use various tools to extract the compiled version of the helper classes in smali form, which is then injected into each Android app we want to optimize. 

* Instrumenter: this is the offline component of Floo. Various scripts are used to call Soot APIs which are able to process each line of code in a given app or framework. This instrumenter generates the read/write signatures of each function, and also modifies the functions to include a call to the memoization helper which is now incorporated into the apps as described above. Note that we need to explicitly exclude certain functions from memoization as they tend to cause a crash if modified - we just find out this list my trial and error. In addition, we also exclude the functions of the cache helper. 

## Instrumenter
* Special Credits: [This](https://github.com/noidsirius/SootTutorial) project was quite helpful in getting this working, and I've based most of my code and Readme on it. 
* `./gradlew run --args="HeapRWFinder <output_json> <apkfile>"`: Generate the read write signatures of each function in the given apk and store it in the `json` file. 
* `./gradlew run --args="DetermineCacheability <apk_file>"`: Add calls to the helper that allows the helper to individually make the decision to cache each invocation or not.
* `./gradlew run --args="ComputeCacher <apk_file>"`: Work in conjuction with the helper to return memoized writes if the function is deemed cacheable.
* `./gradlew run --args="TimestampsPrinter <apk_file>"`: Print timestamps that will be helpful when trying to understand how long each function takes to run, and how the ordering of functions varies. 
* There are more scripts in `src`, and the format for running the scripts is the same: `./gradlew run --args="<script> <args>"`. Comments in the scripts might be helpful in understanding what they do. 

## Cache Helper
* Create and build the helper app using Android Studio as usual, and obtain the apk file. 
* Use [APKStudio](https://github.com/vaibhavpandeyvpz/apkstudio) to disassemble the APK file. 
* Do not go all the way to Java, it is not possible to repackage from Java to Android. Instead, just obtain the `smali` files. 
* Keep the `smali` files of the helper separately. Now open the Android app that has to be optimized. Disassemble it similarly using APKStudio. 
* Copy over the helper `smali` files into the folder containing the target app's disassembled sources. Specifically they should go into a folder called `smali_classesX`. Make sure to put the helper smali files in the last `smali_classesX` folder (i.e. the largest X).
* If the app crashes, it might be because we exceeded a system limitation: there is an upper bound on number of methods allowed cumulatively across all the `smali` files in a `smali_classesX` folder.
* In this case, create a new `smali_classesX` folder which is `+1` of the largest such existing folder and add the helper `smali` files into it.
* Use APKStudio now to recompile the folder which would create the APK file with the helper source code.
* User `uber-apk-signer.jar` to resign any APKs that have been repackaged such that they will be accepted for installation by the Android device. 
* Use `adb install` on this final APK file to install on device. You might have to uninstall the existing version depending on your Android version. 





