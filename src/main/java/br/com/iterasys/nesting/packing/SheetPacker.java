package br.com.iterasys.nesting.packing;

import br.com.iterasys.nesting.engine.OrientationAligner;
import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.strategy.NestingRecipe;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;
import br.com.iterasys.nesting.strategy.StrategySelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Ultima etapa do pipeline: pega a receita vencedora do {@link StrategySelector}
 * (celula + 2 vetores de rede), aplica o {@link OrientationAligner} pra alinhar
 * o maior vetor de rede a um eixo da chapa, ladrilha de verdade dentro de uma
 * chapa WxH com margem, e ENTAO reaproveita a mesma receita girada em
 * 90/180/270 graus na sobra - exatamente a tecnica manual do "Layout C" da
 * peca 15746A (86 pecas principais + 12 extras giradas 90 graus na sobra do
 * topo). Cada peca so e aceita depois de validar geometria real (nunca
 * confia so na matematica da rede; mesma licao que o V49 ja registrava em
 * comentario: amostragem/aproximacao pode dar falso positivo de "cabe").
 *
 * O reaproveitamento de sobra NAO precisa saber o FORMATO da sobra
 * (retangulo, faixa, paralelogramo torto de um vetor de rede diagonal) -
 * ele escaneia a area util inteira de novo com a receita girada, e a
 * checagem de colisao real (indice espacial) sozinha rejeita qualquer
 * posicao ja ocupada. Uma versao anterior restringia a busca a uma faixa
 * retangular calculada a partir do uso primario, o que so funcionava
 * quando os 2 vetores de rede ficavam quase perpendiculares (ex.:
 * TRAP.DXF) - falhava (reaproveitamento sempre 0) quando o 2o vetor era
 * diagonal (ex.: a receita "cola-por-aresta" do BRACKET_COMPACTO.DXF,
 * v1=(-26,10)). Escanear a area inteira em vez de uma faixa resolve isso
 * sem precisar calcular a forma real da sobra.
 *
 * {@link #packBestOrientation} testa a chapa deitada E em pe (WxH e HxW) e
 * fica com a que render mais pecas - a mesma decisao que o usuario tomou
 * manualmente entre o Layout A (1095x914, 106 pecas) e o Layout C (914x1095,
 * 98 pecas) da 15746A: mesma chapa fisica, orientacao diferente, resultado
 * diferente porque a receita de encaixe nao e simetrica em relacao a troca
 * de eixos.
 *
 * {@link #estimate} da uma previa numerica quase instantanea (area util /
 * area-por-peca da melhor receita, sem desenhar posicao nenhuma) ANTES de
 * rodar {@link #pack} de verdade - mesmo espirito do "calcularDicaLimpa" +
 * "autorizarBuscaCompletaKit" do app Basico: mostra um numero rapido,
 * so faz a busca cara se o usuario topar esperar. Deliberadamente NAO
 * desenha geometria nenhuma no modo rapido: uma primeira tentativa que
 * pulava so a validacao final (mas ainda desenhava posicoes com a busca
 * simplificada) deu 500 colisoes reais em 544 pecas no fixture do bracket -
 * a simplificacao usada na busca nao e confiavel o bastante pra pular a
 * validacao final, entao o modo rapido evita o problema por completo nao
 * desenhando nada, so estimando por area.
 *
 * Trava de espelhamento (regra de negocio, nunca automatica): por padrao
 * ({@code mirrorAuthorized=false}, inclusive nas sobrecargas sem esse
 * parametro) so usa a receita SEM espelho, mesmo que uma espelhada renda
 * mais peca/m2. {@code estimate}/{@code pack} sempre devolvem
 * {@code mirrorPending}/{@code mirrorGainPct} no resultado - quem chama
 * mostra isso ao usuario e, SO se ele autorizar explicitamente, chama de
 * novo com {@code mirrorAuthorized=true} pra liberar a receita espelhada.
 * O motor nunca decide isso sozinho.
 *
 * Ainda nao inclui: aproveitamento de vaos (peca pequena no espaco que
 * sobra dentro do encaixe, tipo ENCAKIT) - fica para as proximas etapas.
 */
public final class SheetPacker {

    private static final double[] REUSE_ROTATIONS_DEG = {90.0, 180.0, 270.0};

    public static final class Placement {
        public final boolean mirror;
        public final double rotationDeg;
        public final Point2D position;

        Placement(boolean mirror, double rotationDeg, Point2D position) {
            this.mirror = mirror;
            this.rotationDeg = rotationDeg;
            this.position = position;
        }
    }

    public static final class Result {
        public final List<Placement> placements;
        public final String strategyName;
        public final double alignmentDeltaDeg;
        public final double sheetAreaMm2;
        public final double piecesNetAreaMm2;
        public final double usedAreaMm2;
        public final int primaryCount;
        public final int reuseCount;
        public final double sheetWidthMm;
        public final double sheetHeightMm;
        public final boolean mirrorUsed;
        public final boolean mirrorPending;
        public final double mirrorGainPct;

        Result(List<Placement> placements, String strategyName, double alignmentDeltaDeg,
               double sheetAreaMm2, double piecesNetAreaMm2, int primaryCount, int reuseCount,
               double sheetWidthMm, double sheetHeightMm, boolean mirrorUsed, boolean mirrorPending, double mirrorGainPct) {
            this.placements = placements;
            this.strategyName = strategyName;
            this.alignmentDeltaDeg = alignmentDeltaDeg;
            this.sheetAreaMm2 = sheetAreaMm2;
            this.piecesNetAreaMm2 = piecesNetAreaMm2;
            this.usedAreaMm2 = placements.size() * piecesNetAreaMm2;
            this.primaryCount = primaryCount;
            this.reuseCount = reuseCount;
            this.sheetWidthMm = sheetWidthMm;
            this.sheetHeightMm = sheetHeightMm;
            this.mirrorUsed = mirrorUsed;
            this.mirrorPending = mirrorPending;
            this.mirrorGainPct = mirrorGainPct;
        }
    }

    private SheetPacker() {
    }

    public static final class QuickEstimate {
        public final String strategyName;
        public final int estimatedCount;
        public final double estimatedPct;
        public final boolean mirrorPending;
        public final double mirrorGainPct;

        QuickEstimate(String strategyName, int estimatedCount, double estimatedPct,
                      boolean mirrorPending, double mirrorGainPct) {
            this.strategyName = strategyName;
            this.estimatedCount = estimatedCount;
            this.estimatedPct = estimatedPct;
            this.mirrorPending = mirrorPending;
            this.mirrorGainPct = mirrorGainPct;
        }
    }

    /**
     * Previa quase instantanea: SO conta (area util / area-por-peca da
     * melhor receita), sem desenhar nenhuma posicao real - por isso nunca
     * tem risco de "colisao no preview" (nao ha geometria posicionada pra
     * colidir). Mesmo espirito do "calcularDicaLimpa" do app Basico: mostra
     * um numero rapido pro usuario decidir se topa esperar o {@link #pack}
     * de verdade (que desenha as posicoes e pode levar alguns segundos em
     * pecas com muitos vertices).
     *
     * Primeira tentativa desta etapa foi um "packQuick" que pulava a
     * validacao final em resolucao plena pra ganhar velocidade - e deu 500
     * colisoes reais em 544 pecas no fixture do bracket. A simplificacao de
     * geometria usada na busca nao e confiavel o bastante pra pular essa
     * validacao. Esta versao evita o problema simplesmente NAO desenhando
     * posicao nenhuma no modo rapido.
     */
    public static QuickEstimate estimate(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                                          double marginMm, double gapMm) {
        return estimate(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, false);
    }

    /**
     * Mesma previa, mas {@code mirrorAuthorized} decide se a receita
     * espelhada pode ser usada quando ela for a melhor global - a trava de
     * espelhamento so libera quando quem chama passa {@code true}, o que so
     * deve acontecer depois de o usuario autorizar explicitamente ao ver
     * {@code mirrorPending}/{@code mirrorGainPct} no resultado sem espelho.
     */
    public static QuickEstimate estimate(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                                          double marginMm, double gapMm, boolean mirrorAuthorized) {
        StrategySelector.Result sel = StrategySelector.select(fullOuter, gapMm);
        NestingRecipe recipe = sel.chosen(mirrorAuthorized);
        boolean pending = sel.mirrorPending();
        double gain = sel.mirrorGainPct();
        if (recipe == null) {
            return new QuickEstimate("nenhuma", 0, 0, pending, gain);
        }
        double usableW = sheetW - 2 * marginMm;
        double usableH = sheetH - 2 * marginMm;
        if (usableW <= 0 || usableH <= 0) {
            return new QuickEstimate(recipe.strategyName, 0, 0, pending, gain);
        }
        double areaLiquidaPeca = Math.abs(fullOuter.area());
        for (Polygon h : holes) areaLiquidaPeca -= Math.abs(h.area());

        double usableArea = usableW * usableH;
        int count = (int) Math.floor(usableArea / recipe.areaPerPieceMm2Gross);
        double pct = usableArea > 0 ? 100.0 * count * areaLiquidaPeca / usableArea : 0;
        return new QuickEstimate(recipe.strategyName, count, pct, pending, gain);
    }

    /**
     * Busca completa: melhor estrategia dentre os 3 mecanismos + reaproveitamento
     * de sobra de borda, com as posicoes de verdade desenhadas e validadas em
     * resolucao plena. Pode levar alguns segundos em pecas com muitos vertices
     * - use {@link #estimate} pra mostrar um numero rapido antes e deixar o
     * usuario decidir se topa esperar por este aqui.
     */
    public static Result pack(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                               double marginMm, double gapMm) {
        return pack(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, false);
    }

    /**
     * Mesma busca completa, mas {@code mirrorAuthorized} decide se a receita
     * espelhada pode ser usada quando ela for a melhor global - mesma trava
     * de espelhamento de {@link #estimate(Polygon, List, double, double, double, double, boolean)}.
     * Rode {@link #pack(Polygon, List, double, double, double, double)} (sem
     * espelho) primeiro; se o resultado vier com {@code mirrorPending=true} e
     * o usuario autorizar explicitamente, so entao chame esta sobrecarga com
     * {@code true}.
     */
    public static Result pack(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                               double marginMm, double gapMm, boolean mirrorAuthorized) {
        StrategySelector.Result sel = StrategySelector.select(fullOuter, gapMm);
        NestingRecipe recipe = sel.chosen(mirrorAuthorized);
        boolean pending = sel.mirrorPending();
        double gain = sel.mirrorGainPct();
        boolean mirrorUsed = recipe != null && recipe.usesMirror;
        if (recipe == null) {
            return new Result(List.of(), "nenhuma", 0, sheetW * sheetH, 0, 0, 0, sheetW, sheetH, false, pending, gain);
        }

        double areaLiquidaPeca = Math.abs(fullOuter.area());
        for (Polygon h : holes) areaLiquidaPeca -= Math.abs(h.area());

        // A discretizacao fina de arco do parser gera centenas de vertices por
        // peca - caro demais pra testar colisao milhares de vezes durante a
        // busca. Usa uma versao simplificada so pra essa fase (mesma tecnica
        // do StrategySelector); valida em resolucao PLENA no final, antes de
        // devolver o resultado.
        Polygon searchOuter = GeometryOps.simplify(fullOuter, 0.4);
        Point2D centroid = GeometryOps.centroid(fullOuter);
        Point2D origin = new Point2D(0, 0);
        double[] fullBB = fullOuter.boundingBox();
        double pieceMaxDim = Math.max(fullBB[2] - fullBB[0], fullBB[3] - fullBB[1]);

        Point2D v1 = recipe.latticeV1;
        Point2D v2 = recipe.latticeV2;
        Point2D longer = Math.hypot(v1.x, v1.y) >= Math.hypot(v2.x, v2.y) ? v1 : v2;
        double delta = OrientationAligner.snapToAxisDelta(Math.toDegrees(Math.atan2(longer.y, longer.x)));

        List<PlacedPieceInstance> alignedCell = rotateCell(recipe.cellInstances, delta);
        Point2D av1 = v1.rotateDeg(delta, origin);
        Point2D av2 = v2.rotateDeg(delta, origin);

        double usableMinX = marginMm, usableMinY = marginMm;
        double usableMaxX = sheetW - marginMm, usableMaxY = sheetH - marginMm;
        if (usableMaxX <= usableMinX || usableMaxY <= usableMinY) {
            return new Result(List.of(), recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca, 0, 0, sheetW, sheetH, mirrorUsed, pending, gain);
        }

        List<Placement> accepted = new ArrayList<>();
        List<Polygon> acceptedPolys = new ArrayList<>();

        tileRegion(searchOuter, centroid, alignedCell, av1, av2,
                usableMinX, usableMinY, usableMaxX, usableMaxY, accepted, acceptedPolys);
        int primaryCount = accepted.size();

        // Reaproveitamento de sobra: mesma receita girada 90/180/270,
        // escaneando a area util INTEIRA de novo (nao so uma faixa
        // retangular calculada a partir do uso primario). A checagem de
        // colisao contra o que ja foi aceito (indice espacial) ja rejeita
        // sozinha qualquer posicao ocupada - por isso nao precisa mais
        // adivinhar o FORMATO da sobra (retangulo, faixa, paralelogramo
        // torto do vetor diagonal): ela emerge naturalmente de onde a
        // colisao real deixa espaco. Resolve a limitacao anterior (sobra
        // como retangulo so funcionava com vetores quase perpendiculares).
        tryBestRotationInRegion(searchOuter, centroid, alignedCell, av1, av2,
                usableMinX, usableMinY, usableMaxX, usableMaxY, accepted, acceptedPolys);

        // Validacao final em RESOLUCAO PLENA - a busca acima usou a peca
        // simplificada como atalho; nunca confia nisso sozinho (mesma licao
        // do V49). Remove qualquer aceite que colida de verdade (nao deveria
        // acontecer, mas descarta em vez de arriscar). E o custo dominante em
        // pecas com muitos vizinhos proximos - ver {@link #estimate} pra uma
        // previa que nao paga esse custo (porque nao desenha posicao nenhuma).
        List<Placement> validated = validateFullResolution(fullOuter, centroid, accepted, pieceMaxDim);
        int reuseCount = validated.size() - Math.min(primaryCount, validated.size());
        return new Result(validated, recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca,
                Math.min(primaryCount, validated.size()), reuseCount, sheetW, sheetH, mirrorUsed, pending, gain);
    }

    /**
     * Refaz a checagem de colisao com a geometria completa (nao a
     * simplificada usada na busca). Usa {@link HullIndex}: fecho convexo
     * da peca calculado UMA VEZ, so os poucos vertices do fecho sao
     * transformados a cada posicionamento (o fecho comuta com a
     * transformacao afim de {@code materialize}) - fechos que nao se
     * sobrepoem provam que os poligonos reais tambem nao se sobrepoem
     * (fecho e sempre superconjunto), entao a maioria dos pares distantes
     * e rejeitada sem pagar o teste aresta-a-aresta caro na geometria
     * completa. So confirma com o teste exato quando os fechos SE
     * sobrepoem. Isso era o custo dominante do pipeline antes desta
     * otimizacao (~10-14s pra ~650 pecas do bracket).
     */
    private static List<Placement> validateFullResolution(Polygon fullOuter, Point2D centroid,
                                                            List<Placement> accepted, double pieceMaxDim) {
        Polygon hullBase = GeometryOps.convexHull(fullOuter);
        List<Placement> kept = new ArrayList<>(accepted.size());
        HullIndex index = new HullIndex(Math.max(1.0, pieceMaxDim));
        for (Placement p : accepted) {
            PlacedPieceInstance inst = new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position);
            Polygon poly = inst.materialize(fullOuter, centroid);
            Polygon hull = inst.materialize(hullBase, centroid);
            if (index.overlapsAny(hull, poly)) continue;
            kept.add(p);
            index.insert(hull, poly);
        }
        return kept;
    }

    /**
     * Testa a chapa deitada e em pe (WxH e HxW) e fica com a que render
     * mais pecas - a mesma escolha que o usuario fez manualmente entre o
     * Layout A (1095x914, 106 pecas) e o Layout C (914x1095, 98 pecas) da
     * 15746A. Se W e H forem iguais (chapa quadrada), so roda uma vez.
     */
    public static Result packBestOrientation(Polygon fullOuter, List<Polygon> holes,
                                              double sheetW, double sheetH, double marginMm, double gapMm) {
        return packBestOrientation(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, false);
    }

    /** Mesma escolha de orientacao, repassando a autorizacao de espelho pras duas tentativas. */
    public static Result packBestOrientation(Polygon fullOuter, List<Polygon> holes,
                                              double sheetW, double sheetH, double marginMm, double gapMm,
                                              boolean mirrorAuthorized) {
        Result deitada = pack(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, mirrorAuthorized);
        if (Math.abs(sheetW - sheetH) < 1e-9) {
            return deitada;
        }
        Result emPe = pack(fullOuter, holes, sheetH, sheetW, marginMm, gapMm, mirrorAuthorized);
        return emPe.placements.size() > deitada.placements.size() ? emPe : deitada;
    }

    /**
     * Previa rapida das duas orientacoes, usando {@link #estimate} nas duas
     * (sem desenhar posicao nenhuma) - pra decidir qual orientacao vale a
     * pena mandar pra busca completa, sem pagar o custo dela duas vezes so
     * pra escolher.
     */
    public static QuickEstimate estimateBestOrientation(Polygon fullOuter, List<Polygon> holes,
                                                          double sheetW, double sheetH, double marginMm, double gapMm) {
        return estimateBestOrientation(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, false);
    }

    /** Mesma escolha de orientacao, repassando a autorizacao de espelho pras duas tentativas. */
    public static QuickEstimate estimateBestOrientation(Polygon fullOuter, List<Polygon> holes,
                                                          double sheetW, double sheetH, double marginMm, double gapMm,
                                                          boolean mirrorAuthorized) {
        QuickEstimate deitada = estimate(fullOuter, holes, sheetW, sheetH, marginMm, gapMm, mirrorAuthorized);
        if (Math.abs(sheetW - sheetH) < 1e-9) {
            return deitada;
        }
        QuickEstimate emPe = estimate(fullOuter, holes, sheetH, sheetW, marginMm, gapMm, mirrorAuthorized);
        return emPe.estimatedCount > deitada.estimatedCount ? emPe : deitada;
    }

    private static final double[] SPLIT_FRACTIONS = {0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7};

    public static final class SplitResult {
        public final Result result;
        public final String splitDescription;

        SplitResult(Result result, String splitDescription) {
            this.result = result;
            this.splitDescription = splitDescription;
        }
    }

    /**
     * Considera, alem de {@link #packBestOrientation} (chapa inteira, deitada
     * ou em pe), cortar a chapa em 2 TIRAS de tamanhos diferentes (uma
     * divisao vertical ou horizontal num ponto qualquer) e empacotar cada
     * tira de forma independente com sua propria melhor orientacao. Vale a
     * pena quando a receita vencedora nao ladrilha um retangulo perfeito
     * (sobra de borda) e uma tira com outra proporcao aproveita melhor esse
     * resto - a mesma logica por tras do "modo conjugado" do app Basico
     * (combinar formatos de chapa diferentes), aqui aplicada dentro de UMA
     * chapa fisica so.
     *
     * Faz a varredura de candidatos com {@link #estimateBestOrientation}
     * (barato, nao desenha posicao nenhuma) pra cada fracao de corte
     * candidata - so roda o {@link #pack} de verdade (caro, valida em
     * resolucao plena) UMA VEZ, na configuracao vencedora. Reserva
     * {@code gapMm} entre as 2 tiras como se fosse o corte fisico entre
     * elas. Combina os resultados das 2 tiras deslocando a translacao de
     * cada peca da tira B pelo deslocamento da tira - e ainda assim
     * confere colisao real entre as 2 tiras combinadas antes de devolver
     * (nunca confia so na separacao geometrica das regioes).
     */
    public static SplitResult packBestSplit(Polygon fullOuter, List<Polygon> holes,
                                             double sheetW, double sheetH, double marginMm, double gapMm) {
        QuickEstimate baseline = estimateBestOrientation(fullOuter, holes, sheetW, sheetH, marginMm, gapMm);
        double bestEstCount = baseline.estimatedCount;
        boolean bestVertical = false;
        double bestSplitAt = -1;

        for (double f : SPLIT_FRACTIONS) {
            double wA = sheetW * f;
            double wB = sheetW - wA - gapMm;
            if (wA > 2 * marginMm && wB > 2 * marginMm) {
                double total = estimateBestOrientation(fullOuter, holes, wA, sheetH, marginMm, gapMm).estimatedCount
                        + estimateBestOrientation(fullOuter, holes, wB, sheetH, marginMm, gapMm).estimatedCount;
                if (total > bestEstCount) {
                    bestEstCount = total;
                    bestVertical = true;
                    bestSplitAt = wA;
                }
            }
            double hA = sheetH * f;
            double hB = sheetH - hA - gapMm;
            if (hA > 2 * marginMm && hB > 2 * marginMm) {
                double total = estimateBestOrientation(fullOuter, holes, sheetW, hA, marginMm, gapMm).estimatedCount
                        + estimateBestOrientation(fullOuter, holes, sheetW, hB, marginMm, gapMm).estimatedCount;
                if (total > bestEstCount) {
                    bestEstCount = total;
                    bestVertical = false;
                    bestSplitAt = hA;
                }
            }
        }

        if (bestSplitAt < 0) {
            return new SplitResult(packBestOrientation(fullOuter, holes, sheetW, sheetH, marginMm, gapMm), "sem divisao");
        }

        Result rA, rB;
        double offsetX = 0, offsetY = 0;
        String desc;
        if (bestVertical) {
            double wA = bestSplitAt, wB = sheetW - wA - gapMm;
            rA = packBestOrientation(fullOuter, holes, wA, sheetH, marginMm, gapMm);
            rB = packBestOrientation(fullOuter, holes, wB, sheetH, marginMm, gapMm);
            offsetX = wA + gapMm;
            desc = String.format("vertical em x=%.1fmm (tiras %.1f + %.1fmm)", wA, wA, wB);
        } else {
            double hA = bestSplitAt, hB = sheetH - hA - gapMm;
            rA = packBestOrientation(fullOuter, holes, sheetW, hA, marginMm, gapMm);
            rB = packBestOrientation(fullOuter, holes, sheetW, hB, marginMm, gapMm);
            offsetY = hA + gapMm;
            desc = String.format("horizontal em y=%.1fmm (tiras %.1f + %.1fmm)", hA, hA, hB);
        }

        List<Placement> combined = new ArrayList<>(rA.placements.size() + rB.placements.size());
        combined.addAll(rA.placements);
        for (Placement p : rB.placements) {
            combined.add(new Placement(p.mirror, p.rotationDeg, new Point2D(p.position.x + offsetX, p.position.y + offsetY)));
        }

        // Confere colisao real entre as 2 tiras combinadas - nunca confia so
        // na separacao geometrica das regioes (mesma disciplina do resto do
        // motor). Reusa validateFullResolution: como as tiras ja foram
        // validadas individualmente, so a fronteira entre elas pode, em
        // teoria, ter algo errado.
        double pieceMaxDim = Math.max(fullOuter.boundingBox()[2] - fullOuter.boundingBox()[0],
                fullOuter.boundingBox()[3] - fullOuter.boundingBox()[1]);
        Point2D centroid = GeometryOps.centroid(fullOuter);
        List<Placement> validated = validateFullResolution(fullOuter, centroid, combined, pieceMaxDim);

        double areaLiquidaPeca = rA.piecesNetAreaMm2 > 0 ? rA.piecesNetAreaMm2 : rB.piecesNetAreaMm2;
        int primaryCount = rA.primaryCount + rB.primaryCount;
        int reuseCount = validated.size() - Math.min(primaryCount, validated.size());
        Result combinedResult = new Result(validated, rA.strategyName + " + " + rB.strategyName, Double.NaN,
                sheetW * sheetH, areaLiquidaPeca, Math.min(primaryCount, validated.size()), reuseCount,
                sheetW, sheetH, rA.mirrorUsed || rB.mirrorUsed, false, 0);
        return new SplitResult(combinedResult, desc);
    }

    private static List<PlacedPieceInstance> rotateCell(List<PlacedPieceInstance> cell, double deltaDeg) {
        Point2D origin = new Point2D(0, 0);
        List<PlacedPieceInstance> out = new ArrayList<>();
        for (PlacedPieceInstance inst : cell) {
            Point2D rotatedTranslation = inst.translation.rotateDeg(deltaDeg, origin);
            out.add(new PlacedPieceInstance(inst.mirror, inst.rotationDeg + deltaDeg, rotatedTranslation));
        }
        return out;
    }

    /**
     * Tenta reaproveitar a receita girada em cada um de {@link #REUSE_ROTATIONS_DEG}
     * dentro da regiao dada, mantendo so a rotacao que render mais pecas -
     * cada tentativa parte do estado JA aceito (colide contra tudo que
     * existe), sem acumular entre si.
     */
    private static void tryBestRotationInRegion(Polygon pieceOuter, Point2D centroid,
                                                 List<PlacedPieceInstance> baseCell, Point2D baseV1, Point2D baseV2,
                                                 double regionMinX, double regionMinY, double regionMaxX, double regionMaxY,
                                                 List<Placement> accepted, List<Polygon> acceptedPolys) {
        Point2D origin = new Point2D(0, 0);
        List<Placement> bestNew = null;
        List<Polygon> bestNewPolys = null;

        for (double rot : REUSE_ROTATIONS_DEG) {
            List<PlacedPieceInstance> rotCell = rotateCell(baseCell, rot);
            Point2D rv1 = baseV1.rotateDeg(rot, origin);
            Point2D rv2 = baseV2.rotateDeg(rot, origin);

            List<Placement> trialAccepted = new ArrayList<>();
            List<Polygon> trialPolys = new ArrayList<>(acceptedPolys);
            int before = trialPolys.size();
            tileRegion(pieceOuter, centroid, rotCell, rv1, rv2,
                    regionMinX, regionMinY, regionMaxX, regionMaxY, trialAccepted, trialPolys);

            if (bestNew == null || trialAccepted.size() > bestNew.size()) {
                bestNew = trialAccepted;
                bestNewPolys = new ArrayList<>(trialPolys.subList(before, trialPolys.size()));
            }
        }
        if (bestNew != null && !bestNew.isEmpty()) {
            accepted.addAll(bestNew);
            acceptedPolys.addAll(bestNewPolys);
        }
    }

    /** Ladrilha uma celula (com os vetores de rede ja na orientacao desejada) dentro de um retangulo. */
    private static void tileRegion(Polygon pieceOuter, Point2D centroid,
                                    List<PlacedPieceInstance> cellInstances, Point2D v1, Point2D v2,
                                    double regionMinX, double regionMinY, double regionMaxX, double regionMaxY,
                                    List<Placement> accepted, List<Polygon> acceptedPolys) {
        double regionW = regionMaxX - regionMinX;
        double regionH = regionMaxY - regionMinY;
        if (regionW <= 0 || regionH <= 0) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double pieceMaxDim = 0;
        for (PlacedPieceInstance inst : cellInstances) {
            Polygon poly = inst.materialize(pieceOuter, centroid);
            double[] bb = poly.boundingBox();
            minX = Math.min(minX, bb[0]);
            minY = Math.min(minY, bb[1]);
            pieceMaxDim = Math.max(pieceMaxDim, Math.max(bb[2] - bb[0], bb[3] - bb[1]));
        }

        double shiftX = regionMinX - minX;
        double shiftY = regionMinY - minY;
        double diag = Math.hypot(regionW, regionH);
        int rangeN = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(v1.x, v1.y))) + 2;
        int rangeM = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(v2.x, v2.y))) + 2;

        SpatialIndex index = new SpatialIndex(Math.max(1.0, pieceMaxDim));
        for (Polygon p : acceptedPolys) index.insert(p);

        for (int n = -rangeN; n <= rangeN; n++) {
            for (int m = -rangeM; m <= rangeM; m++) {
                double ox = shiftX + n * v1.x + m * v2.x;
                double oy = shiftY + n * v1.y + m * v2.y;
                for (PlacedPieceInstance inst : cellInstances) {
                    Point2D pos = new Point2D(inst.translation.x + ox, inst.translation.y + oy);
                    PlacedPieceInstance candidate = new PlacedPieceInstance(inst.mirror, inst.rotationDeg, pos);
                    Polygon poly = candidate.materialize(pieceOuter, centroid);
                    double[] bb = poly.boundingBox();
                    if (bb[0] < regionMinX - 1e-6 || bb[1] < regionMinY - 1e-6
                            || bb[2] > regionMaxX + 1e-6 || bb[3] > regionMaxY + 1e-6) {
                        continue;
                    }
                    if (index.overlapsAny(poly)) continue;
                    accepted.add(new Placement(candidate.mirror, candidate.rotationDeg, candidate.translation));
                    acceptedPolys.add(poly);
                    index.insert(poly);
                }
            }
        }
    }
}
