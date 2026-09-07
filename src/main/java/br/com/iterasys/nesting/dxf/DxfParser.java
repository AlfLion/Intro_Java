package br.com.iterasys.nesting.dxf;

import br.com.iterasys.nesting.geometry.PieceGeometry;
import br.com.iterasys.nesting.geometry.Point2D;
import br.com.iterasys.nesting.geometry.Polygon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser DXF ASCII generico (independente da peca): le a secao ENTITIES e
 * reconstroi o(s) contorno(s) fechado(s) a partir de qualquer combinacao de
 * LINE, ARC, CIRCLE e LWPOLYLINE (com bulge/arco embutido).
 *
 * Entidades soltas (LINE/ARC) sao encadeadas por casamento de extremidades
 * (tolerancia {@link #JOIN_TOLERANCE_MM}) ate fechar um ou mais laços. O
 * maior laço por area vira o contorno externo; os demais viram furos - isso
 * cobre tanto uma peca simples quanto uma peca com cavidades internas.
 *
 * Este parser e a peca central para a ferramenta "entender" qualquer peca
 * nova (nao so a 15746A usada nos primeiros testes) - qualquer DXF 2D com
 * contornos fechados deve poder ser carregado por aqui.
 */
public final class DxfParser {

    private static final double JOIN_TOLERANCE_MM = 1e-3;
    private static final int ARC_DISCRETIZATION_STEPS_PER_DEG = 2;

    private DxfParser() {
    }

    public static PieceGeometry parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    public static PieceGeometry parse(InputStream in) throws IOException {
        return classifyLoops(rawLoops(in));
    }

    /**
     * Le TODOS os lacos fechados de um DXF, sem tentar separar contorno
     * externo de furos - util para auditar um arquivo ja nesteado (varias
     * copias de uma ou mais pecas), onde nao existe "a" peca principal.
     * Quem chama decide como agrupar os lacos em instancias de peca (ver
     * {@code NestedInstanceMatcher}).
     */
    public static List<Polygon> parseAllLoops(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parseAllLoops(in);
        }
    }

    public static List<Polygon> parseAllLoops(InputStream in) throws IOException {
        List<Polygon> out = new ArrayList<>();
        for (List<Point2D> loop : rawLoops(in)) {
            out.add(new Polygon(loop));
        }
        return out;
    }

    private static List<List<Point2D>> rawLoops(InputStream in) throws IOException {
        List<String> tokens = readGroupValues(in);
        List<Edge> edges = extractEdges(tokens);
        return chainIntoLoops(edges);
    }

    // ---- Tokenizacao bruta: pares (codigo, valor) ----

    private static List<String> readGroupValues(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static int codeAt(List<String> lines, int i) {
        return Integer.parseInt(lines.get(i).trim());
    }

    // ---- Extracao de arestas (LINE, ARC, CIRCLE, LWPOLYLINE) ----

    private static final class Edge {
        final Point2D start;
        final Point2D end;
        final List<Point2D> polyline; // pontos intermediarios incluindo start e end, em ordem

        Edge(List<Point2D> polyline) {
            this.polyline = polyline;
            this.start = polyline.get(0);
            this.end = polyline.get(polyline.size() - 1);
        }
    }

    private static List<Edge> extractEdges(List<String> lines) {
        List<Edge> edges = new ArrayList<>();
        int i = findSection(lines, "ENTITIES");
        if (i < 0) {
            return edges;
        }
        while (i < lines.size()) {
            int code = codeAt(lines, i);
            if (code == 0) {
                String value = lines.get(i + 1);
                if (value.equals("ENDSEC")) {
                    break;
                }
                int consumed = readEntity(lines, i, value, edges);
                i += consumed;
            } else {
                i += 2;
            }
        }
        return edges;
    }

    private static int findSection(List<String> lines, String sectionName) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).equals("2") && lines.get(i + 1).equals(sectionName)) {
                return i + 2;
            }
        }
        return -1;
    }

    /** Le uma entidade a partir do par (0, TYPE) em start; retorna quantas linhas consumir. */
    private static int readEntity(List<String> lines, int start, String type, List<Edge> edges) {
        int i = start + 2;
        int end = i;
        while (end < lines.size() && !(codeAt(lines, end) == 0)) {
            end += 2;
        }
        List<int[]> range = null; // marker, unused

        switch (type) {
            case "LINE":
                readLine(lines, i, end, edges);
                break;
            case "ARC":
                readArc(lines, i, end, edges);
                break;
            case "CIRCLE":
                readCircle(lines, i, end, edges);
                break;
            case "LWPOLYLINE":
                readLwPolyline(lines, i, end, edges);
                break;
            default:
                break;
        }
        return end - start;
    }

    private static void readLine(List<String> lines, int i, int end, List<Edge> edges) {
        Double x1 = null, y1 = null, x2 = null, y2 = null;
        for (int p = i; p < end; p += 2) {
            int code = codeAt(lines, p);
            String v = lines.get(p + 1);
            if (code == 10) x1 = Double.parseDouble(v);
            else if (code == 20) y1 = Double.parseDouble(v);
            else if (code == 11) x2 = Double.parseDouble(v);
            else if (code == 21) y2 = Double.parseDouble(v);
        }
        if (x1 != null && y1 != null && x2 != null && y2 != null) {
            edges.add(new Edge(List.of(new Point2D(x1, y1), new Point2D(x2, y2))));
        }
    }

    private static void readArc(List<String> lines, int i, int end, List<Edge> edges) {
        Double cx = null, cy = null, radius = null, startDeg = null, endDeg = null;
        for (int p = i; p < end; p += 2) {
            int code = codeAt(lines, p);
            String v = lines.get(p + 1);
            if (code == 10) cx = Double.parseDouble(v);
            else if (code == 20) cy = Double.parseDouble(v);
            else if (code == 40) radius = Double.parseDouble(v);
            else if (code == 50) startDeg = Double.parseDouble(v);
            else if (code == 51) endDeg = Double.parseDouble(v);
        }
        if (cx == null || cy == null || radius == null || startDeg == null || endDeg == null) {
            return;
        }
        double sweep = endDeg - startDeg;
        if (sweep <= 0) {
            sweep += 360.0;
        }
        edges.add(new Edge(discretizeArc(cx, cy, radius, startDeg, sweep)));
    }

    private static void readCircle(List<String> lines, int i, int end, List<Edge> edges) {
        Double cx = null, cy = null, radius = null;
        for (int p = i; p < end; p += 2) {
            int code = codeAt(lines, p);
            String v = lines.get(p + 1);
            if (code == 10) cx = Double.parseDouble(v);
            else if (code == 20) cy = Double.parseDouble(v);
            else if (code == 40) radius = Double.parseDouble(v);
        }
        if (cx == null || cy == null || radius == null) {
            return;
        }
        List<Point2D> pts = discretizeArc(cx, cy, radius, 0.0, 360.0);
        edges.add(new Edge(pts));
    }

    private static void readLwPolyline(List<String> lines, int i, int end, List<Edge> edges) {
        List<double[]> vertices = new ArrayList<>(); // x, y, bulgeToNext
        double curX = 0, curY = 0, curBulge = 0;
        boolean hasPoint = false;
        boolean closed = false;
        for (int p = i; p < end; p += 2) {
            int code = codeAt(lines, p);
            String v = lines.get(p + 1);
            if (code == 70) {
                int flags = Integer.parseInt(v.trim());
                closed = (flags & 1) != 0;
            } else if (code == 10) {
                if (hasPoint) {
                    vertices.add(new double[]{curX, curY, curBulge});
                }
                curX = Double.parseDouble(v);
                curBulge = 0;
                hasPoint = true;
            } else if (code == 20) {
                curY = Double.parseDouble(v);
            } else if (code == 42) {
                curBulge = Double.parseDouble(v);
            }
        }
        if (hasPoint) {
            vertices.add(new double[]{curX, curY, curBulge});
        }
        if (vertices.size() < 2) {
            return;
        }
        List<Point2D> pts = new ArrayList<>();
        int n = vertices.size();
        int segments = closed ? n : n - 1;
        pts.add(new Point2D(vertices.get(0)[0], vertices.get(0)[1]));
        for (int s = 0; s < segments; s++) {
            double[] a = vertices.get(s % n);
            double[] b = vertices.get((s + 1) % n);
            double bulge = a[2];
            Point2D p0 = new Point2D(a[0], a[1]);
            Point2D p1 = new Point2D(b[0], b[1]);
            if (bulge == 0) {
                pts.add(p1);
            } else {
                pts.addAll(bulgeArcPoints(p0, p1, bulge));
            }
        }
        edges.add(new Edge(pts));
    }

    private static List<Point2D> discretizeArc(double cx, double cy, double radius, double startDeg, double sweepDeg) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(sweepDeg) * ARC_DISCRETIZATION_STEPS_PER_DEG));
        List<Point2D> pts = new ArrayList<>(steps + 1);
        for (int k = 0; k <= steps; k++) {
            double a = Math.toRadians(startDeg + sweepDeg * k / steps);
            pts.add(new Point2D(cx + radius * Math.cos(a), cy + radius * Math.sin(a)));
        }
        return pts;
    }

    /**
     * Converte um segmento com bulge (LWPOLYLINE) num trecho de arco.
     * bulge = tan(theta/4); theta > 0 = sentido anti-horario (esquerda de p0->p1).
     * Formula derivada da relacao sagita/raio padrao (AutoCAD DXF group 42).
     */
    private static List<Point2D> bulgeArcPoints(Point2D p0, Point2D p1, double bulge) {
        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        double d = Math.hypot(dx, dy);
        double theta = 4 * Math.atan(bulge);
        double radius = d / (2 * Math.abs(Math.sin(theta / 2)));
        double signedRadius = radius * Math.signum(bulge);
        double sagitta = (d / 2.0) * bulge;
        double midX = (p0.x + p1.x) / 2.0;
        double midY = (p0.y + p1.y) / 2.0;
        double perpX = -dy / d;
        double perpY = dx / d;
        double offset = sagitta - signedRadius;
        double centerX = midX + perpX * offset;
        double centerY = midY + perpY * offset;
        double angle0 = Math.atan2(p0.y - centerY, p0.x - centerX);

        int steps = Math.max(2, (int) Math.ceil(Math.abs(Math.toDegrees(theta)) * ARC_DISCRETIZATION_STEPS_PER_DEG));
        List<Point2D> pts = new ArrayList<>(steps);
        for (int k = 1; k <= steps; k++) {
            double a = angle0 + theta * k / steps;
            pts.add(new Point2D(centerX + radius * Math.cos(a), centerY + radius * Math.sin(a)));
        }
        return pts;
    }

    // ---- Encadeamento de arestas soltas em lacos fechados ----

    private static List<List<Point2D>> chainIntoLoops(List<Edge> edges) {
        List<Edge> remaining = new ArrayList<>(edges);
        List<List<Point2D>> loops = new ArrayList<>();

        while (!remaining.isEmpty()) {
            Edge first = remaining.remove(0);
            List<Point2D> loop = new ArrayList<>(first.polyline);
            Point2D loopStart = first.start;
            Point2D current = first.end;

            boolean progressed = true;
            while (progressed && !near(current, loopStart)) {
                progressed = false;
                for (int j = 0; j < remaining.size(); j++) {
                    Edge candidate = remaining.get(j);
                    if (near(candidate.start, current)) {
                        appendSkippingFirst(loop, candidate.polyline, false);
                        current = candidate.end;
                        remaining.remove(j);
                        progressed = true;
                        break;
                    } else if (near(candidate.end, current)) {
                        appendSkippingFirst(loop, candidate.polyline, true);
                        current = candidate.start;
                        remaining.remove(j);
                        progressed = true;
                        break;
                    }
                }
            }
            loops.add(loop);
        }
        return loops;
    }

    private static void appendSkippingFirst(List<Point2D> loop, List<Point2D> polyline, boolean reversed) {
        if (reversed) {
            for (int k = polyline.size() - 2; k >= 0; k--) {
                loop.add(polyline.get(k));
            }
        } else {
            for (int k = 1; k < polyline.size(); k++) {
                loop.add(polyline.get(k));
            }
        }
    }

    private static boolean near(Point2D a, Point2D b) {
        return a.distanceTo(b) <= JOIN_TOLERANCE_MM;
    }

    // ---- Classificacao: maior laco = contorno externo, resto = furos ----

    private static PieceGeometry classifyLoops(List<List<Point2D>> loops) {
        if (loops.isEmpty()) {
            throw new IllegalStateException("Nenhum contorno fechado encontrado no DXF");
        }
        Polygon outer = null;
        double bestArea = -1;
        List<Polygon> all = new ArrayList<>();
        for (List<Point2D> loop : loops) {
            Polygon polygon = new Polygon(loop);
            all.add(polygon);
            double area = polygon.area();
            if (area > bestArea) {
                bestArea = area;
                outer = polygon;
            }
        }
        List<Polygon> holes = new ArrayList<>();
        for (Polygon p : all) {
            if (p != outer) {
                holes.add(p);
            }
        }
        return new PieceGeometry(outer, holes);
    }
}
