<p align="center"><img src="src/main/resources/createkineticinterference.png" alt="Create: Kinetic Interference 图标" width="180"></p>

# Create: Kinetic Interference

*给动力源留一点空间。*

[English](README.md) | **简体中文**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-5C9E31)
![Loader](https://img.shields.io/badge/Loader-NeoForge-E58B32)
![Create](https://img.shields.io/badge/Create-6.0.9%2B-D9A441)
![License](https://img.shields.io/badge/License-MIT-3B82F6)

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-kinetic-interference) · [源码](https://github.com/JasdewStarfield/CreateKineticInterference) · [问题反馈](https://github.com/JasdewStarfield/CreateKineticInterference/issues) · [更新日志](CHANGELOG.md)

**Create: Kinetic Interference（CKI，机械动力：动力干扰）** 是 [Create（机械动力）](https://www.curseforge.com/minecraft/mc-mods/create) 的附属模组，会降低彼此邻近的风车和水车的应力容量。它鼓励玩家分散布置免费动力源，避免在狭小区域密集堆叠，并为风车与水车提供独立的设置。

## 功能

- **邻近动力源干扰**——风车之间相互影响；小水车与大水车共用另一组干扰计算。两组之间互不干扰。
- **工程师护目镜信息**——运行中且受到干扰的动力源会显示效率和干扰源数量；潜行时还会显示干扰半径提示。
- **可选的干扰源高亮**——启用客户端高亮后，佩戴护目镜并潜行右键动力源，即可描出其记录的干扰源轮廓。
- **可调整的平衡规则**——分别设置风车和水车的干扰半径、系数及距离计算方式。

## 环境与安装

本 README 描述当前 Minecraft 1.21.1 / NeoForge 源码。下载的发布版本不一定包含[更新日志](CHANGELOG.md)中 **Unreleased** 下的改动。

| 项目 | 要求 |
| --- | --- |
| Minecraft | `1.21.1` |
| 模组加载器 | 适用于 Minecraft 1.21.1 的 NeoForge；当前构建使用 `21.1.218` |
| Java | `21` |
| 安装端 | 客户端与服务端 |
| 必需模组 | Create，以 `6.0.9` 为适配基线；当前构建使用 `6.0.9-215`。更高版本可能兼容，详见下方版本说明。 |

安装相互匹配的 NeoForge、Create 和 CKI。将模组 JAR 放入游戏实例的 `mods/` 文件夹；多人游戏还需要在服务端安装，并满足 Create 自身的依赖要求。Create Picky Wheels 和 Flowing Fluids 均为可选模组，使用 CKI 不需要安装它们。

## 快速开始

1. 安装 CKI 和 Create 后进入测试世界。首次体验使用 CKI 默认设置，不添加可选附属模组。
2. 用有效的流水驱动两个小水车，使它们的水平间距小于 32 格，附近没有其他水车。两者都必须自行产生转动，不需要接入同一个动力网络。
3. 佩戴工程师护目镜，观察任意一个水车，等待几秒让定期干扰检查完成。

**预期结果：** 每个水车都会计入另一个干扰源，效率约为 **90.9%**。CKI 降低的是应力容量，不直接降低转速。只有动力源正在运行且效率低于 100% 时，才会出现干扰信息提示。

若要直观看到干扰源，将客户端配置中的 `visuals.enableDebugHighlights` 改为 `true`，重启客户端，然后佩戴护目镜，按住潜行键（默认 `Shift`）并右键水车。高亮默认持续 3 秒。这些交互不需要 OP 权限。

### 干扰如何计算

```text
效率 = 1 / (1 + 数量 × 系数)
```

“数量”指同一维度、同一干扰分组内，处于配置半径范围中的其他已记录动力源数量，不包含自身。例如，附近有四个其他风车、系数为 `0.2` 时，效率为 `1 / (1 + 4 × 0.2) ≈ 55.6%`；附近有一个其他水车、系数为 `0.1` 时，效率为 `1 / (1 + 1 × 0.1) ≈ 90.9%`。

默认距离计算忽略高度，因此把动力源上下堆叠并不能避开干扰。未加载区块中的动力源记录会保留，详见[兼容性与限制](#兼容性与限制)。

## 配置

首次启动游戏并进入世界后，会生成配置文件。以下路径在单人游戏中相对于游戏实例目录，在独立服务器上相对于服务端目录。

| 位置 | 作用范围 | 修改方式 |
| --- | --- | --- |
| `config/createkineticinterference-server.toml` | 由服务端控制的玩法规则 | 退出世界或停止服务器后修改，再重新进入或启动 |
| `config/createkineticinterference-client.toml` | 仅影响当前客户端的高亮 | 关闭客户端后修改，再重新启动 |

在当前 NeoForge 构建下，如果世界目录内存在 `serverconfig/createkineticinterference-server.toml`，它会覆盖 `config/` 中的同名服务端配置。单人世界目录通常是 `saves/<world>/`；独立服务器使用 `level-name` 指定的世界目录。如果存在覆盖文件，应修改实际生效的那一份。修改本地服务端配置不会覆盖多人服务器的规则。

上述重启流程用于避免生效文件和重新计算时机不明确，并不表示每个选项都必须重启。CKI 没有专用的配置重载命令。

### 服务端设置

下表中的“节”是配置键所在的 TOML 节名称。

| 节 | 配置项 | 默认值 | 作用 |
| --- | --- | --- | --- |
| `general.windmill` | `interferenceRadius` | `32.0` | 风车检测半径，单位为格 |
| `general.windmill` | `interferenceFactor` | `0.2` | 每个邻近风车的干扰系数；设为 `0` 关闭惩罚 |
| `general.windmill` | `distanceCalculationMode` | `EUCLIDEAN_2D` | 风车距离计算方式 |
| `general.windmill` | `checkInterval` | `40` | 风车定期检查间隔，单位为 tick；20 TPS 时约为 2 秒 |
| `general.waterwheel` | `interferenceRadius` | `32.0` | 小水车与大水车共用的检测半径 |
| `general.waterwheel` | `interferenceFactor` | `0.1` | 每个邻近水车的干扰系数；设为 `0` 关闭惩罚 |
| `general.waterwheel` | `distanceCalculationMode` | `EUCLIDEAN_2D` | 水车距离计算方式 |

水车沿用 Create 的定期更新周期，没有独立的 CKI `checkInterval` 设置。

| 距离模式 | 判定规则 |
| --- | --- |
| `EUCLIDEAN_2D` | 水平直线距离，忽略高度。**两组的默认模式。** |
| `EUCLIDEAN_3D` | 三维直线距离，检测区域为球形 |
| `MANHATTAN_2D` | X、Z 坐标差绝对值之和 |
| `MANHATTAN_3D` | X、Y、Z 坐标差绝对值之和 |

### 客户端设置

| 节 | 配置项 | 默认值 | 作用 |
| --- | --- | --- | --- |
| `visuals` | `enableDebugHighlights` | `false` | 佩戴护目镜并潜行右键时，显示干扰源轮廓 |
| `visuals` | `debugHighlightsDuration` | `3000` | 高亮持续时间，单位为毫秒 |

## 兼容性与限制

- **Create 版本：** CKI 面向 Create `6.0.9` 及以上版本适配。更高版本可能兼容，但不保证；跨大版本更新大概率需要额外的兼容补丁。当前依赖声明 `[6.0.9,6.1.0)` 中的上限是保守限制，并非已确认的不兼容界线。加载器目前仍会拒绝该声明范围之外的版本，因此扩大范围还需要更新依赖声明，并按需补充兼容修复。
- **Create Picky Wheels 与 Flowing Fluids：** 当前源码包含针对这些附属的兼容调整，旨在让 CKI 的应力、护目镜提示和移除清理行为与它们共存。这不代表所有版本或配置组合都已兼容；护目镜显示以及自然水流、群系条件下的行为仍应在实际整合包中检查。同时使用两个附属时，还需按照 Create Picky Wheels 自身的 Flowing Fluids 配置说明设置水源要求。
- **已加载与未加载的动力源：** 区块卸载后仍保留动力源记录，因此其干扰也可能继续存在。查询已加载区块时，会清理已经消失或被替换的动力源记录；CKI 不会为了修复记录而强制加载区块。
- **适用动力源：** 风车、小水车和大水车。其他动力源不会自动参与干扰计算；替换 Create 应力或生命周期行为的附属可能需要单独适配。

## 从源码构建

使用 Java 21，在当前版本仓库根目录执行：

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

Linux 或 macOS 使用 `bash ./gradlew` 并保留相同参数。模组 JAR 生成在 `build/libs/`。

## 反馈

请在 [Issues](https://github.com/JasdewStarfield/CreateKineticInterference/issues) 提交问题或建议，并附上：

- Minecraft、NeoForge、Create、CKI 及相关附属的版本。
- 问题发生在单人游戏还是独立服务器。
- 复现步骤、相关配置、预期行为与实际行为。
- 日志或崩溃报告；护目镜或高亮问题请同时提供截图。

## 许可证与致谢

代码采用 **MIT License**，详见 [LICENSE](LICENSE)。

- 作者：Jasdew Starfield。
- 基于 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 和 [NeoForge](https://neoforged.net/)。
