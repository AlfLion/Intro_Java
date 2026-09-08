package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.packing.SheetPacker;
import br.com.iterasys.nesting.packing.VoidFiller;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Prova do aproveitamento de vaos: pega o layout ja aceito de
 * {@link SheetPacker#pack} (a peca "grande") e tenta encaixar uma peca
 * "pequena" sintetica (quadrado 6x6mm, tipo uma arruela/reforco) nos
 * espacos que sobraram - o padrao que o usuario mostrou no ENCAKIT.DXF
 * (peca grande + peca pequena no vao), agora GERADO pelo motor em vez de
 * so LIDO de um DXF pronto ({@code NestedKitAuditor}).
 *
 * Ainda nao ha uma fixture real de kit (o ENCAKIT.DXF original nao foi
 * commitado); este demo usa uma peca pequena sintetica so pra provar que
 * o mecanismo funciona e nao gera colisao - o numero de pecas encaixadas
 * NAO deve ser lido como "referencia de negocio", so como regressao de
 * correcao geometrica.
 */
public final class VoidFillingDemo {

    public static void main(String[] args) throws IOException {
        report("/fixtures/BRACKET_COMPACTO.DXF", 914, 1010, 3, 2);
    }

    private static void report(String resource, double sheetW, double sheetH, double marginMm, double gapMm) throws IOException {
        PieceGeometry g = load(resource);
        System.out.println();
        System.out.printf("=== %s | chapa %.0fx%.0fmm ===%n", resource, sheetW, sheetH);

        SheetPacker.Result primary = SheetPacker.pack(g.getOuter(), g.getHoles(), sheetW, sheetH, marginMm, gapMm);
        Point2D primaryCentroid = GeometryOps.centroid(g.getOuter());
        List<Polygon> occupied = new ArrayList<>();
        for (SheetPacker.Placement p : primary.placements) {
            occupied.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(g.getOuter(), primaryCentroid));
        }

        Polygon secondary = new Polygon(List.of(
                new Point2D(0, 0), new Point2D(6, 0), new Point2D(6, 6), new Point2D(0, 6)));

        long t0 = System.currentTimeMillis();
        VoidFiller.Result vf = VoidFiller.fillVoids(secondary, List.of(), occupied, sheetW, sheetH, marginMm, 3.0);
        long t1 = System.currentTimeMillis();

        double usableArea = (sheetW - 2 * marginMm) * (sheetH - 2 * marginMm);
        double pctSoGrande = 100.0 * primary.usedAreaMm2 / usableArea;
        double pctTotal = 100.0 * (primary.usedAreaMm2 + vf.usedAreaMm2) / usableArea;
        System.out.printf("Peca grande: %d | peca pequena no vao: %d (%dms)%n", primary.placements.size(), vf.placements.size(), t1 - t0);
        System.out.printf("Aproveitamento: %.1f%% so grande -> %.1f%% grande+vao%n", pctSoGrande, pctTotal);

        Point2D secCentroid = GeometryOps.centroid(secondary);
        List<Polygon> smallPolys = new ArrayList<>();
        for (SheetPacker.Placement p : vf.placements) {
            smallPolys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(secondary, secCentroid));
        }
        int colisoes = 0;
        for (Polygon sp : smallPolys) {
            for (Polygon bp : occupied) if (GeometryOps.overlaps(sp, bp)) colisoes++;
        }
        for (int i = 0; i < smallPolys.size(); i++) {
            for (int j = i + 1; j < smallPolys.size(); j++) {
                if (GeometryOps.overlaps(smallPolys.get(i), smallPolys.get(j))) colisoes++;
            }
        }
        System.out.printf("Validacao geometria plena: %d colisoes (grande-pequena + pequena-pequena)%n", colisoes);
    }

    private static PieceGeometry load(String resource) throws IOException {
        try (InputStream in = VoidFillingDemo.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Fixture nao encontrada: " + resource);
            return DxfParser.parse(in);
        }
    }
}
