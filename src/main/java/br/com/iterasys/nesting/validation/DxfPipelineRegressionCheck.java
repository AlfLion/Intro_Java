package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.engine.RowFitOptimizer;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Polygon;

import java.io.IOException;
import java.io.InputStream;

/**
 * Teste de regressao do PIPELINE GENERICO (parser DXF + busca de angulo),
 * usando a peca 15746A apenas como UM EXEMPLO de fixture real disponivel -
 * nao e uma peca especial nem uma regra hardcoded no motor. Qualquer novo
 * DXF anexado no futuro deve poder passar pelo mesmo caminho:
 * DxfParser.parse(...) -> Polygon -> RowFitOptimizer.
 *
 * O que este teste confirma:
 * 1. O parser reconstroi corretamente um contorno feito de LINE + ARC soltos
 *    (sem nenhuma constante especifica da peca no codigo de producao).
 * 2. A busca de angulo livre (RowFitOptimizer), sem receber 135 graus como
 *    dica, converge para um angulo e passo de fileira proximos do medido
 *    manualmente por quem fez o nesting a mao.
 */
public final class DxfPipelineRegressionCheck {

    private static final String FIXTURE_RESOURCE = "/fixtures/15746A.DXF";
    private static final double EXPECTED_AREA_MM2 = 5474.6;
    private static final double MEASURED_ANGLE_DEG = 135.0;
    private static final double MEASURED_ROW_STEP_MM = 18.620;
    private static final double MEASURED_ROW_GAP_MM = 1.55;

    public static void main(String[] args) throws IOException {
        PieceGeometry geometry = loadFixture();
        Polygon piece = geometry.getOuter();

        System.out.println("=== Parser DXF generico: fixture 15746A.DXF ===");
        System.out.printf("Furos detectados: %d%n", geometry.getHoles().size());
        System.out.printf("Area do contorno externo: %.1f mm^2 (esperado ~%.1f mm^2)%n",
                piece.area(), EXPECTED_AREA_MM2);
        double[] bbox = piece.boundingBox();
        System.out.printf("Bounding box: %.1f x %.1f mm%n", bbox[2] - bbox[0], bbox[3] - bbox[1]);

        System.out.println();
        System.out.println("=== Busca automatica de angulo (sem receber 135 graus como dica) ===");
        RowFitOptimizer.Result result = RowFitOptimizer.findBestAngleForRowPacking(piece, 1.0, 1000, 0.005);
        double predictedStep = result.contactDistanceMm + MEASURED_ROW_GAP_MM;
        System.out.printf("Angulo encontrado pelo motor: %.1f graus (medido manualmente: %.1f graus)%n",
                result.angleDeg, MEASURED_ANGLE_DEG);
        System.out.printf("Passo previsto (contato %.3f mm + gap %.2f mm): %.3f mm%n",
                result.contactDistanceMm, MEASURED_ROW_GAP_MM, predictedStep);
        System.out.printf("Passo medido manualmente: %.3f mm (diferenca: %.3f mm)%n",
                MEASURED_ROW_STEP_MM, predictedStep - MEASURED_ROW_STEP_MM);
    }

    private static PieceGeometry loadFixture() throws IOException {
        try (InputStream in = DxfPipelineRegressionCheck.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (in == null) {
                throw new IOException("Fixture nao encontrada no classpath: " + FIXTURE_RESOURCE);
            }
            return DxfParser.parse(in);
        }
    }
}
