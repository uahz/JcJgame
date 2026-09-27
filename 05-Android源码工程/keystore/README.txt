此目录是 v2.7 及以后版本的安卓签名证书（debug 用途）。

· fengqi-debug.keystore：别名 androiddebugkey，storepass / keypass 均为 android
· 以后每次出包都必须用这一份 —— 换证书会导致用户无法覆盖安装旧版
· 05-Android源码工程/build_apk.bat 已默认优先使用本目录的证书
