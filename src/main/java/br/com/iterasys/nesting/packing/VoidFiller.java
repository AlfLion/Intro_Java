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
 * guloso, nao otimo, mas simples e correto.
 *
 * Mesma tecnica de {@code SheetPacker#pack}: a varredura em si usa geometria
 * SIMPLIFICADA (Douglas-Peucker) tanto da peca pequena quanto das pecas ja
 * ocupadas - testar milhares de posicoes de grade em resolucao plena contra
 * pecas reais complexas (centenas de vertices por causa da discretizacao
 * fina de arco) nao termina em tempo util (medido: nao terminou em 2min
 * contra uma peca de 919 vertices, caso real do ENCAKIT.DXF). So a
 * validacao FINAL (dos poucos candidatos que a busca simplificada aceitou)
 * roda em resolucao plena de verdade, nunca confiando so na aproximacao -
 * mesma disciplina do resto do motor.
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

        double simplifyEpsilon = 0.4;
        Polygon searchSecondary = GeometryOps.simplify(secondaryOuter, simplifyEpsilon);
        Point2D centroid = GeometryOps.centroid(secondaryOuter);
        double[] bb0 = secondaryOuter.boundingBox();
        double pieceMaxDim = Math.max(bb0[2] - bb0[0], bb0[3] - bb0[1]);

        List<Polygon> searchOccupied = new ArrayList<>(occupiedPolys.size());
        for (Polygon p : occupiedPolys) searchOccupied.add(GeometryOps.simplify(p, simplifyEpsilon));

        SpatialIndex searchIndex = new SpatialIndex(Math.max(1.0, pieceMaxDim));
        for (Polygon p : searchOccupied) searchIndex.insert(p);

        // A peca simplificada e um poligono INSCRITO na peca real (Douglas-
        // Peucker so remove pontos, nunca move os que ficam), entao pode ter
        // bounding box ate simplifyEpsilon menor - encolhe a area util so pra
        // busca por essa margem de seguranca (mesma tecnica e mesmo bug real
        // corrigidos em SheetPacker#pack), senao a busca aceita candidatos
        // flush com a margem que a validacao final em resolucao plena rejeita.
        double searchMinX = usableMinX + simplifyEpsilon, searchMinY = usableMinY + simplifyEpsilon;
        double searchMaxX = usableMaxX - simplifyEpsilon, searchMaxY = usableMaxY - simplifyEpsilon;
        if (searchMaxX <= searchMinX || searchMaxY <= searchMinY) {
            searchMinX = usableMinX;
            searchMinY = usableMinY;
            searchMaxX = usableMaxX;
            searchMaxY = usableMaxY;
        }

        List<SheetPacker.Placement> accepted = new ArrayList<>();

        for (double gy = searchMinY; gy <= searchMaxY; gy += gridStepMm) {
            for (double gx = searchMinX; gx <= searchMaxX; gx += gridStepMm) {
                for (double rot : rotationsDeg) {
                    Point2D translation = new Point2D(gx - centroid.x, gy - centroid.y);
                    PlacedPieceInstance candidate = new PlacedPieceInstance(false, rot, translation);
                    Polygon poly = candidate.materialize(searchSecondary, centroid);
                    double[] bb = poly.boundingBox();
                    if (bb[0] < searchMinX - 1e-6 || bb[1] < searchMinY - 1e-6
                            || bb[2] > searchMaxX + 1e-6 || bb[3] > searchMaxY + 1e-6) {
                        continue;
                    }
                    if (searchIndex.overlapsAny(poly)) continue;
                    accepted.add(new SheetPacker.Placement(false, rot, translation));
                    searchIndex.insert(poly);
                    break;
                }
            }
        }

        List<SheetPacker.Placement> validated = validateFullResolution(secondaryOuter, centroid, occupiedPolys, accepted, pieceMaxDim,
                usableMinX, usableMinY, usableMaxX, usableMaxY);
        return new Result(validated, areaLiquida);
    }

    /**
     * Confirma em resolucao PLENA os candidatos que a busca simplificada
     * aceitou - contra as pecas ocupadas (tambem em resolucao plena) e
     * contra as outras pecas pequenas ja confirmadas antes. Descarta
     * qualquer aceite que na verdade colida (nao deveria acontecer com
     * frequencia, dado o {@code gapMm} implicito da simplificacao, mas
     * nunca confia nisso sem checar - mesma disciplina de
     * {@code SheetPacker#validateFullResolution}). TAMBEM refaz a checagem
     * de limite da area util em resolucao plena - a busca so verifica
     * limite contra a peca SIMPLIFICADA, entao uma peca podia ser aceita
     * mesmo que o contorno completo ultrapasse a margem (mesmo bug real
     * encontrado e corrigido em {@code SheetPacker#validateFullResolution}).
     */
    private static List<SheetPacker.Placement> validateFullResolution(Polygon secondaryOuter, Point2D centroid,
                                                                        List<Polygon> occupiedPolys,
                                                                        List<SheetPacker.Placement> candidates,
                                                                        double pieceMaxDim,
                                                                        double usableMinX, double usableMinY,
                                                                        double usableMaxX, double usableMaxY) {
        Polygon hullBase = GeometryOps.convexHull(secondaryOuter);
        HullIndex index = new HullIndex(Math.max(1.0, pieceMaxDim));
        for (Polygon p : occupiedPolys) index.insert(GeometryOps.convexHull(p), p);

        List<SheetPacker.Placement> kept = new ArrayList<>(candidates.size());
        for (SheetPacker.Placement c : candidates) {
            PlacedPieceInstance inst = new PlacedPieceInstance(c.mirror, c.rotationDeg, c.position);
            Polygon poly = inst.materialize(secondaryOuter, centroid);
            double[] bb = poly.boundingBox();
            if (bb[0] < usableMinX - 1e-6 || bb[1] < usableMinY - 1e-6
                    || bb[2] > usableMaxX + 1e-6 || bb[3] > usableMaxY + 1e-6) {
                continue;
            }
            Polygon hull = inst.materialize(hullBase, centroid);
            if (index.overlapsAny(hull, poly)) continue;
            kept.add(c);
            index.insert(hull, poly);
        }
        return kept;
    }
}
