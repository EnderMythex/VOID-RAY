`libv2ray.aar` (Xray core for Android) goes here. CI downloads a pinned release of
https://github.com/2dust/AndroidLibXrayLite (see `LIBV2RAY_VERSION` in
`.github/workflows/android.yml`; its 64-bit libraries are 16 KB page aligned, as Google
Play requires). For a local build:

    curl -L -o android/app/libs/libv2ray.aar \
      https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.31/libv2ray.aar
