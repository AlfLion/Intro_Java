package br.com.iterasys.nesting.packing;

import br.com.iterasys.nesting.geometry.GeometryOps;
import br.com.iterasys.nesting.geometry.Polygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grade uniforme simples pra acelerar "esse poligono colide com algum dos
 * ja aceitos?". Sem isso, {@code SheetPacker} testa cada candidato contra
 * TODOS os poligonos ja aceitos (O(n) por candidato, O(n^2) no total) - com
 * centenas de pecas isso ja levava ~15s. A grade limita a busca aos poucos
 * poligonos que caem nas mesmas celulas (ou vizinhas) do candidato.
 *
 * {@link GeometryOps#overlaps} ja faz seu proprio filtro rapido de bounding
 * box antes do teste caro aresta-a-aresta; esta grade e um filtro adicional
 * ANTES disso, pra nem chegar a chamar overlaps() na maioria dos pares que
 * obviamente estao longe um do outro.
 */
final class SpatialIndex {

    private final double cellSize;
    private final Map<Long, List<Polygon>> buckets = new HashMap<>();

    SpatialIndex(double cellSizeMm) {
        this.cellSize = Math.max(cellSizeMm, 1e-6);
    }

    void insert(Polygon p) {
        double[] bb = p.boundingBox();
        int cx0 = cellOf(bb[0]), cx1 = cellOf(bb[2]);
        int cy0 = cellOf(bb[1]), cy1 = cellOf(bb[3]);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cy = cy0; cy <= cy1; cy++) {
                buckets.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(p);
            }
        }
    }

    boolean overlapsAny(Polygon candidate) {
        double[] bb = candidate.boundingBox();
        int cx0 = cellOf(bb[0]), cx1 = cellOf(bb[2]);
        int cy0 = cellOf(bb[1]), cy1 = cellOf(bb[3]);
        Set<Polygon> tested = null;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cy = cy0; cy <= cy1; cy++) {
                List<Polygon> bucket = buckets.get(key(cx, cy));
                if (bucket == null) continue;
                for (Polygon other : bucket) {
                    if (cx0 != cx1 || cy0 != cy1) {
                        if (tested == null) tested = new HashSet<>();
                        if (!tested.add(other)) continue;
                    }
                    if (GeometryOps.overlaps(candidate, other)) return true;
                }
            }
        }
        return false;
    }

    private int cellOf(double v) {
        return (int) Math.floor(v / cellSize);
    }

    private static long key(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }
}
