# how to determine amount of cacheability of an app
1. rebuild the apk using apktool + our helpers (https://github.com/muralisr/asyncprinter)
2. run DetermineCacheability on the rebuilt apk and generate instrumented apk
4. run instrumented apk. copy over the timestamp info and the memo table info files
5. run `runtime_finder.py` on the timestamp info to get function name to list of runtimes mapping
6. run `find_memoizable_info.py` on the output of 5 + the memo table file from 4 to determine what fraction of runtime of the trace can be memoized