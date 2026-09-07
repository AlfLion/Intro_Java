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
 * o maior vetor de rede a um eixo da chapa, e ladrilha de verdade dentro de
 * uma chapa WxH com margem - aceitando cada peca so depois de validar
 * geometria real (nunca confia so na matematica da rede; mesma licao que o
 * V49 ja registrava em comentario: amostragem/aproximacao pode dar falso
 * positivo de "cabe").
 *
 * Nao inclui ainda: aproveitamento de vaos (peca pequena no espaco que
 * sobra), reaproveitar a receita girada 90/180/270 pra preencher sobra de
 * formato diferente, nem escolher entre multiplas orientacoes de chapa
 * (deitada/em pe) - fica para as proximas etapas.
 */
public final class SheetPacker {

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

        Result(List<Placement> placements, String strategyName, double alignmentDeltaDeg,
               double sheetAreaMm2, double piecesNetAreaMm2) {
            this.placements = placements;
            this.strategyName = strategyName;
            this.alignmentDeltaDeg = alignmentDeltaDeg;
            this.sheetAreaMm2 = sheetAreaMm2;
            this.piecesNetAreaMm2 = piecesNetAreaMm2;
            this.usedAreaMm2 = placements.size() * piecesNetAreaMm2;
        }
    }

    private SheetPacker() {
    }

    public static Result pack(Polygon fullOuter, List<Polygon> holes, double sheetW, double sheetH,
                               double marginMm, double gapMm) {
        StrategySelector.Result sel = StrategySelector.select(fullOuter, gapMm);
        NestingRecipe recipe = sel.bestNoMirror;
        if (recipe == null) {
            return new Result(List.of(), "nenhuma", 0, sheetW * sheetH, 0);
        }

        double areaLiquidaPeca = Math.abs(fullOuter.area());
        for (Polygon h : holes) areaLiquidaPeca -= Math.abs(h.area());

        Point2D centroid = GeometryOps.centroid(fullOuter);
        Point2D origin = new Point2D(0, 0);

        Point2D v1 = recipe.latticeV1;
        Point2D v2 = recipe.latticeV2;
        Point2D longer = Math.hypot(v1.x, v1.y) >= Math.hypot(v2.x, v2.y) ? v1 : v2;
        double delta = OrientationAligner.snapToAxisDelta(Math.toDegrees(Math.atan2(longer.y, longer.x)));

        List<PlacedPieceInstance> alignedCell = new ArrayList<>();
        for (PlacedPieceInstance inst : recipe.cellInstances) {
            Point2D rotatedTranslation = inst.translation.rotateDeg(delta, origin);
            alignedCell.add(new PlacedPieceInstance(inst.mirror, inst.rotationDeg + delta, rotatedTranslation));
        }
        Point2D av1 = v1.rotateDeg(delta, origin);
        Point2D av2 = v2.rotateDeg(delta, origin);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        List<Polygon> refCell = new ArrayList<>();
        for (PlacedPieceInstance inst : alignedCell) {
            Polygon poly = inst.materialize(fullOuter, centroid);
            refCell.add(poly);
            double[] bb = poly.boundingBox();
            minX = Math.min(minX, bb[0]);
            minY = Math.min(minY, bb[1]);
        }

        double usableW = sheetW - 2 * marginMm;
        double usableH = sheetH - 2 * marginMm;
        if (usableW <= 0 || usableH <= 0) {
            return new Result(List.of(), recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca);
        }

        double shiftX = marginMm - minX;
        double shiftY = marginMm - minY;
        double diag = Math.hypot(usableW, usableH);
        int rangeN = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(av1.x, av1.y))) + 2;
        int rangeM = (int) Math.ceil(diag / Math.max(1.0, Math.hypot(av2.x, av2.y))) + 2;

        List<Placement> accepted = new ArrayList<>();
        List<Polygon> acceptedPolys = new ArrayList<>();

        for (int n = -rangeN; n <= rangeN; n++) {
            for (int m = -rangeM; m <= rangeM; m++) {
                double ox = shiftX + n * av1.x + m * av2.x;
                double oy = shiftY + n * av1.y + m * av2.y;
                for (PlacedPieceInstance inst : alignedCell) {
                    Point2D pos = new Point2D(inst.translation.x + ox, inst.translation.y + oy);
                    PlacedPieceInstance candidate = new PlacedPieceInstance(inst.mirror, inst.rotationDeg, pos);
                    Polygon poly = candidate.materialize(fullOuter, centroid);
                    double[] bb = poly.boundingBox();
                    if (bb[0] < marginMm - 1e-6 || bb[1] < marginMm - 1e-6
                            || bb[2] > sheetW - marginMm + 1e-6 || bb[3] > sheetH - marginMm + 1e-6) {
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

        return new Result(accepted, recipe.strategyName, delta, sheetW * sheetH, areaLiquidaPeca);
    }
}
