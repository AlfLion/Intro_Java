package br.com.iterasys.nesting.geometry;

import java.util.ArrayList;
import java.util.List;

public final class Polygon {

    private final List<Point2D> vertices;

    public Polygon(List<Point2D> vertices) {
        this.vertices = new ArrayList<>(vertices);
    }

    public List<Point2D> getVertices() {
        return vertices;
    }

    public Polygon translated(Point2D vector) {
        List<Point2D> out = new ArrayList<>(vertices.size());
        for (Point2D p : vertices) {
            out.add(p.plus(vector));
        }
        return new Polygon(out);
    }

    public Polygon rotated(double angleDeg, Point2D pivot) {
        List<Point2D> out = new ArrayList<>(vertices.size());
        for (Point2D p : vertices) {
            out.add(p.rotateDeg(angleDeg, pivot));
        }
        return new Polygon(out);
    }

    /** [minX, minY, maxX, maxY] */
    public double[] boundingBox() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Point2D p : vertices) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public double area() {
        double sum = 0;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Point2D a = vertices.get(i);
            Point2D b = vertices.get((i + 1) % n);
            sum += a.x * b.y - b.x * a.y;
        }
        return Math.abs(sum) / 2.0;
    }

    public boolean containsPoint(Point2D p) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point2D vi = vertices.get(i);
            Point2D vj = vertices.get(j);
            boolean intersect = ((vi.y > p.y) != (vj.y > p.y))
                    && (p.x < (vj.x - vi.x) * (p.y - vi.y) / (vj.y - vi.y) + vi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
