# Nesting PRO — Handoff de continuidade (sessão 2026-09-06)

Registro de estado para retomar o trabalho em outra sessão/conta Claude Pro.
A janela de 5h desta sessão está terminando; o usuário confirmou que vai
enviar mais exemplos "amanhã". Nada aqui foi pedido para ser executado agora
além deste registro.

## Método de trabalho combinado (vale para toda a frente "Nesting PRO")

- Nesta frente falamos **só de lógica/matemática do encaixe**, não de
  interface. Não gerar HTML a cada mensagem. Não perder tempo revisando
  Retângulo/Círculo/estética do app Básico.
- O usuário ensina o que faz **manualmente** no CAD para nestear uma peça
  real; a IA traduz isso em regra de cálculo/algoritmo.
- Ele já testou o Deepnest antes: "é bom, mas não excelente". Diretriz
  explícita: **absorver o máximo do que Deepnest/SVGnest já resolvem, mas
  melhorar** — não é para portar 1:1 sem questionar.

## Decisão de arquitetura em andamento (IMPORTANTE — pivô nesta sessão)

Comecei implementando o motor em **Java** (branch `claude/nesting-2d-irregular-engine-m2x79f`
deste repositório, `AlfLion/Intro_Java`). Depois de o usuário mostrar o app
"Básico" (`Basico_260906_1958.html`) — um app **client-side, HTML+JS puro,
arquivo único, sem build step**, com milhares de linhas já maduras
(nesting de retângulo/círculo/kit, exportação DXF real, aproveitamento de
vãos/furos, i18n) — ficou claro que a PRO deveria ser desenvolvida na
**mesma stack (JavaScript puro)**, porque a intenção declarada é *juntar os
dois projetos* no futuro.

**Decisão pendente de confirmação explícita do usuário**, mas fortemente
indicada: abandonar o motor Java deste repositório e reiniciar a lógica em
JS no repositório **`AlfLion/Nesting`** (onde presumo que o Básico já mora).
Esse repositório **ainda não foi anexado a nenhuma sessão** — precisa de
`add_repo` na próxima sessão.

O que sobrevive do trabalho em Java (conceitos, não o código Java em si):
- Parser DXF genérico (LINE/ARC/CIRCLE/LWPOLYLINE+bulge), encadeamento de
  entidades soltas por casamento de extremidade, maior laço = contorno
  externo, resto = furos.
- Busca de ângulo livre por varredura (não travada em 0/90/180°).
- Teste de colisão real polígono-polígono (aresta-aresta + ponto-dentro,
  bbox só como filtro rápido).

Arquivo de fixture real (**vale copiar para onde o trabalho continuar**):
`src/main/resources/fixtures/15746A.DXF` neste repositório — é o DXF real
da peça de estudo, anexado pelo usuário.

## Peças de referência já estudadas

### App "Básico" (`Basico_260906_1958.html`)
Não mexer nele nesta frente. Só serve como referência de convenções que a
PRO deveria manter para integração futura: estrutura de `kitLista`/peças,
`gerarConteudoDXF` (camadas por SKU, fusão de arestas em grade
compartilhada), lógica de "vãos" (`calcularAproveitamentoVao`,
`prepararReducaoPorVaos`, `aproveitarVaoNoDesenho`) — aproveitamento
secundário de peça pequena dentro do vão de peça-moldura/anel, conceito
irmão do "part-in-part" que a PRO vai precisar para furos de peças DXF.

### Protótipo PRO V49 (`260901_18_Pro_V49__ROLO.html`, não aprovado)
Já tem, e é reaproveitável como referência de implementação:
- `parseAsciiDxf`: trata `$INSUNITS` (in/ft/mm/cm/m → normaliza tudo pra
  mm) — eu **não tinha isso** no parser Java, importante não esquecer.
- `dxfContornos`: mesma estratégia de encadeamento por extremidade +
  maior-laço-é-outer que eu cheguei independentemente.
- `dxfPoligonosColidem` / `dxfDistanciaMinimaPoligonos`: colisão real
  aresta-a-aresta + ponto-dentro-de-polígono, bbox só como filtro de
  rejeição. Comentário no código deles é uma lição de produção importante:
  já tentaram "inflar contorno pelo gap + testar toque" e abandonaram
  porque dá falso positivo em cantos côncavos (ex.: abertura de um "C").
- **O motor de nesting do V49 é fraco e é exatamente por isso que a peça
  15746A não funcionaria nele**: é uma heurística de "célula de 2 peças"
  — varre uma grade grosseira→fina buscando o deslocamento entre 2 cópias
  que **minimiza a bounding box da união**, só nas rotações fixas
  `{0°, 90°, 180°}` (`DXF_ROTACOES_NESTING`), sem espelhamento, sem ângulo
  livre. Essa célula vencedora é repetida em grade regular
  (`dxfTilarParNaChapa`), com uma segunda passada de MaxRects para vãos
  mortos (`dxfTentarReaproveitarEspacosMortos`). Minimizar bbox de 2 peças
  funciona bem pra formas tipo "C"/"L" que preenchem bem a própria bbox,
  mas a 15746A ocupa só 5,7% da sua bbox — bbox-fitting não converge pro
  encaixe real, que depende de ângulo livre (135°, fora do conjunto
  {0,90,180}).
- Exportação DXF final honra a geometria real de cada peça (não a bbox) —
  manter esse padrão.

### Peça de estudo 15746A — geometria exata (extraída do DXF real)

- Arco externo: raio 275,585 mm, varredura 352,5°→82,4° (89,9°)
- Arco interno: raio 262,585 mm, varredura 7,5°→97,4° (89,9°), defasado 15°
  do externo
- 2 pontas arredondadas (raio médio ≈269 mm, 15° cada) + 4 segmentos retos
  curtos (~6,4–6,6 mm) fechando o contorno
- Espessura radial constante: 13,00 mm
- Bounding box bruto: 310,2 × 309,1 mm; área real (shoelace): ≈5.474,6 mm²
  (só ~5,7% da bbox — por isso bbox-fitting/rotação em múltiplos de 90°
  é inviável para esta peça)
- Sem simetria de rotação de 180° em torno do próprio centroide (rotacionar
  180° desloca o contorno ~107mm do original) — o encaixe manual não é um
  truque de simetria exata.

### Receita de encaixe manual medida nos dois DXFs nesteados (Layout A e C)

**Layout A** — chapa 1095×914mm (deitada), 106 peças:
- Fileira 1 (53 peças): rotação 135° constante; passo entre peças
  **exatamente (+18,620mm, 0)** — translação pura em X, mesmo ângulo.
- Fileira 2 (53 peças): mesma peça girada **315°** (135°+180°, em torno do
  próprio centro do arco), mesmo passo interno 18,620mm, fileira inteira
  deslocada da fileira 1 pelo vetor **(Δx,Δy) = (−435,80; +437,27) mm**
  (módulo ≈617,4mm).
- 53+53 = 106. Confere exatamente.
- Distância real mínima medida entre peças (amostragem fina): **≈1,55mm**,
  não os 2mm nominais que o usuário citou como parâmetro-alvo.
- Margens medidas: esquerda 3,00 / inferior 3,71mm (batem com os 3mm
  citados); direita 8,37 / topo 48,21mm — sobra grande no topo, não usada
  (menor que o "passo perpendicular" que a técnica precisaria pra caber
  mais uma leva, mesmo girada).
- Conferência de área: 106 × 5.474,6mm² = 580.144mm² ocupados numa área
  útil (margem 3mm) de 911.632mm² → ~63,6% de aproveitamento. Plausível
  para peça fina/curva com gap por todo o perímetro grande (~1.011mm/peça).

**Layout C** — mesmo material (rolo, largura fixa 914mm), chapa em pé
914×1095mm, 98 peças:
- 86 peças "principais": mesma técnica (135°/315°, passo 18,62mm), só que 2
  fileiras de 43 (não 53) porque a largura disponível é 914mm em vez de
  1095mm.
- 12 peças "extras": a MESMA receita girada -90° em bloco (ângulos
  45°/225°), 2 colunas de 6, mesmo passo 18,62mm, vetor coluna→coluna
  (442,34; 435,80) — módulo ≈620,9mm, praticamente igual ao vetor
  fileira→fileira do Layout A (617,4mm), só apontando em outra direção.
  Preenchem a sobra do topo, que aqui é ~229mm (vs 48mm no Layout A)
  porque a chapa em pé tem a dimensão maior nessa direção.
- 86+12 = 98. Confere.

**Conclusão-chave (validada nos dois arquivos, não é hipótese)**: a
"receita" de auto-encaixe — ângulo ótimo, passo dentro da fileira, vetor
fileira→fileira — é uma propriedade da **peça + gap**, não da chapa. Uma
vez encontrada para um ângulo-base, o motor ganha de graça as versões
giradas em 90°/180°/270° e pode testar cada uma contra qualquer sobra de
espaço. O vetor fileira→fileira **não é um número redondo nem múltiplo
limpo** das dimensões da peça — foi achado por tentativa/olho no CAD, não
por fórmula fechada. É exatamente esse "deslizar até encostar respeitando
o gap" que é o conceito de **No-Fit Polygon (NFP)**.

## Validação automática já tentada nesta sessão (Java, conceito reaproveitável)

Implementei um parser DXF genérico + um otimizador de ângulo por varredura
livre (`RowFitOptimizer`, sem receber 135° como dica) e rodei contra o DXF
real da peça isolada:

- **Ângulo encontrado sozinho: 134°** (medido manualmente: 135°) — resolução
  de busca era 1°.
- **Passo previsto: 17,93mm** (contato geométrico + gap medido 1,55mm) vs.
  **18,62mm medido** — diferença ~3,7%.
- Isso usa só uma aproximação simplificada (distância de contato par-a-par
  deslizando em uma única direção, com busca binária), não um NFP completo
  — mas já confirma que "achar o ângulo sozinho por busca sistemática" é
  viável e dá resultado na faixa certa.
- **Tentativa de achar o vetor fileira→fileira falhou** com a abordagem
  ingênua (peça a 135° vs peça a 315°, ambas centradas na mesma origem,
  varredura radial de direção): retornou distância ≈0 em toda direção,
  porque as duas orientações, centradas no mesmo pivô, **não se sobrepõem
  de jeito nenhum** (a peça ocupa só uma fatia angular fina do círculo).
  Diagnóstico: o vetor real vem do contato **fileira-vs-fileira** (silhueta
  de N peças já espaçadas dentro da fileira), não de um par isolado peça-vs-
  peça a partir de um centro compartilhado. Fica registrado para não
  repetir o mesmo erro na próxima tentativa: a próxima versão do cálculo
  do vetor fileira→fileira precisa comparar a **silhueta da fileira
  inteira** (ou pelo menos 2-3 peças já com o passo de 18,62mm aplicado)
  contra a fileira girada, não duas peças soltas.

## Roadmap (itens que faltam construir — nenhum começado em JS ainda)

1. Busca de ângulo livre (prototipado em Java, ver acima; precisa refinar
   resolução/critério e portar pra JS).
2. NFP real peça-contra-peça (não a aproximação por varredura direcional
   usada até agora) — o núcleo que falta.
3. Detecção de "passo limpo" (tiling por translação pura) a partir da
   borda do NFP.
4. Segundo NFP pra achar o vetor fileira→fileira (usando silhueta de
   fileira, não par isolado — ver diagnóstico acima).
5. Reaproveitamento automático da receita girada em 90°/180°/270° contra
   sobras de formato diferente (foi feito manualmente no Layout C; o motor
   precisa fazer sozinho, sem o usuário mandar um segundo DXF).
6. Empacotamento na chapa, incluindo caso de **rolo** (largura fixa,
   comprimento livre) — decidir quantas fileiras cabem na altura útil e
   quantas peças por fileira na largura útil.
7. Validação final com geometria real / amostragem fina o bastante pra não
   dar falso positivo de "cabe" (amostragem grosseira superestima
   distância — visto na prática: 1,58mm → 1,55mm ao refinar).

Regras de negócio ainda não tocadas por nenhum código:
- **Espelhamento nunca automático** — motor ainda nem tenta espelhar, então
  o gate de autorização explícita (regra de negócio 3) ainda não tem onde
  pendurar; entra quando a busca de candidatos incluir espelho como opção.
- **Furos / void-filling (part-in-part)** — estrutura de dados já pensada
  (`PieceGeometry.outer` + `holes`), mas nenhum algoritmo de encaixe dentro
  de furo foi implementado. O usuário vai mandar um exemplo com furo
  interno para estressar isso.

## Próximos passos combinados

1. Usuário vai enviar amanhã (ou depois): (a) uma peça com furo interno
   (testar part-in-part de verdade), (b) uma peça cujo ângulo ótimo não
   seja "bonito" tipo 135° (validar que a busca de ângulo livre generaliza).
2. Decidir e confirmar explicitamente o pivô para JavaScript no repo
   `AlfLion/Nesting` (anexar via `add_repo` na próxima sessão).
3. Só depois disso: estruturar o motor NFP, inspirado no SVGnest/Deepnest
   mas com a diretriz explícita do usuário de **melhorar**, não só portar
   (ele já achou o Deepnest "bom, mas não excelente" — vale mapear onde
   especificamente ele decepciona antes de copiar a arquitetura dele).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_018yahHKc54rrjdTqBwbrQzM
