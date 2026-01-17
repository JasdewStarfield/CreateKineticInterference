# Create Kinetic Interference

![Neoforge](https://img.shields.io/badge/Loader-NeoForge-orange) ![MC Version](https://img.shields.io/badge/Minecraft-1.21.1-green) ![Create Version](https://img.shields.io/badge/Create-6.0.9-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

**Create Kinetic Interference** 是 [Create (机械动力)](https://www.curseforge.com/minecraft/mc-mods/create) 模组的一个附属。它的主要目标是通过抑制在狭小区域内“滥用”或密集堆叠免费动力源（风车和水车）来平衡游戏玩法。

## 🔧 功能特性

### 📉 动力干扰
当多个动力源放置得太近时，它们会相互干扰，从而降低其应力容量的产出。

**效率公式：**
应力源的效率会根据其干扰半径内其他**活跃**应力源的数量而下降：
$$
\text{效率} = \frac{1}{1 + (\text{数量} \times \text{系数})}
$$
*例如：如果附近有 4 个风车，且干扰系数为 0.2，则效率将降至约 55%。*

### 🥽 护目镜集成
当佩戴工程师护目镜（Engineer's Goggles）时：
* **信息提示：** 查看风车轴承或水车时，会显示其当前的 **效率 %** 以及附近的 **干扰源数量**。
* **调试可视化：** 对着发电机按住 `Shift` + `右键`，可以高亮显示当前干扰它的所有其他发电机（需要在客户端配置中启用此功能）。

### ⚙️ 高度可配置
玩家可以在 `createkineticinterference-server.toml` 中调整模组的几乎所有参数：
* **半径：** 干扰生效的距离范围。
* **系数：** 每一个附近的发电机会造成多少效率损失。
* **计算模式：** 选择距离的计算方式：
    * `EUCLIDEAN_3D`：标准 3D 欧氏距离（球形判定，默认）。
    * `EUCLIDEAN_2D`：2D 平面距离（圆柱形判定，忽略高度差）。
    * `MANHATTAN_3D`：曼哈顿距离（基于网格的菱形判定）。
    * `MANHATTAN_2D`：MANHATTAN_3D 的 2D版本，只计算 x 和 z 距离。

## 📥 依赖

1.  **Minecraft 1.21.1**
2.  **NeoForge**
3.  **机械动力** (6.0.9+)

## 📝 配置文件

配置文件位于 `/config/createkineticinterference-server.toml` 和 `/config/createkineticinterference-client.toml`.

## 📄 许可证

本项目采用 MIT License 开源。