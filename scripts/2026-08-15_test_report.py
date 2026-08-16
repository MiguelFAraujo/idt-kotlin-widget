#!/usr/bin/env python3
# registro: 2026-08-15 | autor: miguel | modelo: opencode/big-pickle
# Gera relatorio HTML consolidado das etapas de teste (unit JVM + instrumented sandbox)
# Origem: pedido de TDD com relatorios e etapas de teste bem arquitetadas
# Continua: rodar apos testDebugUnitTest e connectedDebugAndroidTest

import glob
import html
import re
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UNIT_XML = sorted(glob.glob(str(ROOT / "app/build/test-results/testDebugUnitTest/*.xml")))
INST_XML = sorted(glob.glob(str(ROOT / "app/build/outputs/androidTest-results/connected/debug/TEST*.xml")))

ETAPAS = [
    {
        "nome": "Etapa 1 - Unit (JVM)",
        "desc": "Logica pura e ViewModels sem dispositivo (coroutines-test, org.json real)",
        "arquivos": UNIT_XML,
        "classe": "unit",
    },
    {
        "nome": "Etapa 2 - Instrumented (sandbox)",
        "desc": "Integra com Context/SharedPreferences reais no emulador idt-sandbox",
        "arquivos": INST_XML,
        "classe": "inst",
    },
]

def parse_suite(path):
    s = Path(path).read_text()
    suite = re.search(r'<testsuite\s+name="([^"]+)"[^>]*>', s)
    if not suite:
        return None
    name = suite.group(1)
    def attr(k, d=0):
        m = re.search(rf'{k}="(\d+)"', s)
        return int(m.group(1)) if m else d
    tests, fail, err, skip = (attr("tests"), attr("failures"), attr("errors"), attr("skipped"))
    casos = []
    for m in re.finditer(r'<testcase\s+name="([^"]+)"[^>]*(?:/>|>(.*?)</testcase>)', s, re.S):
        body = m.group(2) or ""
        status = "FAIL" if "<failure" in body else ("ERR" if "<error" in body else "PASS")
        casos.append((m.group(1), status))
    return {
        "name": name,
        "tests": int(tests),
        "fail": int(fail),
        "err": int(err),
        "skip": int(skip),
        "casos": casos,
    }
def gerar():
    linhas = []
    total_t = total_f = total_e = 0
    for etapa in ETAPAS:
        suítes = [parse_suite(p) for p in etapa["arquivos"]]
        suítes = [x for x in suítes if x]
        t = sum(x["tests"] for x in suítes)
        f = sum(x["fail"] for x in suítes)
        e = sum(x["err"] for x in suítes)
        total_t += t; total_f += f; total_e += e
        color = "ok" if f + e == 0 else "bad"
        linhas.append(f'<section class="etapa {etapa["classe"]}">')
        linhas.append(f'<h2>{etapa["nome"]} <span class="badge {color}">{t} testes, {f} falhas, {e} erros</span></h2>')
        linhas.append(f'<p>{etapa["desc"]}</p>')
        for su in suítes:
            cls = "suite-pass" if su["fail"] + su["err"] == 0 else "suite-fail"
            linhas.append(f'<details class="{cls}" {"open" if su["fail"]+su["err"] else ""}><summary>{html.escape(su["name"])} ({su["tests"]} testes)</summary><table>')
            for nome, st in su["casos"]:
                linhas.append(f'<tr class="{st.lower()}"><td>{st}</td><td>{html.escape(nome)}</td></tr>')
            linhas.append("</table></details>")
        linhas.append("</section>")

    agora = datetime.now().strftime("%d/%m/%Y %H:%M")
    cor = "ok" if total_f + total_e == 0 else "bad"
    doc = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8"><title>Relatorio de Testes - IDT Widget</title>
<style>
body{{font-family:system-ui,sans-serif;margin:2rem;background:#fafafa;color:#222}}
h1{{font-size:1.4rem}} h2{{font-size:1.1rem;margin-top:2rem}}
.badge{{padding:.2rem .6rem;border-radius:999px;font-size:.8rem;color:#fff}}
.ok{{background:#2e7d32}}.bad{{background:#c62828}}
.suite-pass summary{{color:#2e7d32;cursor:pointer}}
.suite-fail summary{{color:#c62828;cursor:pointer}}
table{{border-collapse:collapse;margin:.5rem 0;width:100%;max-width:800px}}
td{{border:1px solid #ddd;padding:.25rem .6rem;font-size:.85rem}}
tr.pass{{background:#e8f5e9}} tr.fail,tr.err{{background:#ffebee}}
.resumo{{font-weight:bold;padding:.8rem;border-radius:8px}}
.resumo.ok{{background:#e8f5e9;border:1px solid #2e7d32}}
.resumo.bad{{background:#ffebee;border:1px solid #c62828}}
footer{{margin-top:2rem;color:#777;font-size:.8rem}}
</style></head>
<body>
<h1>Relatorio de Testes - IDT Widget</h1>
<div class="resumo {cor}">Total: {total_t} testes | {total_f} falhas | {total_e} erros</div>
{chr(10).join(linhas)}
<footer>Gerado em {agora} por scripts/2026-08-15_test_report.py</footer>
</body></html>"""
    out = ROOT / "build/reports/tests-report.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(doc)
    print(f"{total_t} testes | {total_f} falhas | {total_e} erros -> {out}")

if __name__ == "__main__":
    gerar()
