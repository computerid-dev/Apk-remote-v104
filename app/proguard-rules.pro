# Aturan ProGuard/R8 khusus proyek ini.
# Lihat panduan resmi Android untuk opsi lengkap:
# https://developer.android.com/studio/build/shrink-code

# Simpan nama class thread & handler karena diakses lewat refleksi oleh sebagian tooling debug.
-keep class com.remote.bt.ServerThread { *; }
-keep class com.remote.bt.ClientThread { *; }
-keep class com.remote.bt.CommandHandler { *; }
