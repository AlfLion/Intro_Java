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
- `HullIndex`: como o `SpatialIndex`, mas testa o fecho convexo primeiro
  (barato, sem risco de falso negativo) antes do teste exato — usado só
  na validação final (ver item 4 do "falta fazer").
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
- `HoleFiller.fillHoles()`: part-in-part de verdade — encaixa uma peça
  pequena DENTRO do furo de cada instância aceita da peça grande (nunca
  gasta área extra da chapa). Ver item 3 do "falta fazer".
- `SheetPacker.packBestSplit()`: além de deitada/em pé, considera cortar a
  chapa em 2 tiras de tamanhos diferentes. Ver item 5 do "falta fazer".

### 4. Validação — tudo com geometria real, não só matemática
- `DxfPipelineRegressionCheck`, `StrategySelectionDemo`, `SheetPackingDemo`,
  `VoidFillingDemo`, `EncakitAnalysisDemo`, `RingKitDemo`, `SplitConfigDemo`:
  demos runnáveis contra fixtures reais em `src/main/resources/fixtures/`
  (`15746A.DXF`, `TRAP.DXF`, `BRACKET_COMPACTO.DXF`, `ENCAKIT.DXF`, `2CIRC.DXF`).
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

1. ~~Void-filling automático~~ **FEITO e validado contra kit real**:
   `packing/VoidFiller.fillVoids(...)` — varredura em grade (não rede/lattice,
   porque o vão não tem forma regular) testando algumas rotações fixas em
   cada ponto. Reescrito nesta etapa pra usar a mesma técnica de
   `SheetPacker#pack` (busca em geometria SIMPLIFICADA, validação final em
   resolução PLENA) depois que a primeira versão (testava resolução plena
   direto na busca) não terminava em tempo útil (>2min) contra o
   ENCAKIT.DXF real — a peça "grande" tem 919 vértices e a "média" 738
   (discretização fina de arco), testar milhares de posições de grade nessa
   resolução é inviável. Com a correção: **368ms** pro mesmo caso (antes
   não terminava). **Limitação conhecida**: não aplica `gapMm` como folga
   entre peça pequena e peça grande (só garante zero sobreposição real) —
   documentado no Javadoc.
   **Validado contra o ENCAKIT.DXF real do usuário** (ver `EncakitAnalysisDemo`,
   fixture agora commitada): peça grande (27 encaixadas, 33,6%) + peça
   média no vão (39 encaixadas, 385ms) → 48,7% total numa chapa 600×400mm,
   **0 colisões reais**. `NestedKitAuditor` confirmou que o padrão manual
   real do usuário é interlock θ/θ+180° sem espelho pras duas peças — e o
   motor, analisando cada peça sozinha, encontra `interlock-centroide` como
   2º colocado a **0,08% de distância** do vencedor (`cola-por-aresta`) pra
   peça grande — ou seja, o motor concorda essencialmente com a escolha
   manual do usuário, mesmo sem ver o padrão de antemão.

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

3. ~~Peça com furo interno de verdade (part-in-part)~~ **FEITO — exemplo
   real recebido e resolvido com match exato**: o usuário mandou
   `2CIRC.DXF` (fixture commitada) — anel grande (Ø100/Ø90) com anel
   pequeno (Ø80/Ø70) cortado CONCENTRICAMENTE de dentro do furo do
   grande, repetido numa rede hexagonal em 20 posições numa chapa
   500×500mm. O app Básico hoje (calculadora de Kit) só chega a 12 kits
   (colunas separadas, sem rede hexagonal nem furo aproveitado); o
   usuário a mão chega a 20.

   Isso é um mecanismo DIFERENTE do void-filling do item 1 (que preenche
   sobra de chapa fora das peças) — aqui a peça pequena nunca gasta área
   nenhuma da chapa, sai de graça de dentro do furo da peça grande. Nova
   classe `packing/HoleFiller.fillHoles(...)`: transforma o furo da peça
   primária pelo mesmo rigid-transform de cada instância aceita, tenta
   centralizar a peça secundária no centroide do furo em várias rotações,
   aceita a primeira que caiba inteira sem cruzar a borda (folga
   aproximada encolhendo o furo em direção ao centroide — exata pra furos
   circulares/convexos, o caso real testado).

   **Resultado: o motor sozinho chega aos MESMOS 20/20 kits do usuário**,
   sem nenhuma dica manual — `StrategySelector` já descobre a rede
   hexagonal da peça grande sozinho (`orientacao-unica`, já que peça
   redonda não depende de rotação), `HoleFiller` encaixa a peça pequena
   em 20 de 20 furos disponíveis. Validado com `RingKitDemo`: 0 colisões
   entre cópias da peça pequena, 0 fora de qualquer furo (checado
   vértice-a-vértice, independente da própria lógica do `HoleFiller`).

4. ~~Performance da validação final em resolução plena~~ **PARCIALMENTE
   FEITO**: `GeometryOps.convexHull()` (monotone chain de Andrew) +
   `packing/HullIndex` — testa o fecho convexo (poucos vértices, barato)
   antes do teste aresta-a-aresta exato; fecho é sempre superconjunto do
   polígono real, então "fechos não se sobrepõem" prova com certeza que
   os polígonos reais também não, sem risco nenhum de falso negativo (só
   confirma com o teste caro quando os fechos SE sobrepõem). Fecho da peça
   calculado UMA VEZ; só os poucos vértices do fecho são transformados a
   cada posicionamento (comuta com a transformação afim). Aplicado em
   `SheetPacker#validateFullResolution`: ~9,8s→~7,3-7,6s no bracket
   (648 peças, ~20-25% mais rápido), mesmos números, zero colisões.
   **Testado e descartado** em `VoidFiller` — a peça pequena ali costuma
   ser tão simples/convexa que calcular e testar o fecho é puro overhead
   sem ganho (medido: deixou MAIS LENTO, 8,5s→11s), então `VoidFiller`
   continua com `SpatialIndex` simples. Ainda não é uma solução completa
   (bracket ainda leva ~7,3s, não é instantâneo) — decidido não arriscar
   reduzir a discretização de arco do parser (mudaria a geometria usada em
   TODOS os cálculos, inclusive os já validados contra números exatos do
   usuário) nem uma decomposição convexa completa (escopo maior, mais
   risco). Se precisar de mais velocidade, o próximo passo é paralelizar
   a validação (candidatos são independentes até o momento do aceite) ou
   revisitar a densidade de arco especificamente para o teste de colisão
   (não para área/parsing).

5. ~~Múltiplas configurações de chapa além de deitada/em pé~~ **FEITO
   (corte em 2 tiras)**: `SheetPacker.packBestSplit(...)` — além de
   `packBestOrientation` (chapa inteira, deitada/em pé), agora também
   avalia cortar a chapa em 2 tiras de tamanhos diferentes (corte vertical
   ou horizontal, ~18 frações candidatas) e empacotar cada uma
   independentemente, escolhendo a configuração vencedora por previa
   aritmética (`estimateBestOrientation`) antes de pagar o `pack()` de
   verdade só uma vez — mesma lógica por trás do "modo conjugado" do app
   Básico, aplicada dentro de uma única chapa física. Validado com
   `SplitConfigDemo`: nenhuma das fixtures atuais mostrou ganho (todas já
   aproveitam bem a chapa inteira nesses tamanhos específicos — resultado
   esperado, não um bug). **Combinar materiais/chapas diferentes** (o modo
   conjugado "de verdade" do Básico, com SKUs de chapa distintos) continua
   fora de escopo.

   **Dois bugs reais de segurança encontrados e corrigidos enquanto isso
   era construído** (achados testando `packBestSplit` contra as fixtures
   já validadas, não coisas que o usuário reportou):
   - `validateFullResolution` (em `SheetPacker` e `VoidFiller`) só
     reconferia COLISÃO em resolução plena, nunca LIMITE da área útil —
     a busca só verifica limite contra a peça SIMPLIFICADA (Douglas-Peucker,
     epsilon 0.4mm), que por natureza fica INSCRITA no contorno real
     (DP só remove pontos, nunca move os que ficam), então podia aceitar
     peça que, em resolução plena, ultrapassa a margem por até esse
     epsilon. Achado com a 15746A: 2 peças de 106 furando a margem
     esquerda em ~0,27mm. Corrigido: `validateFullResolution` agora
     também descarta qualquer aceite fora dos limites reais.
   - Consequência do fix acima: encolher só na validação final rejeitava
     peças que estavam genuinamente no limite (ex.: o 2CIRC.DXF real tem
     margem+raio=53mm EXATO, zero folga) — caiu de 20/20 pra 14/20 kits.
     Causa raiz: a BUSCA usa a peça simplificada (que subestima a
     extensão real em até o epsilon) pra decidir se cabe, então propunha
     candidatos que a validação final (agora correta) rejeitava. Corrigido
     encolhendo a área útil só na fase de BUSCA pelo mesmo epsilon
     (`SEARCH_SIMPLIFY_EPSILON_MM`), tornando a busca conservadora o
     bastante pra não propor o que não vai passar. Resultado: 2CIRC
     voltou a 20/20, 15746A continua 106/106 mas agora com 0 violações
     (antes do fix original nem sabíamos que existiam), bracket/trap
     inalterados.

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
- `ENCAKIT` — kit peça-grande (3030mm² bruta, 3 furos) + peça-média-no-vão
  (1024mm² bruta, 3 furos), 60,7% líquido medido manualmente via auditoria
  no bbox do kit isolado (~151×83mm). **Fixture comitada** como
  `ENCAKIT.DXF` — é o DXF do kit inteiro já nesteado (2+2), não peças
  isoladas; `EncakitAnalysisDemo` extrai as 2 peças canônicas por
  contenção geométrica antes de analisar. Padrão real confirmado via
  `NestedKitAuditor`: as 2 cópias de cada peça usam interlock θ/θ+180°
  sem espelho.
- `TRAP/TRAPENC` — trapézio isósceles simples, sem furo. Fixture comitada
  como `TRAP.DXF`.
- `2CIRC` — kit de 2 anéis concêntricos (Ø100/Ø90 + Ø80/Ø70, o pequeno
  cortado de dentro do furo do grande) em rede hexagonal, 20 posições
  numa chapa 500×500mm. Exemplo real de part-in-part. **Fixture comitada**
  como `2CIRC.DXF`. Motor bate os 20/20 kits do usuário sozinho — ver
  `RingKitDemo`.

Se quiser mais fixtures de regressão permanentes, o DXF original do
OUTRAPRT está só no histórico de upload de sessões anteriores — peça pro
usuário reenviar se for útil.

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
