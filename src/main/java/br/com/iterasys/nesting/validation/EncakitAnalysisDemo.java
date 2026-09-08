package br.com.iterasys.nesting.validation;

import br.com.iterasys.nesting.audit.NestedKitAuditor;
import br.com.iterasys.nesting.dxf.DxfParser;
import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;
import br.com.iterasys.nesting.packing.SheetPacker;
import br.com.iterasys.nesting.packing.VoidFiller;
import br.com.iterasys.nesting.strategy.NestingRecipe;
import br.com.iterasys.nesting.strategy.PlacedPieceInstance;
import br.com.iterasys.nesting.strategy.StrategySelector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Primeiro teste do void-filling contra um kit REAL do usuario (ENCAKIT.DXF:
 * 2 copias de uma peca "grande" + 2 copias de uma peca "media", a peca media
 * encaixada no vao que a grande deixa) - ate esta etapa so tinha sido
 * validado contra uma peca pequena sintetica (quadrado).
 *
 * O arquivo original e um DXF JA NESTEADO (o kit inteiro, nao pecas
 * isoladas), entao este demo primeiro extrai as 2 pecas canonicas por
 * agrupamento de contorno/furo por contencao geometrica (mesma tecnica de
 * {@link NestedKitAuditor}), depois usa o proprio {@code NestedKitAuditor}
 * pra descobrir a rotacao/espelho reais que o usuario usou em cada copia -
 * um jeito independente de conferir se o padrao que o motor prefere sozinho
 * bate com o que o usuario fez a mao.
 *
 * Achado real (nao esperado quando o void-filling foi implementado): a peca
 * "media" tem 738 vertices e a "grande" 919 (discretizacao fina de arco) -
 * testar cada posicao de uma varredura em grade em resolucao PLENA contra
 * isso nao terminava em tempo util (>2min). Forcou {@code VoidFiller} a
 * adotar a mesma tecnica de busca-simplificada-depois-valida-plena que
 * {@code SheetPacker#pack} ja usava - sem isso o void-filling so funcionava
 * pra pecas simples/sinteticas, nunca teria funcionado pra um kit real.
 */
public final class EncakitAnalysisDemo {

    public static void main(String[] args) throws IOException {
        List<Polygon> loops = DxfParser.parseAllLoops(EncakitAnalysisDemo.class.getResourceAsStream("/fixtures/ENCAKIT.DXF"));

        List<Polygon> outers = new ArrayList<>();
        for (Polygon cand : loops) {
            boolean contained = false;
            Point2D c = GeometryOps.centroid(cand);
            for (Polygon other : loops) {
                if (other == cand) continue;
                if (Math.abs(other.area()) > Math.abs(cand.area()) && other.containsPoint(c)) { contained = true; break; }
            }
            if (!contained) outers.add(cand);
        }

        PieceGeometry grande = null, media = null;
        for (Polygon outer : outers) {
            List<Polygon> holes = new ArrayList<>();
            for (Polygon cand : loops) {
                if (cand == outer) continue;
                if (Math.abs(outer.area()) > Math.abs(cand.area()) && outer.containsPoint(GeometryOps.centroid(cand))) holes.add(cand);
            }
            double area = Math.abs(outer.area());
            if (area > 2000 && grande == null) grande = new PieceGeometry(outer, holes);
            else if (area < 2000 && media == null) media = new PieceGeometry(outer, holes);
        }

        System.out.println("=== Pecas extraidas do kit real (ENCAKIT.DXF) ===");
        System.out.printf("grande: area bruta=%.1f furos=%d%n", Math.abs(grande.getOuter().area()), grande.getHoles().size());
        System.out.printf("media:  area bruta=%.1f furos=%d%n", Math.abs(media.getOuter().area()), media.getHoles().size());

        List<NestedKitAuditor.CanonicalPiece> canonicals = List.of(
                new NestedKitAuditor.CanonicalPiece("grande", grande),
                new NestedKitAuditor.CanonicalPiece("media", media));
        System.out.println("\n=== Como o usuario nesteou as 4 instancias (auditado do DXF real) ===");
        for (NestedKitAuditor.DetectedInstance d : NestedKitAuditor.audit(canonicals, loops)) {
            System.out.printf("%s @ (%.2f,%.2f) rotacao=%.2f mirror=%s%n", d.type.name, d.centroidX, d.centroidY, d.rotationDeg, d.mirrored);
        }

        System.out.println("\n=== O que o motor prefere pra cada peca SOZINHA (sem ver a outra) ===");
        printTop(StrategySelector.select(grande.getOuter(), 2.0), "grande");
        printTop(StrategySelector.select(media.getOuter(), 2.0), "media");

        double sheetW = 600, sheetH = 400, margin = 3, gap = 2;
        System.out.printf("%n=== Motor automatico de ponta a ponta: chapa %.0fx%.0fmm ===%n", sheetW, sheetH);
        SheetPacker.Result rGrande = SheetPacker.pack(grande.getOuter(), grande.getHoles(), sheetW, sheetH, margin, gap);
        double usableArea = (sheetW - 2 * margin) * (sheetH - 2 * margin);
        System.out.printf("Peca grande: %d [%s] %.1f%%%n", rGrande.placements.size(), rGrande.strategyName, 100.0 * rGrande.usedAreaMm2 / usableArea);

        Point2D grandeCentroid = GeometryOps.centroid(grande.getOuter());
        List<Polygon> occupied = new ArrayList<>();
        for (SheetPacker.Placement p : rGrande.placements) {
            occupied.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(grande.getOuter(), grandeCentroid));
        }

        long t0 = System.currentTimeMillis();
        VoidFiller.Result vf = VoidFiller.fillVoids(media.getOuter(), media.getHoles(), occupied, sheetW, sheetH, margin, 10.0);
        long t1 = System.currentTimeMillis();
        System.out.printf("Peca media no vao: %d (%dms)%n", vf.placements.size(), t1 - t0);

        Point2D mediaCentroid = GeometryOps.centroid(media.getOuter());
        List<Polygon> mediaPolys = new ArrayList<>();
        for (SheetPacker.Placement p : vf.placements) {
            mediaPolys.add(new PlacedPieceInstance(p.mirror, p.rotationDeg, p.position).materialize(media.getOuter(), mediaCentroid));
        }
        int colisoes = 0;
        for (Polygon mp : mediaPolys) for (Polygon gp : occupied) if (GeometryOps.overlaps(mp, gp)) colisoes++;
        for (int i = 0; i < mediaPolys.size(); i++) for (int j = i + 1; j < mediaPolys.size(); j++)
            if (GeometryOps.overlaps(mediaPolys.get(i), mediaPolys.get(j))) colisoes++;

        double pctTotal = 100.0 * (rGrande.usedAreaMm2 + vf.usedAreaMm2) / usableArea;
        System.out.printf("Validacao geometria plena: %d colisoes%n", colisoes);
        System.out.printf("Aproveitamento total (grande+media, motor automatico): %.1f%%%n", pctTotal);
    }

    private static void printTop(StrategySelector.Result sel, String label) {
        System.out.println("  peca " + label + ":");
        for (NestingRecipe r : sel.allEvaluated.subList(0, Math.min(3, sel.allEvaluated.size()))) {
            System.out.printf("    [%s] area/peca=%.1f mirror=%b%n", r.strategyName, r.areaPerPieceMm2Gross, r.usesMirror);
        }
    }
}
