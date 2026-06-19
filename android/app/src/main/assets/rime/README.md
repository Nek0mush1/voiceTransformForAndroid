# Android Rime Runtime

This directory is packaged into the APK and copied to app-private storage on
first IME startup.

- `shared/`: Rime shared data extracted from osfans/Trime v3.3.10 release APK.
- `shared/voice_transform_pinyin.schema.yaml`: Voice Transform simplified
  pinyin schema.
- `shared/voice_transform_pinyin.dict.yaml`: project dictionary generated from
  THUOCL IT terms plus high-priority computer-science terms such as `计组`,
  `计网`, `操作系统`, and `数据结构`.

Native libraries are packaged separately in `android/app/src/main/jniLibs/*/`.
The Java binding classes intentionally use package `com.osfans.trime.core`
because Trime's `librime_jni.so` exports JNI symbols for that package name.

Source projects:

- https://github.com/osfans/trime
- https://github.com/rime/librime
- https://github.com/rime/rime-luna-pinyin
- https://github.com/thunlp/THUOCL
