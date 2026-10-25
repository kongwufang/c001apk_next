# c001apk_next

所属fake coolapk下的项目
本项目虽然不是酷安第三方app项目群中知名度最高的，但致力于做酷安第三方的基础设施，目前已为社区提供token_v3算法和数字联盟id安卓端自动获取解决方案，感谢大家的支持

[![装机量](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dinstalls%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![24小时活跃](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dactive24%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![今日新增](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dnew%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![7天内活跃](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dactive7%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php)<br>
[![30天内活跃](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dactive30%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![7天内新增](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dnew7%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![24小时内活跃总时长](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dplaytime%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php) [![活跃分支数](https://img.shields.io/endpoint?url=https%3A%2F%2Fservice.houlangs.cn%2Fc001apk%2Fstats.php%3Fformat%3Djson%26metric%3Dbranches%26v%3D20261025c)](https://service.houlangs.cn/c001apk/stats.php)

## 下载 / 更新

最新正式版：**[release-latest](https://github.com/kongwufang/c001apk_next/releases/tag/release-latest)**（正式版与测试版共用同一 versionCode，可互相覆盖安装）

App 内「设置 → 关于」可检查更新：更新检查走自建接口，支持多线路下载。

上面的数据来自[自建统计页](https://service.houlangs.cn/c001apk/stats.php)，30 分钟刷新一次。

统计按**匿名设备标识**计，与账号无关：

## 关于本项目 / Origin

本项目基于上游两个开源项目改编，沿用 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布，详见 [`LICENSE.md`](./LICENSE.md)。

代码主干血统（lineage）：

```
bggRGjQaUbCoE/c001apk-flutter  (Flutter 重写，已归档)
        │
        ▼
RzdPai/c001apk                (Kotlin 重写，本项目代码主线)
        │
        ▼
kongwufang/c001apk  ──fork──>  kongwufang/c001apk_next  (本仓库)
```

- 上游 Kotlin 主线：[RzdPai/c001apk](https://github.com/RzdPai/c001apk)
- 上游 Flutter 重写（已归档）：[bggRGjQaUbCoE/c001apk-flutter](https://github.com/bggRGjQaUbCoE/c001apk-flutter)

如上游更新，本项目会视情况进行同步（rebase/merge）。所有上游版权与许可声明均完整保留在 `LICENSE.md`。

---

Token/LoginUtil from: [CoolbbsYou](https://github.com/WaitFme/CoolbbsYou)

