package com.example.c001apk.desktop.core.security

/**
 * 完整性校验用的内嵌公钥（Ed25519, X.509 SubjectPublicKeyInfo 的 base64）。
 *
 * ★ 本文件在**打包时由 CI 覆盖生成**（_rev/win_ref/make_integrity.py），
 *   仓库里的这份是**占位**（空字符串）—— 此时 `Integrity.verify` 会自动跳过校验，
 *   以便本地开发构建不受影响。
 *
 * 不要把真实公钥提交进仓库：它是每个发行密钥独有的，由 CI 在构建时注入。
 */
internal object IntegrityPublicKey {
    const val BASE64: String = ""
}
