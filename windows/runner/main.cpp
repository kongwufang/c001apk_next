#include <flutter/dart_project.h>
#include <flutter/flutter_view_controller.h>
#include <windows.h>

#include "flutter_window.h"
#include "utils.h"

int APIENTRY wWinMain(_In_ HINSTANCE instance, _In_opt_ HINSTANCE prev,
                      _In_ wchar_t *command_line, _In_ int show_command) {
  // Attach to console when present (e.g., 'flutter run') or create a
  // new console when running with a debugger.
  if (!::AttachConsole(ATTACH_PARENT_PROCESS) && ::IsDebuggerPresent()) {
    CreateAndAttachConsole();
  }

  // Initialize COM, so that it is available for use in the library and/or
  // plugins.
  ::CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

  flutter::DartProject project(L"data");

  // ★★ 关掉 Impeller，回到 Skia 渲染器 ★★
  //
  // ## 为什么（2026-10-18 真实用户反馈）
  // 用户在**另一台电脑**装好安装程序后启动：进程活着、内存 130MB、**窗口全白** ✗
  // 日志里唯一可疑的一行：
  //   [IMPORTANT:...embedder_surface_gl_impeller.cc(126)]
  //     Using the Impeller rendering backend (OpenGLESSDF).
  // ⇒ Impeller 在部分显卡 / 驱动组合下渲染不出任何东西 ✗
  //
  // ## 为什么之前那些办法都没用（都实测过 ✗）
  //   flutter build windows --no-enable-impeller  → CI 报 "Could not find an option"
  //   exe --no-enable-impeller / --disable-impeller / --enable-impeller=false → 仍用 Impeller
  //   FLUTTER_IMPELLER=false / 0（环境变量）      → 仍用 Impeller
  //   钉 Flutter 3.27.4                          → pub get 连环失败（依赖要新 Dart）
  //
  // ## 正确的位置就是这里（官方 opt-out ✓）
  //   macOS  : `FLTEnableImpeller=false` in Info.plist
  //   Windows: `project.set_impeller_switch(flutter::ImpellerSwitch::Disabled)`  ← 本行
  //   Linux  : `fl_dart_project_set_enable_impeller(project, FALSE)`
  //
  // 代价：用不上 Impeller 的新特性（SDF 文字、无 shader jank ✗）
  // —— 但**稳定压倒一切**：白屏的机器根本用不了 ✓
  project.set_impeller_switch(flutter::ImpellerSwitch::Disabled);

  std::vector<std::string> command_line_arguments =
      GetCommandLineArguments();

  project.set_dart_entrypoint_arguments(std::move(command_line_arguments));

  FlutterWindow window(project);
  Win32Window::Point origin(10, 10);
  Win32Window::Size size(1280, 720);
  if (!window.Create(L"c001apk-flutter", origin, size)) {
    return EXIT_FAILURE;
  }
  window.SetQuitOnClose(true);

  ::MSG msg;
  while (::GetMessage(&msg, nullptr, 0, 0)) {
    ::TranslateMessage(&msg);
    ::DispatchMessage(&msg);
  }

  ::CoUninitialize();
  return EXIT_SUCCESS;
}
