# StegoVault Architecture & Formats

StegoVault is a modern, legally clean Kotlin-based Android application that wraps a "Roxify-style" steganographic engine. It enables users to compress, encrypt, and embed files and directories into standard PNG images (or internal steganographic archives).

## 1. High-Level Architecture

The application follows Clean Architecture principles:

```text
[UI Layer (Material 3 + Fragments + ViewModels)]
       |
       v
[Domain / Use-Cases (EncodeUseCase, DecodeUseCase, DiagnosticsUseCase)]
       |
       v
[Data / Background Layer (WorkManager, DocumentFile SAF, SharedPreferences)]
       |
       v
[Engine Layer (StegoEngine Abstraction)]
       |
       v
[CryptoEngine] <--> [ArchiveManager (Zstd/Deflate)] <--> [StegoPng (SVLT / Screenshot mode)]
```

- **UI Layer:** Implements a modern Material 3 design, adaptive theming, bottom navigation, and a toolbox of sub-features (QR share, Clipboard Locker, Diagnostics).
- **Domain Layer:** Coordinates the interactions between the UI and the underlying engines asynchronously using Kotlin Coroutines and WorkManager.
- **Engine Layer:** The `StegoEngine` interface provides a robust, streaming-capable wrapper around compression, encryption, and PNG manipulation. It intelligently chooses strategies based on payload size and device capabilities.

## 2. Core Pipeline (Roxify-Style)

### Encoding Pipeline (Binary -> PNG)
1.  **Input:** Streams from an arbitrary binary or a directory (traversed recursively via SAF).
2.  **Compression:** Data is compressed to minimize the footprint. (Currently using Deflate/Zstd based on configuration).
3.  **Encryption (Optional):** If a passphrase is provided, a 256-bit key is derived using PBKDF2-HMAC-SHA256 (iteration count determined by device profile or user setting). The compressed payload is encrypted using AES-256-GCM. If no passphrase is provided, the data is stored in plaintext.
4.  **PNG Embedding:**
    *   **Compact Mode (Default for small payloads):** The payload is injected into a 1x1 or small dummy PNG using a custom chunk (`SVLT`).
    *   **Screenshot Mode (For larger files):** The payload bits are encoded directly into RGB pixel variances (gradient/noise patterns), producing a larger but robust PNG that easily avoids chunk-stripping algorithms.
    *   **Multi-Part (For massive files):** Large payloads exceeding single-image capacity are split into multiple parts, each wrapped in its own PNG with a manifest pointing to the sequence.

### Decoding Pipeline (PNG -> Binary)
1.  **Input:** PNG buffer or stream.
2.  **Detection:** Reads headers to determine if the payload relies on chunk injection (`SVLT`) or pixel-based steganography.
3.  **Extraction:** Extracts the encrypted/compressed payload.
4.  **Decryption:** Prompts for a passphrase (if flagged as encrypted) and attempts AES-GCM decryption using the extracted salt, nonce, and iterations.
5.  **Decompression & Unpacking:** Decompresses the payload and restores the original file/directory structure via the Android Storage Access Framework.

## 3. Cryptographic Layer

- **Key Derivation:** PBKDF2 with HMAC-SHA256.
    - *Salt:* 16 bytes (Randomly generated per archive).
    - *Iterations:* Adaptive. Default 200,000, dynamically adjustable for low-end devices.
    - *Output:* 32 bytes (256-bit AES Key).
- **Encryption Algorithm:** AES-256-GCM.
    - *Nonce:* 12 bytes (Randomly generated).
    - *Tag:* 16 bytes (128-bit Authentication Tag).
- **Security Posture:** Data authenticity and confidentiality are guaranteed. Without the correct password, `AEADBadTagException` terminates decoding safely.

## 4. Archive Binary Format

StegoVault packs files into a flat binary stream before compression:

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

## 5. QR Share Protocol

For small payloads (keys, text, small archives), StegoVault uses QR Codes:
- **Single Frame:** Standard QR code encoding up to ~2.9KB.
- **Multi-Frame (Future):** Payload split into chunks `[1/N][Data]`, `[2/N][Data]`. The scanner buffers parts until the set is complete.
- *Note:* QRs represent a secondary offline channel that perfectly interfaces with the app's cryptographic engine.

## 6. Future Extensions

1.  **Audio Steganography:** Hiding encrypted archives within FLAC or WAV file headers/LSBs.
2.  **Advanced Compression:** Implement true multi-threaded Rust/C++ Zstd or BWT-ANS natively via JNI for massive performance gains.
3.  **Cloud Sync:** Encrypted cloud backup integration ensuring zero-knowledge synchronization of vaults.
4.  **Desktop Companion App:** A Kotlin Multiplatform or Rust/Tauri desktop client capable of reading the exact same `SVLT` PNGs.
