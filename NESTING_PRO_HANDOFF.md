# Nesting PRO — Handoff de continuidade

Backup de continuidade. Tudo abaixo está commitado e pushado em
`AlfLion/Intro_Java`, branch `claude/nesting-2d-irregular-engine-m2x79f`,
até o commit `09cd927`.

## Método de trabalho (vale pra toda a frente Nesting PRO)

- Só lógica/matemática do encaixe aqui. Sem gerar HTML a cada mensagem, sem
  retrabalhar Retângulo/Círculo/estética do app Básico.
- O usuário ensina o que faz manualmente no CAD (manda DXF de peça isolada
  e/ou já nesteada); a IA traduz em regra de cálculo, valida contra
  geometria real (nunca confiar em aproximação sem checar), e só então
  formaliza em código.
- Deepnest/SVGnest: absorver o que já resolvem, mas **melhorar**, não só
  portar (usuário já testou o Deepnest: "bom, mas não excelente").
- Seguir as âncoras/diretrizes já existentes no app Básico quando
  aplicável — ex.: o padrão "calcularDicaLimpa" +
  "abrirModalBuscaCompletaKit"/"autorizarBuscaCompletaKit": mostrar um
  número rápido primeiro, só rodar a busca cara se o usuário topar
  esperar. Já aplicado no motor (`SheetPacker.estimate`) e na bancada.
- **Stack**: motor em Java neste repo. Existe também um porte 1:1 em
  JavaScript (`tools/bancada.html`) só pra teste interativo em navegador —
  a fonte de verdade é sempre o Java em `src/main/java`; mudanças de lógica
  lá precisam ser replicadas manualmente na bancada (não há sincronia
  automática). Decisão de unificar com o app Básico (que é JS puro) ainda
  em aberto, não bloqueante.

## RESUMO DO QUE JÁ FOI FEITO

### 1. Geometria e parsing (`geometry/`, `dxf/`)
- `Point2D`, `Polygon`, `PieceGeometry`: primitivas.
- `GeometryOps`: colisão real polígono×polígono e conjunto×conjunto
  (`overlaps`, `overlapsAny`), distância de contato por varredura direcional
  (`minSeparation`, `minSeparationAny`), centroide real (area-ponderado),
  `simplify()` (Douglas-Peucker, pra buscas rápidas sem perder precisão
  onde importa).
- `DxfParser`: LINE/ARC/CIRCLE/LWPOLYLINE(+bulge), trata unidades
  ($INSUNITS), encadeia entidades soltas por extremidade em contorno(s)
  fechado(s), separa maior laço (externo) dos furos. `parseAllLoops()` lê
  um DXF já nesteado (múltiplas peças) sem presumir "peça única".
- `NestedKitAuditor`: audita um DXF já nesteado contra peças canônicas
  conhecidas — descobre rotação/espelho/posição de cada instância
  automaticamente, inclusive kits com peça grande + peça pequena no vão.

### 2. Motor de decisão — "a peça escolhe a estratégia" (`strategy/`)
Em vez de um classificador que adivinha o tipo de peça, gera candidatos por
3 mecanismos diferentes e escolhe pelo número (mm²/peça):
- `SingleOrientationGenerator`: orientação única (piso sempre disponível).
- `CentroidPairGenerator`: interlock θ/θ+180° em torno do centroide
  (testa espelho também, sinaliza sem aplicar).
- `EdgeGluePairGenerator`: cola por aresta reta — gira 180° em torno do
  ponto médio de uma aresta. Descoberta desta sessão (não veio do
  usuário): zero-desperdício teórico em trapézios, e **supera a receita
  manual do usuário** no bracket (70,7% vs. 55,6%).
- `LatticeFinder`: dado 1+ peças já posicionadas (uma "célula"), acha os 2
  vetores que a repetem pelo plano minimizando área/peça. Valida os
  vizinhos diagonais (v1+v2, v1−v2), não só v1/v2 isolados — bug real
  encontrado e corrigido (dava >2000% de "aproveitamento").
- `StrategySelector`: roda os 3 geradores, avalia com `LatticeFinder`,
  ordena por mm²/peça, separa vencedor sem espelho do vencedor geral
  (trava de espelhamento: nunca aplica automaticamente).

### 3. Alinhamento e empacotamento (`engine/`, `packing/`)
- `OrientationAligner`: alinha a aresta/vetor mais longo a um eixo da
  chapa — validado contra exemplo real do usuário (TRAPENC.DXF): sugere
  −3,348°, usuário girou manualmente +3,3°.
- `SpatialIndex`: grade uniforme pra acelerar colisão (evita testar contra
  TODOS os já aceitos).
- `SheetPacker`:
  - `pack()` / `packBestOrientation()`: busca completa — melhor receita +
    alinhamento + reaproveitamento de sobra girada 90/180/270° + validação
    final em RESOLUÇÃO PLENA (nunca confia só na busca simplificada).
  - `estimate()` / `estimateBestOrientation()`: prévia rápida por área,
    sem desenhar posição nenhuma — ~20× mais rápido, zero risco de
    colisão no preview (ver lição abaixo).
- `VoidFiller.fillVoids()`: aproveitamento de vãos — varredura em grade
  (não rede) tentando encaixar uma peça pequena diferente nos espaços que
  a receita da peça grande deixou, gulosa mas com colisão real validada
  a cada aceite. Ver item 1 do "falta fazer" pra limitações conhecidas.

### 4. Validação — tudo com geometria real, não só matemática
- `DxfPipelineRegressionCheck`, `StrategySelectionDemo`, `SheetPackingDemo`:
  demos runnáveis contra fixtures reais em `src/main/resources/fixtures/`
  (`15746A.DXF`, `TRAP.DXF`, `BRACKET_COMPACTO.DXF`).
- Validações fortes conseguidas:
  - Trapézio: `cola-por-aresta` → 98,2% líquido (bate com a análise
    analítica: colar 2 trapézios pela perna forma paralelogramo ~100%
    teórico antes do gap).
  - Bracket: `cola-por-aresta` → 70,7% líquido, **supera os 55,6%** que o
    usuário conseguiu manualmente (θ/θ+180° no ENCAIXE1.DXF dele).
  - 15746A: `packBestOrientation` na chapa real 1095×914mm → **106 peças,
    número EXATO** do Layout A manual do usuário.
  - Reaproveitamento de sobra corrigido: bracket 646→648, trapézio numa
    chapa grande 61→71 (72,3%→84,2%).
  - Todas as validações acima confirmadas com **zero colisões reais** em
    resolução plena (não só na busca aproximada).

### 5. Bugs reais encontrados e corrigidos nesta sessão
1. **Pivô errado pra θ/θ+180°**: girar em torno do canto do DXF em vez do
   centroide faz duas orientações "nascerem" sem sobreposição nenhuma.
2. **`LatticeFinder` sem validar vizinhos diagonais**: v1/v2 válidos
   isoladamente não bastam.
3. **Reaproveitamento de sobra tratado como retângulo**: só funcionava com
   vetores de rede quase perpendiculares. Corrigido escaneando a área útil
   inteira (a colisão real já filtra) em vez de tentar adivinhar a forma
   da sobra.
4. **"Modo rápido" que desenhava posições aproximadas**: deu 500 colisões
   reais em 544 peças no bracket. Lição: um preview que desenha posição
   (mesmo aproximada) promete implicitamente "essas peças não se tocam" —
   só pode prometer isso em resolução plena. Corrigido trocando por
   `estimate()`, que não desenha nada (só conta por área), então não tem
   essa promessa pra quebrar.

### 6. Ferramenta de teste interativa
- `tools/bancada.html`: porte 1:1 do motor em JavaScript puro, testado
  contra os mesmos fixtures via Node antes de publicar (números batem
  exatos com o Java). Fluxo em 2 passos: **Estimar** (prévia instantânea)
  → **Buscar encaixe completo** (busca real, só depois de confirmar).
  Publicado como Artifact: https://claude.ai/code/artifact/97babc63-34c9-4241-b06f-0cb9c561431d

## DETALHAMENTO DO QUE FALTA FAZER

1. ~~Void-filling automático~~ **FEITO** (mecanismo genérico, ainda sem
   validação contra kit real): `packing/VoidFiller.fillVoids(...)` — varredura
   em grade (não rede/lattice, porque o vão não tem forma regular) testando
   algumas rotações fixas em cada ponto, aceita o primeiro encaixe sem
   colisão real (contra as peças grandes E contra as pequenas já aceitas)
   e segue pro próximo ponto. Guloso, não ótimo, mas correto — validado
   com `VoidFillingDemo` (peça pequena sintética 6×6mm nos vãos do
   BRACKET_COMPACTO.DXF: 840 encaixadas, 0 colisões, aproveitamento
   64,5%→67,8%). **Limitação conhecida**: não aplica `gapMm` como folga
   entre peça pequena e peça grande (só garante zero sobreposição real,
   que é sempre seguro, mas pode encostar sem vão nenhum) — documentado
   no Javadoc da classe. Falta: nenhum exemplo real de kit (ENCAKIT.DXF)
   foi testado ainda — ver item 3 abaixo.

2. ~~Regra de negócio do espelhamento — falta o fluxo, não a detecção~~
   **FEITO no motor e na bancada**: `StrategySelector.Result.chosen(mirrorAuthorized)`
   + `mirrorGainPct()` (Java) e o equivalente em `packSheet(...,
   mirrorAuthorized)` (JS) — sem autorização explícita sempre usa a receita
   sem espelho; `SheetPacker.pack/estimate/packBestOrientation` ganharam
   sobrecarga com esse parâmetro. Na bancada.html já existe um botão real
   "Autorizar espelhamento e refazer busca" que só aparece quando
   `mirrorPending=true`. Falta só: nenhuma peça real testada até agora
   dispara `mirrorPending=true` (ver item 3), então o caminho autorizado
   nunca foi validado contra geometria real — só contra 9 casos sintéticos
   (Java) + os mesmos 9 replicados em JS, todos passando.

3. **Peça com furo interno de verdade (part-in-part)**. Nenhum exemplo do
   usuário até agora tinha um furo grande o bastante pra caber outra peça
   dentro — só furos de parafuso pequenos. Precisa de um exemplo novo pra
   testar isso de verdade.

4. **Performance da validação final em resolução plena**. Ainda é o custo
   dominante em peças com muitos vizinhos próximos (~13-14s pros 648 do
   bracket, ~5s por orientação da 15746A). Caminhos possíveis: reduzir a
   discretização de arco do parser (perde precisão), ou trocar o teste de
   colisão segmento-a-segmento bruto por algo mais esperto (ex.: SAT com
   decomposição convexa). Não resolvido ainda.

5. **Múltiplas configurações de chapa além de deitada/em pé**. Hoje
   `packBestOrientation` só testa as 2 orientações da MESMA chapa. Não
   testa, por exemplo, cortar a chapa em 2 tiras de tamanhos diferentes,
   nem combinar materiais (o "modo conjugado" que o app Básico já tem pra
   Retângulo/Círculo).

6. ~~Sincronizar `tools/bancada.html` com o motor Java~~ **FEITO**:
   `packSheet` agora tem `tileRegion`/`tryBestRotationInRegion` portados
   (reaproveitamento de sobra escaneando a área útil inteira, igual ao
   Java), e a trava de espelhamento (`mirrorAuthorized`) com botão real
   de autorização na UI. Verificado via Node contra as 3 fixtures: números
   batem exatos com o Java (bracket 648=646+2, trapézio 10 e 71=61+10,
   15746A 106), zero colisões. Ainda sem `SpatialIndex` (usa varredura
   linear) — no fixture do bracket isso levou ~37s em Node (mais lento que
   o Java, que usa grade espacial); aceitável pro uso interativo atual mas
   é o próximo alvo se performance virar problema real.

7. **Decisão de arquitetura Java→JS pendente** desde o início da sessão
   anterior — pra integrar de verdade com o app Básico (JS puro,
   client-side) algum dia, vai precisar ou portar o motor todo pra JS
   (a bancada.html é um começo, mas incompleto — ver item 6) ou o Básico
   virar Java/rodar via backend. Não decidido, não bloqueante pro que já
   foi feito.

## Peças de referência já estudadas
- `15746A` — anel curvo fino (5,7% da bbox). Fixture comitada.
- `ORIGINAL/ENCAIXE1/ENCAIXE2` — bracket compacto com 3 furos (35% bbox).
  Fixture comitada como `BRACKET_COMPACTO.DXF`.
- `OUTRAPRT` — bracket em L maior, assimétrico, ângulo ótimo não-redondo
  (76°). Não comitado como fixture ainda.
- `ENCAKIT` — kit peça-grande + peça-pequena-no-vão, 60,7% líquido medido
  manualmente via auditoria. Não comitado como fixture ainda.
- `TRAP/TRAPENC` — trapézio isósceles simples, sem furo. Fixture comitada
  como `TRAP.DXF`.

Se quiser mais fixtures de regressão permanentes, os DXFs originais
(OUTRAPRT, ENCAKIT) estão só no histórico de upload de sessões anteriores —
peça pro usuário reenviar se for útil.

## Como compilar e testar (sem Gradle configurado com dependências)

```bash
find src/main/java -name "*.java" | xargs javac -d /tmp/out
cp -r src/main/resources/* /tmp/out/
java -cp /tmp/out br.com.iterasys.nesting.validation.SheetPackingDemo
java -cp /tmp/out br.com.iterasys.nesting.validation.StrategySelectionDemo
java -cp /tmp/out br.com.iterasys.nesting.validation.DxfPipelineRegressionCheck
```

Ou abrir `tools/bancada.html` (ou o Artifact publicado) num navegador pra
testar visualmente com upload de DXF.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018yahHKc54rrjdTqBwbrQzM
