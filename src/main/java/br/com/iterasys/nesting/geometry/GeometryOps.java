package br.com.iterasys.nesting.geometry;

import java.util.List;

/**
 * Operacoes de colisao/separacao entre poligonos simples (podem ser concavos,
 * sem furos). Usadas para aproximar o No-Fit Polygon por varredura radial
 * (ver {@code NfpRadialScanner}) em vez de um NFP exato por "orbiting", que
 * fica para uma proxima etapa quando o motor precisar de busca em GA.
 */
public final class GeometryOps {

    private GeometryOps() {
    }

    public static boolean segmentsIntersect(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        double d1 = cross(p3, p4, p1);
        double d2 = cross(p3, p4, p2);
        double d3 = cross(p1, p2, p3);
        double d4 = cross(p1, p2, p4);

        if (((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))) {
            return true;
        }
        if (d1 == 0 && onSegment(p3, p4, p1)) return true;
        if (d2 == 0 && onSegment(p3, p4, p2)) return true;
        if (d3 == 0 && onSegment(p1, p2, p3)) return true;
        return d4 == 0 && onSegment(p1, p2, p4);
    }

    private static double cross(Point2D a, Point2D b, Point2D c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static boolean onSegment(Point2D a, Point2D b, Point2D p) {
        return Math.min(a.x, b.x) <= p.x && p.x <= Math.max(a.x, b.x)
                && Math.min(a.y, b.y) <= p.y && p.y <= Math.max(a.y, b.y);
    }

    public static boolean overlaps(Polygon a, Polygon b) {
        double[] boxA = a.boundingBox();
        double[] boxB = b.boundingBox();
        if (boxA[2] < boxB[0] || boxB[2] < boxA[0] || boxA[3] < boxB[1] || boxB[3] < boxA[1]) {
            return false;
        }

        List<Point2D> va = a.getVertices();
        List<Point2D> vb = b.getVertices();
        int na = va.size();
        int nb = vb.size();
        for (int i = 0; i < na; i++) {
            Point2D a1 = va.get(i);
            Point2D a2 = va.get((i + 1) % na);
            for (int j = 0; j < nb; j++) {
                Point2D b1 = vb.get(j);
                Point2D b2 = vb.get((j + 1) % nb);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }
        return a.containsPoint(vb.get(0)) || b.containsPoint(va.get(0));
    }

    /**
     * Menor distancia de translacao ao longo de {@code direction} (a partir da
     * posicao atual de {@code moving}) para que {@code moving} pare de sobrepor
     * {@code fixedPoly}. Retorna 0 se ja nao ha sobreposicao. Retorna NaN se nem em
     * {@code maxDistance} a sobreposicao cessa.
     */
    public static double minSeparation(Polygon fixedPoly, Polygon moving, Point2D direction,
                                        double maxDistance, double tolerance) {
        double len = Math.hypot(direction.x, direction.y);
        Point2D dir = new Point2D(direction.x / len, direction.y / len);

        if (!overlaps(fixedPoly, moving)) {
            return 0.0;
        }

        double lo = 0;
        double hi = maxDistance;
        if (overlaps(fixedPoly, moving.translated(dir.scale(hi)))) {
            return Double.NaN;
        }
        while (hi - lo > tolerance) {
            double mid = (lo + hi) / 2.0;
            if (overlaps(fixedPoly, moving.translated(dir.scale(mid)))) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }
}
