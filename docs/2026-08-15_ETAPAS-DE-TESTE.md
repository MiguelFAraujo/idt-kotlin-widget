# Etapas de Teste — IDT Widget

> Registro: 2026-08-15 · Autor: miguel · Modelo: opencode/big-pickle
> Estado: ativo

## Origem
Pedido de TDD com relatórios e etapas de teste bem arquitetadas, com tudo rodando no sandbox antes de tocar o ambiente real.

## Arquitetura em 3 etapas

| Etapa | Onde roda | Custo | Cobertura | Comando |
|-------|-----------|-------|-----------|---------|
| 1 — Unit (JVM) | Host, sem dispositivo | ~7s | Lógica pura + ViewModels (coroutines-test, org.json real) | `./gradlew testDebugUnitTest` |
| 2 — Instrumented (sandbox) | Emulador `idt-sandbox` | ~12s | Integração com Context/SharedPreferences reais | `./gradlew connectedDebugAndroidTest` |
| 3 — E2E (sandbox) | Emulador `idt-sandbox` via adb | manual | Fluxo completo do app + widget renderizado | ver `#Etapa 3` |

**Princípio**: todo build/execução acontece no sandbox. O ambiente real só recebe o release após Etapa 1 + 2 verdes.

## Etapa 1 — Unit (JVM) — 36 testes

- `ScanViewModelTest` (7): scan atualiza estado, toggle por id, `addSelected` deduplica (não duplica ao tocar 2x)
- `StatusWidgetProviderTest` (6): `statusText` contagem online, nomes online listados, offline limitado a 2 + sufixo `⛔+N`, todos online, sem offline
- `UpdateCheckerTest` (6): `parse` do manifest, campos ausentes, `isNewerThan` (versionName igual + versionCode maior = nova)
- `DashboardViewModelTest` (5): agrega contagem online, timestamp de atualização, conta só habilitados, uptime como fração, propaga update
- `ServiceCheckerTest` (4): portas/HOOK no dispositivo real (host local)
- `HistoryStoreTest` (6): histórico persistido em SharedPreferences
- `ServiceCatalogTest` (2): catálogo conhecido

**Decisões de arquitetura:**
1. `PortScanner` como interface + `PortScannerTcp` injetado — permite testar o `ScanViewModel` sem abrir sockets reais.
2. `statusText()` extraído como função pura estática em `StatusWidgetProvider` — testável sem Android framework.
3. `parse()` em `UpdateChecker` testável com `org.json:json` real no classpath JVM (o android.jar stuba `JSONObject` e lança em unit test).
4. ViewModels usam `CoroutineDispatcher` injetado (testDispatcher) + `StandardTestDispatcher`/`advanceUntilIdle`.

## Etapa 2 — Instrumented (sandbox) — 2 testes

- `StatusDataInstrumentedTest`: roundtrip `StatusData.write → read` preserva resultados/latência/mensagem em SharedPreferences reais; leitura sem dados retorna vazio.

**Cuidado de sandbox:** o androidTest usa APK de debug (assinatura própria). Se o release estiver instalado no emulador, `adb uninstall com.idt.widget` antes; após os testes, reinstalar o release.

## Etapa 3 — E2E (sandbox)

```bash
# 1. Build do release + instala no emulador sandbox
./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk

# 2. Abrir o app e validar fluxo de scan manualmente
adb shell am start -n com.idt.widget/.MainActivity

# 3. Adicionar widget e validar contagem real
#    (launcher + widget de status — os mesmos passos de 2026-08-15)
```

## Relatório

`python3 scripts/2026-08-15_test_report.py` consolida Etapas 1+2 num HTML único:
`build/reports/tests-report.html` (38 testes, 0 falhas em 2026-08-15).

## Continua
- Ampliar Etapa 3 para E2E automatizado (UiAutomator) no sandbox.
- Adicionar testes para atualização do widget (broadcast intent).
- CI: rodar Etapa 1 no host + Etapa 2 num emulador efêmero por PR.
