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
 * Prova de ponta a ponta: pega uma peca real (fixture), acha a melhor
 * estrategia sozinha ({@code StrategySelector}, via {@code SheetPacker}),
 * ladrilha de verdade dentro de uma chapa com margem e GAP real, e valida
 * com geometria em RESOLUCAO PLENA que nenhuma peca aceita colide com
 * outra nem sai da area util - a mesma disciplina de "nunca confiar so na
 * matematica da rede" que o proprio V49 ja registrava em comentario.
 *
 * Nota sobre densidade: o aproveitamento liquido reportado aqui DEPENDE do
 * tamanho da chapa em relacao a peca (desperdicio de borda quando a ultima
 * coluna/fileira nao fecha inteira) - nao e uma propriedade fixa da peca
 * sozinha. Confirmado empiricamente: o TRAP.DXF chega a ~98% numa celula
 * isolada, mas so ~61-72% numa chapa de poucos metros porque sobra uma
 * faixa de borda.
 *
 * O {@code SheetPacker} tenta reaproveitar essa sobra com a mesma receita
 * girada 90/180/270 (como no Layout C da 15746A), escaneando a area util
 * inteira de novo em vez de supor o formato da sobra - funciona tanto pra
 * sobra em faixa limpa (TRAP.DXF numa chapa grande: 61->71 pecas) quanto
 * pra sobra irregular de um vetor de rede diagonal (BRACKET_COMPACTO.DXF
 * com "cola-por-aresta": 646->648 pecas).
 */
public final class SheetPackingDemo {

    public static void main(String[] args) throws IOException {
        report("/fixtures/BRACKET_COMPACTO.DXF", 914, 1010, 3, 2);
        report("/fixtures/TRAP.DXF", 1200, 2200, 5, 3);
        report("/fixtures/TRAP.DXF", 3200, 4200, 5, 3);
    }

    private static void report(String resource, double sheetW, double sheetH, double marginMm, double gapMm) throws IOException {
        PieceGeometry g = load(resource);
        System.out.println();
        System.out.printf("=== %s | chapa %.0fx%.0fmm margem=%.0f gap=%.0f ===%n", resource, sheetW, sheetH, marginMm, gapMm);

        long t0 = System.currentTimeMillis();
        SheetPacker.Result r = SheetPacker.pack(g.getOuter(), g.getHoles(), sheetW, sheetH, marginMm, gapMm);
        long t1 = System.currentTimeMillis();

        double usableArea = (sheetW - 2 * marginMm) * (sheetH - 2 * marginMm);
        System.out.printf("Tempo: %dms | estrategia: %s | alinhamento aplicado: %.2f graus%n",
                t1 - t0, r.strategyName, r.alignmentDeltaDeg);
        System.out.printf("Pecas encaixadas: %d (primaria=%d + reaproveitada=%d) | aproveitamento liquido: %.1f%%%n",
                r.placements.size(), r.primaryCount, r.reuseCount, 100.0 * r.usedAreaMm2 / usableArea);
        if (r.mirrorPending) {
            System.out.printf("Espelho pendente de autorizacao: receita espelhada renderia +%.1f%% peca/m2 (nao aplicado sem autorizacao explicita)%n",
                    r.mirrorGainPct);
        }

        Point2D centroid = GeometryOps.centroid(g.getOuter());
        List<Polygon> polys = new ArrayList<>();
        for (SheetPacker.Placement p : r.placements) {
            polys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(g.getOuter(), centroid));
        }
        int colisoes = 0;
        for (int i = 0; i < polys.size(); i++) {
            for (int j = i + 1; j < polys.size(); j++) {
                if (GeometryOps.overlaps(polys.get(i), polys.get(j))) colisoes++;
            }
        }
        int fora = 0;
        for (Polygon p : polys) {
            double[] bb = p.boundingBox();
            if (bb[0] < marginMm - 0.01 || bb[1] < marginMm - 0.01
                    || bb[2] > sheetW - marginMm + 0.01 || bb[3] > sheetH - marginMm + 0.01) fora++;
        }
        System.out.printf("Validacao geometria plena: %d colisoes, %d peca(s) fora da area util%n", colisoes, fora);
    }

    private static PieceGeometry load(String resource) throws IOException {
        try (InputStream in = SheetPackingDemo.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Fixture nao encontrada: " + resource);
            return DxfParser.parse(in);
        }
    }
}
