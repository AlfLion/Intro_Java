package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.PieceFactory;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

/**
 * Primeira meta de validacao do motor de nesting PRO: usando so a geometria
 * reconstruida da peca 15746A (sem DXF anexado nesta sessao), verificar se um
 * NFP aproximado (varredura radial + busca binaria de contato) converge
 * sozinho para os numeros medidos manualmente no Layout A:
 * - passo dentro da fileira (translacao pura em X, mesma rotacao 135 graus): 18,62 mm
 * - vetor fileira -> fileira (peca girada +180 graus / 315 graus): modulo ~620 mm
 *
 * Isto NAO e um NFP exato (orbiting/Minkowski) - e uma aproximacao por
 * amostragem direcional, suficiente para validar a receita antes de investir
 * no NFP completo + busca de angulo livre + GA.
 */
public final class Nfp15746AValidation {

    private static final double TARGET_AREA_MM2 = 5474.6;
    private static final double TARGET_BBOX_SIDE_MM = 310.0;
    private static final double MEASURED_ROW_STEP_MM = 18.620;
    private static final double MEASURED_ROW_GAP_MM = 1.55;
    private static final double MEASURED_ROW_VECTOR_MAGNITUDE_MM = Math.hypot(435.80, 437.27);

    public static void main(String[] args) {
        Polygon basePiece = PieceFactory.build15746A();

        double[] bbox = basePiece.boundingBox();
        double bboxWidth = bbox[2] - bbox[0];
        double bboxHeight = bbox[3] - bbox[1];

        System.out.println("=== Geometria exata da peca 15746A (extraida do DXF real) ===");
        System.out.printf("Area do poligono: %.1f mm^2 (alvo medido: %.1f mm^2)%n",
                basePiece.area(), TARGET_AREA_MM2);
        System.out.printf("Bounding box: %.1f x %.1f mm (alvo medido aprox.: %.0f x %.0f mm)%n",
                bboxWidth, bboxHeight, TARGET_BBOX_SIDE_MM, TARGET_BBOX_SIDE_MM);

        Point2D origin = new Point2D(0, 0);
        Polygon piece135 = basePiece.rotated(135.0, origin);
        Polygon piece315 = basePiece.rotated(315.0, origin);

        System.out.println();
        System.out.println("=== Passo dentro da fileira (mesma rotacao 135 graus, translacao em X) ===");
        double rowContact = GeometryOps.minSeparation(piece135, piece135, new Point2D(1, 0), 1000, 0.001);
        double rowStepPredicted = rowContact + MEASURED_ROW_GAP_MM;
        System.out.printf("Distancia de contato (gap=0): %.3f mm%n", rowContact);
        System.out.printf("Passo previsto (contato + gap medido %.2f mm): %.3f mm%n",
                MEASURED_ROW_GAP_MM, rowStepPredicted);
        System.out.printf("Passo medido manualmente: %.3f mm (diferenca: %.3f mm)%n",
                MEASURED_ROW_STEP_MM, rowStepPredicted - MEASURED_ROW_STEP_MM);

        System.out.println();
        System.out.println("=== Vetor fileira -> fileira (peca 135 graus vs peca 315 graus) ===");
        System.out.println("Varredura radial (passo 2 graus) buscando a direcao de contato mais proxima:");

        double bestDistance = Double.MAX_VALUE;
        double bestAngleDeg = 0;
        for (int deg = 0; deg < 360; deg += 2) {
            double rad = Math.toRadians(deg);
            Point2D dir = new Point2D(Math.cos(rad), Math.sin(rad));
            double d = GeometryOps.minSeparation(piece135, piece315, dir, 1000, 0.05);
            if (!Double.isNaN(d) && d < bestDistance) {
                bestDistance = d;
                bestAngleDeg = deg;
            }
        }
        System.out.printf("Direcao mais proxima encontrada: %.0f graus, distancia de contato: %.2f mm%n",
                bestAngleDeg, bestDistance);
        System.out.printf("Vetor medido (Layout A): modulo %.2f mm%n", MEASURED_ROW_VECTOR_MAGNITUDE_MM);
        System.out.printf("Diferenca (motor - medido): %.2f mm%n",
                bestDistance - MEASURED_ROW_VECTOR_MAGNITUDE_MM);
    }
}
