@echo off
rem Usage: run_build.cmd [release]  (default: debug build)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_HOME=C:\Users\Andrew\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0
if /I "%~1"=="release" (
  "%JAVA_HOME%\bin\java" -cp "%GRADLE_HOME%\lib\gradle-launcher-9.5.0.jar" org.gradle.launcher.GradleMain :app:assembleRelease :app:bundleRelease
) else (
  "%JAVA_HOME%\bin\java" -cp "%GRADLE_HOME%\lib\gradle-launcher-9.5.0.jar" org.gradle.launcher.GradleMain :app:assembleDebug %*
)
