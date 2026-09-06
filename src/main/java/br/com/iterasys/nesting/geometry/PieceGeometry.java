package br.com.iterasys.nesting.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometria completa de uma peca: contorno externo + eventuais furos/cavidades
 * internas (necessario para o aproveitamento secundario / void filling).
 */
public final class PieceGeometry {

    private final Polygon outer;
    private final List<Polygon> holes;

    public PieceGeometry(Polygon outer, List<Polygon> holes) {
        this.outer = outer;
        this.holes = new ArrayList<>(holes);
    }

    public Polygon getOuter() {
        return outer;
    }

    public List<Polygon> getHoles() {
        return holes;
    }
}
