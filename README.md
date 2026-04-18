# JoyniX Stego Vault – Android + Roxify

> FOSS steganographic archive explorer compatible with Roxify (PC/CLI & JS), built for Android.

***

## 1. Overview

JoyniX Stego Vault est une application Android FOSS qui utilise le moteur **Roxify** pour :

- compresser et chiffrer des fichiers / dossiers,
- les encapsuler dans des PNG compatibles Roxify,
- les extraire ensuite, sur téléphone **ou** sur PC avec `rox decode` / `decodePngToBinary()`.

Objectifs principaux :

- Interop totale avec Roxify PC/JS (même format de conteneur, même chiffrement AES‑GCM, mêmes métadonnées).
- Appli Android moderne, stable, qui ne génère **jamais** de PNG à 0 octet ou invalides (utilisation de vraies libs PNG / Bitmap).
- Toolbox de fonctionnalités utiles (vault, partage stego, notes, QR offline, scanner Roxify, etc.).

***

## 2. Repository layout

Structure recommandée du repo :

```text
joynix-stego-vault/
  engine/roxify/       # Roxify (submodule ou copie) – Rust + TypeScript/JS
  android-app/         # Appli Android Kotlin
  docs/                # Docs projet (ce README, schémas, etc.)
  scripts/             # Scripts build / CI
```

***

## 3. Engine Roxify (engine/roxify)

Le dossier `engine/roxify` correspond au projet Roxify original : Rust natif (N‑API + CLI) et TypeScript.

### 3.1 Fichiers Rust (native/*.rs)

Roxify met la logique critique (compression, crypto, PNG, ECC) dans `native/` :

| Fichier Rust                | Rôle principal                                                                 |
|----------------------------|-------------------------------------------------------------------------------|
| `native/lib.rs`            | Entrée N‑API : expose les fonctions Rust vers Node (JS/TS).                  |
| `native/main.rs`           | Entrée CLI : implémente `roxify_native` (binaire CLI).                       |
| `native/core.rs`           | Fonctions bas niveau : CRC32, Adler32, delta‑coding, wrappers Zstd.         |
| `native/archive.rs`        | Format d’archives (type tar) + compression Zstd optionnelle. |
| `native/packer.rs`         | Sérialisation de répertoires en archive binaire (équivalent de `packPaths`).|
| `native/bwt.rs`            | Burrows–Wheeler Transform (BWT) + Move‑to‑Front + RLE (pipeline BWT‑ANS). |
| `native/mtf.rs`            | Move‑to‑Front transform pour BWT.                                            |
| `native/context_mixing.rs` | Modélisation statistique (context mixing) pour l’encodeur entropique.       |
| `native/rans.rs`           | rANS (Asymmetric Numeral Systems) – encodeur entropique principal. |
| `native/rans_byte.rs`      | Variante rANS orientée octets.                                               |
| `native/pool.rs`           | Buffer pool / zero‑copy pour limiter les allocs et transferts.               |
| `native/hybrid.rs`         | `HybridCompressor` : pipeline bloc‑par‑bloc BWT→context→rANS (CPU/GPU). |
| `native/image_utils.rs`    | Utilitaires image (formats, tailles, conversions).                           |
| `native/png_utils.rs`      | Lecture/écriture bas niveau de chunks PNG.                                   |
| `native/png_chunk_writer.rs` | Écriture de chunks PNG custom (ex. `rXDT` pour mode compact). |
| `native/encoder.rs`        | Encodage payload→PNG : modes screenshot/compact, métadonnées. |
| `native/reconstitution.rs` | Détection/correction de screenshots zoomés, unstretch d’images. |
| `native/streaming_encode.rs` | Encodage streaming dir→PNG avec progrès.                                   |
| `native/streaming_decode.rs` | Décodage streaming PNG→dir.                                                |
| `native/streaming.rs`      | Glue pour les pipelines streaming.                                           |
| `native/audio.rs`          | Conteneur audio WAV (MFSK, PCM, mode sound).                  |
| `native/ecc.rs` \*         | (dans certaines branches) ECC Reed–Solomon GF(256) pour mode lossy. |
| `native/progress.rs`       | Suivi de progression pour les opérations longues.            |
| `native/io_advice.rs`      | Optimisation I/O (POSIX_FADV_DONTNEED, etc.).                |
| `native/test_small_bwt.rs` | Tests internes BWT.                                                           |
| `native/test_stages.rs`    | Tests de pipeline (développement).                                           |
| `native/bench_hybrid.rs`   | Benchmarks du `HybridCompressor`.                                            |

### 3.2 Fichiers TypeScript / JS (src/*)

Le dossier `src/` gère la glue JS, l’API, le CLI TS.

| Fichier TS/JS                      | Rôle principal                                                          |
|-----------------------------------|-------------------------------------------------------------------------|
| `src/index.ts`                    | Entrée lib JS : expose `encodeBinaryToPng`, `decodePngToBinary`, etc. |
| `src/cli.ts`                      | Entrée CLI TS (`rox encode`, `rox decode`), parse options. |
| `src/pack.ts`                     | `packPaths` / `unpackBuffer` : pack/unpack d’arbres de fichiers. |
| `src/stub-progress.ts`            | Implémentation minimale de reporting de progrès.                        |
| `src/utils/constants.ts`          | Constantes globales (modes, magic, etc.).                               |
| `src/utils/types.ts`              | Types TS communs.                                                       |
| `src/utils/native.ts`             | Wrapper N‑API, chargement des modules natifs `.node`.   |
| `src/utils/zstd.ts`               | Intégration compression Zstd via module natif.                          |
| `src/utils/encoder.ts`            | Orchestration encodage binaire → PNG (choix mode, options). |
| `src/utils/decoder.ts`            | Orchestration PNG → binaire, auto‑détection de format/mode. |
| `src/utils/errors.ts`             | Gestion des erreurs, messages (“Incorrect passphrase”, etc.). |
| `src/utils/helpers.ts`            | Fonctions utilitaires (delta, XOR, conversions).                        |
| `src/utils/inspection.ts`         | Inspection d’images, extraction des métadonnées.                        |
| `src/utils/reconstitution.ts`     | Reconstitution de screenshots stretchés.                                |
| `src/utils/ecc.ts`                | ECC Reed–Solomon, interleaving (mode lossy).            |
| `src/utils/robust-image.ts`       | Encodage image robuste (QR‑like blocks) pour lossyResilient. |
| `src/utils/robust-audio.ts`       | Encodage audio MFSK robuste (mode son).                  |
| `src/utils/audio.ts`              | Conteneur WAV (PCM).                                                    |
| `src/utils/crc.ts`                | CRC32 côté JS.                                                          |
| `src/utils/optimization.ts`       | Optimisation PNG (passes supplémentaires).                              |
| `src/utils/rust-cli-wrapper.ts`   | Intégration du CLI `roxify_native` depuis Node.                         |
| `src/types/fflate.d.ts`           | Typages pour lib de compression.                                       |
| `src/types/roxify-tdoc.d.ts`      | Typages TSDoc pour l’API Roxify.                                       |

Ces fichiers forment le moteur JS/CLI. L’appli Android ne les exécute pas directement, mais **doit respecter le même format** que ce code génère.

***

## 4. Android app (android-app/)

### 4.1 Objectif Android

L’appli Android doit :
- utiliser Roxify comme **spécification de format** (AES‑256‑GCM, PBKDF2, Zstd, modes PNG `screenshot`/`compact`),
- garantir que :
  - un PNG généré sur le téléphone se décode avec `rox decode` sur PC,
  - un PNG généré par `rox encode` se décode dans l’app (via format Roxify).

### 4.2 Modules Android proposés

```text
android-app/
  app/                     # module application
    src/main/java/com/stegovault/
      engine/              # wrapper Roxify (JNI ou Kotlin)
      ui/                  # écrans et navigation (Material 3)
    src/main/res/          # layouts, themes, widgets, strings EN
    src/main/res/values-fr # strings FR
```

### 4.3 Intégration moteur Roxify

Nous utilisons l'Option B: **Réimplémentation du format Roxify en Kotlin**.
- L'app lit la spec (header binaire, crypto PBKDF2/AES-GCM, Zstd).
- Encapsule le tout dans des chunks PNG (`rXDT`).
- N'embarque aucun binaire Rust pour garantir la sécurité et la compatibilité FOSS sur toutes les architectures Android.

***

## 5. Android – Architecture fonctionnelle

### 5.1 Stego Archive Vault
- Écran principal pour encoder un ensemble de fichiers/dossiers → PNG Roxify, ou décoder un PNG/archive → fichiers.

### 5.2 Offline QR Session
- Session QR offline pour partager un petit payload chiffré (clé, note, mini archive).

***

## 6. Build & installation

Dans le dossier root :
```bash
./gradlew assembleDebug
# APK dans app/build/outputs/apk/debug/
```
- Vérifier que le manifest et le Gradle n’excluent pas d’ABI / device.
- Tester sur un vrai Xiaomi / Android 14 / HyperOS.

***

## 7. Validation interop Roxify

### 7.1 Encode Android → Decode PC
1. Sur Android : sélectionner fichiers, encoder en `archive.png`.
2. Sur PC : `rox decode archive.png -p "passphrase" -o out/`

### 7.2 Encode PC → Decode Android
1. Sur PC : `rox encode myfiles.zip myfiles.png -p "passphrase"`
2. Sur Android : Sélectionner `myfiles.png` et décoder.
