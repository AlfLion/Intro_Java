package br.com.iterasys.nesting.geometry;

import java.util.List;

/**
 * Operacoes de colisao/separacao entre poligonos simples (podem ser concavos,
 * sem furos). Usadas para aproximar o No-Fit Polygon por varredura radial
 * (ver {@code br.com.iterasys.nesting.strategy.LatticeFinder}) em vez de um
 * NFP exato por "orbiting", que fica para uma proxima etapa quando o motor
 * precisar de busca em GA.
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

    /**
     * Simplificacao de Douglas-Peucker: reduz o numero de vertices mantendo
     * a forma dentro de {@code epsilonMm}. Usado para acelerar buscas (a
     * discretizacao fina de arcos do parser gera centenas de pontos quase
     * colineares) - a peca de resolucao plena deve ser usada de novo para a
     * VALIDACAO final do candidato vencedor, nunca so a versao simplificada.
     */
    public static Polygon simplify(Polygon p, double epsilonMm) {
        List<Point2D> v = p.getVertices();
        if (v.size() <= 3) return p;
        List<Point2D> reduced = douglasPeucker(v, epsilonMm);
        return new Polygon(reduced);
    }

    private static List<Point2D> douglasPeucker(List<Point2D> pts, double epsilon) {
        int n = pts.size();
        if (n < 3) return new java.util.ArrayList<>(pts);
        Point2D first = pts.get(0);
        Point2D last = pts.get(n - 1);
        double maxDist = -1;
        int index = -1;
        for (int i = 1; i < n - 1; i++) {
            double d = perpendicularDistance(pts.get(i), first, last);
            if (d > maxDist) { maxDist = d; index = i; }
        }
        if (maxDist > epsilon) {
            List<Point2D> left = douglasPeucker(pts.subList(0, index + 1), epsilon);
            List<Point2D> right = douglasPeucker(pts.subList(index, n), epsilon);
            List<Point2D> result = new java.util.ArrayList<>(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        }
        List<Point2D> result = new java.util.ArrayList<>();
        result.add(first);
        result.add(last);
        return result;
    }

    private static double perpendicularDistance(Point2D p, Point2D a, Point2D b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) return p.distanceTo(a);
        return Math.abs((p.x - a.x) * dy - (p.y - a.y) * dx) / len;
    }

    /** Centroide real (area-ponderado) do poligono - equivariante a rotacao/translacao rigida. */
    public static Point2D centroid(Polygon p) {
        List<Point2D> v = p.getVertices();
        double a = 0, cx = 0, cy = 0;
        int n = v.size();
        for (int i = 0; i < n; i++) {
            Point2D p0 = v.get(i);
            Point2D p1 = v.get((i + 1) % n);
            double cross = p0.x * p1.y - p1.x * p0.y;
            a += cross;
            cx += (p0.x + p1.x) * cross;
            cy += (p0.y + p1.y) * cross;
        }
        a /= 2.0;
        return new Point2D(cx / (6 * a), cy / (6 * a));
    }

    public static boolean overlapsAny(List<Polygon> a, List<Polygon> b) {
        for (Polygon pa : a) {
            for (Polygon pb : b) {
                if (overlaps(pa, pb)) return true;
            }
        }
        return false;
    }

    private static List<Polygon> translateAll(List<Polygon> polys, Point2D vector) {
        List<Polygon> out = new java.util.ArrayList<>(polys.size());
        for (Polygon p : polys) out.add(p.translated(vector));
        return out;
    }

    /**
     * Generalizacao de {@link #minSeparation} para CONJUNTOS de poligonos (uma
     * "celula" com mais de uma peca, tratada como corpo rigido). Menor distancia
     * de translacao ao longo de {@code direction} para que nenhum poligono de
     * {@code moving} sobreponha nenhum poligono de {@code fixedSet}.
     */
    public static double minSeparationAny(List<Polygon> fixedSet, List<Polygon> moving, Point2D direction,
                                           double maxDistance, double tolerance) {
        double len = Math.hypot(direction.x, direction.y);
        Point2D dir = new Point2D(direction.x / len, direction.y / len);

        if (!overlapsAny(fixedSet, moving)) {
            return 0.0;
        }
        double lo = 0;
        double hi = maxDistance;
        if (overlapsAny(fixedSet, translateAll(moving, dir.scale(hi)))) {
            return Double.NaN;
        }
        while (hi - lo > tolerance) {
            double mid = (lo + hi) / 2.0;
            if (overlapsAny(fixedSet, translateAll(moving, dir.scale(mid)))) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }
}
