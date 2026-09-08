package br.com.iterasys.nesting.packing;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Encaixe PART-IN-PART de verdade: uma peca "secundaria" cortada de dentro
 * do FURO de uma peca "primaria" ja posicionada - nao confundir com
 * {@link VoidFiller}, que preenche a sobra de chapa FORA das pecas. Padrao
 * descoberto no exemplo real do usuario (2CIRC.DXF): anel grande (o90/100)
 * com anel pequeno (o70/80) cortado concentricamente dentro do furo do
 * grande, repetido numa rede hexagonal - a chapa app Basico hoje faz so
 * 12 kits (colunas separadas); o usuario a mao chega a 20 (rede hexagonal
 * do proprio {@code StrategySelector}, que o motor JA descobre sozinho
 * para a peca grande - ver {@code EncakitAnalysisDemo}/handoff) MAIS a
 * peca pequena de graca dentro de cada furo, sem gastar area extra
 * nenhuma da chapa.
 *
 * Estrategia: pra cada instancia ja aceita da peca primaria, transforma o
 * FURO dela pelo mesmo rigid-transform da peca (furo se move junto -
 * mesma convencao de {@link PlacedPieceInstance#materialize}), tenta
 * centralizar a peca secundaria no centroide do furo em cada rotacao de
 * {@code rotationsDeg}, aceita a primeira que caiba inteira dentro do
 * furo (nenhum vertice fora, nenhuma aresta cruzando a borda do furo).
 *
 * Limitacao conhecida: a folga ({@code marginMm}) contra a borda do furo e
 * aproximada encolhendo o poligono do furo movendo cada vertice em
 * direcao ao proprio centroide - e uma aproximacao EXATA pra furos
 * circulares/convexos regulares (o caso real testado), mas pode ser
 * imprecisa pra furos muito alongados ou concavos. Nunca aplica espelho
 * automaticamente - mesma regra de negocio do resto do motor.
 */
public final class HoleFiller {

    private static final double[] DEFAULT_ROTATIONS_DEG = {0.0, 30.0, 60.0, 90.0, 120.0, 150.0};

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

    private HoleFiller() {
    }

    /**
     * @param secondaryOuter    contorno da peca a encaixar dentro dos furos
     * @param secondaryHoles    furos da peca secundaria (so pra area liquida)
     * @param primaryOuterBase  contorno da peca primaria, no MESMO referencial
     *                          usado pra gerar {@code primaryPlacements} (ex.:
     *                          o {@code fullOuter} passado a {@code SheetPacker.pack})
     * @param primaryHoleBase   um dos furos da peca primaria (mesmo referencial
     *                          de {@code primaryOuterBase}) onde tentar encaixar
     *                          a secundaria - chame de novo por furo se a
     *                          primaria tiver mais de um furo aproveitavel
     * @param primaryPlacements posicoes ja aceitas da peca primaria (de
     *                          {@code SheetPacker.Result.placements})
     * @param marginMm          folga minima aproximada contra a borda do furo
     */
    public static Result fillHoles(Polygon secondaryOuter, List<Polygon> secondaryHoles,
                                    Polygon primaryOuterBase, Polygon primaryHoleBase,
                                    List<SheetPacker.Placement> primaryPlacements, double marginMm) {
        return fillHoles(secondaryOuter, secondaryHoles, primaryOuterBase, primaryHoleBase,
                primaryPlacements, marginMm, DEFAULT_ROTATIONS_DEG);
    }

    public static Result fillHoles(Polygon secondaryOuter, List<Polygon> secondaryHoles,
                                    Polygon primaryOuterBase, Polygon primaryHoleBase,
                                    List<SheetPacker.Placement> primaryPlacements, double marginMm,
                                    double[] rotationsDeg) {
        double areaLiquida = Math.abs(secondaryOuter.area());
        for (Polygon h : secondaryHoles) areaLiquida -= Math.abs(h.area());

        Point2D primaryCentroid = GeometryOps.centroid(primaryOuterBase);
        Point2D secondaryCentroid = GeometryOps.centroid(secondaryOuter);

        List<SheetPacker.Placement> accepted = new ArrayList<>();
        for (SheetPacker.Placement pp : primaryPlacements) {
            PlacedPieceInstance primaryInst = new PlacedPieceInstance(pp.mirror, pp.rotationDeg, pp.position);
            Polygon holeInSheet = primaryInst.materialize(primaryHoleBase, primaryCentroid);
            Polygon shrunkHole = shrinkTowardCentroid(holeInSheet, marginMm);
            Point2D holeCentroid = GeometryOps.centroid(holeInSheet);

            for (double rot : rotationsDeg) {
                Point2D translation = new Point2D(holeCentroid.x - secondaryCentroid.x, holeCentroid.y - secondaryCentroid.y);
                PlacedPieceInstance candidate = new PlacedPieceInstance(false, rot, translation);
                Polygon poly = candidate.materialize(secondaryOuter, secondaryCentroid);
                if (fitsInside(poly, shrunkHole)) {
                    accepted.add(new SheetPacker.Placement(false, rot, translation));
                    break;
                }
            }
        }
        return new Result(accepted, areaLiquida);
    }

    /** Move cada vertice do furo em direcao ao seu centroide por ate marginMm - exato pra furos convexos/circulares. */
    private static Polygon shrinkTowardCentroid(Polygon hole, double marginMm) {
        if (marginMm <= 0) return hole;
        Point2D c = GeometryOps.centroid(hole);
        List<Point2D> shrunk = new ArrayList<>(hole.getVertices().size());
        for (Point2D v : hole.getVertices()) {
            double dx = c.x - v.x, dy = c.y - v.y;
            double len = Math.hypot(dx, dy);
            if (len <= marginMm) {
                shrunk.add(c);
            } else {
                shrunk.add(new Point2D(v.x + dx / len * marginMm, v.y + dy / len * marginMm));
            }
        }
        return new Polygon(shrunk);
    }

    /** Candidato cabe inteiro dentro do furo: nenhum vertice fora, nenhuma aresta cruza a borda. */
    private static boolean fitsInside(Polygon candidate, Polygon hole) {
        for (Point2D v : candidate.getVertices()) {
            if (!hole.containsPoint(v)) return false;
        }
        List<Point2D> cv = candidate.getVertices();
        List<Point2D> hv = hole.getVertices();
        int nc = cv.size(), nh = hv.size();
        for (int i = 0; i < nc; i++) {
            Point2D a1 = cv.get(i), a2 = cv.get((i + 1) % nc);
            for (int j = 0; j < nh; j++) {
                Point2D b1 = hv.get(j), b2 = hv.get((j + 1) % nh);
                if (GeometryOps.segmentsIntersect(a1, a2, b1, b2)) return false;
            }
        }
        return true;
    }
}
