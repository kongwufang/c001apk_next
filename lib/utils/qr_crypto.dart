import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/export.dart';

/// 扫码登录用到的加解密（**独立文件**，出错不影响其它代码）。
///
/// ## 硬约束（用户定的，不可动摇）
/// - **私钥只存在于桌面端内存**：不落盘、不上传、不写日志
/// - 服务端只转发密文，拿不到明文 `refreshToken`
/// - 开源可审计、无后门
///
/// ## 与手机端的格式约定（两端都是本项目代码 ✓）
/// | 项 | 约定 |
/// |---|---|
/// | 公钥传输 | `modulus` 与 `exponent` 各自用**大端无符号字节的 base64**（**不做** DER/ASN.1）|
/// | RSA padding | **PKCS#1 v1.5**（Kotlin: `RSA/ECB/PKCS1Padding`）|
/// | AES | **CBC + PKCS7**（Kotlin: `AES/CBC/PKCS5Padding`，AES 下二者等价）|
/// | 签名 | **SHA-256 with RSA**（Kotlin: `SHA256withRSA`）|
///
/// > 选 PKCS#1 v1.5 而不是 OAEP：OAEP 在 Dart/Kotlin 两边的
/// > MGF1 摘要默认值容易不一致（一个 SHA-1 一个 SHA-256）⇒ 解不出来 ✗
///
/// ## ⚠️ 这里用的是 `pointycastle` 的 API，签名我按文档推的
/// 若有偏差，`flutter analyze` 会精确报行号 —— 这正是把它单独放一个文件的目的 ✓
class QrCrypto {
  QrCrypto._();

  // ── RSA 密钥对 ──────────────────────────────────────────────

  /// 生成 RSA-2048 密钥对。**私钥用完即弃**，不落盘。
  static AsymmetricKeyPair<PublicKey, PrivateKey> generateKeyPair() {
    final rnd = Random.secure();
    final seeds = Uint8List(32);
    for (var i = 0; i < seeds.length; i++) {
      seeds[i] = rnd.nextInt(256);
    }
    final secureRandom = FortunaRandom()..seed(KeyParameter(seeds));

    final gen = RSAKeyGenerator()
      ..init(ParametersWithRandom(
        RSAKeyGeneratorParameters(BigInt.from(65537), 2048, 64),
        secureRandom,
      ));
    return gen.generateKeyPair();
  }

  /// BigInt → 大端无符号字节 → base64
  static String _b64OfBigInt(BigInt v) {
    final hex = v.toRadixString(16);
    final padded = hex.length.isOdd ? '0$hex' : hex;
    final bytes = Uint8List(padded.length ~/ 2);
    for (var i = 0; i < bytes.length; i++) {
      bytes[i] = int.parse(padded.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return base64Encode(bytes);
  }

  /// 公钥的 modulus（base64，大端无符号）
  static String modulusB64(RSAPublicKey pub) => _b64OfBigInt(pub.modulus!);

  /// 公钥的 exponent（base64，大端无符号）
  static String exponentB64(RSAPublicKey pub) => _b64OfBigInt(pub.exponent!);

  // ── 签名（第 ② 步：用私钥签「设备信息 + requestId」）──────────

  /// SHA-256 with RSA（PKCS#1 v1.5），返回 base64。
  static String signSha256(RSAPrivateKey priv, String data) {
    final signer = RSASigner(SHA256Digest(), '0609608648016503040201')
      ..init(true, PrivateKeyParameter<RSAPrivateKey>(priv));
    final sig = signer.generateSignature(Uint8List.fromList(utf8.encode(data)));
    return base64Encode(sig.bytes);
  }

  // ── 解密（第 ⑧ 步：私钥解 AES 密钥 → AES 解 refreshToken）─────

  /// 用私钥解 PKCS#1 v1.5 密文（手机端用公钥加密的 AES 密钥）。
  static Uint8List decryptPkcs1(RSAPrivateKey priv, String cipherB64) {
    final input = base64Decode(cipherB64);
    final engine = PKCS1Encoding(RSAEngine())
      ..init(false, PrivateKeyParameter<RSAPrivateKey>(priv));
    return engine.process(input);
  }

  /// AES-CBC + PKCS7 解密。`keyB64` 是 32 字节（AES-256）/ 16 字节均可。
  ///
  /// 约定：把 IV 拼在密文**最前面**（前 16 字节），手机端照此生成 ✓
  static String aesCbcDecrypt(String keyB64, String cipherB64) {
    final key = base64Decode(keyB64);
    final all = base64Decode(cipherB64);
    if (all.length <= 16) {
      throw ArgumentError('密文太短');
    }
    final iv = all.sublist(0, 16);
    final body = all.sublist(16);

    final cipher = PaddedBlockCipherImpl(
      PKCS7Padding(),
      CBCBlockCipher(AESEngine()),
    )..init(false, PaddedBlockCipherParameters<CipherParameters, CipherParameters>(
        ParametersWithIV<KeyParameter>(KeyParameter(key), iv),
        null));

    final out = cipher.process(body);
    return utf8.decode(out);
  }
}
