package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.packing.HoleFiller;
import br.com.iterasys.nesting.packing.SheetPacker;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Prova do encaixe PART-IN-PART de verdade (nao void-filling de sobra):
 * {@code 2CIRC.DXF} e um exemplo real do usuario com 2 aneis concentricos
 * por posicao - anel grande (o100/o90) com anel pequeno (o80/o70) cortado
 * de dentro do FURO do grande, repetido numa rede hexagonal por 20
 * posicoes na chapa 500x500mm. O app Basico hoje ({@code Kit} calculator)
 * so alcanca 12 kits (colunas separadas, sem aproveitar nem a rede
 * hexagonal nem o furo); o usuario a mao chega a 20.
 *
 * Este demo prova que o motor PRO chega aos MESMOS 20 kits sozinho, sem
 * nenhuma dica manual: {@code StrategySelector} descobre a rede hexagonal
 * da peca grande sozinho ({@code orientacao-unica}, peca redonda entao
 * rotacao nao importa), e {@link HoleFiller} encaixa a peca pequena dentro
 * do furo de CADA uma das 20 copias aceitas - 20/20, zero colisoes reais,
 * verificado de forma independente do proprio HoleFiller (confere
 * vertice-a-vertice que cada peca pequena esta dentro do furo certo).
 */
public final class RingKitDemo {

    public static void main(String[] args) throws IOException {
        List<Polygon> loops = DxfParser.parseAllLoops(RingKitDemo.class.getResourceAsStream("/fixtures/2CIRC.DXF"));

        Polygon p1outer = null, p1hole = null, p2outer = null, p2hole = null;
        for (Polygon p : loops) {
            double[] bb = p.boundingBox();
            double r = (bb[2] - bb[0] + bb[3] - bb[1]) / 4.0;
            Point2D c = GeometryOps.centroid(p);
            if (Math.abs(c.x - 53.0) > 1 || Math.abs(c.y - 53.0) > 1) continue;
            if (Math.abs(r - 50) < 0.5) p1outer = p;
            else if (Math.abs(r - 45) < 0.5) p1hole = p;
            else if (Math.abs(r - 40) < 0.5) p2outer = p;
            else if (Math.abs(r - 35) < 0.5) p2hole = p;
        }
        PieceGeometry piece1 = new PieceGeometry(p1outer, List.of(p1hole));
        PieceGeometry piece2 = new PieceGeometry(p2outer, List.of(p2hole));

        double sheetW = 500, sheetH = 500, margin = 3, gap = 3;
        SheetPacker.Result r1 = SheetPacker.pack(piece1.getOuter(), piece1.getHoles(), sheetW, sheetH, margin, gap);
        System.out.printf("Peca 1 (anel grande o100/o90): %d encaixadas [%s]%n", r1.placements.size(), r1.strategyName);

        HoleFiller.Result hf = HoleFiller.fillHoles(piece2.getOuter(), piece2.getHoles(),
                piece1.getOuter(), p1hole, r1.placements, gap);
        System.out.printf("Peca 2 (anel pequeno o80/o70, dentro do furo da peca 1): %d de %d furos%n",
                hf.placements.size(), r1.placements.size());

        Point2D c1 = GeometryOps.centroid(piece1.getOuter());
        Point2D c2 = GeometryOps.centroid(piece2.getOuter());
        List<Polygon> holePolys = new ArrayList<>();
        for (SheetPacker.Placement p : r1.placements) {
            holePolys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(p1hole, c1));
        }
        List<Polygon> p2polys = new ArrayList<>();
        for (SheetPacker.Placement p : hf.placements) {
            p2polys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(piece2.getOuter(), c2));
        }

        int colisoesEntrePecas2 = 0;
        for (int i = 0; i < p2polys.size(); i++) {
            for (int j = i + 1; j < p2polys.size(); j++) {
                if (GeometryOps.overlaps(p2polys.get(i), p2polys.get(j))) colisoesEntrePecas2++;
            }
        }

        // Verificacao independente do HoleFiller: confere vertice-a-vertice que
        // cada copia da peca 2 esta genuinamente dentro de algum furo real.
        int foraDoFuro = 0;
        for (Polygon p2p : p2polys) {
            boolean achouFuro = false;
            for (Polygon hole : holePolys) {
                boolean todosDentro = true;
                for (Point2D v : p2p.getVertices()) {
                    if (!hole.containsPoint(v)) { todosDentro = false; break; }
                }
                if (todosDentro) { achouFuro = true; break; }
            }
            if (!achouFuro) foraDoFuro++;
        }

        System.out.printf("Validacao: %d colisoes entre copias da peca 2, %d fora de qualquer furo%n", colisoesEntrePecas2, foraDoFuro);
        int totalKits = Math.min(r1.placements.size(), hf.placements.size());
        System.out.printf("TOTAL DE KITS COMPLETOS: %d (app Basico hoje: 12 | usuario manual: 20)%n", totalKits);
    }
}
