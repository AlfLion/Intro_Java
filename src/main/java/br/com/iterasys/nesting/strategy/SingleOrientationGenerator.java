package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Estrategia de base (sempre disponivel, funciona pra qualquer peca): so
 * gira a peca sozinha e deixa o {@link LatticeFinder} achar a melhor grade
 * pra essa unica orientacao. E o "piso" contra o qual as outras estrategias
 * (interlocking, cola-por-aresta) precisam ganhar pra valer a pena.
 */
public final class SingleOrientationGenerator implements PairGenerator {

    private static final double ANGLE_STEP_DEG = 5.0;
    private static final int TOP_CANDIDATES = 5;

    @Override
    public String name() {
        return "orientacao-unica";
    }

    @Override
    public List<List<PlacedPieceInstance>> proposeCells(Polygon piece, Point2D centroid, double gapMm) {
        List<double[]> ranked = new ArrayList<>(); // [theta, proxyPitch]
        for (double theta = 0; theta < 180; theta += ANGLE_STEP_DEG) {
            Polygon rotated = new PlacedPieceInstance(false, theta, new Point2D(0, 0)).materialize(piece, centroid);
            double d = GeometryOps.minSeparation(rotated, rotated, new Point2D(1, 0), 3000, 0.1);
            if (!Double.isNaN(d) && d > 0) {
                ranked.add(new double[]{theta, d});
            }
        }
        ranked.sort(Comparator.comparingDouble(a -> a[1]));

        List<List<PlacedPieceInstance>> out = new ArrayList<>();
        int top = Math.min(TOP_CANDIDATES, ranked.size());
        for (int i = 0; i < top; i++) {
            double theta = ranked.get(i)[0];
            out.add(List.of(new PlacedPieceInstance(false, theta, new Point2D(0, 0))));
        }
        return out;
    }
}
