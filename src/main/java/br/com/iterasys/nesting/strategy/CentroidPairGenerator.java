package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interlocking theta / theta+180 em torno do PROPRIO centroide da peca -
 * validado em 3 exemplos reais nesta sessao (15746A, bracket ENCAIXE1/2, as
 * 2 pecas grandes do ENCAKIT). Tambem testa a variante espelhada (marcando
 * usesMirror na celula resultante - quem chama decide se pede autorizacao).
 *
 * Limitacao conhecida (ja diagnosticada com a 15746A): quando as duas
 * copias, centradas no mesmo centroide, simplesmente NAO se sobrepoem em
 * nenhuma direcao (peca fina/concava que ocupa so uma fatia angular), essa
 * tecnica degenera - o candidato e descartado aqui, e a receita fileira-vs-
 * fileira de verdade (usando a silhueta de varias pecas, nao um par) fica
 * para uma proxima etapa.
 */
public final class CentroidPairGenerator implements PairGenerator {

    private static final double THETA_STEP_DEG = 5.0;
    private static final double COARSE_DIR_STEP_DEG = 6.0;
    private static final double FINE_DIR_STEP_DEG = 2.0;
    private static final int TOP_CANDIDATES = 6;
    private static final double DEGENERATE_EPS_MM = 0.05;

    @Override
    public String name() {
        return "interlock-centroide";
    }

    @Override
    public List<List<PlacedPieceInstance>> proposeCells(Polygon piece, Point2D centroid, double gapMm) {
        Polygon pieceA = new PlacedPieceInstance(false, 0, new Point2D(0, 0)).materialize(piece, centroid);

        List<double[]> ranked = new ArrayList<>(); // [theta, mirror(0/1), proxyDist]
        for (double theta = 0; theta < 180; theta += THETA_STEP_DEG) {
            for (int m = 0; m <= 1; m++) {
                boolean mirror = m == 1;
                Polygon pieceB = new PlacedPieceInstance(mirror, theta + 180, new Point2D(0, 0)).materialize(piece, centroid);
                double bestD = bestContactDistance(pieceA, pieceB, COARSE_DIR_STEP_DEG, gapMm);
                if (bestD < Double.MAX_VALUE && bestD >= DEGENERATE_EPS_MM) {
                    ranked.add(new double[]{theta, m, bestD});
                }
            }
        }
        ranked.sort(Comparator.comparingDouble(a -> a[2]));

        List<List<PlacedPieceInstance>> out = new ArrayList<>();
        int top = Math.min(TOP_CANDIDATES, ranked.size());
        for (int i = 0; i < top; i++) {
            double theta = ranked.get(i)[0];
            boolean mirror = ranked.get(i)[1] == 1;
            Polygon pieceB = new PlacedPieceInstance(mirror, theta + 180, new Point2D(0, 0)).materialize(piece, centroid);
            double[] vec = bestContactVector(pieceA, pieceB, FINE_DIR_STEP_DEG, gapMm);
            if (vec == null) continue;
            out.add(List.of(
                    new PlacedPieceInstance(false, 0, new Point2D(0, 0)),
                    new PlacedPieceInstance(mirror, theta + 180, new Point2D(vec[0], vec[1]))
            ));
        }
        return out;
    }

    private static double bestContactDistance(Polygon a, Polygon b, double dirStepDeg, double gapMm) {
        double best = Double.MAX_VALUE;
        for (double deg = 0; deg < 360; deg += dirStepDeg) {
            double rad = Math.toRadians(deg);
            Point2D dir = new Point2D(Math.cos(rad), Math.sin(rad));
            double d = GeometryOps.minSeparation(a, b, dir, 3000, 0.1);
            if (!Double.isNaN(d) && d < best) best = d;
        }
        return best;
    }

    private static double[] bestContactVector(Polygon a, Polygon b, double dirStepDeg, double gapMm) {
        double bestD = Double.MAX_VALUE, bestDeg = 0;
        for (double deg = 0; deg < 360; deg += dirStepDeg) {
            double rad = Math.toRadians(deg);
            Point2D dir = new Point2D(Math.cos(rad), Math.sin(rad));
            double d = GeometryOps.minSeparation(a, b, dir, 3000, 0.05);
            if (!Double.isNaN(d) && d >= DEGENERATE_EPS_MM && d < bestD) {
                bestD = d;
                bestDeg = deg;
            }
        }
        if (bestD == Double.MAX_VALUE) return null;
        double total = bestD + gapMm;
        return new double[]{total * Math.cos(Math.toRadians(bestDeg)), total * Math.sin(Math.toRadians(bestDeg))};
    }
}
