# Create: Kinetic Interference

![Neoforge](https://img.shields.io/badge/Loader-NeoForge-orange) ![MC Version](https://img.shields.io/badge/Minecraft-1.21.1-green) ![Create Version](https://img.shields.io/badge/Create-6.0.9-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

**Create: Kinetic Interference** is an addon for the [Create](https://www.curseforge.com/minecraft/mc-mods/create) mod. Its primary goal is to balance gameplay by discouraging the "spamming" or dense stacking of free kinetic energy sources (Windmills and Waterwheels) in a small area.

## 🔧 Features

### 📉 Kinetic Interference
When multiple kinetic sources are placed too close to each other, they will interfere with one another, reducing their stress capacity output.

**Efficiency Formula:**
The efficiency of a generator decreases based on the number of other active generators within its interference radius:
$$
\text{Efficiency} = \frac{1}{1 + (\text{Count} \times \text{Factor})}
$$
*For example: If you have 4 windmills nearby and the interference factor is 0.2, the efficiency drops to ~55%.*

### 🥽 Goggles Integration
When wearing Engineer's Goggles:
* **Tooltip:** Looking at a windmill bearing or waterwheel shows its current **Efficiency %** and the number of **interfering sources**.
* **Debug Visualization:** Hold `Shift` + `Right-Click` on a generator to visually highlight all other generators that are currently interfering with it (Requires Client Config enabled).

### ⚙️ Highly Configurable
Players can tweak almost every aspect of the mod in `createkineticinterference-server.toml`:
* **Radius:** How far the interference reaches.
* **Factor:** How much efficiency is lost per nearby generator.
* **Calculation Mode:** Choose how distance is measured:
    * `EUCLIDEAN_3D`: Standard spherical radius (default).
    * `EUCLIDEAN_2D`: Cylindrical radius (ignores height difference).
    * `MANHATTAN_3D`: Grid-based diamond shape calculation.
    * `MANHATTAN_2D`: A 2D variant of MANHATTAN_3D that only calculates x and z distance.

## 📥 Dependencies

1.  **Minecraft 1.21.1**
2.  **NeoForge**
3.  **Create** (6.0.9+)

## 📝 Configuration

Configuration files are located in `/config/createkineticinterference-server.toml` and `/config/createkineticinterference-client.toml`.

## Compatibility

The compatibility update composes CKI's capacity, tooltip, and removal hooks with Create Picky Wheels and Flowing Fluids. Unloaded source records are preserved; stale records in loaded chunks are repaired automatically when queried. When using both addons, follow Create Picky Wheels' own Flowing Fluids configuration guidance for water-source requirements.

## 📄 License

This project is licensed under the MIT License.
