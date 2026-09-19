#!/usr/bin/env sh

# Gradle wrapper launcher. File gradle-wrapper.jar tidak disertakan dalam
# paket ini, jadi script ini akan memakai instalasi Gradle di sistem (jika
# ada) sebagai fallback. Cara paling mudah: buka proyek ini di Android
# Studio dan pilih "Sync Project with Gradle Files" — Android Studio akan
# otomatis melengkapi gradle-wrapper.jar dan mengganti script ini dengan
# wrapper resmi.

if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -jar "gradle/wrapper/gradle-wrapper.jar" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
    echo "gradle-wrapper.jar belum ada, memakai 'gradle' dari sistem sebagai fallback..."
    exec gradle "$@"
fi

echo "gradle-wrapper.jar tidak ditemukan dan Gradle tidak terpasang di sistem."
echo "Buka proyek ini di Android Studio agar wrapper dilengkapi otomatis."
exit 1
