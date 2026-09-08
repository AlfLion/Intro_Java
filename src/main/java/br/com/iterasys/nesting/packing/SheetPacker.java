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
 * 90/180/270 graus nas faixas de sobra que sobraram (direita e topo) -
 * exatamente a tecnica manual do "Layout C" da peca 15746A (86 pecas
 * principais + 12 extras giradas 90 graus na sobra do topo). Cada peca so e
 * aceita depois de validar geometria real (nunca confia so na matematica da
 * rede; mesma licao que o V49 ja registrava em comentario: amostragem/
 * aproximacao pode dar falso positivo de "cabe").
 *
 * Limitacao conhecida do reaproveitamento de sobra: a faixa "que sobrou" e
 * tratada como um RETANGULO (do fim do uso primario ate a borda util) - funciona
 * bem quando os 2 vetores de rede da receita ficam quase perpendiculares
 * (ex.: TRAP.DXF, onde um vetor fica exatamente no eixo apos o alinhamento:
 * validado ganhando peca extra numa chapa deliberadamente alta). Quando o
 * SEGUNDO vetor fica diagonal em vez de perpendicular ao primeiro (ex.: a
 * receita "cola-por-aresta" do BRACKET_COMPACTO.DXF, onde v1=(-26,10) nao e
 * nem perto de um eixo), a sobra real e um paralelogramo torto, nao um
 * retangulo - o reaproveitamento nao encontra nada nesse caso (testado:
 * 0 pecas extras, mas tambem 0 falso positivo, so fica conservador demais).
 * Resolver isso de verdade exigiria tratar a sobra como poligono, nao
 * retangulo - fica pra uma proxima iteracao se a diferenca de aproveitamento
 * justificar o esforco.
 *
 * Ainda nao inclui: aproveitamento de vaos (peca pequena no espaco que
 * sobra dentro do encaixe, tipo ENCAKIT), nem escolher entre multiplas
 * orientacoes de chapa (deitada/em pe) - fica para as proximas etapas.
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

        Result(List<Placement> placements, String strategyName, double alignmentDeltaDeg,
               double sheetAreaMm2, double piecesNetAreaMm2, int primaryCount, int reuseCount) {
            this.placements = placements;
            this.strategyName = strategyName;
            this.alignmentDeltaDeg = alignmentDeltaDeg;
            this.sheetAreaMm2 = sheetAreaMm2;
            this.piecesNetAreaMm2 = piecesNetAreaMm2;
            this.usedAreaMm2 = placements.size() * piecesNetAreaMm2;
            this.primaryCount = primaryCount;
            this.reuseCount = reuseCount;
        }
    }

    private SheetPacker() {
    }

    public static Result pack(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                               double marginMm, double gapMm) {
        StrategySelector.Result sel = StrategySelector.select(fullOuter, gapMm);
        NestingRecipe recipe = sel.bestNoMirror;
        if (recipe == null) {
            return new Result(List.of(), "nenhuma", 0, sheetW * sheetH, 0, 0, 0);
        }

        double areaLiquidaPeca = Math.abs(fullOuter.area());
        for (Polygon h : holes) areaLiquidaPeca -= Math.abs(h.area());

        Point2D centroid = GeometryOps.centroid(fullOuter);
        Point2D origin = new Point2D(0, 0);

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
            return new Result(List.of(), recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca, 0, 0);
        }

        List<Placement> accepted = new ArrayList<>();
        List<Polygon> acceptedPolys = new ArrayList<>();

        tileRegion(fullOuter, centroid, alignedCell, av1, av2,
                usableMinX, usableMinY, usableMaxX, usableMaxY, accepted, acceptedPolys);
        int primaryCount = accepted.size();

        // Reaproveitamento de sobra: mesma receita girada 90/180/270 na faixa
        // que sobrou a direita (do fim do que a receita principal ocupou ate
        // a borda util), depois na faixa que sobrou no topo (recalculada
        // depois da faixa direita, ja que ela pode ter avancado o uso em Y).
        double[] used = usedExtent(acceptedPolys, usableMinX, usableMinY);
        tryBestRotationInRegion(fullOuter, centroid, alignedCell, av1, av2,
                used[0], usableMinY, usableMaxX, usableMaxY, accepted, acceptedPolys);

        used = usedExtent(acceptedPolys, usableMinX, usableMinY);
        tryBestRotationInRegion(fullOuter, centroid, alignedCell, av1, av2,
                usableMinX, used[1], usableMaxX, usableMaxY, accepted, acceptedPolys);

        int reuseCount = accepted.size() - primaryCount;
        return new Result(accepted, recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca, primaryCount, reuseCount);
    }

    /** [maxX, maxY] ocupados ate agora (ao menos o minimo util, se nada foi aceito). */
    private static double[] usedExtent(List<Polygon> acceptedPolys, double minX, double minY) {
        double maxX = minX, maxY = minY;
        for (Polygon p : acceptedPolys) {
            double[] bb = p.boundingBox();
            maxX = Math.max(maxX, bb[2]);
            maxY = Math.max(maxY, bb[3]);
        }
        return new double[]{maxX, maxY};
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
    private static void tryBestRotationInRegion(Polygon fullOuter, Point2D centroid,
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
            tileRegion(fullOuter, centroid, rotCell, rv1, rv2,
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
    private static void tileRegion(Polygon fullOuter, Point2D centroid,
                                    List<PlacedPieceInstance> cellInstances, Point2D v1, Point2D v2,
                                    double regionMinX, double regionMinY, double regionMaxX, double regionMaxY,
                                    List<Placement> accepted, List<Polygon> acceptedPolys) {
        double regionW = regionMaxX - regionMinX;
        double regionH = regionMaxY - regionMinY;
        if (regionW <= 0 || regionH <= 0) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (PlacedPieceInstance inst : cellInstances) {
            Polygon poly = inst.materialize(fullOuter, centroid);
            double[] bb = poly.boundingBox();
            minX = Math.min(minX, bb[0]);
            minY = Math.min(minY, bb[1]);
        }

        double shiftX = regionMinX - minX;
        double shiftY = regionMinY - minY;
        double diag = Math.hypot(regionW, regionH);
        int rangeN = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(v1.x, v1.y))) + 2;
        int rangeM = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(v2.x, v2.y))) + 2;

        for (int n = -rangeN; n <= rangeN; n++) {
            for (int m = -rangeM; m <= rangeM; m++) {
                double ox = shiftX + n * v1.x + m * v2.x;
                double oy = shiftY + n * v1.y + m * v2.y;
                for (PlacedPieceInstance inst : cellInstances) {
                    Point2D pos = new Point2D(inst.translation.x + ox, inst.translation.y + oy);
                    PlacedPieceInstance candidate = new PlacedPieceInstance(inst.mirror, inst.rotationDeg, pos);
                    Polygon poly = candidate.materialize(fullOuter, centroid);
                    double[] bb = poly.boundingBox();
                    if (bb[0] < regionMinX - 1e-6 || bb[1] < regionMinY - 1e-6
                            || bb[2] > regionMaxX + 1e-6 || bb[3] > regionMaxY + 1e-6) {
                        continue;
                    }
                    boolean collide = false;
                    for (Polygon other : acceptedPolys) {
                        if (GeometryOps.overlaps(poly, other)) {
                            collide = true;
                            break;
                        }
                    }
                    if (collide) continue;
                    accepted.add(new Placement(candidate.mirror, candidate.rotationDeg, candidate.translation));
                    acceptedPolys.add(poly);
                }
            }
        }
    }
}
