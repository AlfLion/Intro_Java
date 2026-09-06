package br.com.iterasys.nesting.engine;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

/**
 * Busca generica de angulo de rotacao para encaixe em fileira: para qualquer
 * peca (nao apenas a 15746A), varre angulos candidatos e mede, para cada um,
 * a distancia de contato entre duas copias da peca deslizando ao longo do
 * eixo X do material (direcao da fileira). O angulo com menor distancia de
 * contato e o que da o passo mais apertado, logo o melhor aproveitamento
 * linear nessa direcao.
 *
 * Isto substitui a suposicao fixa de "rotacao 135 graus" usada nos primeiros
 * testes com a peca 15746A: a ferramenta agora decide o angulo sozinha a
 * partir da geometria.
 */
public final class RowFitOptimizer {

    public static final class Result {
        public final double angleDeg;
        public final double contactDistanceMm;

        public Result(double angleDeg, double contactDistanceMm) {
            this.angleDeg = angleDeg;
            this.contactDistanceMm = contactDistanceMm;
        }
    }

    private RowFitOptimizer() {
    }

    public static Result findBestAngleForRowPacking(Polygon basePiece, double angleStepDeg,
                                                      double maxSlideMm, double tolerance) {
        Point2D origin = new Point2D(0, 0);
        Point2D rowDirection = new Point2D(1, 0);

        double bestAngle = 0;
        double bestDistance = Double.MAX_VALUE;

        for (double angle = 0; angle < 180.0; angle += angleStepDeg) {
            Polygon rotated = basePiece.rotated(angle, origin);
            double distance = GeometryOps.minSeparation(rotated, rotated, rowDirection, maxSlideMm, tolerance);
            if (!Double.isNaN(distance) && distance < bestDistance) {
                bestDistance = distance;
                bestAngle = angle;
            }
        }
        return new Result(bestAngle, bestDistance);
    }
}
