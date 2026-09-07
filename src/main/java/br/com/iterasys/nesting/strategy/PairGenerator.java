package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.List;

/**
 * Um mecanismo candidato de encaixe: propoe celulas (1 ou mais copias da
 * peca, ja posicionadas uma em relacao a outra) para o {@link LatticeFinder}
 * avaliar. Cada implementacao encarna UM "jeito de pensar" o encaixe -
 * orientacao unica, interlocking theta/theta+180 em torno do centroide, ou
 * colagem por aresta reta - validados nos exemplos reais desta sessao.
 */
public interface PairGenerator {

    String name();

    List<List<PlacedPieceInstance>> proposeCells(Polygon piece, Point2D centroid, double gapMm);
}
