# IDT Status Widget

> Registro: 2026-08-15 · Autor: Miguel Araujo · Modelo: opencode/big-pickle
> Estado: ativo

Monitor autônomo e completo de serviços para homelabs — painel animado, histórico de uptime, gráficos de latência, utilidades de rede, widget de tela inicial e alertas automáticos. Referência de monitoramento self-hosted.

## Funcionalidades

- **Dashboard animado** — anel de uptime com progressão animada, cartões por serviço com status em tempo real (online/offline), latência e sparkline de uptime.
- **Histórico local** — janela deslizante de até 300 amostras por endpoint (persistido), uptime % e latência média derivados automaticamente.
- **Gráficos** — linha de latência média com gradiente e média pontilhada; tira de uptime por serviço.
- **Motor de verificação de 3 subprocessos + 5 rodadas de auth** — TCP probe → HTTP probe → rodadas R2 (Basic), R3 (Bearer), R4 (WebDAV PROPFIND), R5 (X-IDT-Token).
- **Alertas autônomos** — notifica quando um serviço cai ou volta (deduplicado por endpoint, estado persistido).
- **Utilidades de rede** — scan de portas comuns, resolução DNS, latência HTTP, exportação de configuração JSON e compartilhamento de relatório.
- **Widget de tela inicial** — atualização periódica via WorkManager (mín. 15 min) + toque para refresh manual.
- **Auto-atualização** — verificação diária de `update.json` (GitHub Releases), notificação e banner in-app com download direto.

## Arquitetura

```
app/src/main/java/com/idt/widget/
├── data/
│   ├── remote/
│   │   ├── ServiceChecker.kt       # motor de verificação (TCP/HTTP/auth)
│   │   ├── ServiceCatalog.kt       # catálogo default do IDT-Lab
│   │   ├── UpdateChecker.kt        # manifesto de versão (update.json)
│   │   └── DiagnosticsTool.kt      # scan de portas, DNS, latência
│   ├── history/
│   │   ├── HistoryStore.kt         # motor puro (rolling window, uptime)
│   │   └── PersistentHistoryRepository.kt
│   ├── local/ConfigDataSource.kt   # SharedPreferences config
│   ├── ServiceRepositoryImpl.kt    # endpoints + persistência
│   └── model/
│       ├── ServiceModels.kt
│       ├── AppConfig.kt
│       └── UpdateInfo.kt
├── domain/repository/              # interfaces (Service, History)
├── ui/
│   ├── dashboard/                  # painel + cards + ViewModel
│   ├── endpoints/                  # CRUD de endpoints
│   ├── addendpoint/
│   ├── settings/
│   ├── diagnostics/                # utilidades de rede
│   └── view/                       # Views custom (ring, charts, strip)
├── util/
│   ├── NotificationHelper.kt       # notificação de update
│   └── AlertNotifier.kt            # alertas de queda/retorno
└── widget/
    ├── StatusWidgetProvider.kt
    ├── WidgetRefreshWorker.kt
    ├── UpdateCheckWorker.kt
    ├── StatusData.kt               # cache síncrono para o widget
    └── WidgetScheduler.kt          # WorkManager
```

## Requisitos

- JDK 17 (`/home/miguel/jdk17` ou `temurin:17`)
- Android SDK: `compileSdk 35`, `minSdk 26`, `targetSdk 35`
- Gradle 8.11.1

## Build

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleDebug              # APK de debug
./gradlew assembleRelease            # APK de release (unsigned)
./gradlew testDebugUnitTest          # testes unitários
./gradlew bundleRelease              # AAB (futuro Play Store)
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Instalação

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# ou: copie o APK e instale via "fontes desconhecidas"
```

Adicione o widget "IDT Status" à tela inicial segurando a área livre > Widgets.

## Auto-atualização

O app verifica `updates/update.json` (raw no GitHub) uma vez por dia. O manifesto aponta para o APK assinado publicado em **GitHub Releases**. Ao publicar uma nova versão:

1. Atualize `versionCode`/`versionName` em `app/build.gradle.kts`.
2. Commit + push.
3. Crie a tag `vX.Y.Z` — o workflow `Build & Release` gera o APK e a release automaticamente:

```bash
git tag v2.0.0
git push origin v2.0.0
```

O workflow também gera o `update.json` correspondente na release. O app detecta a versão nova, mostra banner no painel e notifica em segundo plano.

## Distribuição

- **Canal principal**: GitHub Releases + `update.json` (self-hosted, open source, sem custo).
- **Futuro**: Play Store via `bundleRelease` (AAB) + Play App Signing; F-Droid requer build reproduzível.

## Testes

13 testes unitários (motor de histórico, catálogo, checker de serviços, ViewModel):

```bash
./gradlew testDebugUnitTest
```

## Roadmap

- [ ] Assinatura de release (keystore) + workflow assinado
- [ ] Tela de detalhe por serviço (histórico completo + gráfico)
- [ ] Suporte a HTTPS self-signed e TLS custom
- [ ] Widget com gráfico mini de latência
- [ ] Publicação na Play Store (AAB + fastlane)

## Licença

MIT — veja [LICENSE](LICENSE).

---

Origem: decisão 2026-08-15 (widget APK nativo → monitor completo e autônomo)
Continua: assinatura de release + Play Store (híbrido GitHub Releases + update.json)
