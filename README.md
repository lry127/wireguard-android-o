# Android GUI for [wireguard-o](https://github.com/lry127/wireguard-o)

## 介绍

本项目为wireguard魔改版，通过对所有数据包执行xor计算以去除wireguard包特征，避免被GFW封锁。本repo运行在Android端。[服务器端repo](https://github.com/lry127/wireguard-o).

> [!WARNING]  
> [Release](https://github.com/lry127/wireguard-android-o/releases/tag/v1.0)中提供的APK仅支持xor密钥为`your_secret_key_or_password`的服务器。如需变更，需修改 `tunnel/src/main/java/com/wireguard/android/backend/SimpleUdpForwarder.java` 并重新编译APK.


## Building

```
$ git clone --recurse-submodules https://github.com/lry127/wireguard-android-o.git
$ cd wireguard-android-o
$ ./gradlew assembleRelease
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).


## Translating

Please help us translate the app into several languages on [our translation platform](https://crowdin.com/project/WireGuard).
