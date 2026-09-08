package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "A ferramenta entende a peca e escolhe a melhor estrategia": em vez de um
 * classificador que adivinha o tipo de peca, roda VARIOS mecanismos de
 * encaixe (ver {@link PairGenerator}) sobre a mesma peca, mede mm^2/peca de
 * cada um com a mesma regua ({@link LatticeFinder}) e deixa o numero
 * decidir. A peca "revela" sozinha qual mecanismo serve melhor - um
 * trapezio ganha muito com cola-por-aresta (quase 100% teorico), um bracket
 * com furos ganha com interlocking em torno do centroide.
 *
 * Trava de espelhamento (regra de negocio): o resultado separa a melhor
 * receita SEM espelho (segura, pronta pra usar) da melhor receita entre
 * TODAS incluindo espelho. Se a melhor global usa espelho e bate
 * significativamente a melhor sem espelho, quem chama deve pedir
 * autorizacao explicita antes de usar - nunca aplicar automaticamente.
 */
public final class StrategySelector {

    private static final double SIMPLIFY_EPSILON_MM = 0.4;
    private static final double LATTICE_ANGLE_STEP_DEG = 4.0;
    private static final double LATTICE_MAX_DISTANCE_MM = 4000;
    private static final double LATTICE_TOLERANCE_MM = 0.15;

    public static final class Result {
        public final NestingRecipe bestNoMirror;
        public final NestingRecipe bestOverall;
        public final List<NestingRecipe> allEvaluated;

        Result(NestingRecipe bestNoMirror, NestingRecipe bestOverall, List<NestingRecipe> allEvaluated) {
            this.bestNoMirror = bestNoMirror;
            this.bestOverall = bestOverall;
            this.allEvaluated = allEvaluated;
        }

        public boolean mirrorPending() {
            return bestOverall != null && bestOverall.usesMirror
                    && (bestNoMirror == null || bestOverall.areaPerPieceMm2Gross < bestNoMirror.areaPerPieceMm2Gross - 1e-6);
        }
    }

    private StrategySelector() {
    }

    /**
     * Roda os 3 mecanismos de encaixe (orientacao unica, interlock em torno
     * do centroide, cola-por-aresta) e escolhe pelo mm^2/peca. Rapido o
     * bastante pra rodar sempre (nao produz posicoes de peca, so a
     * "receita" - ver {@code SheetPacker.estimate} pra uma previa numerica
     * baseada nisso, e {@code SheetPacker.pack} pra desenhar as posicoes de
     * verdade, essa sim cara o bastante pra pedir autorizacao antes).
     */
    public static Result select(Polygon fullResolutionOuter, double gapMm) {
        Polygon simplified = GeometryOps.simplify(fullResolutionOuter, SIMPLIFY_EPSILON_MM);
        Point2D centroid = GeometryOps.centroid(simplified);

        List<PairGenerator> generators = List.of(
                new SingleOrientationGenerator(),
                new CentroidPairGenerator(),
                new EdgeGluePairGenerator(15.0));

        List<NestingRecipe> all = new ArrayList<>();
        for (PairGenerator gen : generators) {
            for (List<PlacedPieceInstance> cellInstances : gen.proposeCells(simplified, centroid, gapMm)) {
                List<Polygon> materialized = new ArrayList<>();
                boolean usesMirror = false;
                for (PlacedPieceInstance inst : cellInstances) {
                    materialized.add(inst.materialize(simplified, centroid));
                    usesMirror |= inst.mirror;
                }
                LatticeFinder.Lattice lat = LatticeFinder.find(
                        materialized, gapMm, LATTICE_ANGLE_STEP_DEG, LATTICE_MAX_DISTANCE_MM, LATTICE_TOLERANCE_MM);
                if (lat == null || lat.cellAreaMm2 <= 0) continue;
                double areaPerPiece = lat.cellAreaMm2 / cellInstances.size();
                all.add(new NestingRecipe(gen.name(), cellInstances, lat.v1, lat.v2, areaPerPiece, usesMirror));
            }
        }

        all.sort(Comparator.comparingDouble(r -> r.areaPerPieceMm2Gross));
        NestingRecipe bestOverall = all.isEmpty() ? null : all.get(0);
        NestingRecipe bestNoMirror = all.stream().filter(r -> !r.usesMirror).findFirst().orElse(null);
        return new Result(bestNoMirror, bestOverall, all);
    }
}
