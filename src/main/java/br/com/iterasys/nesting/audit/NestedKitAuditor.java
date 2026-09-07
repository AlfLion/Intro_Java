package br.com.iterasys.nesting.audit;

import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Le um DXF ja nesteado (varias copias de uma ou mais pecas, incluindo o
 * caso de kit: peca grande + peca pequena encaixada no vao da peca grande)
 * e reconstroi, para cada copia encontrada, qual peca ela e, em que
 * rotacao, se esta espelhada e onde esta posicionada.
 *
 * Serve tanto para AUDITAR um arquivo que um humano nesteou a mao (como os
 * exemplos ENCAIXE1/ENCAIXE2/ENCAKIT usados para calibrar o motor) quanto,
 * futuramente, como validador de qualquer exportacao da propria ferramenta.
 *
 * Limitacao atual: a identificacao de rotacao/espelho usa como "impressao
 * digital" o vetor do centroide do contorno externo ate o maior furo da
 * peca, validado contra os furos menores. So funciona em pecas com pelo
 * menos 1 furo assimetricamente posicionado (nao concentrico com o
 * centroide). Pecas sem furo, ou com furos simetricos, precisam de outra
 * estrategia (ex.: casar o contorno inteiro por amostragem) - nao
 * implementada aqui ainda.
 */
public final class NestedKitAuditor {

    private NestedKitAuditor() {
    }

    public static final class CanonicalPiece {
        public final String name;
        public final Polygon outer;
        public final List<Polygon> holes;
        public final double centroidX;
        public final double centroidY;
        public final double area;

        public CanonicalPiece(String name, PieceGeometry geometry) {
            this.name = name;
            this.outer = geometry.getOuter();
            this.holes = geometry.getHoles();
            double[] c = centroid(outer);
            this.centroidX = c[0];
            this.centroidY = c[1];
            this.area = Math.abs(outer.area());
        }
    }

    public static final class DetectedInstance {
        public final CanonicalPiece type;
        public final Polygon outer;
        public final List<Polygon> holes;
        public final double centroidX;
        public final double centroidY;
        public final double area;
        /** NaN se nao foi possivel identificar rotacao (ver limitacao na classe). */
        public final double rotationDeg;
        /** null se rotationDeg for NaN. */
        public final Boolean mirrored;

        DetectedInstance(CanonicalPiece type, Polygon outer, List<Polygon> holes,
                          double centroidX, double centroidY, double area,
                          double rotationDeg, Boolean mirrored) {
            this.type = type;
            this.outer = outer;
            this.holes = holes;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
            this.area = area;
            this.rotationDeg = rotationDeg;
            this.mirrored = mirrored;
        }
    }

    public static List<DetectedInstance> audit(List<CanonicalPiece> canonicals, List<Polygon> allLoops) {
        List<Double> outerAreas = new ArrayList<>();
        List<Double> holeAreas = new ArrayList<>();
        for (CanonicalPiece c : canonicals) {
            outerAreas.add(c.area);
            for (Polygon h : c.holes) holeAreas.add(Math.abs(h.area()));
        }

        List<Polygon> outerLoops = new ArrayList<>();
        List<Polygon> holeLoops = new ArrayList<>();
        for (Polygon p : allLoops) {
            double area = Math.abs(p.area());
            if (closestDistance(area, outerAreas) <= closestDistance(area, holeAreas)) {
                outerLoops.add(p);
            } else {
                holeLoops.add(p);
            }
        }

        List<double[]> outerCentroids = new ArrayList<>();
        for (Polygon o : outerLoops) outerCentroids.add(centroid(o));

        List<List<Polygon>> holesPerInstance = new ArrayList<>();
        for (int i = 0; i < outerLoops.size(); i++) holesPerInstance.add(new ArrayList<>());

        for (Polygon h : holeLoops) {
            double[] c = centroid(h);
            Point2D pc = new Point2D(c[0], c[1]);
            int owner = -1;
            for (int i = 0; i < outerLoops.size(); i++) {
                if (outerLoops.get(i).containsPoint(pc)) { owner = i; break; }
            }
            if (owner < 0) {
                double bestD = Double.MAX_VALUE;
                for (int i = 0; i < outerLoops.size(); i++) {
                    double[] oc = outerCentroids.get(i);
                    double d = Math.hypot(c[0] - oc[0], c[1] - oc[1]);
                    if (d < bestD) { bestD = d; owner = i; }
                }
            }
            holesPerInstance.get(owner).add(h);
        }

        List<DetectedInstance> result = new ArrayList<>();
        for (int i = 0; i < outerLoops.size(); i++) {
            Polygon outer = outerLoops.get(i);
            double area = Math.abs(outer.area());
            double[] c = outerCentroids.get(i);
            List<Polygon> holes = holesPerInstance.get(i);

            CanonicalPiece tipo = null;
            double bestD = Double.MAX_VALUE;
            for (CanonicalPiece cp : canonicals) {
                double d = Math.abs(area - cp.area);
                if (d < bestD) { bestD = d; tipo = cp; }
            }

            double[] rm = matchRotationAndMirror(tipo, c, holes);
            Boolean mirrored = Double.isNaN(rm[0]) ? null : rm[1] == 1;
            result.add(new DetectedInstance(tipo, outer, holes, c[0], c[1], area, rm[0], mirrored));
        }
        return result;
    }

    private static double closestDistance(double value, List<Double> candidates) {
        double best = Double.MAX_VALUE;
        for (double c : candidates) best = Math.min(best, Math.abs(value - c));
        return best;
    }

    /** [rotDeg, mirror(0/1)] ou [NaN,-1] se nao deu pra identificar. */
    private static double[] matchRotationAndMirror(CanonicalPiece canon, double[] instCentroid, List<Polygon> instHoles) {
        if (canon.holes.isEmpty() || instHoles.size() != canon.holes.size()) {
            return new double[]{Double.NaN, -1};
        }
        Polygon bigHoleCanon = biggestHole(canon.holes);
        Polygon bigHoleInst = biggestHole(instHoles);
        double[] hc = centroid(bigHoleCanon);
        double[] hi = centroid(bigHoleInst);
        double hcx = hc[0] - canon.centroidX, hcy = hc[1] - canon.centroidY;
        double hix = hi[0] - instCentroid[0], hiy = hi[1] - instCentroid[1];

        List<double[]> smallCanon = new ArrayList<>();
        for (Polygon h : canon.holes) {
            if (h == bigHoleCanon) continue;
            double[] c = centroid(h);
            smallCanon.add(new double[]{c[0] - canon.centroidX, c[1] - canon.centroidY});
        }
        List<double[]> smallInst = new ArrayList<>();
        for (Polygon h : instHoles) {
            if (h == bigHoleInst) continue;
            double[] c = centroid(h);
            smallInst.add(new double[]{c[0] - instCentroid[0], c[1] - instCentroid[1]});
        }

        double angInst = Math.atan2(hiy, hix);
        for (int mirror = 0; mirror <= 1; mirror++) {
            double scy = mirror == 1 ? -1 : 1;
            double angCanonM = Math.atan2(hcy * scy, hcx);
            double rot = Math.toDegrees(angInst - angCanonM);
            rot = ((rot % 360) + 360) % 360;
            if (validaPequenos(smallCanon, smallInst, rot, scy)) {
                return new double[]{rot, mirror};
            }
        }
        return new double[]{Double.NaN, -1};
    }

    private static boolean validaPequenos(List<double[]> canon, List<double[]> inst, double rotDeg, double scy) {
        if (canon.size() != inst.size()) return false;
        if (canon.isEmpty()) return true;
        double rad = Math.toRadians(rotDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        List<double[]> transf = new ArrayList<>();
        for (double[] c : canon) {
            double x = c[0], y = c[1] * scy;
            transf.add(new double[]{x * cos - y * sin, x * sin + y * cos});
        }
        if (transf.size() == 1) return dist(transf.get(0), inst.get(0)) < 1.0;
        return (dist(transf.get(0), inst.get(0)) < 1.0 && dist(transf.get(1), inst.get(1)) < 1.0)
                || (dist(transf.get(0), inst.get(1)) < 1.0 && dist(transf.get(1), inst.get(0)) < 1.0);
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static Polygon biggestHole(List<Polygon> holes) {
        Polygon best = null;
        double bestArea = -1;
        for (Polygon h : holes) {
            double a = Math.abs(h.area());
            if (a > bestArea) { bestArea = a; best = h; }
        }
        return best;
    }

    private static double[] centroid(Polygon p) {
        List<Point2D> v = p.getVertices();
        double a = 0, cx = 0, cy = 0;
        int n = v.size();
        for (int i = 0; i < n; i++) {
            Point2D p0 = v.get(i), p1 = v.get((i + 1) % n);
            double cross = p0.x * p1.y - p1.x * p0.y;
            a += cross;
            cx += (p0.x + p1.x) * cross;
            cy += (p0.y + p1.y) * cross;
        }
        a /= 2.0;
        return new double[]{cx / (6 * a), cy / (6 * a)};
    }
}
