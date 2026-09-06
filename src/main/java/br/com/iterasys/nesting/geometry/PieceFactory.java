package br.com.iterasys.nesting.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstrucao da peca 15746A a partir das entidades exatas do DXF real
 * (15746A.DXF, secao ENTITIES): 4 arcos concentricos na origem + 4 linhas
 * radiais curtas ligando-os. Nao e uma anel simples de 2 arcos: cada ponta
 * tem um "degrau" - um arco intermediario de 15 graus a um raio proprio,
 * ligado ao arco externo e ao interno por segmentos radiais curtos.
 *
 * Constantes abaixo copiadas diretamente dos grupos 10/20/40/50/51 do DXF
 * (todos os arcos estao centrados em (0,0)):
 * - arco externo: R=275.5854459725008, de 352.5 a 82.4 graus (sweep 89.9)
 * - arco intermediario "A" (ponta perto de 82.4-97.4): R=268.9854459724937, 15 graus
 * - arco interno: R=262.5854459725003, de 7.5 a 97.4 graus (sweep 89.9)
 * - arco intermediario "B" (ponta perto de 352.5-7.5): R=269.1854459725004, 15 graus
 */
public final class PieceFactory {

    public static final double R_OUTER = 275.5854459725008;
    public static final double R_MID_A = 268.9854459724937;
    public static final double R_INNER = 262.5854459725003;
    public static final double R_MID_B = 269.1854459725004;

    public static final double ANGLE_OUTER_START_DEG = 352.5;
    public static final double ANGLE_OUTER_END_DEG = 82.4;
    public static final double ANGLE_MID_A_END_DEG = 97.4;
    public static final double ANGLE_INNER_END_DEG = 7.5;

    private static final int ARC_STEPS_MAIN = 360;
    private static final int ARC_STEPS_STEP = 60;

    private PieceFactory() {
    }

    public static Polygon build15746A() {
        List<Point2D> pts = new ArrayList<>();

        addArc(pts, R_OUTER, ANGLE_OUTER_START_DEG, ANGLE_OUTER_START_DEG + 89.9, ARC_STEPS_MAIN);
        addRadial(pts, R_OUTER, R_MID_A, ANGLE_OUTER_END_DEG);
        addArc(pts, R_MID_A, ANGLE_OUTER_END_DEG, ANGLE_MID_A_END_DEG, ARC_STEPS_STEP);
        addRadial(pts, R_MID_A, R_INNER, ANGLE_MID_A_END_DEG);
        addArc(pts, R_INNER, ANGLE_MID_A_END_DEG, ANGLE_INNER_END_DEG, ARC_STEPS_MAIN);
        addRadial(pts, R_INNER, R_MID_B, ANGLE_INNER_END_DEG);
        addArc(pts, R_MID_B, ANGLE_INNER_END_DEG, ANGLE_OUTER_START_DEG - 360.0, ARC_STEPS_STEP);
        addRadial(pts, R_MID_B, R_OUTER, ANGLE_OUTER_START_DEG);

        return new Polygon(pts);
    }

    /** Adiciona pontos de angleStartDeg ate angleEndDeg (pode decrescer) a um dado raio fixo. */
    private static void addArc(List<Point2D> pts, double radius, double angleStartDeg, double angleEndDeg, int steps) {
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(angleStartDeg + (angleEndDeg - angleStartDeg) * i / steps);
            pts.add(new Point2D(radius * Math.cos(a), radius * Math.sin(a)));
        }
    }

    private static void addRadial(List<Point2D> pts, double fromRadius, double toRadius, double angleDeg) {
        double a = Math.toRadians(angleDeg);
        pts.add(new Point2D(toRadius * Math.cos(a), toRadius * Math.sin(a)));
    }
}
