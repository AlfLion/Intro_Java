package br.com.iterasys.nesting.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstrucao parametrica da peca 15746A ("banana": segmento de anel curvo)
 * a partir das medidas descritas manualmente:
 * - espessura radial constante 13,00 mm
 * - varredura do arco externo e interno, ambas 89,9 graus
 * - arcos defasados 15 graus entre si (por isso nao e um setor anelar simples)
 * - 2 pontas arredondadas fechando o contorno
 *
 * Nao ha DXF anexado nesta sessao: o raio externo e calibrado numericamente
 * para que a area do poligono resultante bata com a area real medida
 * (~5.474,6 mm^2). Quando o DXF/X_T real estiver disponivel, substituir este
 * gerador por um parser real e comparar bounding box / area como regressao.
 */
public final class PieceFactory {

    public static final double THICKNESS_MM = 13.0;
    public static final double SWEEP_OUTER_DEG = 89.9;
    public static final double SWEEP_INNER_DEG = 89.9;
    public static final double ANGULAR_OFFSET_DEG = 15.0;

    private static final int ARC_STEPS = 180;
    private static final int TIP_STEPS = 40;

    private PieceFactory() {
    }

    public static Polygon build15746A(double outerRadiusMm) {
        double ro = outerRadiusMm;
        double ri = ro - THICKNESS_MM;

        double outerStart = 0.0;
        double outerEnd = SWEEP_OUTER_DEG;
        double innerStart = ANGULAR_OFFSET_DEG;
        double innerEnd = ANGULAR_OFFSET_DEG + SWEEP_INNER_DEG;

        List<Point2D> pts = new ArrayList<>();

        for (int i = 0; i <= ARC_STEPS; i++) {
            double a = Math.toRadians(outerStart + (outerEnd - outerStart) * i / ARC_STEPS);
            pts.add(new Point2D(ro * Math.cos(a), ro * Math.sin(a)));
        }

        Point2D outerEndPt = pts.get(pts.size() - 1);
        Point2D innerEndPt = new Point2D(
                ri * Math.cos(Math.toRadians(innerEnd)),
                ri * Math.sin(Math.toRadians(innerEnd)));
        addRoundedTip(pts, outerEndPt, innerEndPt);

        for (int i = 0; i <= ARC_STEPS; i++) {
            double a = Math.toRadians(innerEnd - (innerEnd - innerStart) * i / ARC_STEPS);
            pts.add(new Point2D(ri * Math.cos(a), ri * Math.sin(a)));
        }

        Point2D innerStartPt = pts.get(pts.size() - 1);
        Point2D outerStartPt = new Point2D(
                ro * Math.cos(Math.toRadians(outerStart)),
                ro * Math.sin(Math.toRadians(outerStart)));
        addRoundedTip(pts, innerStartPt, outerStartPt);

        return new Polygon(pts);
    }

    /**
     * Ponta arredondada: semicirculo cujo diametro e a corda entre "from" e
     * "to" (centro = ponto medio, raio = metade da corda), escolhendo o lado
     * que se afasta da origem (bojo para fora da peca).
     */
    private static void addRoundedTip(List<Point2D> pts, Point2D from, Point2D to) {
        Point2D center = new Point2D((from.x + to.x) / 2.0, (from.y + to.y) / 2.0);
        double radius = from.distanceTo(to) / 2.0;
        double startAngle = Math.atan2(from.y - center.y, from.x - center.x);

        double midAngleCcw = startAngle + Math.PI / 2.0;
        double ccwMidX = center.x + radius * Math.cos(midAngleCcw);
        double ccwMidY = center.y + radius * Math.sin(midAngleCcw);
        double distCcw = Math.hypot(ccwMidX, ccwMidY);
        double distCenter = Math.hypot(center.x, center.y);
        double sign = distCcw >= distCenter ? 1.0 : -1.0;

        for (int i = 1; i < TIP_STEPS; i++) {
            double a = startAngle + sign * Math.PI * i / TIP_STEPS;
            pts.add(new Point2D(center.x + radius * Math.cos(a), center.y + radius * Math.sin(a)));
        }
    }

    /** Busca binaria do raio externo cuja area do poligono bate com targetAreaMm2. */
    public static double calibrateOuterRadius(double targetAreaMm2, double lo, double hi) {
        for (int iter = 0; iter < 60; iter++) {
            double mid = (lo + hi) / 2.0;
            double area = build15746A(mid).area();
            if (area < targetAreaMm2) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }
}
