package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.Point2D;

import java.util.List;

/**
 * Um candidato completo de encaixe: qual gerador propos, a celula (1+
 * copias da peca posicionadas entre si) e os 2 vetores de rede que a
 * repetem pelo plano. {@code areaPerPieceMm2Gross} e a area do envelope da
 * rede dividida pelo numero de copias na celula (bruta - sem descontar
 * furos, que sao os mesmos pra qualquer receita da mesma peca, entao nao
 * mudam qual receita e a melhor).
 */
public final class NestingRecipe {

    public final String strategyName;
    public final List<PlacedPieceInstance> cellInstances;
    public final Point2D latticeV1;
    public final Point2D latticeV2;
    public final double areaPerPieceMm2Gross;
    public final boolean usesMirror;

    public NestingRecipe(String strategyName, List<PlacedPieceInstance> cellInstances,
                          Point2D latticeV1, Point2D latticeV2,
                          double areaPerPieceMm2Gross, boolean usesMirror) {
        this.strategyName = strategyName;
        this.cellInstances = cellInstances;
        this.latticeV1 = latticeV1;
        this.latticeV2 = latticeV2;
        this.areaPerPieceMm2Gross = areaPerPieceMm2Gross;
        this.usesMirror = usesMirror;
    }
}
