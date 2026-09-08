package br.com.iterasys.nesting.packing;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Aproveitamento de vaos: dado um layout ja aceito (as pecas "grandes" que
 * {@link SheetPacker} ja posicionou, ou qualquer outro conjunto de poligonos
 * ja ocupados na chapa), tenta encaixar copias de uma peca "pequena"
 * diferente nos espacos que sobraram entre/dentro delas - o padrao que o
 * usuario mostrou no ENCAKIT.DXF (peca grande + peca pequena no vao) e que
 * {@code NestedKitAuditor} ja sabia LER de um DXF pronto, mas que o motor
 * ainda nao sabia GERAR sozinho.
 *
 * Estrategia: varredura em grade (nao rede/lattice, porque o vao nao tem
 * forma regular) tentando algumas rotacoes fixas em cada ponto da grade;
 * aceita o primeiro encaixe sem colisao e passa pro proximo ponto -
 * guloso, nao otimo, mas simples e correto (cada aceite e validado por
 * colisao geometrica real contra TUDO que ja foi aceito antes, incluindo
 * outras copias da propria peca pequena ja encaixadas no mesmo vao).
 *
 * Limitacao conhecida: nao aplica o {@code gapMm} como folga entre a peca
 * pequena e as pecas grandes vizinhas (so garante ZERO sobreposicao real,
 * que e sempre seguro, mas peca pequena pode tocar peca grande sem vao
 * nenhum). Para furos/gaps de corte que exigem folga minima explicita
 * entre peca pequena e peca grande, isso ainda precisa ser tratado por
 * quem chama (ex.: encolhendo a peca pequena por gapMm/2 antes de chamar).
 *
 * Nunca usa espelho automaticamente - mesma regra de negocio do resto do
 * motor (quem chama decide se autoriza, este metodo nem aceita a opcao).
 */
public final class VoidFiller {

    private static final double[] DEFAULT_ROTATIONS_DEG = {0.0, 90.0, 180.0, 270.0};

    public static final class Result {
        public final List<SheetPacker.Placement> placements;
        public final double areaLiquidaPecaMm2;
        public final double usedAreaMm2;

        Result(List<SheetPacker.Placement> placements, double areaLiquidaPecaMm2) {
            this.placements = placements;
            this.areaLiquidaPecaMm2 = areaLiquidaPecaMm2;
            this.usedAreaMm2 = placements.size() * areaLiquidaPecaMm2;
        }
    }

    private VoidFiller() {
    }

    /**
     * @param secondaryOuter contorno da peca pequena a encaixar nos vaos
     * @param secondaryHoles furos da peca pequena (so pra calcular area liquida)
     * @param occupiedPolys  poligonos JA ocupados na chapa em resolucao plena
     *                       (ex.: {@code SheetPacker.Result.placements} ja
     *                       materializados contra a peca grande) - a peca
     *                       pequena nunca pode colidir com esses
     * @param gridStepMm     passo da varredura em grade; menor = mais vaos
     *                       encontrados, mais lento. Use algo perto da menor
     *                       dimensao da peca pequena (ex.: metade dela)
     */
    public static Result fillVoids(Polygon secondaryOuter, List<Polygon> secondaryHoles,
                                    List<Polygon> occupiedPolys, double sheetW, double sheetH,
                                    double marginMm, double gridStepMm) {
        return fillVoids(secondaryOuter, secondaryHoles, occupiedPolys, sheetW, sheetH,
                marginMm, gridStepMm, DEFAULT_ROTATIONS_DEG);
    }

    public static Result fillVoids(Polygon secondaryOuter, List<Polygon> secondaryHoles,
                                    List<Polygon> occupiedPolys, double sheetW, double sheetH,
                                    double marginMm, double gridStepMm, double[] rotationsDeg) {
        double areaLiquida = Math.abs(secondaryOuter.area());
        for (Polygon h : secondaryHoles) areaLiquida -= Math.abs(h.area());

        double usableMinX = marginMm, usableMinY = marginMm;
        double usableMaxX = sheetW - marginMm, usableMaxY = sheetH - marginMm;
        if (usableMaxX <= usableMinX || usableMaxY <= usableMinY || gridStepMm <= 0) {
            return new Result(List.of(), areaLiquida);
        }

        Point2D centroid = GeometryOps.centroid(secondaryOuter);
        double[] bb0 = secondaryOuter.boundingBox();
        double pieceMaxDim = Math.max(bb0[2] - bb0[0], bb0[3] - bb0[1]);

        // Nao usa HullIndex aqui (ao contrario de SheetPacker#validateFullResolution):
        // a peca "pequena" que preenche vaos costuma ser tao simples (poucos
        // vertices, muitas vezes ja convexa) que calcular/testar o fecho dela
        // e puro overhead sem ganho - medido: deixou este metodo MAIS LENTO
        // (8.5s -> 11s no fixture do bracket). O SpatialIndex simples ja
        // filtra pela distancia (bounding box + grade); o teste exato restante
        // e barato porque o lado pequeno da comparacao tem poucos vertices.
        SpatialIndex index = new SpatialIndex(Math.max(1.0, pieceMaxDim));
        for (Polygon p : occupiedPolys) index.insert(p);

        List<SheetPacker.Placement> accepted = new ArrayList<>();

        for (double gy = usableMinY; gy <= usableMaxY; gy += gridStepMm) {
            for (double gx = usableMinX; gx <= usableMaxX; gx += gridStepMm) {
                for (double rot : rotationsDeg) {
                    Point2D translation = new Point2D(gx - centroid.x, gy - centroid.y);
                    PlacedPieceInstance candidate = new PlacedPieceInstance(false, rot, translation);
                    Polygon poly = candidate.materialize(secondaryOuter, centroid);
                    double[] bb = poly.boundingBox();
                    if (bb[0] < usableMinX - 1e-6 || bb[1] < usableMinY - 1e-6
                            || bb[2] > usableMaxX + 1e-6 || bb[3] > usableMaxY + 1e-6) {
                        continue;
                    }
                    if (index.overlapsAny(poly)) continue;
                    accepted.add(new SheetPacker.Placement(false, rot, translation));
                    index.insert(poly);
                    break;
                }
            }
        }
        return new Result(accepted, areaLiquida);
    }
}
