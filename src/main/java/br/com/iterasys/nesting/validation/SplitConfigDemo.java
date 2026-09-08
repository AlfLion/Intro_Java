package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.packing.SheetPacker;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Prova de {@link SheetPacker#packBestSplit} - considera, alem da chapa
 * inteira deitada/em pe, cortar em 2 tiras de tamanhos diferentes. Compara
 * contra {@link SheetPacker#packBestOrientation} nas mesmas fixtures reais
 * ja validadas em outros demos, confirmando que: (a) o resultado nunca
 * piora (a divisao so e escolhida quando a previa aritmetica indica ganho),
 * (b) a validacao em resolucao plena continua com zero colisoes/zero fora
 * da area util, e (c) o custo de avaliar os candidatos de corte fica
 * proximo do custo de UM pack() so (a selecao de estrategia e calculada
 * uma vez e reusada - bug de performance real corrigido nesta etapa: sem
 * o cache, esta chamada levava 197s pra 15746A; com o cache, poucos
 * segundos, dominados pelo pack() final de qualquer forma).
 *
 * Nenhuma das fixtures atuais mostrou ganho com a divisao (todas escolhem
 * "sem divisao") - as receitas ja tiram bom proveito da chapa inteira
 * nesses tamanhos especificos. Isso nao invalida o mecanismo: e o
 * resultado correto e esperado quando dividir realmente nao ajuda.
 */
public final class SplitConfigDemo {

    public static void main(String[] args) throws IOException {
        report("/fixtures/TRAP.DXF", 1200, 2200, 5, 3);
        report("/fixtures/BRACKET_COMPACTO.DXF", 914, 1010, 3, 2);
        report("/fixtures/15746A.DXF", 1095, 914, 3, 2);
    }

    private static void report(String resource, double sheetW, double sheetH, double marginMm, double gapMm) throws IOException {
        PieceGeometry g = load(resource);
        System.out.println();
        System.out.printf("=== %s | chapa %.0fx%.0fmm ===%n", resource, sheetW, sheetH);

        long t0 = System.currentTimeMillis();
        SheetPacker.Result baseline = SheetPacker.packBestOrientation(g.getOuter(), g.getHoles(), sheetW, sheetH, marginMm, gapMm);
        long t1 = System.currentTimeMillis();
        SheetPacker.SplitResult split = SheetPacker.packBestSplit(g.getOuter(), g.getHoles(), sheetW, sheetH, marginMm, gapMm);
        long t2 = System.currentTimeMillis();

        System.out.printf("Sem divisao: %d pecas (%dms) | Com divisao [%s]: %d pecas (%dms)%n",
                baseline.placements.size(), t1 - t0, split.splitDescription, split.result.placements.size(), t2 - t1);

        Point2D centroid = GeometryOps.centroid(g.getOuter());
        List<Polygon> polys = new ArrayList<>();
        for (SheetPacker.Placement p : split.result.placements) {
            polys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(g.getOuter(), centroid));
        }
        int colisoes = 0;
        for (int i = 0; i < polys.size(); i++) {
            for (int j = i + 1; j < polys.size(); j++) {
                if (GeometryOps.overlaps(polys.get(i), polys.get(j))) colisoes++;
            }
        }
        int fora = 0;
        double sw = split.result.sheetWidthMm, sh = split.result.sheetHeightMm;
        for (Polygon p : polys) {
            double[] bb = p.boundingBox();
            if (bb[0] < marginMm - 0.01 || bb[1] < marginMm - 0.01 || bb[2] > sw - marginMm + 0.01 || bb[3] > sh - marginMm + 0.01) fora++;
        }
        System.out.printf("Validacao geometria plena: %d colisoes, %d peca(s) fora da area util%n", colisoes, fora);
    }

    private static PieceGeometry load(String resource) throws IOException {
        try (InputStream in = SplitConfigDemo.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Fixture nao encontrada: " + resource);
            return DxfParser.parse(in);
        }
    }
}
