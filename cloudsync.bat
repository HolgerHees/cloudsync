@echo off


PATH %PATH%;%JAVA_HOME%\bin\
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k%%l%%m"

if  %jver% LSS 17000 (
    echo Found unsupported java version. Please install jdk 7 or newer
) else (
    java -cp ".\bin\;.\lib\*;.\lib\drive\*;.\lib\drive\libs\*" cloudsync.Cloudsync %*
)

:end