@echo off
rem Gradle wrapper launcher (Windows). File gradle-wrapper.jar tidak
rem disertakan dalam paket ini. Cara paling mudah: buka proyek ini di
rem Android Studio dan pilih "Sync Project with Gradle Files" — Android
rem Studio akan otomatis melengkapi gradle-wrapper.jar dan script ini.

if exist "gradle\wrapper\gradle-wrapper.jar" (
    java -jar "gradle\wrapper\gradle-wrapper.jar" %*
    goto :eof
)

where gradle >nul 2>nul
if %errorlevel__==0 (
    echo gradle-wrapper.jar belum ada, memakai 'gradle' dari sistem sebagai fallback...
    gradle %*
    goto :eof
)

echo gradle-wrapper.jar tidak ditemukan dan Gradle tidak terpasang di sistem.
echo Buka proyek ini di Android Studio agar wrapper dilengkapi otomatis.
exit /b 1
