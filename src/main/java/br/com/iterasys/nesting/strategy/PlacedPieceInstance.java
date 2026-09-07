package br.com.iterasys.nesting.strategy;

import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma copia da peca dentro de uma celula: espelho (opcional) + rotacao em
 * torno do PROPRIO centroide da peca base + translacao. Convencao de
 * transformacao fixa em todo o motor: espelha primeiro (reflete o eixo Y
 * local em torno do centroide), depois gira, depois translada - a mesma
 * ordem usada em {@code NestedKitAuditor} ao casar instancias de um DXF ja
 * nesteado, para que auditoria e geracao usem a mesma convencao.
 */
public final class PlacedPieceInstance {

    public final boolean mirror;
    public final double rotationDeg;
    public final Point2D translation;

    public PlacedPieceInstance(boolean mirror, double rotationDeg, Point2D translation) {
        this.mirror = mirror;
        this.rotationDeg = rotationDeg;
        this.translation = translation;
    }

    public static PlacedPieceInstance identity() {
        return new PlacedPieceInstance(false, 0.0, new Point2D(0, 0));
    }

    public Polygon materialize(Polygon base, Point2D baseCentroid) {
        double rad = Math.toRadians(rotationDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        List<Point2D> out = new ArrayList<>(base.getVertices().size());
        for (Point2D p : base.getVertices()) {
            double x = p.x - baseCentroid.x;
            double y = p.y - baseCentroid.y;
            if (mirror) y = -y;
            double xr = x * cos - y * sin;
            double yr = x * sin + y * cos;
            out.add(new Point2D(baseCentroid.x + xr + translation.x, baseCentroid.y + yr + translation.y));
        }
        return new Polygon(out);
    }
}
