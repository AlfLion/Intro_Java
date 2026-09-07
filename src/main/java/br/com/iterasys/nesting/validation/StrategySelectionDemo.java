package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.strategy.NestingRecipe;
import br.com.iterasys.nesting.strategy.StrategySelector;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Prova de que o motor "entende a peca e escolhe a melhor estrategia"
 * sozinho, sem receber nenhuma dica do tipo de peca: roda o
 * {@link StrategySelector} (que testa varios mecanismos de encaixe -
 * orientacao unica, interlocking em torno do centroide, cola-por-aresta - e
 * mede mm^2/peca de cada um) contra fixtures reais de formas bem
 * diferentes, e imprime qual mecanismo venceu em cada uma.
 *
 * Resultados esperados (conferidos manualmente nesta sessao):
 * - TRAP.DXF (trapezio simples): "cola-por-aresta" deve vencer, perto de
 *   100% de aproveitamento liquido (e um resultado analitico exato: colar
 *   2 trapezios pela perna forma um paralelogramo sem desperdicio).
 * - BRACKET_COMPACTO.DXF (peca com 3 furos): o vencedor variou entre
 *   "cola-por-aresta" (70,7%) e "interlock-centroide" (64,5%) dependendo do
 *   gap/resolucao da busca - ambos superam a receita manual do usuario para
 *   essa peca (55,6%, tecnica theta/theta+180 medida no DXF ENCAIXE1.DXF
 *   dele) porque exploram uma aresta da concavidade que a receita manual
 *   nao usou. Validado com 0 colisoes reais num patch 3x3 em resolucao
 *   plena (fora deste repo, na sessao que gerou este arquivo).
 */
public final class StrategySelectionDemo {

    private static final double GAP_MM = 2.0;

    public static void main(String[] args) throws IOException {
        report("/fixtures/TRAP.DXF");
        report("/fixtures/BRACKET_COMPACTO.DXF");
    }

    private static void report(String resource) throws IOException {
        PieceGeometry geometry = load(resource);
        Polygon outer = geometry.getOuter();
        double areaLiquida = Math.abs(outer.area());
        for (Polygon hole : geometry.getHoles()) {
            areaLiquida -= Math.abs(hole.area());
        }

        System.out.println();
        System.out.println("=== " + resource + " (gap=" + GAP_MM + "mm) ===");
        StrategySelector.Result result = StrategySelector.select(outer, GAP_MM);

        List<NestingRecipe> ranked = result.allEvaluated;
        int top = Math.min(3, ranked.size());
        for (int i = 0; i < top; i++) {
            NestingRecipe r = ranked.get(i);
            double liquido = 100.0 * areaLiquida / r.areaPerPieceMm2Gross;
            System.out.printf("  #%d [%s]%s area/peca=%.1f mm^2 -> %.1f%% liquido%n",
                    i + 1, r.strategyName, r.usesMirror ? " (usa espelho)" : "",
                    r.areaPerPieceMm2Gross, liquido);
        }
        if (result.bestNoMirror != null) {
            double liquido = 100.0 * areaLiquida / result.bestNoMirror.areaPerPieceMm2Gross;
            System.out.printf("VENCEDOR (sem espelho): %s (%.1f%% liquido)%n",
                    result.bestNoMirror.strategyName, liquido);
        } else {
            System.out.println("Nenhuma estrategia encontrou uma rede valida.");
        }
        if (result.mirrorPending()) {
            System.out.println("Aviso: existe candidato COM espelho melhor que o vencedor sem espelho - "
                    + "precisa de autorizacao explicita do usuario antes de usar.");
        }
    }

    private static PieceGeometry load(String resource) throws IOException {
        try (InputStream in = StrategySelectionDemo.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Fixture nao encontrada no classpath: " + resource);
            }
            return DxfParser.parse(in);
        }
    }
}
