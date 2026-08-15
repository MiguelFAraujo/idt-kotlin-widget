# Projeto: IDT-Kotlin-Widget
# registro: 2026-08-15 | autor: miguel | modelo: omniroute/gpt-4o-mini

## Stack
- Kotlin + Gradle (Kotlin DSL)
- Min SDK 26, Target SDK 35
- AppWidgetProvider + CoroutineWorker

## Estrutura
app/
  src/main/
    AndroidManifest.xml
    java/com/idt/widget/
      StatusWidgetProvider.kt
      StatusWorker.kt
      StatusConfig.kt
    res/
      xml/status_widget_info.xml
      layout/status_widget.xml

## Datasource
- Poll HTTP endpoints locais:
  - Prometheus http://127.0.0.1:9091/api/v1/query?query=up
  - Netdata http://127.0.0.1:19999/api/v1/data
- Fallback: ping TCP simples nas portas conhecidas

## Build
- ./gradlew assembleDebug
- adb install app/build/outputs/apk/debug/app-debug.apk

## TODO
- [ ] Instalar JDK 17
- [ ] Instalar Android SDK cmdline-tools + platform 35
- [ ] Bootstrappar projeto Gradle
- [ ] Implementar StatusWorker
- [ ] Implementar widget layout
- [ ] Testar em dispositivo

Origem: decisao 2026-08-15 (widget APK nativo)
Continua: setup do toolchain + scaffold do projeto
