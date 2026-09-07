package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.List;

/**
 * Dada uma "celula" (uma ou mais pecas ja posicionadas uma em relacao a
 * outra), acha os 2 vetores de translacao que repetem essa celula pelo
 * plano sem sobrepor nem violar o gap - isto e, o reticulado (lattice) de
 * tiling. E o mesmo raciocinio que aplicamos manualmente a 15746A (passo
 * dentro da fileira + vetor fileira-fileira), generalizado pra qualquer
 * celula: varre direcoes ao redor da celula, acha a distancia de contato em
 * cada uma (perfil polar do NFP da celula contra si mesma), usa a direcao
 * de menor distancia como primeiro vetor e, entre as direcoes
 * suficientemente independentes dele, a que minimiza a area do
 * paralelogramo formado (nao necessariamente a segunda menor distancia -
 * foi exatamente esse padrao que vimos no exemplo real do bracket, onde o
 * vetor de interlocking vencedor NAO era o de menor modulo isolado).
 */
public final class LatticeFinder {

    public static final class Lattice {
        public final Point2D v1;
        public final Point2D v2;
        public final double cellAreaMm2;

        Lattice(Point2D v1, Point2D v2, double cellAreaMm2) {
            this.v1 = v1;
            this.v2 = v2;
            this.cellAreaMm2 = cellAreaMm2;
        }
    }

    private LatticeFinder() {
    }

    public static Lattice find(List<Polygon> cell, double gapMm, double angleStepDeg,
                                double maxDistance, double tolerance) {
        int steps = (int) Math.round(360.0 / angleStepDeg);
        double[] dist = new double[steps];
        for (int i = 0; i < steps; i++) {
            double rad = Math.toRadians(i * angleStepDeg);
            Point2D dir = new Point2D(Math.cos(rad), Math.sin(rad));
            double d = GeometryOps.minSeparationAny(cell, cell, dir, maxDistance, tolerance);
            dist[i] = (Double.isNaN(d) || d <= 0) ? Double.POSITIVE_INFINITY : d;
        }

        int i1 = -1;
        for (int i = 0; i < steps; i++) {
            if (i1 < 0 || dist[i] < dist[i1]) i1 = i;
        }
        if (i1 < 0 || Double.isInfinite(dist[i1])) return null;

        double deg1 = i1 * angleStepDeg;
        Point2D v1 = vector(dist[i1] + gapMm, deg1);

        int i2 = -1;
        double bestCross = Double.MAX_VALUE;
        double minSeparationDeg = 15.0;
        Point2D bestV2 = null;
        for (int i = 0; i < steps; i++) {
            if (Double.isInfinite(dist[i])) continue;
            double deg = i * angleStepDeg;
            double diff = angularDifferenceDeg(deg, deg1);
            if (diff < minSeparationDeg || diff > 180 - minSeparationDeg) continue;
            Point2D cand = vector(dist[i] + gapMm, deg);
            double cross = Math.abs(v1.x * cand.y - v1.y * cand.x);
            if (cross >= bestCross) continue;
            // v1 e v2 sozinhos nao colidirem nao basta: os vizinhos diagonais
            // da rede (v1+v2 e v1-v2) tambem precisam ser posicoes validas,
            // senao a "rede" e matematicamente consistente mas fisicamente
            // colide (bug real encontrado testando contra TRAP.DXF: um v2
            // quase antiparalelo a v1 passava nos dois testes isolados e
            // dava area/peca impossivel, porque v1+v2 colidia de verdade).
            if (!diagonalNeighborsValid(cell, v1, cand, maxDistance)) continue;
            bestCross = cross;
            i2 = i;
            bestV2 = cand;
        }
        if (i2 < 0) return null;

        return new Lattice(v1, bestV2, Math.abs(v1.x * bestV2.y - v1.y * bestV2.x));
    }

    private static boolean diagonalNeighborsValid(List<Polygon> cell, Point2D v1, Point2D v2, double maxDistance) {
        Point2D sum = new Point2D(v1.x + v2.x, v1.y + v2.y);
        Point2D diff = new Point2D(v1.x - v2.x, v1.y - v2.y);
        return !overlapsAt(cell, sum) && !overlapsAt(cell, diff);
    }

    private static boolean overlapsAt(List<Polygon> cell, Point2D offset) {
        List<Polygon> moved = new java.util.ArrayList<>(cell.size());
        for (Polygon p : cell) moved.add(p.translated(offset));
        return GeometryOps.overlapsAny(cell, moved);
    }

    private static Point2D vector(double len, double angDeg) {
        double rad = Math.toRadians(angDeg);
        return new Point2D(len * Math.cos(rad), len * Math.sin(rad));
    }

    private static double angularDifferenceDeg(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }
}
