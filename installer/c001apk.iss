; c001apk Windows 桌面端 安装程序脚本（Inno Setup 6）
;
; 为什么用 Inno Setup：
;   - GitHub 的 `windows-2022` 运行器**自带** Inno Setup 6（`C:\Program Files (x86)\Inno Setup 6\ISCC.exe`）
;     ⇒ CI 里不用额外装东西 ✓
;   - 产出一个**单个 .exe 安装程序**（不是压缩包）✓ 双击即装 ✓
;   - 支持开始菜单 / 桌面快捷方式 / 卸载项 ✓
;
; 用法（CI 里由 workflow 调用）：
;   ISCC.exe /DAppVersion=1.0.0 /DSourceDir=<Release 目录> /DOutputDir=<输出目录> c001apk.iss
;
; 本地手动编译：
;   "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DAppVersion=1.0.0 ^
;       /DSourceDir=build\windows\x64\runner\Release /DOutputDir=build\installer installer\c001apk.iss

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\windows\x64\runner\Release"
#endif
#ifndef OutputDir
  #define OutputDir "..\build\installer"
#endif

#define AppName "c001apk"
#define AppPublisher "kongwufang"
#define AppUrl "https://github.com/kongwufang/c001apk_next"
#define AppExeName "c001apk-flutter.exe"

[Setup]
AppId={{8E1D3F2A-6C4B-4D8E-9A7F-1B2C3D4E5F60}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppUrl}
AppSupportURL={#AppUrl}
AppUpdatesURL={#AppUrl}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; 用户目录安装不需要管理员；这里选「管理员」是因为要写 Program Files
PrivilegesRequired=admin
OutputDir={#OutputDir}
; ★ 命名规则（2026-10-18 用户规定）：
;   c001apk_next_desktop-V1.0.6-release
;   前缀 = 仓库名 + _desktop；版本号与安卓端统一（同一个 VERSION_CODE / VERSION_NAME）
OutputBaseFilename=c001apk_next_desktop-V{#AppVersion}-release
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; 64 位
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; 卸载时不删用户数据（Hive 在 %APPDATA%，不在这里）
UninstallDisplayIcon={app}\{#AppExeName}
SetupLogging=yes

; ⚠️ Inno Setup **不自带简体中文语言包** ✗（`Languages\ChineseSimplified.isl` 不存在）
;    所以界面只有英文 ✓ 要中文得把社区维护的 .isl 一起提交进仓库（以后再说）
[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加任务:"; Flags: unchecked

[Files]
; 整个 Release 目录（exe + DLL + data/）
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\卸载 {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "立即运行 {#AppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; 卸载时清掉安装目录残留（用户数据在 %APPDATA%\Integral\c001apk-flutter，**不动** ✓）
Type: filesandordirs; Name: "{app}"
