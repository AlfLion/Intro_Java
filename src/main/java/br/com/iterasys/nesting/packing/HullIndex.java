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
 * Como {@link SpatialIndex}, mas guarda pares (fecho convexo, poligono em
 * resolucao plena) e testa colisao em 2 estagios: primeiro o fecho convexo
 * (poucos vertices, barato) - se os fechos nao se sobrepoem, os poligonos
 * reais tambem nao (o fecho e sempre superconjunto), entao rejeita sem
 * pagar o teste caro. So quando os fechos SE sobrepoem faz o teste
 * aresta-a-aresta de verdade na geometria completa, que e o unico jeito
 * de confirmar (fecho sobrepor nao implica poligono concavo sobrepor).
 *
 * Usado so em {@code SheetPacker#validateFullResolution} - a etapa que
 * antes era o custo dominante do pipeline (~10-14s pra ~650 pecas do
 * bracket) por testar centenas de vertices por par mesmo quando as pecas
 * estavam obviamente longe uma da outra.
 */
final class HullIndex {

    private static final class Entry {
        final Polygon hull;
        final Polygon full;

        Entry(Polygon hull, Polygon full) {
            this.hull = hull;
            this.full = full;
        }
    }

    private final double cellSize;
    private final Map<Long, List<Entry>> buckets = new HashMap<>();

    HullIndex(double cellSizeMm) {
        this.cellSize = Math.max(cellSizeMm, 1e-6);
    }

    void insert(Polygon hull, Polygon full) {
        double[] bb = full.boundingBox();
        int cx0 = cellOf(bb[0]), cx1 = cellOf(bb[2]);
        int cy0 = cellOf(bb[1]), cy1 = cellOf(bb[3]);
        Entry e = new Entry(hull, full);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cy = cy0; cy <= cy1; cy++) {
                buckets.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(e);
            }
        }
    }

    boolean overlapsAny(Polygon candidateHull, Polygon candidateFull) {
        double[] bb = candidateFull.boundingBox();
        int cx0 = cellOf(bb[0]), cx1 = cellOf(bb[2]);
        int cy0 = cellOf(bb[1]), cy1 = cellOf(bb[3]);
        Set<Entry> tested = null;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cy = cy0; cy <= cy1; cy++) {
                List<Entry> bucket = buckets.get(key(cx, cy));
                if (bucket == null) continue;
                for (Entry other : bucket) {
                    if (cx0 != cx1 || cy0 != cy1) {
                        if (tested == null) tested = new HashSet<>();
                        if (!tested.add(other)) continue;
                    }
                    if (!GeometryOps.overlaps(candidateHull, other.hull)) continue;
                    if (GeometryOps.overlaps(candidateFull, other.full)) return true;
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
