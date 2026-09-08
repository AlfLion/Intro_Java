# Nesting PRO — Handoff de continuidade (sessão 2026-09-07)

Tudo abaixo está commitado e pushado em `AlfLion/Intro_Java`, branch
`claude/nesting-2d-irregular-engine-m2x79f`.

## Atualização (mesma sessão, depois do primeiro handoff)

Sessão continuou (mesma conta) depois do primeiro handoff abaixo. Adicionado
desde então, tudo commitado:
- `SheetPacker.packBestOrientation`: testa a chapa deitada e em pé, fica
  com a melhor. Validado contra o fixture 15746A.DXF na chapa real
  1095×914mm: deitada deu **106 peças, número EXATO** do Layout A manual
  do usuário; em pé deu 92 (vs 98 manual — mesma limitação de "sobra como
  retângulo" abaixo). Escolheu a deitada corretamente.
- `SpatialIndex` (grade uniforme) + busca em geometria simplificada no
  `SheetPacker`: reduz o custo da busca, mas a validação final em
  resolução plena continua sendo o custo dominante pra peças com muitos
  vizinhos genuinamente próximos (~10-11s pros 646 do bracket) — registrado
  como limitação conhecida, não resolvido de verdade ainda.
- Limitação nova encontrada e documentada: o reaproveitamento de sobra
  (`SheetPacker`, item já existente) trata a sobra como retângulo; só
  funciona quando os 2 vetores de rede da receita ficam quase
  perpendiculares. Bracket com "cola-por-aresta" tem 2º vetor diagonal →
  reaproveitamento fica em 0 (conservador, não erra, só não aproveita).

Próximo passo natural: tratar a sobra como polígono (resolveria o caso
acima) OU void-filling automático (peça pequena no vão, tipo ENCAKIT) OU
resolver o custo da validação final em resolução plena de vez (reduzir
discretização de arco, ou trocar por teste de colisão mais esperto que
segmento-a-segmento bruto). Nenhum desses começado ainda.

## Método de trabalho (vale pra toda a frente Nesting PRO)

- Só lógica/matemática do encaixe aqui. Sem gerar HTML a cada mensagem, sem
  retrabalhar Retângulo/Círculo/estética do app Básico.
- O usuário ensina o que faz manualmente no CAD (manda DXF de peça isolada
  e/ou já nesteada); a IA traduz em regra de cálculo, valida contra
  geometria real (nunca confiar em aproximação sem checar), e só então
  formaliza em código.
- Deepnest/SVGnest: absorver o que já resolvem, mas **melhorar**, não só
  portar (o usuário já testou o Deepnest: "bom, mas não excelente").
- **Decisão de stack**: começamos em Java neste repo (`AlfLion/Intro_Java`).
  Foi cogitado migrar pra JavaScript no repo `AlfLion/Nesting` (pra
  eventualmente juntar com o app Básico, que é JS puro client-side) — usuário
  disse "vou deixar ao seu critério" e a sessão simplesmente continuou em
  Java sem mais discussão. **Isso ainda é uma decisão em aberto**: o motor
  Java de hoje é sólido e testado, mas juntar com o Básico algum dia vai
  exigir portar pra JS (ou o Básico virar Java/rodar via backend — não
  decidido). Não é bloqueante pra continuar evoluindo o motor.

## Onde está o código (tudo em `src/main/java/br/com/iterasys/nesting/`)

```
geometry/
  Point2D, Polygon, PieceGeometry   - primitivas
  GeometryOps                        - colisao/separacao real (poligono x poligono
                                        E conjunto x conjunto), centroide,
                                        simplify() (Douglas-Peucker, p/ busca rapida)
dxf/
  DxfParser                          - LINE/ARC/CIRCLE/LWPOLYLINE(+bulge), unidades,
                                        encadeamento por extremidade -> contorno+furos.
                                        parseAllLoops() le DXF ja nesteado (varias pecas)
audit/
  NestedKitAuditor                   - casa instancias de um DXF ja nesteado contra
                                        pecas canonicas (rotacao+espelho+posicao),
                                        multi-tipo (kit: peca grande + peca pequena)
engine/
  RowFitOptimizer                    - busca de angulo livre p/ auto-fileira (1a versao,
                                        superada pelo StrategySelector mas ainda funciona)
  OrientationAligner                 - alinha aresta/vetor mais longo a eixo da chapa
                                        (snapToAxisDelta = utilitario generico)
strategy/                            - NUCLEO ATUAL DO MOTOR
  PlacedPieceInstance                - mirror + rotacao(em torno do centroide) + translacao
  LatticeFinder                      - dada 1+ pecas ja posicionadas (uma "celula"), acha
                                        os 2 vetores que ladrilham o plano minimizando
                                        area/peca. CUIDADO: precisa validar v1+v2 e v1-v2
                                        (vizinhos diagonais), nao so v1 e v2 isolados -
                                        bug real ja corrigido aqui, documentado no codigo.
  PairGenerator (interface)          - um "mecanismo" de encaixe propoe celulas candidatas
  SingleOrientationGenerator         - so gira a peca sozinha (piso de comparacao)
  CentroidPairGenerator              - interlock theta/theta+180 em torno do centroide
                                        (testa espelho tambem, sinaliza usesMirror)
  EdgeGluePairGenerator              - gira 180 em torno do MEIO DE UMA ARESTA RETA
                                        (descoberta nova desta sessao - ver abaixo)
  NestingRecipe                      - resultado de 1 candidato: celula + v1 + v2 + area/peca
  StrategySelector                   - roda os 3 geradores, avalia com LatticeFinder,
                                        ordena por mm^2/peca, separa vencedor sem espelho
packing/
  SheetPacker                        - pega a receita vencedora, aplica OrientationAligner,
                                        ladrilha numa chapa WxH com margem, valida geometria
                                        real (bbox + colisao) antes de aceitar cada peca
validation/                          - demos runnaveis, servem de regressao
  DxfPipelineRegressionCheck         - parser generico + RowFitOptimizer vs fixture 15746A
  StrategySelectionDemo              - StrategySelector vs fixtures TRAP e BRACKET_COMPACTO
  SheetPackingDemo                   - pipeline completo (StrategySelector+Aligner+Packer)
                                        vs os mesmos 2 fixtures, com validacao final real
```

Fixtures reais em `src/main/resources/fixtures/`: `15746A.DXF` (peca anel
curvo), `TRAP.DXF` (trapezio simples), `BRACKET_COMPACTO.DXF` (peca com 3
furos, ~35% ocupacao de bbox). Todos os `validation/*Demo*.java` rodam
direto contra esses fixtures via `getResourceAsStream` — **como testar**
(sem Gradle configurado com dependencias, compilar com javac puro):

```bash
find src/main/java -name "*.java" | xargs javac -d /tmp/out
cp -r src/main/resources/* /tmp/out/
java -cp /tmp/out br.com.iterasys.nesting.validation.SheetPackingDemo
java -cp /tmp/out br.com.iterasys.nesting.validation.StrategySelectionDemo
java -cp /tmp/out br.com.iterasys.nesting.validation.DxfPipelineRegressionCheck
```

## O que foi PROVADO nesta sessão (com validação geométrica real, não só matemática)

1. **Parser DXF genérico** (LINE/ARC/CIRCLE/LWPOLYLINE+bulge, unidades,
   contorno+furos, múltiplas peças por arquivo) — testado contra 7+ DXFs
   reais do usuário sem nenhum ajuste específico de peça.
2. **`StrategySelector` "entende a peça sozinho"**: em vez de classificar o
   tipo de peça, gera candidatos por 3 mecanismos diferentes e escolhe pelo
   número. Peça revela sozinha qual mecanismo serve:
   - Trapézio → `cola-por-aresta` vence com **98,2% líquido** (bate com a
     análise analítica: colar 2 trapézios pela perna forma um paralelogramo
     ~100% teórico antes do gap).
   - Bracket com 3 furos → `cola-por-aresta` vence com **70,7% líquido**,
     **superando os 55,6% que o próprio usuário conseguiu manualmente**
     (técnica θ/θ+180° no ENCAIXE1.DXF dele) — o motor achou uma aresta da
     concavidade que a receita manual não usou. Validado com 0 colisões
     reais.
3. **`SheetPacker` fecha o pipeline**: peça → estratégia → alinhamento →
   chapa real com margem/gap. Testado: bracket em 914×1010mm → 646 peças,
   64,3% líquido, 0 colisões em 208.335 pares (resolução plena). Trapézio
   em 1200×2200mm → 10 peças, 60,8% (chapa pequena relativa à peça — subiu
   pra 72,3% numa chapa 3200×4200, confirmando que é desperdício de borda
   real, não bug).
4. **Descoberta nova (não veio do usuário, o motor achou sozinho)**:
   mecanismo "cola-por-aresta" — girar uma cópia 180° em torno do ponto
   médio de uma aresta reta qualquer da peça. Zero-desperdício teórico pra
   trapézios, e supera a técnica manual do usuário no bracket. Funciona
   pra qualquer peça com pelo menos 1 lado reto, côncava inclusive.

## Bugs reais encontrados e corrigidos nesta sessão (documentar evita repetir)

- **Pivô errado pra θ/θ+180°**: girar em torno do canto do DXF (bbox
  origin) em vez do centroide da peça faz duas orientações "nascerem" sem
  nenhuma sobreposição (peça fina ocupa só uma fatia angular do círculo em
  torno do canto). Sempre pivotar no centroide (`GeometryOps.centroid`).
- **`LatticeFinder` sem validar vizinhos diagonais**: aceitar v1 e v2
  válidos isoladamente não garante rede física válida — v1+v2 e v1−v2
  também precisam ser posições sem colisão, senão dá "aproveitamento" de
  milhares de %. Corrigido, ver `LatticeFinder.diagonalNeighborsValid`.
- **Performance**: a discretização fina de arco do parser gera 700-900
  vértices por peça complexa. Buscas (LatticeFinder, geradores) rodam
  sobre uma versão **simplificada** (Douglas-Peucker, `GeometryOps.simplify`,
  epsilon ~0.4mm) — a peça de resolução plena só entra de novo na validação
  final do candidato vencedor.

## Regras de negócio ainda pendentes (nenhum código toca nisso ainda)

- **Espelhamento nunca automático** (regra de negócio 3): o motor já
  detecta e sinaliza (`StrategySelector.Result.mirrorPending()`), mas não
  existe fluxo de aprovação do usuário — quem chama hoje sempre usa o
  `bestNoMirror`. Falta decidir a UX de "perguntar" quando isso importar.
- **Furos / void-filling (part-in-part)**: `PieceGeometry.holes` existe,
  `NestedKitAuditor` já lê kits com peça-grande+peça-pequena-no-vão, mas o
  `StrategySelector`/`SheetPacker` ainda não tentam automaticamente meter
  peça extra no vão que a receita vencedora deixa.

## Roadmap — próximos passos (nenhum começado)

1. **Reaproveitar a sobra de borda** com a receita girada 90/180/270°
   (como o Layout C manual da 15746A) — é o que mais rapidamente fecha a
   diferença entre os 60-70% líquido do `SheetPacker` hoje e o ~98%
   teórico da célula isolada.
2. **Void-filling automático**: detectar o vão que a receita vencedora
   deixa (ex.: entre 2 peças coladas pela aresta) e tentar encaixar peça
   menor do kit lá dentro — generaliza o `NestedKitAuditor`/lógica de
   "vãos" do app Básico pra qualquer geometria via NFP real.
3. **Performance**: validação de colisão é O(n²) hoje (aceitável até
   centenas de peças, ~10s pros 646 do bracket) — vai precisar de
   indexação espacial (grid/quadtree) se crescer muito mais.
4. **Múltiplas orientações de chapa** (deitada vs em pé, como Layout A vs C
   da 15746A) — hoje `SheetPacker.pack` recebe W,H fixos, não testa as duas.
5. **Fluxo de aprovação de espelho** (UX, não só o sinalizador que já existe).
6. **Peça com furo interno testado de verdade** (part-in-part propriamente
   dito, não só furo-de-parafuso pequeno demais pra aproveitar) — nenhum
   exemplo do usuário até agora tinha isso.
7. Decidir a migração Java→JS (ou não) pra integrar com o app Básico.

## Exemplos de peças reais já estudados (todos no histórico do git, alguns
não comitados como fixture — pedir de novo ao usuário se precisar)

- `15746A` — anel curvo fino (5,7% da bbox). Fixture comitada.
- `ORIGINAL/ENCAIXE1/ENCAIXE2` — bracket compacto com 3 furos (35% bbox).
  Fixture comitada como `BRACKET_COMPACTO.DXF`. ENCAIXE1/2 (peça já
  nesteada, não comitados) deram a receita manual θ=125,5°/305,5° a 55,6%
  líquido — superada pelo motor (70,7%, cola-por-aresta).
- `OUTRAPRT` — bracket em L maior, assimétrico, ângulo ótimo não-redondo
  (76°). Não comitado como fixture ainda.
- `ENCAKIT` — kit peça-grande + peça-pequena-no-vão, 60,7% líquido medido
  manualmente via auditoria. Não comitado como fixture ainda.
- `TRAP/TRAPENC` — trapézio isósceles simples, sem furo. Fixture comitada
  como `TRAP.DXF`. TRAPENC (par já colado e alinhado manualmente pelo
  usuário, 3,3° de rotação) confirmou o `OrientationAligner` quase exato
  (motor sugere -3,348°, usuário girou +3,3° — mesma magnitude).

Se quiser mais fixtures de regressão permanentes, os DXFs originais estão
só no histórico de upload da sessão anterior — peça pro usuário reenviar
`OUTRAPRT.DXF` e `ENCAKIT.DXF` se for útil.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018yahHKc54rrjdTqBwbrQzM
