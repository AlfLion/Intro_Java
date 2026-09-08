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
     * Testa se dois poligonos se sobrepoem OU se a distancia real minima
     * entre eles (aresta-a-aresta) fica abaixo de {@code threshold} - com
     * early-exit assim que confirma (nao precisa achar a distancia minima
     * exata pra rejeitar). Diferente de {@link #overlaps}, que so prova
     * ausencia de sobreposicao (distancia > 0), este metodo prova uma folga
     * MINIMA real entre os dois poligonos. Usado para respeitar o gapMm
     * pedido em todo lugar que antes so testava sobreposicao zero (bug real:
     * ver historico de {@code SheetPacker#tileRegion} e
     * {@code LatticeFinder#diagonalNeighborsValid}).
     */
    public static boolean closerThan(Polygon a, Polygon b, double threshold) {
        if (overlaps(a, b)) return true;
        if (threshold <= 0) return false;
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
                if (segmentDistance(a1, a2, b1, b2) < threshold) return true;
            }
        }
        return false;
    }

    private static double segmentDistance(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        return Math.min(
                Math.min(distPointSeg(p1, p3, p4), distPointSeg(p2, p3, p4)),
                Math.min(distPointSeg(p3, p1, p2), distPointSeg(p4, p1, p2)));
    }

    private static double distPointSeg(Point2D p, Point2D a, Point2D b) {
        double abx = b.x - a.x, aby = b.y - a.y;
        double denom = abx * abx + aby * aby;
        double t = denom < 1e-12 ? 0 : ((p.x - a.x) * abx + (p.y - a.y) * aby) / denom;
        t = Math.max(0, Math.min(1, t));
        double cx = a.x + abx * t, cy = a.y + aby * t;
        return Math.hypot(p.x - cx, p.y - cy);
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

    /**
     * Fecho convexo (monotone chain de Andrew). Usado como filtro rapido e
     * EXATO de colisao: o fecho e sempre um superconjunto do poligono
     * original, entao "fechos nao se sobrepoem" implica com certeza
     * "poligonos nao se sobrepoem" (zero risco de falso negativo) - so
     * quando os fechos SE sobrepoem e preciso o teste caro aresta-a-aresta
     * de verdade (fecho sobrepor nao implica poligono concavo sobrepor).
     * Como fecho convexo comuta com transformacao afim (espelho+rotacao+
     * translacao), dá pra calcular o fecho da peca UMA VEZ e so transformar
     * os poucos vertices do fecho a cada posicionamento, em vez de
     * recalcular pra cada instancia - ver {@code packing.HullIndex}.
     */
    public static Polygon convexHull(Polygon p) {
        List<Point2D> pts = new java.util.ArrayList<>(p.getVertices());
        pts.sort((a, b) -> a.x != b.x ? Double.compare(a.x, b.x) : Double.compare(a.y, b.y));
        // remove duplicatas consecutivas apos ordenar, pra nao confundir o cross-product
        List<Point2D> uniq = new java.util.ArrayList<>();
        for (Point2D pt : pts) {
            if (uniq.isEmpty() || pt.distanceTo(uniq.get(uniq.size() - 1)) > 1e-9) uniq.add(pt);
        }
        int n = uniq.size();
        if (n < 3) return new Polygon(uniq);

        Point2D[] lower = new Point2D[n];
        int lowerSize = 0;
        for (Point2D pt : uniq) {
            while (lowerSize >= 2 && cross(lower[lowerSize - 2], lower[lowerSize - 1], pt) <= 0) lowerSize--;
            lower[lowerSize++] = pt;
        }
        Point2D[] upper = new Point2D[n];
        int upperSize = 0;
        for (int i = n - 1; i >= 0; i--) {
            Point2D pt = uniq.get(i);
            while (upperSize >= 2 && cross(upper[upperSize - 2], upper[upperSize - 1], pt) <= 0) upperSize--;
            upper[upperSize++] = pt;
        }
        List<Point2D> hull = new java.util.ArrayList<>(lowerSize + upperSize - 2);
        for (int i = 0; i < lowerSize - 1; i++) hull.add(lower[i]);
        for (int i = 0; i < upperSize - 1; i++) hull.add(upper[i]);
        return new Polygon(hull);
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
