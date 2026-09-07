package br.com.iterasys.nesting.engine;

import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.List;

/**
 * Passo de pos-processamento: como o material e sempre uma chapa retangular,
 * depois de achar o encaixe local (angulo relativo entre pecas + vetores de
 * rede), vale girar o CONJUNTO inteiro (a celula-unidade ja montada) como
 * corpo rigido para alinhar a aresta reta mais longa a um eixo da chapa
 * (0/90/180/270 graus). Isso nao desmancha o encaixe - o angulo relativo
 * entre as pecas do grupo continua exatamente o mesmo, so a orientacao
 * absoluta do grupo na chapa muda - e reduz o envelope retangular
 * desperdicado quando a celula e repetida em grade pela chapa.
 *
 * Confirmado empiricamente contra TRAPENC.DXF (exemplo do usuario): as duas
 * pecas do par tem a aresta mais longa (perna de 1001,71mm) a 89,952 graus e
 * -90,048 graus - a menos de 0,05 grau de vertical, batendo com os 3,3 graus
 * que o usuario girou manualmente a peca original (perna a 86,65 graus) pra
 * deixar "o mais reto possivel".
 */
public final class OrientationAligner {

    private OrientationAligner() {
    }

    /**
     * Delta de rotacao (graus) que, aplicado ao poligono, deixa sua aresta
     * reta mais longa o mais proximo possivel de um eixo (0/90/180/270).
     * Como uma aresta e uma reta (nao importa o sentido), so o resto mod 90
     * importa.
     */
    public static double angleToAxisAlignLongestEdge(Polygon p) {
        List<Point2D> v = p.getVertices();
        double maxLen = -1;
        double maxAngDeg = 0;
        int n = v.size();
        for (int i = 0; i < n; i++) {
            Point2D a = v.get(i);
            Point2D b = v.get((i + 1) % n);
            double len = a.distanceTo(b);
            if (len > maxLen) {
                maxLen = len;
                maxAngDeg = Math.toDegrees(Math.atan2(b.y - a.y, b.x - a.x));
            }
        }
        return snapToAxisDelta(maxAngDeg);
    }

    /**
     * Delta (graus) que, somado a {@code angDeg}, deixa o angulo o mais
     * proximo possivel de um eixo (0/90/180/270). Reaproveitado tanto para
     * a aresta mais longa de um poligono quanto para o maior vetor de rede
     * de uma receita de encaixe (ver {@code SheetPacker}).
     */
    public static double snapToAxisDelta(double angDeg) {
        double mod90 = ((angDeg % 90) + 90) % 90;
        return mod90 > 45 ? 90 - mod90 : -mod90;
    }
}
