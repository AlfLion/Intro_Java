package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * "Cola pela aresta": para cada aresta reta suficientemente longa da peca,
 * gira uma copia 180 graus em torno do PONTO MEDIO dessa aresta (mais um
 * empurrao de {@code gapMm} pra fora, ao longo da normal) - a aresta da
 * copia cai exatamente sobre a aresta original. Descoberto analisando o
 * TRAP.DXF/TRAPENC.DXF do usuario: para um trapezio, isso forma um
 * paralelogramo com ZERO desperdicio geometrico (100% teorico antes do
 * gap). Funciona pra qualquer peca com pelo menos um lado reto, nao so
 * trapezios - concava inclusive, desde que a colagem nao esbarre em outra
 * parte do proprio contorno (por isso valida com overlap real antes de
 * aceitar o candidato).
 */
public final class EdgeGluePairGenerator implements PairGenerator {

    private final double minEdgeLengthMm;

    public EdgeGluePairGenerator(double minEdgeLengthMm) {
        this.minEdgeLengthMm = minEdgeLengthMm;
    }

    @Override
    public String name() {
        return "cola-por-aresta";
    }

    @Override
    public List<List<PlacedPieceInstance>> proposeCells(Polygon piece, Point2D centroid, double gapMm) {
        List<List<PlacedPieceInstance>> out = new ArrayList<>();
        Polygon pieceA = new PlacedPieceInstance(false, 0, new Point2D(0, 0)).materialize(piece, centroid);
        List<Point2D> v = piece.getVertices();
        int n = v.size();

        for (int i = 0; i < n; i++) {
            Point2D a = v.get(i);
            Point2D b = v.get((i + 1) % n);
            double len = a.distanceTo(b);
            if (len < minEdgeLengthMm) continue;

            Point2D mid = new Point2D((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
            double dx = b.x - a.x, dy = b.y - a.y;
            double edgeLen = Math.hypot(dx, dy);
            double nx = dy / edgeLen, ny = -dx / edgeLen;
            double dot = (mid.x - centroid.x) * nx + (mid.y - centroid.y) * ny;
            if (dot < 0) { nx = -nx; ny = -ny; }

            // Posicao BASE (sem gap): reflete a peca em torno do ponto medio
            // da aresta - alinha a aresta espelhada exatamente sobre a
            // original. Peca CONVEXA: essa posicao ja e o contato real
            // (distancia 0). Peca CONCAVA: outras partes da peca podem
            // colidir antes disso - somar gapMm as cegas sem medir podia
            // entregar folga real MENOR que o pedido (bug real, encontrado
            // testando espacamento grande contra peca em L). Corrige medindo
            // a distancia de contato VERDADEIRA na direcao normal via
            // minSeparation (mesma tecnica que LatticeFinder ja usa) antes
            // de somar o gap.
            double baseTx = 2 * (mid.x - centroid.x);
            double baseTy = 2 * (mid.y - centroid.y);
            Polygon pieceBBase = new PlacedPieceInstance(false, 180.0, new Point2D(baseTx, baseTy)).materialize(piece, centroid);
            double contactD = GeometryOps.minSeparation(pieceA, pieceBBase, new Point2D(nx, ny), 3000, 0.05);
            if (Double.isNaN(contactD)) continue;
            double total = contactD + gapMm;
            double tx = baseTx + nx * total;
            double ty = baseTy + ny * total;
            PlacedPieceInstance candidate = new PlacedPieceInstance(false, 180.0, new Point2D(tx, ty));
            Polygon pieceB = candidate.materialize(piece, centroid);

            if (!GeometryOps.overlaps(pieceA, pieceB)) {
                out.add(List.of(new PlacedPieceInstance(false, 0, new Point2D(0, 0)), candidate));
            }
        }
        return out;
    }
}
