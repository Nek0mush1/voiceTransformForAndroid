# IME Local Dictionaries

This directory contains local dictionaries that are still used as the Java
fallback when the native Rime engine cannot start.

- `luna_pinyin.dict.yaml`: single-character pinyin readings from `rime/rime-luna-pinyin`.
- `THUOCL_IT.txt`: computer-science/IT domain terms from `thunlp/THUOCL`.

The primary Chinese candidate engine now lives under `assets/rime/` and uses
Trime's Android `librime_jni.so`. These files remain useful for fallback and
for regenerating the Voice Transform Rime domain dictionary.

`jieba` is intentionally not packaged here. Its main dictionary is useful for
Chinese word segmentation on the backend, but it is larger and does not directly
provide pinyin-to-word candidates for the Android keyboard.
