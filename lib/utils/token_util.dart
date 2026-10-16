import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:bcrypt/bcrypt.dart';
import 'package:crypto/crypto.dart';

import '../constants/constants.dart';

/// 酷安客户端签名工具。
///
/// ## 背景
/// 上游 Flutter 工程（2024 年）只有 [getTokenV2]；用户 Android 端已升级到
/// **X-App-Token v3**（`TokenDeviceUtils.getTokenV3`），这里补齐。
///
/// ## v3 的两条铁律（实测得出，见 `_rev/win_ref/README.md`）
/// 1. **密钥取窗必须在 base64 文本上按 off 切片再解码**，
///    不是「先整表解码、再按字节切片」—— 后者被服务端拒为「请求验证错误」。
/// 2. `versionCode` 必须与同一请求的 `X-App-Code` 完全一致，
///    `deviceCode` 必须与 `X-App-Device` 完全一致。
///    实测：token 用 2609291、头里报 10004 → **HTTP 403**。
///
/// ## 规范实现
/// `_rev/v3_token.py` / `_rev/win_ref/coolapk_protocol.py` 是已被服务端接受的
/// 规范实现，本文件与其**逐步对应**，并由 `test/token_v3_test.dart`
/// 用 golden 向量逐字节校验。
class TokenUtils {
  static bool isPreGetLoginParam = false;
  static bool isGetLoginParam = false;
  static bool isOnLogin = false;
  static bool isGetCaptcha = false;

  static String createRandomNumber() {
    return Random().nextDouble().toString().replaceAll('.', 'undefined');
  }

  /// `X-App-Device` 的编码：`UTF-8 → base64 → 整体反转 → 去掉 \r\n 与 '='`
  static Future<String> encodeDevice(String deviceInfo) async {
    var bytes = utf8.encode(deviceInfo);
    var base64Str = base64Encode(bytes);
    var reversedStr = base64Str.split('').reversed.join();
    var cleanedStr = reversedStr.replaceAll(
        RegExp(r'(\r\n|\r|\n|=)'), Constants.EMPTY_STRING);
    return cleanedStr;
  }

  /// 解码：`补 '=' → 反转 → base64 解码 → UTF-8`（[encodeDevice] 的逆运算）
  static String decodeDevice(String deviceCode) {
    final reversed = deviceCode.split('').reversed.join();
    final padded = reversed + '=' * ((4 - reversed.length % 4) % 4);
    return utf8.decode(base64Decode(padded));
  }

  static String getTokenV2(String deviceCode) {
    var timeStamp = (DateTime.now().millisecondsSinceEpoch ~/ 1000).toString();
    var base64TimeStamp = getBase64(timeStamp);
    var md5TimeStamp = _getMD5(timeStamp);
    var md5DeviceCode = _getMD5(deviceCode);
    var token =
        '${Constants.APP_LABEL}?$md5TimeStamp\$$md5DeviceCode&${Constants.APP_ID}';
    var base64Token = getBase64(token);
    var md5Base64Token = _getMD5(base64Token);
    var md5Token = _getMD5(token);
    var bcryptSalt =
        '${"\$2a\$10\$$base64TimeStamp/$md5Token".substring(0, 31)}u';
    var bcryptResult = BCrypt.hashpw(md5Base64Token, bcryptSalt);
    return 'v2${base64Encode(utf8.encode(bcryptResult.replaceRange(0, 3, "\$2y"))).replaceAll("=", "")}';
  }

  /// 酷安 `X-App-Token` **v3**（逆向自官方 `libauth.so` 的 `getToken`）。
  ///
  /// ```
  /// off   = 4 * ((t + versionCode) % 100) + 128
  /// B2    = base64_decode(KEY[off, off+128])                   // 96 字节
  /// S     = APP_ID & B2 & md5hex(device) & t & versionCode      // 全程原始字节
  /// salt  = base64(hex(t) + "/" + md5hex(S))[:22]
  /// key   = md5hex(base64(S))                                   // bcrypt 的 password
  /// hash  = bcrypt(key, "$2y$04$" + salt)
  /// token = "v3" + base64(hash)
  /// ```
  ///
  /// [deviceCode] 与 [versionCode] 必须与同一请求里的 `X-App-Device` /
  /// `X-App-Code` 头完全一致，否则服务端返回 403。
  static String getTokenV3(
    String deviceCode, {
    required String versionCode,
    int? timestamp,
  }) {
    final t = timestamp ?? (DateTime.now().millisecondsSinceEpoch ~/ 1000);
    final vc = int.tryParse(versionCode) ?? int.parse(Constants.VERSION_CODE);
    final off = 4 * ((t + vc) % 100) + 128;

    // ★ 铁律 1：在 base64 **文本**上按 off 切片再解码。
    // KEY 表解出来后本身就是一段 base64 文本（930 字节 ASCII）。
    final keyBytes = base64Decode(_KEY_B64);
    final window = ascii.decode(keyBytes.sublist(off, off + 128));
    final b2 = base64Decode(window); // 96 字节，可能含任意二进制

    // 全程按原始字节拼接，避免非 ASCII 字节被二次编码
    final s = Uint8List.fromList(<int>[
      ...ascii.encode(Constants.APP_ID),
      ...ascii.encode('&'),
      ...b2,
      ...ascii.encode('&'),
      ...ascii.encode(_getMD5(deviceCode)),
      ...ascii.encode('&'),
      ...ascii.encode('$t'),
      ...ascii.encode('&'),
      ...ascii.encode('$vc'),
    ]);

    final mS = _getMD5Bytes(s); // 只用于拼 salt 明文
    final salt =
        base64Encode(ascii.encode('${t.toRadixString(16)}/$mS')).substring(0, 22);
    final mKey = _getMD5(base64Encode(s)); // bcrypt 的 password

    // bcrypt 包（与 jbcrypt 同源）不认 $2y 盐：用 $2a 生成后改写前缀。
    // 实测二者哈希本体逐字节相同（_rev/win_ref/check_bcrypt_prefix.py）。
    final hashed = BCrypt.hashpw(mKey, '\$2a\$04\$$salt');
    final hash = '\$2y${hashed.substring(3)}';

    return 'v3${base64Encode(ascii.encode(hash))}';
  }

  static String getBase64(String input) {
    return base64Encode(utf8.encode(input)).replaceAll('=', '');
  }

  static String _getMD5(String input) {
    var bytes = utf8.encode(input);
    var digest = md5.convert(bytes);
    return digest.toString().replaceAll('-', '');
  }

  static String _getMD5Bytes(List<int> bytes) {
    return md5.convert(bytes).toString().replaceAll('-', '');
  }

  /// `libauth.so` 中 930 字节的 KEY 表（此处为其 base64 文本，跨版本稳定）。
  /// 与 Android `TokenDeviceUtils.KEY_B64` / `_rev/v3_token.py` 的 `_KEY_B64` 一致。
  static const _KEY_B64 =
      'VFRCVU9GUXNRMEVsTFVNa1dERWpRU0VzVXlFbUxETkFVeTFEUEZZdUl5MGlNVU01SXpCVUpGY3RN'
      'eWtoTVVRMUpTMURPU1V4TXpoUUxWTXdWMDBzSXloU01UUXNXVEZEUkZFd1EwUlZNU1FrVVMwaktT'
      'TXRKRGhZTFZRa1VqRXpLU1FzTTBCVkxUUXdVaTAwTlNNc00yQlhMVk1sSkMxRE5GbE5MQ1EwV1RF'
      'ekxTUXhRMEVoTEVNbElqRXpLRmN3TXowbExpTkVWekVqUFNZdU5Ea2tMRk13Vml4VEtGa3RRemhY'
      'TEZNc1dDNGpMU1l0VTJCVFRUQTBMRkV1TXpoVExqTWhJakZETUZNc0kwQlZMQ1FrVUN3ekpTSXNJ'
      'ekJTTUVNOFVDeEVMU0l0STJCUUxVUW9VeTBqTEZNc0l5VWtNRlF3VmswdFJDeFlNVE1sSkM0ME5T'
      'RXdVMFJRTEZNaElpMGtOU013VTJCUUxqTmdVeXdqT0ZFc05DeFRMRU13V0N4VUxTRXRJeVJUTERO'
      'Qkl5MDBKRlpOTVVNc1VEQkVKRmd0TkMwa0xEUWtXQzFETUZNeFJDa2pMVk0wVml4VE1GZ3hJeTBt'
      'TFZNeEpTMHpKRlF4TTJCUUxTUTRXVEJETUZNdUkwVWhUU3d6UEZndFJEQlJNRE5FVVMwak9GWXVK'
      'RGtsTVNRMFV6QXpOU013UkRra01UTWtWU3hVTUZRdFJDeFlNVE5GSXl4RUxGWXNJelVoTGlRMFYw'
      'MHhOQ3hWTENNMFZ5NHpKRlV4TkN4VExGUTRWeTRqWUZZeEpEVWpMVU13VXpGRFBGSXNNMEVsTERN'
      'c1ZEQTBPU011TkNrbUxFUWtWaXhFTVNKTkxUTW9VaXd6TEZjdFUwUlFMaVF3VlRGREpTVXdVeWtq'
      'TVVNaEl5MHpJU1VzSTBFak1UTXdVUzFVTkZjeFJERWxMVVF0SlM0a05GSXNJemhaVFRCVFlGRXdR'
      'eVJZTFNNbElTNDBMRmdzVkRSWkxVUW9XVEJEUUZZdFJDeFdMak5FVkRCRE1Ga3RORFVsTEZRNFZT'
      'MVVKRmd3TXpSV0xEUTRVVTB3UXkwbUxVUTRXUzFUTVNNc1V5VWlNVU5BVVN4VUxGWXVJMFJVTGlN'
      'c1dERkVMRk14STJCUUxWTTBXUzRrT0Zjd00wUlhMVk1vV0MwaktGRXhMQ00wVml4VE5GQXRSRFJX'
      'TFVRbEpDMVRKRkF3TkN4Z1lB';
}
