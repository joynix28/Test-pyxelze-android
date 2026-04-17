# StegoVault Architecture & Formats

**Made by JoyniX**
*Inspired conceptually by Roxify and Pyxelze. Fully FOSS (Apache 2.0).*

StegoVault is a modern, mathematically strict, lossless steganography toolkit for Android. It embeds binary archives into valid PNG images dynamically without arbitrarily exploding file sizes.

---

## 1. High-Level Pipeline (Strict "Compression-First" Approach)

To ensure PNG output sizes do not explode into 40MB unwieldy objects from 1MB payloads, StegoVault mandates a **Strict Compress-First Pipeline**:

1. **Input Collection:** Traverse files/directories and build a flat `ArchiveEntry` sequence.
2. **Compression:** All data is **ALWAYS** compressed first (via Java's standard `Deflater` or advanced external tools like `Zstd`). Compression generally reduces text/documents by 60%+ ensuring the stego-payload remains small.
3. **Encryption (Optional):** If a passphrase is provided:
   - Key Derivation: `PBKDF2-HMAC-SHA256` (Default 100,000 iterations + 16 byte salt).
   - Encryption: `AES-256-GCM` (96-bit nonce, 128-bit MAC tag).
4. **Embedding (Steganography):** Data is embedded via two strictly controlled methodologies based on mathematically bounded capacity.

---

## 2. PNG Steganography & Capacity Mathematics

StegoVault treats PNGs as lossless, compressed datasets, NOT uncompressed bitmaps.

### A) LSB / Pixel Mode (For Stealth & Small Files)
Using the Least Significant Bit (LSB) for RGB pixels provides a highly stealthy, noise-like steganographic cover. However, writing too much entropy into a PNG bitmap *destroys the PNG Deflate compression efficiency*, causing file size blowups.

**The Rule of Thumb:**
- `Capacity_Bits ≈ Width * Height * Channels (3 for RGB)` using exactly **1 bit per channel**.
- A standard 512x512 image has ~262,144 pixels.
  - `262,144 * 3 bits = 786,432 bits ≈ 98 KB` of strict payload capacity.
- **Engine Logic:** If the payload is > ~500KB, **LSB mode is disabled** because creating a 2000x2000 image solely to hold 1MB of data will explode the generated PNG size beyond 5x the payload.

### B) Chunk Mode (For Large Files & Bulk Archives)
For any payload exceeding typical LSB capacities (i.e. > ~500KB), the engine automatically routes to **Chunk Mode**.
- The image pixels form a simple, easily compressible gradient or text (e.g. 512x512 gradient with "StegoVault Archive").
- The payload is injected into a custom, non-critical PNG ancillary chunk called `SVLT`.
- **Efficiency:** The base PNG pixels compress down to a few kilobytes, and the chunk simply appends the compressed payload size. Total Output Size ≈ `Base Image Size (10KB) + Compressed Payload Size + Chunk Overhead (12 bytes)`.
- This strictly enforces `Output Size <= Original Size`.

---

## 3. App Toolbox Features

StegoVault wraps this core technology into a robust "toolbox" of 6 features:

1. **Stego Archive (Vault):** Encode/Decode standard files and directories.
2. **Multi-Part Archive Manager:** Slices multi-gigabyte files into sequence chunks to prevent OOM errors.
3. **Secure Clipboard Locker:** Instantly encrypt copied text to a steganographic PNG or QR code.
4. **Camera Stego:** Take a live photo and immediately embed a secret note or file into the image pixels.
5. **Secure QR Share:** An offline, app-to-app sharing channel. Encrypts a small payload and slices it into multi-frame QR codes.
6. **Diagnostics:** Benchmarks device compute power to categorize `LOW/MEDIUM/HIGH` tiers, dynamically adjusting PBKDF2 iterations to maintain fast UX on weak Androids.

---

## 4. UI Architecture & OS Integration

- **UI:** Strict Material 3 (MVVM architecture, Kotlin Coroutines, Fragments, Adaptive Theming).
- **Background Processing:** `WorkManager` delegates heavy streaming IO jobs (packing, encryption) off the main thread, offering persistent notifications and progress bars.
- **OS Hooks:** Handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE` to catch files directly from the Android Share Menu. Also provides `AppWidgetProvider` for 1-tap encoding.

---

## 5. Archive Binary Specification

```text
[Header Block]
- 4 bytes: Magic ("SV01")
- 1 byte: Version (0x01)
- 1 byte: Flags (bit 0: Encrypted, bit 1: Compact/Screenshot, bit 2: Multi-part)
- 1 byte: Compression Type (0=None, 1=Zstd, 2=Deflate)
- 1 byte: Reserved
- 16 bytes: PBKDF2 Salt
- 12 bytes: AES-GCM Nonce
- 4 bytes: PBKDF2 Iterations
- 4 bytes: Entry Count (N)
- 8 bytes: Total Compressed Payload Size

[Entries Block] (Repeated N times)
- 4 bytes: Path length (uint32)
- X bytes: UTF-8 Path
- 1 byte: Type (0=File, 1=Directory)
- 8 bytes: Uncompressed Size

[Data Block]
- Raw binary data of all files concatenated sequentially.
```